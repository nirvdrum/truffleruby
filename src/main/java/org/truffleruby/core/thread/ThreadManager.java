/*
 * Copyright (c) 2013, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.thread;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;

import jnr.constants.platform.Errno;
import jnr.constants.platform.Signal;
import jnr.posix.Timeval;
import org.truffleruby.Layouts;
import org.truffleruby.Log;
import org.truffleruby.RubyContext;
import org.truffleruby.core.InterruptMode;
import org.truffleruby.core.fiber.FiberManager;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.SafepointManager;
import org.truffleruby.language.backtrace.BacktraceFormatter;
import org.truffleruby.language.control.ExitException;
import org.truffleruby.language.control.JavaException;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.control.ReturnException;
import org.truffleruby.language.control.KillException;
import org.truffleruby.language.objects.AllocateObjectNode;
import org.truffleruby.language.objects.ObjectIDOperations;
import org.truffleruby.language.objects.ReadObjectFieldNode;
import org.truffleruby.language.objects.shared.SharedObjects;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.platform.TruffleNFIPlatform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

public class ThreadManager {

    private final RubyContext context;

    private final DynamicObject rootThread;
    private final ThreadLocal<DynamicObject> currentThread = new ThreadLocal<>();

    private final Set<DynamicObject> runningRubyThreads
            = Collections.newSetFromMap(new ConcurrentHashMap<DynamicObject, Boolean>());

    private final Map<Thread, UnblockingAction> unblockingActions = new ConcurrentHashMap<>();
    private static final UnblockingAction EMPTY_UNBLOCKING_ACTION = () -> {
    };

    private final ThreadLocal<UnblockingAction> blockingNativeCallUnblockingAction = new ThreadLocal<>();

    public ThreadManager(RubyContext context) {
        this.context = context;
        this.rootThread = createBootThread("main");
    }

    public void initialize() {
        if (context.getOptions().NATIVE_INTERRUPT) {
            setupSignalHandler(context);
        }

        start(rootThread);
    }

    private static final InterruptMode DEFAULT_INTERRUPT_MODE = InterruptMode.IMMEDIATE;
    private static final ThreadStatus DEFAULT_STATUS = ThreadStatus.RUN;

    public DynamicObject createBootThread(String info) {
        final DynamicObject thread = context.getCoreLibrary().getThreadFactory().newInstance(packThreadFields(nil(), info));
        setFiberManager(thread);
        return thread;
    }

    public DynamicObject createThread(DynamicObject rubyClass, AllocateObjectNode allocateObjectNode) {
        final DynamicObject currentGroup = Layouts.THREAD.getThreadGroup(getCurrentThread());
        final DynamicObject thread = allocateObjectNode.allocate(rubyClass,
                packThreadFields(currentGroup, "<uninitialized>"));
        setFiberManager(thread);
        return thread;
    }

    private void setFiberManager(DynamicObject thread) {
        // Because it is cyclic
        Layouts.THREAD.setFiberManagerUnsafe(thread, new FiberManager(context, thread));
    }

    private Object[] packThreadFields(DynamicObject currentGroup, String info) {
        return Layouts.THREAD.build(
                createThreadLocals(),
                DEFAULT_INTERRUPT_MODE,
                DEFAULT_STATUS,
                new ArrayList<>(),
                null,
                new CountDownLatch(1),
                getGlobalAbortOnException(),
                null,
                null,
                null,
                new AtomicBoolean(false),
                Thread.NORM_PRIORITY,
                currentGroup,
                info,
                nil());
    }

    private boolean getGlobalAbortOnException() {
        final DynamicObject threadClass = context.getCoreLibrary().getThreadClass();
        return (boolean) ReadObjectFieldNode.read(threadClass, "@abort_on_exception", null);
    }

    private DynamicObject createThreadLocals() {
        final DynamicObject threadLocals = Layouts.BASIC_OBJECT.createBasicObject(context.getCoreLibrary().getObjectFactory());
        threadLocals.define("$!", nil());
        threadLocals.define("$?", nil());
        threadLocals.define("$SAFE", 0);
        return threadLocals;
    }

    private static void setupSignalHandler(RubyContext context) {
        TruffleNFIPlatform nfi = context.getNativePlatform().getTruffleNFI();
        if (nfi != null) {
            if (!Signal.SIGVTALRM.defined()) {
                throw new UnsupportedOperationException("SIGVTALRM not defined");
            }

            TruffleObject libC = nfi.getDefaultLibrary();
            // We use abs() as a function taking a int and having no side effects

            TruffleObject abs;

            try {
                abs = nfi.lookup(libC, "abs");
            } catch (JavaException e) {
                if (e.getCause() instanceof UnknownIdentifierException) {
                    Log.LOGGER.warning("not able to set up a native signal handler - maybe the NFI was not available");
                    return;
                }

                throw e;
            }

            TruffleObject sigaction = (TruffleObject) nfi.invoke(nfi.lookup(libC, "sigaction"), "bind", "(SINT32,POINTER,POINTER):SINT32");

            // flags = 0 is OK as we want no SA_RESTART so we can interrupt blocking syscalls.
            try (Pointer structSigAction = context.getNativePlatform().createSigAction(nfi.asPointer(abs))) {
                int result = (int) nfi.execute(sigaction, Signal.SIGVTALRM.intValue(), structSigAction.getAddress(), 0L);
                if (result != 0) {
                    throw new UnsupportedOperationException("sigaction() failed: errno=" + context.getNativePlatform().getPosix().errno());
                }
            }
        }
    }

    public void initialize(DynamicObject thread, Node currentNode, String info, Runnable task) {
        assert RubyGuards.isRubyThread(thread);
        final Thread t = context.getLanguage().createThread(context,
                () -> threadMain(thread, currentNode, info, task));
        t.start();
        FiberManager.waitForInitialization(context, Layouts.THREAD.getFiberManager(thread).getRootFiber(), currentNode);
    }

    private void threadMain(DynamicObject thread, Node currentNode, String info, Runnable task) {
        Layouts.THREAD.setSourceLocation(thread, info);
        final String name = "Ruby Thread id=" + Thread.currentThread().getId() + " from " + info;
        Thread.currentThread().setName(name);

        start(thread);
        try {
            task.run();
        // Handlers in the same order as in FiberManager
        } catch (KillException e) {
            setThreadValue(context, thread, nil());
        } catch (ExitException e) {
            rethrowOnMainThread(currentNode, e);
            setThreadValue(context, thread, nil());
        } catch (RaiseException e) {
            setException(context, thread, e.getException(), currentNode);
        } catch (ReturnException e) {
            setException(context, thread, context.getCoreExceptions().unexpectedReturn(currentNode), currentNode);
        } finally {
            cleanup(thread);
            assert Layouts.THREAD.getValue(thread) != null || Layouts.THREAD.getException(thread) != null;
        }
    }

    private void rethrowOnMainThread(Node currentNode, ExitException e) {
        context.getSafepointManager().pauseRubyThreadAndExecute(getRootThread(), currentNode, (rubyThread, actionCurrentNode) -> {
            throw e;
        });
    }

    private static void setThreadValue(RubyContext context, DynamicObject thread, Object value) {
        // A Thread is always shared (Thread.list)
        SharedObjects.propagate(context, thread, value);
        Layouts.THREAD.setValue(thread, value);
    }

    private static void setException(RubyContext context, DynamicObject thread, DynamicObject exception, Node currentNode) {
        // A Thread is always shared (Thread.list)
        SharedObjects.propagate(context, thread, exception);
        final DynamicObject mainThread = context.getThreadManager().getRootThread();
        final boolean isSystemExit = Layouts.BASIC_OBJECT.getLogicalClass(exception) == context.getCoreLibrary().getSystemExitClass();
        if (thread != mainThread && (isSystemExit || Layouts.THREAD.getAbortOnException(thread))) {
            ThreadNodes.ThreadRaisePrimitiveNode.raiseInThread(context, mainThread, exception, currentNode);
        }
        Layouts.THREAD.setException(thread, exception);
    }

    public void start(DynamicObject thread) {
        Layouts.THREAD.setThread(thread, Thread.currentThread());
        registerThread(thread);

        final FiberManager fiberManager = Layouts.THREAD.getFiberManager(thread);
        fiberManager.start(fiberManager.getRootFiber());
    }

    public void cleanup(DynamicObject thread) {
        // First mark as dead for Thread#status
        Layouts.THREAD.setStatus(thread, ThreadStatus.DEAD);

        final FiberManager fiberManager = Layouts.THREAD.getFiberManager(thread);
        fiberManager.shutdown();

        unregisterThread(thread);
        Layouts.THREAD.setThread(thread, null);

        for (Lock lock : Layouts.THREAD.getOwnedLocks(thread)) {
            lock.unlock();
        }
        Layouts.THREAD.getFinishedLatch(thread).countDown();
    }

    public DynamicObject getRootThread() {
        return rootThread;
    }

    public interface BlockingAction<T> {
        boolean SUCCESS = true;

        T block() throws InterruptedException;
    }

    public interface BlockingTimeoutAction<T> {
        T block(Timeval timeoutToUse) throws InterruptedException;
    }

    public interface UnblockingAction {
        void unblock();
    }

    @TruffleBoundary
    public <T> T runUntilResultKeepStatus(Node currentNode, BlockingAction<T> action) {
        T result = null;

        do {
            try {
                result = action.block();
            } catch (InterruptedException e) {
                // We were interrupted, possibly by the SafepointManager.
                context.getSafepointManager().pollFromBlockingCall(currentNode);
            }
        } while (result == null);

        return result;
    }

    /**
     * Runs {@code action} until it returns a non-null value.
     * The given action should throw an {@link InterruptedException} when {@link Thread#interrupt()} is called.
     * Otherwise, the {@link SafepointManager} will not be able to interrupt this action.
     * See {@link ThreadManager#runBlockingSystemCallUntilResult(Node, BlockingAction)} for blocking native calls.
     * If the action throws an {@link InterruptedException},
     * it will be retried until it returns a non-null value.
     *
     * @param action must not touch any Ruby state
     * @return the first non-null return value from {@code action}
     */
    @TruffleBoundary
    public <T> T runUntilResult(Node currentNode, BlockingAction<T> action) {
        final DynamicObject runningThread = getCurrentThread();
        T result = null;

        do {
            final ThreadStatus status = Layouts.THREAD.getStatus(runningThread);
            Layouts.THREAD.setStatus(runningThread, ThreadStatus.SLEEP);

            try {
                try {
                    result = action.block();
                } finally {
                    Layouts.THREAD.setStatus(runningThread, status);
                }
            } catch (InterruptedException e) {
                // We were interrupted, possibly by the SafepointManager.
                context.getSafepointManager().pollFromBlockingCall(currentNode);
            }
        } while (result == null);

        return result;
    }

    /**
     * Runs {@code action} until it returns a non-null value. The blocking action might be
     * {@link Thread#interrupted()}, for instance by the {@link SafepointManager}, in which case it
     * will be run again. The unblocking action is registered with the thread manager and will be
     * invoked if the {@link SafepointManager} needs to interrupt the thread. If the blocking action
     * is making a native call, simply interrupting the thread will not unblock the action. It is
     * the responsibility of the unblocking action to break out of the native call so the thread can
     * be interrupted.
     *
     * @param blockingAction must not touch any Ruby state
     * @param unblockingAction must not touch any Ruby state
     * @return the first non-null return value from {@code action}
     */
    @TruffleBoundary
    public <T> T runUntilResult(Node currentNode, BlockingAction<T> blockingAction, UnblockingAction unblockingAction) {
        assert unblockingAction != null;
        final Thread thread = Thread.currentThread();

        final UnblockingAction oldUnblockingAction = unblockingActions.put(thread, unblockingAction);
        try {
            return runUntilResult(currentNode, blockingAction);
        } finally {
            unblockingActions.put(thread, oldUnblockingAction);
        }
    }

    /**
     * Similar to {@link ThreadManager#runUntilResult(Node, BlockingAction)} but purposed for
     * blocking native calls. If the {@link SafepointManager} needs to interrupt the thread, it will
     * send a SIGVTALRM to abort the blocking syscall which will return with a value < 0 and
     * errno=EINTR.
     */
    @TruffleBoundary
    public int runBlockingSystemCallUntilResult(Node currentNode, BlockingAction<Integer> action) {
        assert Errno.EINTR.defined();
        int EINTR = Errno.EINTR.intValue();

        return runUntilResult(currentNode, () -> {
            int result = action.block();
            if (result < 0 && context.getNativePlatform().getPosix().errno() == EINTR) {
                throw new InterruptedException("EINTR");
            }
            return result;
        }, blockingNativeCallUnblockingAction.get());
    }

    public void initializeValuesBasedOnCurrentJavaThread(DynamicObject rubyThread, long pThreadID) {
        assert RubyGuards.isRubyThread(rubyThread);
        currentThread.set(rubyThread);

        final int SIGVTALRM = jnr.constants.platform.Signal.SIGVTALRM.intValue();

        blockingNativeCallUnblockingAction.set(() -> {
            context.getNativePlatform().getThreads().pthread_kill(pThreadID, SIGVTALRM);
        });

        unblockingActions.put(Thread.currentThread(), EMPTY_UNBLOCKING_ACTION);
    }

    public void cleanupValuesBasedOnCurrentJavaThread() {
        unblockingActions.remove(Thread.currentThread());
    }

    @TruffleBoundary
    public DynamicObject getCurrentThread() {
        return currentThread.get();
    }

    public synchronized void registerThread(DynamicObject thread) {
        assert RubyGuards.isRubyThread(thread);
        runningRubyThreads.add(thread);

        if (context.getOptions().SHARED_OBJECTS_ENABLED && runningRubyThreads.size() > 1) {
            context.getSharedObjects().startSharing();
            SharedObjects.writeBarrier(context, thread);
        }
    }

    public synchronized void unregisterThread(DynamicObject thread) {
        assert RubyGuards.isRubyThread(thread);
        runningRubyThreads.remove(thread);
        currentThread.set(null);
    }

    @TruffleBoundary
    public void shutdown() {
        if (getCurrentThread() != rootThread) {
            throw new UnsupportedOperationException("ThreadManager.shutdown() must be called on the root Ruby Thread");
        }

        final FiberManager fiberManager = Layouts.THREAD.getFiberManager(rootThread);

        if (fiberManager.getRubyFiberFromCurrentJavaThread() != fiberManager.getRootFiber()) {
            throw new UnsupportedOperationException("ThreadManager.shutdown() must be called on the root Fiber of the main Thread");
        }

        try {
            if (runningRubyThreads.size() > 1) {
                killOtherThreads();
            }
        } finally {
            cleanup(rootThread);
        }
    }

    /**
     * Kill all Ruby threads, except the currently executing Thread. Waits that the killed threads
     * have finished their cleanup and killed their fibers.
     */
    @TruffleBoundary
    private void killOtherThreads() {
        final Thread initiatingJavaThread = Thread.currentThread();
        final List<CountDownLatch> threadsToWait = Collections.synchronizedList(new ArrayList<>());

        while (true) {
            try {
                context.getSafepointManager().pauseAllThreadsAndExecute(null, false, (thread, currentNode) -> {
                    if (Thread.currentThread() != initiatingJavaThread) {
                        final FiberManager fiberManager = Layouts.THREAD.getFiberManager(thread);
                        final DynamicObject fiber = fiberManager.getRubyFiberFromCurrentJavaThread();

                        if (fiberManager.getCurrentFiber() == fiber) {
                            threadsToWait.add(Layouts.THREAD.getFinishedLatch(thread));
                            Layouts.THREAD.setStatus(thread, ThreadStatus.ABORTING);
                            throw new KillException();
                        }
                    }
                });
                break; // Successfully executed the safepoint and sent the exceptions.
            } catch (RaiseException e) {
                final DynamicObject rubyException = e.getException();
                BacktraceFormatter.createDefaultFormatter(context).printBacktrace(context, rubyException, Layouts.EXCEPTION.getBacktrace(rubyException));
            }
        }

        for (CountDownLatch finishedLatch : threadsToWait) {
            runUntilResultKeepStatus(null, () -> {
                finishedLatch.await();
                return BlockingAction.SUCCESS;
            });
        }
    }

    @TruffleBoundary
    public Object[] getThreadList() {
        return runningRubyThreads.toArray(new Object[runningRubyThreads.size()]);
    }

    @TruffleBoundary
    public Iterable<DynamicObject> iterateThreads() {
        return runningRubyThreads;
    }

    @TruffleBoundary
    public void interrupt(Thread thread) {
        final UnblockingAction action = unblockingActions.get(thread);

        if (action != null) {
            action.unblock();
        }

        thread.interrupt();
    }

    public String getThreadDebugInfo() {
        final StringBuilder builder = new StringBuilder();

        for (DynamicObject thread : runningRubyThreads) {
            builder.append("thread @");
            builder.append(ObjectIDOperations.verySlowGetObjectID(context, thread));

            if (thread == rootThread) {
                builder.append(" (root)");
            }

            if (thread == getCurrentThread()) {
                builder.append(" (current)");
            }

            builder.append("\n");

            final FiberManager fiberManager = Layouts.THREAD.getFiberManager(thread);
            builder.append(fiberManager.getFiberDebugInfo());
        }

        if (builder.length() == 0) {
            return "no ruby threads\n";
        } else {
            return builder.toString();
        }
    }

    private DynamicObject nil() {
        return context.getCoreLibrary().getNil();
    }

}
