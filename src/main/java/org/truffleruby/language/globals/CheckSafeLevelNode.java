/*
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.language.globals;


import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.SnippetNode;

public class CheckSafeLevelNode extends RubyNode {

    @Child private RubyNode rhs;
    @Child private SnippetNode snippetNode;

    public CheckSafeLevelNode(RubyNode rhs) {
        this.rhs = rhs;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object value = rhs.execute(frame);
        if (snippetNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            snippetNode = insert(new SnippetNode());
        }
        return snippetNode.execute(frame, "Rubinius::Type.check_safe_level(object)", "object", value);
    }

}
