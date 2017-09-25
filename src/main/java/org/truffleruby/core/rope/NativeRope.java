/*
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.rope;

import org.jcodings.Encoding;
import org.truffleruby.core.FinalizationService;
import org.truffleruby.core.Hashing;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.truffleruby.extra.ffi.Pointer;

public class NativeRope extends Rope {

    private Pointer pointer;

    public NativeRope(FinalizationService finalizationService, byte[] bytes, Encoding encoding, int characterLength, CodeRange codeRange) {
        super(encoding, codeRange, false, bytes.length, characterLength, 1, null);
        pointer = Pointer.malloc(bytes.length + 1);
        pointer.enableAutorelease(finalizationService);
        pointer.writeBytes(0, bytes, 0, bytes.length);
        pointer.writeByte(bytes.length, (byte) 0);
    }

    @Override
    public byte[] getBytes() {
        // Always re-read bytes from the native pointer as they might have changed.
        final byte[] bytes = new byte[byteLength()];
        copyTo(0, bytes, 0);
        return bytes;
    }

    @TruffleBoundary
    public void copyTo(int from, byte[] buffer, int bufferPos) {
        pointer.readBytes(from, buffer, bufferPos, byteLength());
    }

    @Override
    public byte getByteSlow(int index) {
        return get(index);
    }

    @TruffleBoundary
    @Override
    public byte get(int index) {
        assert 0 <= index && index < byteLength();
        return pointer.readByte(index);
    }

    @Override
    public int hashCode() {
        // TODO (pitr-ch 16-May-2017): this forces Rope#hashCode to be non-final, which is bad for performance
        return Long.hashCode(
                Hashing.hash(
                        MURMUR_SEED,
                        RopeOperations.hashForRange(this, 1, 0, byteLength())));
    }

    @Override
    public String toString() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Rope withEncoding(Encoding newEncoding, CodeRange newCodeRange) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canFastEncode(Encoding newEncoding, CodeRange newCodeRange) {
        return false;
    }

    public Pointer getNativePointer() {
        return pointer;
    }

    public LeafRope toLeafRope() {
        return RopeOperations.create(getBytes(), getEncoding(), CodeRange.CR_UNKNOWN);
    }
}
