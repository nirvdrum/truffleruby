/*
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.format.exceptions;

public class NoImplicitConversionException extends FormatException {

    private static final long serialVersionUID = -2509958825294561087L;

    private final Object object;
    private final String target;

    public NoImplicitConversionException(Object object, String target) {
        this.object = object;
        this.target = target;
    }

    public Object getObject() {
        return object;
    }

    public String getTarget() {
        return target;
    }

}
