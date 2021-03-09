/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.task.quartzimpl.run;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.repo.api.PreconditionViolationException;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.*;
import com.evolveum.midpoint.task.quartzimpl.*;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.quartz.*;
import org.springframework.security.core.Authentication;

import java.util.concurrent.Future;

import static com.evolveum.midpoint.task.quartzimpl.run.GroupLimitsChecker.*;
import static com.evolveum.midpoint.task.quartzimpl.run.StopJobException.Severity.*;
import static com.evolveum.midpoint.util.MiscUtil.stateCheck;

/**
 * Executes a Quartz job i.e. midPoint task.
 */
@DisallowConcurrentExecution
public class JobExecutor implements InterruptableJob {

    private static final Trace LOGGER = TraceManager.getTrace(JobExecutor.class);

    private static final String DOT_CLASS = JobExecutor.class.getName() + ".";
    public static final String OP_EXECUTE = DOT_CLASS + "execute";

    /**
     * JobExecutor is instantiated at each execution of the task, so we can store
     * the task here.
     *
     * http://quartz-scheduler.org/documentation/quartz-2.1.x/tutorials/tutorial-lesson-03
     * "Each (and every) time the scheduler executes the job, it creates a new instance of
     * the class before calling its execute(..) method."
     */
    private volatile RunningTaskQuartzImpl task;

    /** Quartz execution context. To be used from the handling thread only. */
    private JobExecutionContext context;

    /**
     * This is a result used for task run preparation. It is written into the task on selected occasions.
     * See {@link #closeFlawedTaskRecordingResult(OperationResult)}}.
     */
    private OperationResult executionResult;

    /** Useful Spring beans. */
    private static TaskBeans beans;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            this.context = context;
            this.executionResult = new OperationResult(OP_EXECUTE);
            executeInternal(executionResult);
        } catch (StopJobException e) {
            e.log(LOGGER);
            if (e.getCause() != null) {
                throw new JobExecutionException(e.getMessage(), e.getCause());
            }
        } catch (Exception e) {
            LoggingUtils.logUnexpectedException(LOGGER, "Unexpected exception occurred during task execution", e);
            // We do not need to propagate this.
        } catch (Throwable t) {
            LoggingUtils.logUnexpectedException(LOGGER, "Unexpected exception occurred during task execution", t);
            throw t;
        }
    }

    public void executeInternal(OperationResult result)
            throws StopJobException, SchemaException, ObjectNotFoundException, ObjectAlreadyExistsException {

        stateCheck(beans != null, "Task manager beans are not correctly set");

        String oid = context.getJobDetail().getKey().getName();
        LOGGER.trace("Starting execution of task {}", oid);

        fetchTheTask(oid, result);

        checkTaskReady();
        fixTaskExecutionInformation(result);
        checkLocalSchedulerRunning(result);

        boolean isRecovering = applyThreadStopActionForRecoveringJobs(result);

        checkGroupLimits(result);

        boolean nodeAndStateSet = false;
        boolean taskRegistered = false;
        TaskHandler handler = null;
        try {
            checkForConcurrentExecution(result);

            setExecutionNodeAndState(result);
            nodeAndStateSet = true;

            task.setExecutingThread(Thread.currentThread());
            beans.localNodeState.registerRunningTask(task);
            taskRegistered = true;

            setupThreadLocals();

            handler = getHandler(result);

            LOGGER.debug("Task thread run STARTING: {}, handler = {}, isRecovering = {}", task, handler, isRecovering);
            beans.listenerRegistry.notifyTaskThreadStart(task, isRecovering, result);

            setupSecurityContext(result);

            executeHandler(handler, result);

        } finally {

            unsetSecurityContext();

            LOGGER.debug("Task thread run FINISHED: {}, handler = {}", task, handler);
            beans.listenerRegistry.notifyTaskThreadFinish(task, result);

            unsetThreadLocals();

            task.setExecutingThread(null);
            if (taskRegistered) {
                beans.localNodeState.unregisterRunningTask(task);
            }

            if (nodeAndStateSet) {
                resetTaskExecutionNodeAndState(result);
            }

            if (!task.canRun()) {
                processTaskStop(result);
            }

            // this is only a safety net; because we've waited for children just after executing a handler
            waitForTransientChildrenAndCloseThem(result);
        }
    }

    private void executeHandler(TaskHandler handler, OperationResult result) throws StopJobException {
        TaskCycleExecutor taskCycleExecutor = new TaskCycleExecutor(task, handler, this, beans);
        taskCycleExecutor.execute(result);
    }

    private void setupSecurityContext(OperationResult result) throws StopJobException {
        PrismObject<? extends FocusType> taskOwner = task.getOwner(result);
        try {
            // just to be sure we won't run the owner-setting login with any garbage security context (see MID-4160)
            beans.securityContextManager.setupPreAuthenticatedSecurityContext((Authentication) null);
            beans.securityContextManager.setupPreAuthenticatedSecurityContext(taskOwner);
        } catch (Throwable t) {
            throw new StopJobException(UNEXPECTED_ERROR, "Couldn't set security context for task %s", t, task);
        }
    }

    private void unsetSecurityContext() {
        beans.securityContextManager.setupPreAuthenticatedSecurityContext((Authentication) null);
    }

    private void setupThreadLocals() {
        beans.cacheConfigurationManager.setThreadLocalProfiles(task.getCachingProfiles());
        OperationResult.setThreadLocalHandlingStrategy(task.getOperationResultHandlingStrategyName());
    }

    private void unsetThreadLocals() {
        beans.cacheConfigurationManager.unsetThreadLocalProfiles();
        OperationResult.setThreadLocalHandlingStrategy(null);
    }

    private TaskHandler getHandler(OperationResult result) throws StopJobException {
        TaskHandler handler = beans.handlerRegistry.getHandler(task.getHandlerUri());
        if (handler != null) {
            return handler;
        }

        LOGGER.error("No handler for URI '{}', task {} - closing it.", task.getHandlerUri(), task);
        closeFlawedTaskRecordingResult(result);
        throw new StopJobException();
    }

    /**
     * In case of leftover tasks, let us fix the execution state and node information.
     */
    private void fixTaskExecutionInformation(OperationResult result) throws ObjectAlreadyExistsException, ObjectNotFoundException,
            SchemaException {
        assert task.getSchedulingState() == TaskSchedulingStateType.READY;

        if (context.isRecovering()) {
            LOGGER.info("Task {} is recovering", task);
        }

        if (task.getNode() != null) {
            LOGGER.info("Clearing executing node information (was: {}) for {}", task.getNode(), task);
            task.setNode(null);
        }
        if (task.getExecutionState() != TaskExecutionStateType.RUNNABLE) {
            LOGGER.info("Fixing execution state from {} to RUNNABLE for {}", task.getExecutionState(), task);
            task.setExecutionState(TaskExecutionStateType.RUNNABLE);
        }
        task.flushPendingModifications(result);
        // Not handling any exceptions here. If we cannot write such simple
        // information into the task, something is seriously broken.
    }

    /**
     * Returns whether the job is recovering.
     */
    private boolean applyThreadStopActionForRecoveringJobs(OperationResult result) throws StopJobException {
        if (!context.isRecovering()) {
            return false;
        }

        if (task.getThreadStopAction() == ThreadStopActionType.CLOSE) {
            LOGGER.info("Closing recovered non-resilient task {}", task);
            closeTask(task, result);
            throw new StopJobException();
        } else if (task.getThreadStopAction() == ThreadStopActionType.SUSPEND) {
            LOGGER.info("Suspending recovered non-resilient task {}", task);
            // Using DO_NOT_STOP because we are the task that is to be suspended
            beans.taskStateManager.suspendTaskNoException(task, TaskManager.DO_NOT_STOP, result);
            throw new StopJobException();
        } else if (task.getThreadStopAction() == null || task.getThreadStopAction() == ThreadStopActionType.RESTART) {
            LOGGER.info("Recovering resilient task {}", task);
            return true;
        } else if (task.getThreadStopAction() == ThreadStopActionType.RESCHEDULE) {
            if (task.isRecurring() && task.isLooselyBound()) {
                LOGGER.info("Recovering resilient task with RESCHEDULE thread stop action - exiting the execution, "
                        + "the task will be rescheduled; task = {}", task);
                throw new StopJobException();
            } else {
                LOGGER.info("Recovering resilient task {}", task);
                return true;
            }
        } else {
            throw new SystemException("Unknown value of ThreadStopAction: " + task.getThreadStopAction() + " for task " + task);
        }
    }

    private void checkLocalSchedulerRunning(OperationResult result) throws StopJobException {
        // if task manager is stopping or stopped, stop this task immediately
        // this can occur in rare situations, see https://jira.evolveum.com/browse/MID-1167
        if (beans.localScheduler.isRunningChecked()) {
            return;
        }

        LOGGER.warn("Task was started while task manager is not running: exiting and rescheduling (if needed)");
        processTaskStop(result);
        throw new StopJobException();
    }

    private void checkTaskReady() throws StopJobException {
        if (task.isReady()) {
            return;
        }

        try {
            LOGGER.debug("Unscheduling non-ready task {}", task);
            context.getScheduler().unscheduleJob(context.getTrigger().getKey());
            throw new StopJobException(WARNING, "Task is not in READY state (its state is {}), exiting its execution after "
                    + "removed the Quartz trigger. Task = {}", null, task.getSchedulingState(), task);
        } catch (Throwable t) {
            throw new StopJobException(UNEXPECTED_ERROR, "Couldn't unschedule job for a non-READY task {}", t, task);
        }
    }

    private void fetchTheTask(String oid, OperationResult result) throws StopJobException {
        try {
            TaskQuartzImpl taskWithResult = beans.taskRetriever.getTaskWithResult(oid, result);
            String rootOid = beans.taskRetriever.getRootTaskOid(taskWithResult, result);
            task = beans.taskInstantiator.toRunningTaskInstance(taskWithResult, rootOid);
        } catch (ObjectNotFoundException e) {
            beans.localScheduler.deleteTaskFromQuartz(oid, false, result);
            throw new StopJobException(ERROR, "Task with OID %s no longer exists. "
                    + "Removed the Quartz job and exiting the execution routine.", e, oid);
        } catch (Throwable t) {
            throw new StopJobException(UNEXPECTED_ERROR, "Task with OID %s could not be retrieved. "
                    + "Please correct the problem or resynchronize midPoint repository with Quartz job store. "
                    + "Now exiting the execution routine.", t, oid);
        }
    }

    private void checkForConcurrentExecution(OperationResult result)
            throws ObjectNotFoundException, SchemaException, StopJobException {
        if (beans.configuration.isCheckForTaskConcurrentExecution()) {
            new ConcurrentExecutionChecker(task, beans)
                    .check(result);
        }
    }

    private void setExecutionNodeAndState(OperationResult result) throws ObjectNotFoundException, SchemaException, ObjectAlreadyExistsException {
        task.setExecutionState(TaskExecutionStateType.RUNNING);
        task.setNode(beans.configuration.getNodeId());
        task.flushPendingModifications(result);
    }

    private void resetTaskExecutionNodeAndState(OperationResult result) {
        try {
            task.setNode(null);
            task.flushPendingModifications(result);
        } catch (Exception e) {
            LoggingUtils.logUnexpectedException(LOGGER, "Couldn't reset task execution node information for {}", e, task);
        }

        try {
            task.refresh(result);
            // If the task was suspended or closed or whatever in the meanwhile, most probably the new value is reflected here.
            if (task.getSchedulingState() == TaskSchedulingStateType.READY) {
                // But to be sure, let us do preconditions-based modification
                try {
                    task.setExecutionAndSchedulingStateImmediate(TaskExecutionStateType.RUNNABLE, TaskSchedulingStateType.READY,
                            TaskSchedulingStateType.READY, result);
                } catch (PreconditionViolationException e) {
                    LOGGER.trace("The scheduling state was no longer READY. Let us refresh the task.", e);
                    task.refresh(result);
                    resetExecutionState(result);
                }
            } else {
                resetExecutionState(result);
            }
        } catch (Exception e) {
            LoggingUtils.logUnexpectedException(LOGGER, "Couldn't set execution state information for {}", e, task);
        }
    }

    private void resetExecutionState(OperationResult result) throws ObjectAlreadyExistsException, ObjectNotFoundException,
            SchemaException {
        TaskExecutionStateType newExecutionState = getNewExecutionState();
        if (newExecutionState != null) {
            task.setExecutionState(newExecutionState);
            task.flushPendingModifications(result);
        }
    }

    private TaskExecutionStateType getNewExecutionState() {
        if (task.getSchedulingState() == null) {
            LOGGER.error("No scheduling state in {}. Setting execution state to SUSPENDED.", task);
            return TaskExecutionStateType.SUSPENDED;
        }
        switch (task.getSchedulingState()) {
            case SUSPENDED:
                return TaskExecutionStateType.SUSPENDED;
            case CLOSED:
                return TaskExecutionStateType.CLOSED;
            case WAITING:
                // The current execution state should be OK. It is because the switch to WAITING was done internally
                // by the task handler, and was accompanied by the change in the execution state.
                return null;
            case READY: // Not much probable, but can occur in theory.
                return TaskExecutionStateType.RUNNABLE;
            default:
                throw new AssertionError(task.getSchedulingState());
        }
    }

    private void checkGroupLimits(OperationResult result) throws StopJobException {
        GroupLimitsChecker checker = new GroupLimitsChecker(task, beans);
        RescheduleTime rescheduleTime = checker.checkIfAllowed(result);
        if (rescheduleTime == null) {
            return; // everything ok
        }

        if (!rescheduleTime.regular) {
            try {
                beans.localScheduler.rescheduleLater(task, rescheduleTime.timestamp);
            } catch (Exception e) {
                throw new StopJobException(UNEXPECTED_ERROR, "Couldn't reschedule task " + task + " (rescheduled because" +
                        " of execution constraints): " + e.getMessage(), e);
            }
        }
        throw new StopJobException();
    }

    void waitForTransientChildrenAndCloseThem(OperationResult result) {
        beans.lightweightTaskManager.waitForTransientChildrenAndCloseThem(task, result);
    }

    // returns true if the execution of the task should continue

    // called when task is externally stopped (can occur on node shutdown, node scheduler stop, node threads deactivation, or task suspension)
    // we have to act (i.e. reschedule resilient tasks or close/suspend non-resilient tasks) in all cases, except task suspension
    // we recognize it by looking at task status: RUNNABLE means that the task is stopped as part of node shutdown
    private void processTaskStop(OperationResult executionResult) {

        try {
            task.refresh(executionResult);

            if (!task.isReady()) {
                LOGGER.trace("processTaskStop: task scheduling status is not READY (it is " + task.getSchedulingState() + "), so ThreadStopAction does not apply; task = " + task);
                return;
            }

            if (task.getThreadStopAction() == ThreadStopActionType.CLOSE) {
                LOGGER.info("Closing non-resilient task on node shutdown; task = {}", task);
                closeTask(task, executionResult);
            } else if (task.getThreadStopAction() == ThreadStopActionType.SUSPEND) {
                LOGGER.info("Suspending non-resilient task on node shutdown; task = {}", task);
                // we must NOT wait here, as we would wait infinitely -- we do not have to stop the task neither, because we are that task
                beans.taskStateManager.suspendTaskNoException(task, TaskManager.DO_NOT_STOP, executionResult);
            } else if (task.getThreadStopAction() == null || task.getThreadStopAction() == ThreadStopActionType.RESTART) {
                LOGGER.info("Node going down: Rescheduling resilient task to run immediately; task = {}", task);
                beans.taskStateManager.scheduleTaskNow(task, executionResult);
            } else if (task.getThreadStopAction() == ThreadStopActionType.RESCHEDULE) {
                if (task.isRecurring() && task.isLooselyBound()) {
                    // nothing to do, task will be automatically started by Quartz on next trigger fire time
                } else {
                    // for tightly-bound tasks we do not know next schedule time, so we run them immediately
                    beans.taskStateManager.scheduleTaskNow(task, executionResult);
                }
            } else {
                throw new SystemException("Unknown value of ThreadStopAction: " + task.getThreadStopAction() + " for task " + task);
            }
        } catch (ObjectNotFoundException e) {
            LoggingUtils.logException(LOGGER, "ThreadStopAction cannot be applied, because the task no longer exists: " + task, e);
        } catch (SchemaException e) {
            LoggingUtils.logUnexpectedException(LOGGER, "ThreadStopAction cannot be applied, because of schema exception. Task = " + task, e);
        }
    }

    // Note that the result is most probably == executionResult. But it is no problem.
    void closeFlawedTaskRecordingResult(OperationResult result) {
        LOGGER.info("Closing flawed task {}", task);
        try {
            task.setResultImmediate(executionResult, result);
        } catch (ObjectNotFoundException  e) {
            LoggingUtils.logException(LOGGER, "Couldn't store operation result into the task {}", e, task);
        } catch (SchemaException e) {
            LoggingUtils.logUnexpectedException(LOGGER, "Couldn't store operation result into the task {}", e, task);
        }
        closeTask(task, result);
    }

    private void closeTask(RunningTaskQuartzImpl task, OperationResult result) {
        try {
            beans.taskStateManager.closeTask(task, result);
        } catch (ObjectNotFoundException e) {
            LoggingUtils.logException(LOGGER, "Cannot close task {}, because it does not exist in repository.", e, task);
        } catch (SchemaException e) {
            LoggingUtils.logUnexpectedException(LOGGER, "Cannot close task {} due to schema exception", e, task);
        } catch (SystemException e) {
            LoggingUtils.logUnexpectedException(LOGGER, "Cannot close task {} due to system exception", e, task);
        }
    }

    @Override
    public void interrupt() {
        boolean interruptsAlways = beans.configuration.getUseThreadInterrupt() == UseThreadInterrupt.ALWAYS;
        boolean interruptsMaybe = beans.configuration.getUseThreadInterrupt() != UseThreadInterrupt.NEVER;
        if (task != null) {
            LOGGER.trace("Trying to shut down the task {}, executing in thread {}", task, task.getExecutingThread());
            task.unsetCanRun();
            for (RunningTaskQuartzImpl subtask : task.getRunnableOrRunningLightweightAsynchronousSubtasks()) {
                subtask.unsetCanRun();
                // if we want to cancel the Future using interrupts, we have to do it now
                // because after calling cancel(false) subsequent calls to cancel(true) have no effect whatsoever
                Future<?> future = subtask.getLightweightHandlerFuture();
                if (future != null) {
                    future.cancel(interruptsMaybe);
                }
            }
            if (interruptsAlways) {
                sendThreadInterrupt(false); // subtasks were interrupted by their futures
            }
        }
    }

    public void sendThreadInterrupt() {
        sendThreadInterrupt(true);
    }

    // beware: Do not touch task prism here, because this method can be called asynchronously
    private void sendThreadInterrupt(boolean alsoSubtasks) {
        Thread thread = task.getExecutingThread();
        if (thread != null) {            // in case this method would be (mistakenly?) called after the execution is over
            LOGGER.trace("Calling Thread.interrupt on thread {}.", thread);
            thread.interrupt();
            LOGGER.trace("Thread.interrupt was called on thread {}.", thread);
        }
        if (alsoSubtasks) {
            for (RunningTaskQuartzImpl subtask : task.getRunningLightweightAsynchronousSubtasks()) {
                //LOGGER.trace("Calling Future.cancel(mayInterruptIfRunning:=true) on a future for LAT subtask {}", subtask);
                subtask.getLightweightHandlerFuture().cancel(true);
            }
        }
    }

    public Thread getExecutingThread() {
        return task.getExecutingThread();
    }

    /**
     * Ugly hack - this class is instantiated not by Spring but explicitly by Quartz.
     */
    public static void setTaskManagerQuartzImpl(TaskManagerQuartzImpl taskManager) {
        beans = taskManager.getBeans();
    }
}
