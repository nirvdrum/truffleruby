/*
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.format.write;

import org.truffleruby.core.format.FormatFrameDescriptor;
import org.truffleruby.language.RubyBaseWithoutContextNode;

import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class OutputNode extends RubyBaseWithoutContextNode {

    public Object execute(VirtualFrame frame) {
        try {
            return frame.getObject(FormatFrameDescriptor.OUTPUT_SLOT);
        } catch (FrameSlotTypeException e) {
            throw new IllegalStateException(e);
        }
    }

}
