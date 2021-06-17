/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.tools.layout;

import java.nio.file.Path;

import org.springframework.boot.loader.PropertiesLauncher;

/**
 * This is a "pre-launcher" to {@link PropertiesLauncher} which is the actual launcher.
 *
 * `PropertiesLauncher` is used because we can specify additional classpath components with it.
 * When started as a WAR it needs to contain also WEB-INFO subdirectories.
 * This launcher sets `loader.path` system property if not specified.
 * If `loader.path` is specified the user/system administrator is fully responsible for it.
 *
 * This also sets `midpoint.home` system property if not set using `MIDPOINT_HOME` environment
 * variable as the first default and then `${user.home}/midpoint`.
 *
 * Finally, this class also provides {@link #stop(String[])} method for Windows service.
 */
public class MidPointWarLauncher {

    private MidPointWarLauncher() {
        throw new AssertionError("Non-instantiable launcher class");
    }

    public static void main(String[] args) throws Exception {
        String midpointHome = System.getProperty("midpoint.home");
        if (midpointHome == null) {
            midpointHome = System.getenv("MIDPOINT_HOME");
            if (midpointHome == null) {
                midpointHome = Path.of(System.getProperty("user.home"), "midpoint").toString();
            }
        }
        midpointHome = Path.of(midpointHome).toAbsolutePath().toString();
        System.setProperty("midpoint.home", midpointHome);
        System.out.println("midPoint home: " + midpointHome);

        // if loader.path is set we will respect it, although it's unlikely
        if (System.getProperty("loader.path") == null) {
            System.setProperty("loader.path",
                    "WEB-INF/classes,WEB-INF/lib,WEB-INF/lib-provided,"
                            + Path.of(midpointHome, "lib").toString());
        }
        System.out.println("Using loader path: " + System.getProperty("loader.path"));

        PropertiesLauncher.main(args);
    }

    /**
     * Used as Windows service stop method.
     */
    @SuppressWarnings("unused")
    public static synchronized void stop(String[] args) {
        System.exit(0);
    }
}
