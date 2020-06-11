package org.truffleruby.core.array;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.objects.shared.IsSharedNode;
import org.truffleruby.language.objects.shared.WriteBarrierNode;

import static org.truffleruby.Layouts.ARRAY;

/**
 * Copies a portion of an array to another array, whose store is known to have sufficient capacity, and to be
 * compatible with the source array's store.
 *
 * <p>This never checks the array's sizes, which may therefore be adjusted afterwards.
 *
 * <p>Also propagates sharing from the source array to destination array.
 *
 * <p>Typically only called after {@link ArrayPrepareForCopyNode} has been invoked on the destination.</p>
 */
@ImportStatic(ArrayGuards.class)
@ReportPolymorphism
public abstract class ArrayCopyCompatibleRangeNode extends RubyBaseNode {

    public static ArrayCopyCompatibleRangeNode create() {
        return ArrayCopyCompatibleRangeNodeGen.create();
    }

    public abstract void execute(DynamicObject dst, DynamicObject src, int dstStart, int srcStart, int length);

    @Specialization(limit = "storageStrategyLimit()")
    void copy(DynamicObject dst, DynamicObject src, int dstStart, int srcStart, int length,
            @CachedLibrary("getStore(src)") ArrayStoreLibrary stores,
            @Cached IsSharedNode isSharedNode,
            @Cached WriteBarrierNode writeBarrierNode,
            @Cached ConditionProfile share) {

        final Object srcStore = ARRAY.getStore(src);
        stores.copyContents(srcStore, srcStart, ARRAY.getStore(dst), dstStart, length);

        if (share.profile(srcStore instanceof Object[] &&
                isSharedNode.executeIsShared(dst) &&
                !isSharedNode.executeIsShared(src))) {
            for (int i = 0; i < length; ++i) {
                writeBarrierNode.executeWriteBarrier(stores.read(srcStore, i));
            }
        }
    }
}
