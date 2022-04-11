/*
 * Copyright (C) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.page.admin.server;

import static java.util.Collections.singletonList;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.evolveum.midpoint.authentication.api.authorization.AuthorizationAction;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.authentication.api.authorization.PageDescriptor;
import com.evolveum.midpoint.authentication.api.authorization.Url;
import com.evolveum.midpoint.authentication.api.util.AuthUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.wicket.Page;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.prism.wrapper.*;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.gui.impl.component.AjaxCompositedIconButton;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIcon;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIconBuilder;
import com.evolveum.midpoint.gui.impl.component.icon.IconCssStyle;
import com.evolveum.midpoint.gui.impl.page.admin.task.RootTaskLoader;
import com.evolveum.midpoint.gui.impl.prism.wrapper.PrismContainerValueWrapperImpl;
import com.evolveum.midpoint.gui.impl.prism.wrapper.PrismReferenceValueWrapperImpl;
import com.evolveum.midpoint.model.api.authentication.GuiProfiledPrincipal;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.report.api.ReportConstants;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.ObjectDeltaOperation;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.statistics.ActivityStatisticsUtil;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.AjaxButton;
import com.evolveum.midpoint.web.component.AjaxDownloadBehaviorFromStream;
import com.evolveum.midpoint.web.component.AjaxIconButton;
import com.evolveum.midpoint.web.component.ObjectSummaryPanel;
import com.evolveum.midpoint.web.component.dialog.ConfirmationPanel;
import com.evolveum.midpoint.web.component.objectdetails.AbstractObjectMainPanel;
import com.evolveum.midpoint.web.component.refresh.Refreshable;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.page.admin.PageAdminObjectDetails;
import com.evolveum.midpoint.web.page.admin.reports.PageCreatedReports;
import com.evolveum.midpoint.web.util.OnePageParameterEncoder;
import com.evolveum.midpoint.web.util.TaskOperationUtils;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

@PageDescriptor(
        urls = {
                @Url(mountUrl = "/admin/task", matchUrlForSecurity = "/admin/task")
        },
        encoder = OnePageParameterEncoder.class,
        action = {
                @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_TASKS_ALL_URL,
                        label = "PageAdminUsers.auth.usersAll.label",
                        description = "PageAdminUsers.auth.usersAll.description"),
                @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_TASK_URL,
                        label = "PageUser.auth.user.label",
                        description = "PageUser.auth.user.description")
        })
public class PageTask extends PageAdminObjectDetails<TaskType> implements Refreshable {
    private static final long serialVersionUID = 1L;

    private static final Trace LOGGER = TraceManager.getTrace(PageTask.class);
    private static final String DOT_CLASS = PageTask.class.getName() + ".";
    protected static final String OPERATION_EXECUTE_TASK_CHANGES = DOT_CLASS + "executeTaskChanges";
    private static final String OPERATION_LOAD_REPORT_OUTPUT = DOT_CLASS + "loadReport";

    private static final int REFRESH_INTERVAL = 2000;

    private Boolean refreshEnabled;

    private TaskTabsVisibility taskTabsVisibility;
    private PrismObjectWrapper<TaskType> rememberedObjectWrapper;

    public PageTask() {
        initialize(null);
    }

    public PageTask(PageParameters parameters) {
        super(parameters);
        initialize(null);
    }

    public PageTask(final PrismObject<TaskType> taskToEdit, boolean isNewObject) {
        super(taskToEdit, isNewObject);
        initialize(taskToEdit, isNewObject);
    }

    @Override
    public Class<TaskType> getCompileTimeClass() {
        return TaskType.class;
    }

    @Override
    public TaskType createNewObject() {
        return new TaskType();
    }

    @Override
    protected ObjectSummaryPanel<TaskType> createSummaryPanel(IModel<TaskType> summaryModel) {
        return new TaskSummaryPanel(ID_SUMMARY_PANEL, summaryModel, WebComponentUtil.getSummaryPanelSpecification(TaskType.class, getCompiledGuiProfile()));
    }

    @Override
    protected Collection<SelectorOptions<GetOperationOptions>> buildGetOptions() {
        //TODO use full options as defined in TaskDtoProviderOptions.fullOptions()

        return getOperationOptionsBuilder()
                // retrieve
                .item(TaskType.F_SUBTASK_REF).retrieve()
                .item(TaskType.F_NODE_AS_OBSERVED).retrieve()
                .item(TaskType.F_NEXT_RUN_START_TIMESTAMP).retrieve()
                .item(TaskType.F_NEXT_RETRY_TIMESTAMP).retrieve()
                .item(TaskType.F_RESULT).retrieve()         // todo maybe only when it is to be displayed
                .build();
    }

    protected void initOperationalButtons(RepeatingView repeatingView) {
        createSuspendButton(repeatingView);
        createResumeButton(repeatingView);
        createRunNowButton(repeatingView);

        createManageLivesyncTokenButton(repeatingView);
        createDownloadReportButton(repeatingView);
        createCleanupPerformanceButton(repeatingView);
        createCleanupResultsButton(repeatingView);

//        AjaxIconButton cleanupErrors = new AjaxIconButton(repeatingView.newChildId(), new Model<>(GuiStyleConstants.CLASS_ICON_TRASH),
//        createStringResource("operationalButtonsPanel.cleanupErrors")) {
//
//            @Override
//            public void onClick(AjaxRequestTarget target) {
//                refresh(target);
//            }
//        };
//        cleanupErrors.add(AttributeAppender.append("class", "btn btn-default btn-margin-left btn-sm"));
//        cleanupErrors.add(new VisibleBehaviour(this::isNotRunning));
//        repeatingView.add(cleanupErrors);

        setOutputMarkupId(true);

    }

    //TODO later migrate higher.. this might be later used for all focuses
    @Override
    protected void initStateButtons(RepeatingView stateButtonsView) {
        createRefreshNowIconButton(stateButtonsView);
        createResumePauseButton(stateButtonsView);
        final Label status = new Label(stateButtonsView.newChildId(), this::createRefreshingLabel);
        status.setOutputMarkupId(true);
        stateButtonsView.add(status);
    }

    private void createSuspendButton(RepeatingView repeatingView) {
        AjaxButton suspend = new AjaxButton(repeatingView.newChildId(), createStringResource("pageTaskEdit.button.suspend")) {
            @Override
            public void onClick(AjaxRequestTarget target) {
                PrismObject<TaskType> task = getObjectWrapper().getObject();
                if (task == null) {
                    return;
                }
                OperationResult result = TaskOperationUtils.suspendTasks(singletonList(task.asObjectable()), PageTask.this);
                afterOperation(target, result);
            }
        };
        suspend.add(new VisibleBehaviour(() -> WebComponentUtil.canSuspendTask(getTask(), PageTask.this)));
        suspend.add(AttributeAppender.append("class", "btn-danger btn-sm"));
        repeatingView.add(suspend);
    }

    private void createResumeButton(RepeatingView repeatingView) {
        AjaxButton resume = new AjaxButton(repeatingView.newChildId(), createStringResource("pageTaskEdit.button.resume")) {
            @Override
            public void onClick(AjaxRequestTarget target) {
                PrismObject<TaskType> task = getObjectWrapper().getObject();
                if (task == null) {
                    return;
                }
                OperationResult result = TaskOperationUtils.resumeTasks(singletonList(task.asObjectable()), PageTask.this);
                afterOperation(target, result);
            }
        };
        resume.add(AttributeAppender.append("class", "btn-primary btn-sm"));
        resume.add(new VisibleBehaviour(() -> WebComponentUtil.canResumeTask(getTask(), PageTask.this)));
        repeatingView.add(resume);
    }

    private void createRunNowButton(RepeatingView repeatingView) {
        AjaxButton runNow = new AjaxButton(repeatingView.newChildId(), createStringResource("pageTaskEdit.button.runNow")) {
            @Override
            public void onClick(AjaxRequestTarget target) {
                String oid = getObjectWrapper().getOid();
                refreshEnabled = Boolean.TRUE;
                OperationResult result = TaskOperationUtils.runNowPerformed(singletonList(oid), PageTask.this);
                afterOperation(target, result);
            }
        };
        runNow.add(AttributeAppender.append("class", "btn-success btn-sm"));
        runNow.add(new VisibleBehaviour(() -> WebComponentUtil.canRunNowTask(getTask(), PageTask.this)));
        repeatingView.add(runNow);
    }

    private void createManageLivesyncTokenButton(RepeatingView repeatingView) {
        AjaxButton manageLivesyncToken = new AjaxButton(repeatingView.newChildId(), createStringResource("PageTask.livesync.token")) {

            @Override
            public void onClick(AjaxRequestTarget ajaxRequestTarget) {
                LivesyncTokenEditorPanel tokenEditor = new LivesyncTokenEditorPanel(PageTask.this.getMainPopupBodyId(), getObjectModel()) {

                    @Override
                    protected void saveTokenPerformed(ObjectDelta<TaskType> tokenDelta, AjaxRequestTarget target) {
                        saveTaskChanges(target, tokenDelta);
                    }
                };
                tokenEditor.setOutputMarkupId(true);
                PageTask.this.showMainPopup(tokenEditor, ajaxRequestTarget);
            }
        };
        manageLivesyncToken.add(new VisibleEnableBehaviour() {
            @Override
            public boolean isVisible() {
                return WebComponentUtil.isLiveSync(getTask()) && isNotRunning() && getTask().asPrismObject().findProperty(LivesyncTokenEditorPanel.PATH_TOKEN) != null;
            }

            @Override
            public boolean isEnabled() {
                return isNotRunning();
            }
        });
        manageLivesyncToken.add(AttributeAppender.append("class", "btn-default btn-sm"));
        manageLivesyncToken.setOutputMarkupId(true);
        repeatingView.add(manageLivesyncToken);
    }

    private void createDownloadReportButton(RepeatingView repeatingView) {
        final AjaxDownloadBehaviorFromStream ajaxDownloadBehavior = new AjaxDownloadBehaviorFromStream() {
            private static final long serialVersionUID = 1L;

            @Override
            protected InputStream initStream() {
                ReportDataType reportObject = getReportData();
                if (reportObject != null) {
                    return PageCreatedReports.createReport(reportObject, this, PageTask.this);
                } else {
                    return null;
                }
            }

            @Override
            public String getFileName() {
                ReportDataType reportObject = getReportData();
                return PageCreatedReports.getReportFileName(reportObject);
            }
        };
        add(ajaxDownloadBehavior);

        AjaxButton download = new AjaxButton(repeatingView.newChildId(), createStringResource("PageTask.download.report")) {

            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                ajaxDownloadBehavior.initiate(target);
            }
        };
        download.add(new VisibleBehaviour(this::isDownloadReportVisible));
        download.add(AttributeAppender.append("class", "btn-primary btn-sm"));
        repeatingView.add(download);
    }

    private boolean isDownloadReportVisible() {
        return WebComponentUtil.isReport(getTask())
                && getReportDataOid() != null;
    }

    private ReportDataType getReportData() {
        String reportData = getReportDataOid();
        if (reportData == null) {
            return null;
        }

        Task opTask = createSimpleTask(OPERATION_LOAD_REPORT_OUTPUT);
        OperationResult result = opTask.getResult();

        PrismObject<ReportDataType> report = WebModelServiceUtils.loadObject(ReportDataType.class, reportData, this, opTask, result);
        if (report == null) {
            return null;
        }
        result.computeStatusIfUnknown();
        showResult(result, false);

        return report.asObjectable();

    }

    private String getReportDataOid() {
        PrismObject<TaskType> task = getTask().asPrismObject();
        PrismReference reportData = task.findReference(ItemPath.create(TaskType.F_EXTENSION, ReportConstants.REPORT_DATA_PROPERTY_NAME));
        if (reportData == null || reportData.getRealValue() == null || reportData.getRealValue().getOid() == null) {
            PrismProperty<String> reportOutputOid = task.findProperty(ItemPath.create(TaskType.F_EXTENSION, ReportConstants.REPORT_OUTPUT_OID_PROPERTY_NAME));
            if (reportOutputOid == null) {
                return null;
            }
            return reportOutputOid.getRealValue();
        }

        return reportData.getRealValue().getOid();
    }

    private void createRefreshNowIconButton(RepeatingView repeatingView) {
        AjaxIconButton refreshNow = new AjaxIconButton(repeatingView.newChildId(), new Model<>("fa fa-refresh"), createStringResource("autoRefreshPanel.refreshNow")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                refresh(target);
            }
        };
        refreshNow.add(AttributeAppender.append("class", "btn btn-default btn-sm"));
        repeatingView.add(refreshNow);
    }

    private void createResumePauseButton(RepeatingView repeatingView) {
        AjaxIconButton resumePauseRefreshing = new AjaxIconButton(repeatingView.newChildId(), (IModel<String>) this::createResumePauseButtonLabel, createStringResource("autoRefreshPanel.resumeRefreshing")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                refreshEnabled = !isRefreshEnabled();
                refresh(target);
            }
        };
        resumePauseRefreshing.add(AttributeAppender.append("class", "btn btn-default btn-margin-left btn-sm"));
        repeatingView.add(resumePauseRefreshing);
    }

    private void createCleanupPerformanceButton(RepeatingView repeatingView) {
        AjaxCompositedIconButton cleanupPerformance = new AjaxCompositedIconButton(repeatingView.newChildId(), getTaskCleanupCompositedIcon(GuiStyleConstants.CLASS_ICON_PERFORMANCE),
                createStringResource("operationalButtonsPanel.cleanupEnvironmentalPerformance")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                ConfirmationPanel dialog = new ConfirmationPanel(getMainPopupBodyId(), createStringResource("operationalButtonsPanel.cleanupEnvironmentalPerformance.confirmation")) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void yesPerformed(AjaxRequestTarget target) {
                        try {
                            deleteStatistics(target);
                        } catch (Exception e) {
                            LOGGER.error("Cannot delete task operation statistics, {}", e.getMessage(), e);
                            getSession().error(PageTask.this.getString("PageTask.cleanup.operationStatistics.failed"));
                        }
                    }
                };
                showMainPopup(dialog, target);
            }
        };
        cleanupPerformance.add(AttributeAppender.append("class", "btn btn-default btn-margin-left btn-sm"));
        cleanupPerformance.add(new VisibleBehaviour(this::isNotRunning));
        repeatingView.add(cleanupPerformance);
    }

    private void deleteStatistics(AjaxRequestTarget target) throws SchemaException {
        List<ItemPath> statisticsPaths = new ArrayList<>();
        statisticsPaths.add(TaskType.F_OPERATION_STATS);
        statisticsPaths.addAll(ActivityStatisticsUtil.getAllStatisticsPaths(getTask()));
        deleteItem(target, statisticsPaths.toArray(new ItemPath[0]));
    }

    private void deleteItem(AjaxRequestTarget target, ItemPath... paths) throws SchemaException {
        Collection<ItemDelta<?, ?>> itemDeltas = new ArrayList<>();
        for (ItemPath path : paths) {
            ItemDelta<?, ?> delta = createDeleteItemDelta(path);
            if (delta == null) {
                LOGGER.trace("Nothing to delete for {}", path);
                continue;
            }
            itemDeltas.add(delta);
        }

        ObjectDelta<TaskType> taskDelta = getPrismContext().deltaFor(TaskType.class)
                .asObjectDelta(getTask().getOid());
        taskDelta.addModifications(itemDeltas);

        saveTaskChanges(target, taskDelta);
    }

    private void createCleanupResultsButton(RepeatingView repeatingView) {
        AjaxCompositedIconButton cleanupResults = new AjaxCompositedIconButton(repeatingView.newChildId(), getTaskCleanupCompositedIcon(GuiStyleConstants.CLASS_ICON_TASK_RESULTS),
                createStringResource("operationalButtonsPanel.cleanupResults")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                ConfirmationPanel dialog = new ConfirmationPanel(getMainPopupBodyId(), createStringResource("operationalButtonsPanel.cleanupResults.confirmation")) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void yesPerformed(AjaxRequestTarget target) {
                        try {
                            deleteItem(target, TaskType.F_RESULT, TaskType.F_RESULT_STATUS);
                        } catch (Exception e) {
                            LOGGER.error("Cannot clear task results: {}", e.getMessage());
                            getSession().error(PageTask.this.getString("PageTask.cleanup.result.failed"));
                        }
                    }
                };
                showMainPopup(dialog, target);
            }
        };
        cleanupResults.add(new VisibleBehaviour(this::isNotRunning));
        cleanupResults.add(AttributeAppender.append("class", "btn btn-default btn-margin-left btn-sm"));
        repeatingView.add(cleanupResults);
    }

    private ItemDelta<?, ?> createDeleteItemDelta(ItemPath itemPath) throws SchemaException {
        // Originally here was a code that looked at item wrapper - why?
        // Removed because it didn't allow to remove values not covered by wrapper, like activityState/activity/statistics.
        return getPrismContext().deltaFor(TaskType.class)
                .item(itemPath).replace()
                .asItemDelta();
    }

    private void saveTaskChanges(AjaxRequestTarget target, ObjectDelta<TaskType> taskDelta) {
        if (taskDelta.isEmpty()) {
            getSession().warn("Nothing to save, no changes were made.");
            target.add(getFeedbackPanel());
            return;
        }

        OperationResult result = new OperationResult(OPERATION_EXECUTE_TASK_CHANGES);
        Task task = createSimpleTask(OPERATION_EXECUTE_TASK_CHANGES);

        try {
            taskDelta.revive(getPrismContext()); //do we need revive here?
            getModelService().executeChanges(MiscUtil.createCollection(taskDelta), null, task, result);
            result.computeStatus();
        } catch (Exception e) {
            LoggingUtils.logUnexpectedException(LOGGER, "Cannot save tasks changes", e);
            result.recordFatalError("Cannot save tasks changes, " + e.getMessage(), e);
        }
        afterOperation(target, result);
    }

    private CompositedIcon getTaskCleanupCompositedIcon(String basicIconClass) {
        CompositedIconBuilder iconBuilder = new CompositedIconBuilder();
        return iconBuilder
                .setBasicIcon(basicIconClass, IconCssStyle.IN_ROW_STYLE)
                .appendLayerIcon(WebComponentUtil.createIconType(GuiStyleConstants.CLASS_ICON_TRASH), IconCssStyle.BOTTOM_RIGHT_STYLE)
                .build();
    }

    private void afterOperation(AjaxRequestTarget target, OperationResult result) {
        taskTabsVisibility = new TaskTabsVisibility();
        showResult(result);
        getObjectModel().reset();
        refresh(target);
    }

    @Override
    public void savePerformed(AjaxRequestTarget target) {
        savePerformed(target, false);
    }

    private boolean saveAndRun = false;

    public void saveAndRunPerformed(AjaxRequestTarget target) {
        saveAndRun = true;
        savePerformed(target, true);
    }

    private void savePerformed(AjaxRequestTarget target, boolean run) {
        PrismObjectWrapper<TaskType> taskWrapper = getObjectWrapper();
        try {
            // TODO MID-6783
            setTaskInitialState(taskWrapper, run);

            setupOwner(taskWrapper);
        } catch (SchemaException e) {
            LoggingUtils.logUnexpectedException(LOGGER, "Error while finishing task settings.", e);
            target.add(getFeedbackPanel());
            return;
        }

        super.savePerformed(target);
    }

    private void setTaskInitialState(PrismObjectWrapper<TaskType> taskWrapper, boolean run) throws SchemaException {
        PrismPropertyWrapper<TaskExecutionStateType> executionState = taskWrapper.findProperty(ItemPath.create(TaskType.F_EXECUTION_STATE));
        PrismPropertyWrapper<TaskSchedulingStateType> schedulingState = taskWrapper.findProperty(ItemPath.create(TaskType.F_SCHEDULING_STATE));
        if (executionState == null || schedulingState == null) {
            throw new SchemaException("Task cannot be set as running, no execution status or scheduling status present");
        }
        if (run) {
            setTaskInitiallyRunning(executionState, schedulingState);
        } else {
            if (!isAdd()) {
                return;
            }
            setTaskInitiallySuspended(executionState, schedulingState);
        }
    }

    private void setTaskInitiallyRunning(PrismPropertyWrapper<TaskExecutionStateType> executionState, PrismPropertyWrapper<TaskSchedulingStateType> schedulingState) throws SchemaException {
        executionState.getValue().setRealValue(TaskExecutionStateType.RUNNABLE);
        schedulingState.getValue().setRealValue(TaskSchedulingStateType.READY);
    }

    private void setTaskInitiallySuspended(PrismPropertyWrapper<TaskExecutionStateType> executionState, PrismPropertyWrapper<TaskSchedulingStateType> schedulingState) throws SchemaException {
        executionState.getValue().setRealValue(TaskExecutionStateType.SUSPENDED);
        schedulingState.getValue().setRealValue(TaskSchedulingStateType.SUSPENDED);
    }

    private void setupOwner(PrismObjectWrapper<TaskType> taskWrapper) throws SchemaException {
        PrismReferenceWrapper<Referencable> taskOwner = taskWrapper.findReference(ItemPath.create(TaskType.F_OWNER_REF));
        if (taskOwner == null) {
            return;
        }
        PrismReferenceValueWrapperImpl<Referencable> taskOwnerValue = taskOwner.getValue();
        if (taskOwnerValue == null) {
            return;
        }

        if (taskOwnerValue.getNewValue() == null || taskOwnerValue.getNewValue().isEmpty()) {
            GuiProfiledPrincipal guiPrincipal = AuthUtil.getPrincipalUser();
            if (guiPrincipal == null) {
                //BTW something very strange must happened
                return;
            }
            FocusType focus = guiPrincipal.getFocus();
            taskOwnerValue.setRealValue(ObjectTypeUtil.createObjectRef(focus, SchemaConstants.ORG_DEFAULT));
        }
    }

    @Override
    public void finishProcessing(AjaxRequestTarget target, Collection<ObjectDeltaOperation<? extends ObjectType>> executedDeltas, boolean returningFromAsync, OperationResult result) {
        if (isPreviewRequested()) {
            super.finishProcessing(target, executedDeltas, returningFromAsync, result);
            return;
        }

        if (result.isSuccess() && executedDeltas != null) {
            String taskOid = ObjectDeltaOperation.findFocusDeltaOidInCollection(executedDeltas);
            if (taskOid != null) {
                if (saveAndRun) {
                    result.setInProgress();
                }
                result.setBackgroundTaskOid(taskOid);
            }
        }
        super.finishProcessing(target, executedDeltas, returningFromAsync, result);
    }

    private String createRefreshingLabel() {
        if (isRefreshEnabled()) {
            return createStringResource("autoRefreshPanel.refreshingEach", getRefreshInterval() / 1000).getString();
        } else {
            return createStringResource("autoRefreshPanel.noRefreshing").getString();
        }
    }

    private String createResumePauseButtonLabel() {
        if (isRefreshEnabled()) {
            return "fa fa-pause";
        }
        return "fa fa-play";
    }

    @Override
    protected AbstractObjectMainPanel<TaskType> createMainPanel(String id) {
        taskTabsVisibility = new TaskTabsVisibility();
        taskTabsVisibility.computeAll(this, getObjectWrapper());
        IModel<TaskType> rootTaskModel = RootTaskLoader.createRootTaskModel(this::getTask, () -> this);
        return new TaskMainPanel(id, getObjectModel(), rootTaskModel, this);
    }

    TaskType getTask() {
        return getObjectWrapper().getObject().asObjectable();
    }

    @Override
    protected Class<? extends Page> getRestartResponsePage() {
        return PageTasks.class;
    }

    @Override
    public int getRefreshInterval() {
        return REFRESH_INTERVAL;
    }

    private boolean isNotRunning() {
        return !WebComponentUtil.isRunningTask(getTask());
    }

    public boolean isRefreshEnabled() {
        if (refreshEnabled == null) {
            return WebComponentUtil.isRunningTask(getTask());
        }

        return refreshEnabled;

    }

    @Override
    public void refresh(AjaxRequestTarget target) {
        rememberShowEmptyState();

        TaskTabsVisibility taskTabsVisibilityNew = new TaskTabsVisibility();
        taskTabsVisibilityNew.computeAll(this, getObjectWrapper());

        boolean soft = false;
        if (taskTabsVisibilityNew.equals(taskTabsVisibility)) {
            soft = true;
        }

        taskTabsVisibility = taskTabsVisibilityNew;
        super.refresh(target, soft);
    }

    public TaskTabsVisibility getTaskTabVisibilty() {
        return taskTabsVisibility;
    }

    private void rememberShowEmptyState() {
        rememberedObjectWrapper = getObjectWrapper();
    }

    @Override
    protected PrismObjectWrapper<TaskType> loadObjectWrapper(PrismObject<TaskType> task, boolean isReadonly) {
        PrismObjectWrapper<TaskType> objectWrapper = super.loadObjectWrapper(task, isReadonly);
//        if (rememberedObjectWrapper != null) {  //TODO: what is this for?
//            applyOldPageObjectState(objectWrapper);
//            rememberedObjectWrapper = null;
//        }
        return objectWrapper;
    }

    private void applyOldPageObjectState(PrismObjectWrapper<TaskType> objectWrapperAfterReload) {
        applyOldPageContainersState(rememberedObjectWrapper.getValue(), objectWrapperAfterReload.getValue());
        applyOldVirtualContainerState(objectWrapperAfterReload);
    }

    private <IW extends ItemWrapper, VW extends PrismValueWrapper> void applyOldPageContainersState(List<IW> newItemList) {
        for (IW iw : newItemList) {
            if (!(iw instanceof PrismContainerWrapper)) {
                continue;
            }
            if (((PrismContainerWrapper) iw).isVirtual()) {
                continue;
            }
            List<VW> values = iw.getValues();
            PrismContainerWrapper oldValWrapper;
            try {
                oldValWrapper = rememberedObjectWrapper.findContainer(iw.getPath());
                if (oldValWrapper != null) {
                    for (VW val : values) {
                        if (!(val instanceof PrismContainerValueWrapper)) {
                            continue;
                        }
                        ItemPath lastItemPath = ItemPath.create(singletonList(((PrismContainerValueWrapper) val).getPath().last()));
                        PrismContainerValueWrapper oldVal = oldValWrapper.findContainerValue(lastItemPath);
                        applyOldPageContainersState(oldVal, (PrismContainerValueWrapper) val);
                    }
                }
            } catch (SchemaException ex) {
                //just skip
            }
        }
    }

    private void applyOldPageContainersState(PrismContainerValueWrapper oldVal, PrismContainerValueWrapper newVal) {
        if (oldVal == null || newVal == null) {
            return;
        }
        newVal.setShowEmpty(oldVal.isShowEmpty());
        newVal.setExpanded(oldVal.isExpanded());
        applyOldPageContainersState(newVal.getItems());
    }

    private void applyOldVirtualContainerState(PrismObjectWrapper<TaskType> objectWrapperAfterReload) {
        List<PrismContainerWrapper<? extends Containerable>> containers = objectWrapperAfterReload.getValue().getContainers();
        for (PrismContainerWrapper pcw : containers) {
            if (!pcw.isVirtual()) {
                continue;
            }
            PrismContainerValueWrapper virtualCont = CollectionUtils.isNotEmpty(pcw.getValues()) ?
                    (PrismContainerValueWrapper) pcw.getValues().get(0) : null;
            applyOldVirtualContainerState(virtualCont);
        }

    }

    private void applyOldVirtualContainerState(PrismContainerValueWrapper value) {
        if (value == null || !value.isVirtual()) {
            return;
        }
        for (PrismContainerWrapper oldVirtualContainer : rememberedObjectWrapper.getValue().getContainers()) {
            if (!oldVirtualContainer.isVirtual()) {
                continue;
            }
            PrismContainerValueWrapper oldVal = CollectionUtils.isNotEmpty(oldVirtualContainer.getValues()) ?
                    (PrismContainerValueWrapper) oldVirtualContainer.getValues().get(0) : null;
            if (isSimilarVirtualContainer((PrismContainerValueWrapperImpl) value, (PrismContainerValueWrapperImpl) oldVal)) {
                value.setShowEmpty(oldVal.isShowEmpty());
                value.setExpanded(oldVal.isExpanded());
            }
        }
    }

    //todo rewrite when virtual container has his own identifier
    private boolean isSimilarVirtualContainer(PrismContainerValueWrapperImpl val1, PrismContainerValueWrapperImpl val2) {
        if (val1 == null || val2 == null || !val1.isVirtual() || !val2.isVirtual()) {
            return false;
        }
        if (val1.getVirtualItems() == null && val2.getVirtualItems() != null) {
            return false;
        }
        if (val1.getVirtualItems() != null && val2.getVirtualItems() == null) {
            return false;
        }
        if (val1.getVirtualItems() == null && val2.getVirtualItems() == null) {
            return true;
        }
        if (val1.getVirtualItems().size() != val2.getVirtualItems().size()) {
            return false;
        }
        List<VirtualContainerItemSpecificationType> virtItems = val1.getVirtualItems();
        for (VirtualContainerItemSpecificationType spec : virtItems) {
            List<VirtualContainerItemSpecificationType> virtItems2 = val2.getVirtualItems();
            boolean valContainsVirtItem = false;
            for (VirtualContainerItemSpecificationType spec2 : virtItems2) {
                if (spec.getPath().equivalent(spec2.getPath())) {
                    valContainsVirtItem = true;
                    break;
                }
            }
            if (!valContainsVirtItem) {
                return false;
            }
        }
        return true;
    }
}
