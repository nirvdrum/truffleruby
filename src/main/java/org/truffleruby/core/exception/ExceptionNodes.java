/*
 * Copyright (c) 2013, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.exception;

import org.truffleruby.Layouts;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.CoreModule;
import org.truffleruby.builtins.NonStandard;
import org.truffleruby.builtins.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.builtins.PrimitiveNode;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.Visibility;
import org.truffleruby.language.backtrace.Backtrace;
import org.truffleruby.language.backtrace.BacktraceFormatter;
import org.truffleruby.language.methods.LookupMethodNode;
import org.truffleruby.language.objects.AllocateObjectNode;
import org.truffleruby.language.objects.ReadObjectFieldNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreModule(value = "Exception", isClass = true)
public abstract class ExceptionNodes {

    protected final static String CUSTOM_BACKTRACE_FIELD = "@custom_backtrace";

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Child private AllocateObjectNode allocateObjectNode = AllocateObjectNode.create();

        @Specialization
        protected DynamicObject allocateException(DynamicObject rubyClass) {
            return allocateObjectNode
                    .allocate(rubyClass, Layouts.EXCEPTION.build(nil(), null, null, nil(), null, null));
        }

    }

    @CoreMethod(names = "initialize", optional = 1)
    public abstract static class InitializeNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected DynamicObject initialize(DynamicObject exception, NotProvided message) {
            Layouts.EXCEPTION.setMessage(exception, nil());
            return exception;
        }

        @Specialization(guards = "wasProvided(message)")
        protected DynamicObject initialize(DynamicObject exception, Object message) {
            Layouts.EXCEPTION.setMessage(exception, message);
            return exception;
        }

    }

    @CoreMethod(names = "initialize_copy", required = 1)
    public abstract static class InitializeCopyNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "self == from")
        protected Object initializeCopySelfIsSameAsFrom(DynamicObject self, DynamicObject from) {
            return self;
        }

        @Specialization(
                guards = { "self != from", "isRubyException(from)", "!isNameError(from)", "!isSystemCallError(from)" })
        protected Object initializeCopy(DynamicObject self, DynamicObject from) {
            initializeExceptionCopy(self, from);
            return self;
        }

        @Specialization(guards = { "self != from", "isSystemCallError(from)" })
        protected Object initializeSystemCallErrorCopy(DynamicObject self, DynamicObject from) {
            initializeExceptionCopy(self, from);
            Layouts.SYSTEM_CALL_ERROR.setErrno(self, Layouts.SYSTEM_CALL_ERROR.getErrno(from));
            return self;
        }

        @Specialization(guards = { "self != from", "isNoMethodError(from)" })
        protected Object initializeCopyNoMethodError(DynamicObject self, DynamicObject from) {
            initializeExceptionCopy(self, from);
            initializeNameErrorCopy(self, from);
            Layouts.NO_METHOD_ERROR.setArgs(self, Layouts.NO_METHOD_ERROR.getArgs(from));
            return self;
        }

        @Specialization(
                guards = { "self != from", "isNameError(from)", "!isNoMethodError(from)" })
        protected Object initializeCopyNameError(DynamicObject self, DynamicObject from) {
            initializeExceptionCopy(self, from);
            initializeNameErrorCopy(self, from);
            return self;
        }

        protected boolean isNameError(DynamicObject object) {
            return Layouts.NAME_ERROR.isNameError(object);
        }

        protected boolean isNoMethodError(DynamicObject object) {
            return Layouts.NO_METHOD_ERROR.isNoMethodError(object);
        }

        protected boolean isSystemCallError(DynamicObject object) {
            return Layouts.SYSTEM_CALL_ERROR.isSystemCallError(object);
        }

        private void initializeNameErrorCopy(DynamicObject self, DynamicObject from) {
            Layouts.NAME_ERROR.setName(self, Layouts.NAME_ERROR.getName(from));
            Layouts.NAME_ERROR.setReceiver(self, Layouts.NAME_ERROR.getReceiver(from));
        }

        private void initializeExceptionCopy(DynamicObject self, DynamicObject from) {
            Backtrace backtrace = Layouts.EXCEPTION.getBacktrace(from);
            if (backtrace != null) {
                Layouts.EXCEPTION.setBacktrace(self, backtrace.copy(getContext(), self));
            } else {
                Layouts.EXCEPTION.setBacktrace(self, backtrace);
            }
            Layouts.EXCEPTION.setFormatter(self, Layouts.EXCEPTION.getFormatter(from));
            Layouts.EXCEPTION.setMessage(self, Layouts.EXCEPTION.getMessage(from));
            Layouts.EXCEPTION.setCause(self, Layouts.EXCEPTION.getCause(from));
            Layouts.EXCEPTION.setBacktraceStringArray(self, Layouts.EXCEPTION.getBacktraceStringArray(from));
            Layouts.EXCEPTION.setBacktraceLocations(self, Layouts.EXCEPTION.getBacktraceLocations(from));
        }

    }

    @CoreMethod(names = "backtrace")
    public abstract static class BacktraceNode extends CoreMethodArrayArgumentsNode {

        @Child private ReadObjectFieldNode readCustomBacktraceNode;

        @Specialization
        protected Object backtrace(
                DynamicObject exception,
                @Cached("createBinaryProfile()") ConditionProfile hasCustomBacktraceProfile,
                @Cached("createBinaryProfile()") ConditionProfile hasBacktraceProfile) {
            final Object customBacktrace = getReadCustomBacktraceNode()
                    .execute(exception, CUSTOM_BACKTRACE_FIELD, null);

            if (hasCustomBacktraceProfile.profile(customBacktrace != null)) {
                return customBacktrace;
            } else if (hasBacktraceProfile.profile(Layouts.EXCEPTION.getBacktrace(exception) != null)) {
                DynamicObject backtraceStringArray = Layouts.EXCEPTION.getBacktraceStringArray(exception);
                if (backtraceStringArray == null) {
                    backtraceStringArray = getContext().getUserBacktraceFormatter().formatBacktraceAsRubyStringArray(
                            exception,
                            Layouts.EXCEPTION.getBacktrace(exception));
                    Layouts.EXCEPTION.setBacktraceStringArray(exception, backtraceStringArray);
                }
                return backtraceStringArray;
            } else {
                return nil();
            }
        }

        private ReadObjectFieldNode getReadCustomBacktraceNode() {
            if (readCustomBacktraceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readCustomBacktraceNode = insert(ReadObjectFieldNode.create());
            }

            return readCustomBacktraceNode;
        }

    }

    @CoreMethod(names = "backtrace_locations")
    public abstract static class BacktraceLocationsNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected Object backtraceLocations(DynamicObject exception,
                @Cached("createBinaryProfile()") ConditionProfile hasBacktraceProfile,
                @Cached("createBinaryProfile()") ConditionProfile hasLocationsProfile) {
            if (hasBacktraceProfile.profile(Layouts.EXCEPTION.getBacktrace(exception) != null)) {
                Object backtraceLocations = Layouts.EXCEPTION.getBacktraceLocations(exception);
                if (hasLocationsProfile.profile(backtraceLocations == null)) {
                    Backtrace backtrace = Layouts.EXCEPTION.getBacktrace(exception);
                    backtraceLocations = backtrace.getBacktraceLocations(GetBacktraceException.UNLIMITED, null);
                    Layouts.EXCEPTION.setBacktraceLocations(exception, backtraceLocations);
                }
                return backtraceLocations;
            } else {
                return nil();
            }
        }
    }

    @Primitive(name = "exception_backtrace?")
    public abstract static class BacktraceQueryPrimitiveNode extends PrimitiveArrayArgumentsNode {

        protected static final String METHOD = "backtrace";

        @Child private ReadObjectFieldNode readCustomBacktraceNode;

        /* We can cheaply determine if an Exception has a backtrace via object inspection. However, if
         * `Exception#backtrace` is redefined, then `Exception#backtrace?` needs to follow along to be consistent. So,
         * we check if the method has been redefined here and if so, fall back to the Ruby code for the method by
         * returning `FAILURE` in the fallback specialization. */
        @Specialization(
                guards = {
                        "lookupNode.lookup(frame, exception, METHOD) == getContext().getCoreMethods().EXCEPTION_BACKTRACE", },
                limit = "1")
        protected boolean backtraceQuery(VirtualFrame frame, DynamicObject exception,
                @Cached LookupMethodNode lookupNode) {
            final Object customBacktrace = readCustomBacktrace(exception);

            return !(customBacktrace == null && Layouts.EXCEPTION.getBacktrace(exception) == null);
        }

        @Specialization
        protected Object fallback(DynamicObject exception) {
            return FAILURE;
        }

        private Object readCustomBacktrace(DynamicObject exception) {
            if (readCustomBacktraceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readCustomBacktraceNode = insert(ReadObjectFieldNode.create());
            }

            return readCustomBacktraceNode.execute(exception, CUSTOM_BACKTRACE_FIELD, null);
        }

    }

    @NonStandard
    @CoreMethod(names = "capture_backtrace!", optional = 1, lowerFixnum = 1)
    public abstract static class CaptureBacktraceNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected Object captureBacktrace(DynamicObject exception, NotProvided offset) {
            return captureBacktrace(exception, 1);
        }

        @Specialization
        protected Object captureBacktrace(DynamicObject exception, int offset) {
            final Backtrace backtrace = getContext().getCallStack().getBacktrace(this, offset);
            Layouts.EXCEPTION.setBacktrace(exception, backtrace);
            return nil();
        }

    }

    @Primitive(name = "exception_message")
    public abstract static class MessagePrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected Object message(DynamicObject exception) {
            final Object message = Layouts.EXCEPTION.getMessage(exception);
            if (message == null) {
                return nil();
            } else {
                return message;
            }
        }

    }

    @Primitive(name = "exception_set_message")
    public abstract static class MessageSetNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected Object setMessage(DynamicObject error, Object message) {
            Layouts.EXCEPTION.setMessage(error, message);
            return error;
        }

    }

    @Primitive(name = "exception_formatter")
    public abstract static class FormatterPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected Object formatter(DynamicObject exception) {
            final DynamicObject formatter = Layouts.EXCEPTION.getFormatter(exception);
            if (formatter == null) {
                return nil();
            } else {
                return formatter;
            }
        }

    }

    @CoreMethod(names = "cause")
    public abstract static class CauseNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected Object cause(DynamicObject exception) {
            return Layouts.EXCEPTION.getCause(exception);
        }

    }

    @Primitive(name = "exception_set_cause")
    public abstract static class ExceptionSetCauseNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected DynamicObject setCause(DynamicObject exception, DynamicObject cause) {
            Layouts.EXCEPTION.setCause(exception, cause);
            return exception;
        }

    }

    @Primitive(name = "exception_errno_error", lowerFixnum = 1)
    public static abstract class ExceptionErrnoErrorPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Child ErrnoErrorNode errnoErrorNode = ErrnoErrorNode.create();

        @Specialization
        protected DynamicObject exceptionErrnoError(DynamicObject message, int errno) {
            return errnoErrorNode.execute(errno, message, null);
        }

    }

    @Primitive(name = "java_breakpoint")
    @SuppressWarnings("unused")
    public static abstract class Breakpoint extends PrimitiveNode {

        @TruffleBoundary
        @Specialization
        protected boolean breakpoint() {
            // have a Ruby backtrace at hand
            String printableRubyBacktrace = BacktraceFormatter.printableRubyBacktrace(getContext(), this);
            return true; // place to put a Java breakpoint
        }


    }

}
