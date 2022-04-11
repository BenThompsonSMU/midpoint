/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.test;

import java.io.File;
import java.io.IOException;

import com.evolveum.midpoint.test.asserter.TaskAsserter;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.annotation.Experimental;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskType;

/**
 * Task that is to be used in tests.
 *
 * Currently supporting only plain tasks, i.e. not task trees.
 */
@Experimental
public class TestTask extends TestResource<TaskType> {

    private static final long DEFAULT_TIMEOUT = 250_000;

    /** Default timeout when waiting for this task completion. */
    private final long defaultTimeout;

    /** Temporary: this is how we access the necessary functionality. */
    private AbstractIntegrationTest test;

    public TestTask(@NotNull File dir, @NotNull String fileName, @NotNull String oid, long defaultTimeout) {
        super(dir, fileName, oid);
        this.defaultTimeout = defaultTimeout;
    }

    public TestTask(@NotNull File dir, @NotNull String fileName, @NotNull String oid) {
        this(dir, fileName, oid, DEFAULT_TIMEOUT);
    }

    /**
     * Initializes the task - i.e. imports it into repository (via model).
     * This may or may not start the task, depending on the execution state in the file.
     *
     * @param test To provide access to necessary functionality. Temporary!
     */
    public void initialize(AbstractIntegrationTest test, Task task, OperationResult result)
            throws IOException, CommonException {
        this.test = test;
        importObject(task, result);
    }

    /**
     * Starts the task and waits for the completion.
     *
     * TODO better name
     */
    public void rerun(OperationResult result) throws CommonException {
        long startTime = System.currentTimeMillis();
        test.restartTask(oid, result);
        test.waitForTaskFinish(oid, true, startTime, defaultTimeout, false);
    }

    public void rerunErrorsOk(OperationResult result) throws CommonException {
        long startTime = System.currentTimeMillis();
        test.restartTask(oid, result);
        test.waitForTaskFinish(oid, true, startTime, defaultTimeout, true);
    }

    public TaskAsserter<Void> assertAfter() throws SchemaException, ObjectNotFoundException {
        return test.assertTask(oid, "after")
                .display();
    }
}
