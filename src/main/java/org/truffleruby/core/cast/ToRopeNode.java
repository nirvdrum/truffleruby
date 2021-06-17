/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */

package org.truffleruby.core.cast;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.core.rope.Rope;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

@NodeChild(value = "child", type = RubyNode.class)
public abstract class ToRopeNode extends RubyContextSourceNode {

    public abstract Rope executeToRope(Object object);

    public static ToRopeNode create() {
        return ToRopeNodeGen.create(null);
    }

    @Specialization
    protected Rope coerceRubyString(RubyString string) {
        return string.rope;
    }

    @Specialization
    protected Rope coerceImmutableRubyString(ImmutableRubyString string) {
        return string.rope;
    }

    @Specialization
    protected Rope coerceSymbol(RubySymbol symbol) {
        return symbol.getRope();
    }
}
