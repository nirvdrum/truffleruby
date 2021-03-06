/*
 * Copyright (c) 2015, 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.core.rubinius;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectFactory;
import com.oracle.truffle.api.object.dsl.Layout;
import org.truffleruby.core.basicobject.BasicObjectLayout;

@Layout
public interface IOBufferLayout extends BasicObjectLayout {

    String STORAGE_IDENTIFIER = "@storage";
    String USED_IDENTIFIER = "@used";
    String TOTAL_IDENTIFIER = "@total";

    DynamicObjectFactory createIOBufferShape(DynamicObject logicalClass,
                                             DynamicObject metaClass);

    DynamicObject createIOBuffer(DynamicObjectFactory factory,
                                 DynamicObject storage,
                                 int used,
                                 int total);

    DynamicObject getStorage(DynamicObject object);

    int getUsed(DynamicObject object);
    void setUsed(DynamicObject object, int value);

    int getTotal(DynamicObject object);

}
