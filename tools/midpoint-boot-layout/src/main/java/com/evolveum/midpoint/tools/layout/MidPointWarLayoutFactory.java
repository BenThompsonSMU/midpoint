/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.tools.layout;

import java.io.File;

import org.springframework.boot.loader.tools.Layout;
import org.springframework.boot.loader.tools.LayoutFactory;

// Used in POM repackaging the application.
@SuppressWarnings("unused")
public class MidPointWarLayoutFactory implements LayoutFactory {

    @Override
    public Layout getLayout(File source) {
        return new MidPointWarLayout();
    }
}
