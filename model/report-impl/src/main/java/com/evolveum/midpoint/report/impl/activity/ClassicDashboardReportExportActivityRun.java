/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.report.impl.activity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.repo.common.activity.run.*;
import com.evolveum.midpoint.repo.common.activity.run.processing.ItemProcessingRequest;
import com.evolveum.midpoint.report.impl.ReportServiceImpl;
import com.evolveum.midpoint.report.impl.ReportUtils;
import com.evolveum.midpoint.report.impl.activity.ExportDashboardActivitySupport.DashboardWidgetHolder;
import com.evolveum.midpoint.report.impl.controller.*;
import com.evolveum.midpoint.schema.ObjectHandler;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.RunningTask;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * Activity execution specifics for classical report export.
 */
public final class ClassicDashboardReportExportActivityRun
        extends PlainIterativeActivityRun
        <ExportDashboardReportLine<Containerable>,
                ClassicReportExportWorkDefinition,
                ClassicReportExportActivityHandler,
                ReportExportWorkStateType> {

    @NotNull private final ExportDashboardActivitySupport support;

    /** The report service Spring bean. */
    @NotNull private final ReportServiceImpl reportService;

    /**
     * Data writer which complete context of report.
     */
    private ReportDataWriter<? extends ExportedReportDataRow, ? extends ExportedReportHeaderRow> dataWriter;

    /**
     * Map of controllers for objects searched based on widgets.
     */
    private Map<String, DashboardWidgetHolder> mapOfWidgetsController;

    /**
     * Controller for widgets.
     */
    private DashboardWidgetExportController basicWidgetController;

    ClassicDashboardReportExportActivityRun(
            ActivityRunInstantiationContext<ClassicReportExportWorkDefinition, ClassicReportExportActivityHandler> context) {
        super(context, "Dashboard report export");
        reportService = context.getActivity().getHandler().reportService;
        support = new ExportDashboardActivitySupport(this, reportService,
                getActivityHandler().objectResolver, getWorkDefinition());
        setInstanceReady();
    }

    @Override
    public @NotNull ActivityReportingCharacteristics createReportingCharacteristics() {
        return super.createReportingCharacteristics()
                .determineOverallSizeDefault(ActivityOverallItemCountingOptionType.ALWAYS);
    }

    @Override
    public void beforeRun(OperationResult result) throws ActivityRunException, CommonException {
        RunningTask task = getRunningTask();
        support.beforeExecution(result);
        @NotNull ReportType report = support.getReport();

        support.stateCheck(result);

        List<DashboardWidgetType> widgets = support.getDashboard().getWidget();

        mapOfWidgetsController = new LinkedHashMap<>();

        dataWriter = ReportUtils.createDashboardDataWriter(
                report, getActivityHandler().reportService, support.getMapOfCompiledViews());
        basicWidgetController = new DashboardWidgetExportController(dataWriter, report, reportService);
        basicWidgetController.initialize();
        basicWidgetController.beforeBucketExecution(1, result);

        for (DashboardWidgetType widget : widgets) {
            if (support.isWidgetTableVisible()) {
                String widgetIdentifier = widget.getIdentifier();
                ContainerableReportDataSource searchSpecificationHolder = new ContainerableReportDataSource(support);
                DashboardExportController<Containerable> controller = new DashboardExportController(
                        searchSpecificationHolder,
                        dataWriter,
                        report,
                        reportService,
                        support.getCompiledCollectionView(widgetIdentifier),
                        widgetIdentifier,
                        support.getReportParameters());
                controller.initialize(task, result);
                controller.beforeBucketExecution(1, result);

                mapOfWidgetsController.put(widgetIdentifier, new DashboardWidgetHolder(searchSpecificationHolder, controller));
            }
        }
    }

    @Override
    protected @NotNull ObjectReferenceType getDesiredTaskObjectRef() {
        return support.getReportRef();
    }

    @Override
    public Integer determineOverallSize(OperationResult result) throws CommonException {
        int expectedTotal = support.getDashboard().getWidget().size();
        for (DashboardWidgetHolder holder : mapOfWidgetsController.values()) {
            expectedTotal += support.countRecords(
                    holder.getSearchSpecificationHolder().getType(),
                    holder.getSearchSpecificationHolder().getQuery(),
                    holder.getSearchSpecificationHolder().getOptions(),
                    result);
        }
        return expectedTotal;
    }

    @Override
    public void iterateOverItemsInBucket(OperationResult gResult) throws CommonException {
        // Issue the search to audit or model/repository
        // And use the following handler to handle the results

        List<DashboardWidgetType> widgets = support.getDashboard().getWidget();
        AtomicInteger widgetSequence = new AtomicInteger(1);
        for (DashboardWidgetType widget : widgets) {

            ExportDashboardReportLine<Containerable> widgetLine = new ExportDashboardReportLine<>(widgetSequence.getAndIncrement(), widget);
            ItemProcessingRequest<ExportDashboardReportLine<Containerable>> widgetRequest = new ExportDashboardReportLineProcessingRequest(
                    widgetLine, this);
            coordinator.submit(widgetRequest, gResult);

            if (support.isWidgetTableVisible()) {
                AtomicInteger sequence = new AtomicInteger(1);
                ObjectHandler<Containerable> handler = (record, lResult) -> {
                    ExportDashboardReportLine<Containerable> line = new ExportDashboardReportLine<>(sequence.getAndIncrement(),
                            record,
                            widget.getIdentifier());
                    ItemProcessingRequest<ExportDashboardReportLine<Containerable>> request =
                            new ExportDashboardReportLineProcessingRequest(line, this);
                    coordinator.submit(request, lResult);
                    return true;
                };

                DashboardWidgetHolder holder = mapOfWidgetsController.get(widget.getIdentifier());
                ContainerableReportDataSource searchSpecificationHolder = holder.getSearchSpecificationHolder();
                searchSpecificationHolder.run(handler, gResult);
            }
        }
    }

    @Override
    public boolean processItem(@NotNull ItemProcessingRequest<ExportDashboardReportLine<Containerable>> request,
            @NotNull RunningTask workerTask, OperationResult result)
            throws CommonException, ActivityRunException {

        ExportDashboardReportLine<Containerable> item = request.getItem();
        getController(item)
                .handleDataRecord(request.getSequentialNumber(), item.getContainer(), workerTask, result);
        return true;
    }

    private <C extends Containerable> ExportController<C> getController(@NotNull ExportDashboardReportLine<Containerable> item) {
        if (item.isBasicWidgetRow()) {
            //noinspection unchecked
            return (ExportController<C>) basicWidgetController;
        }
        DashboardWidgetHolder holder = mapOfWidgetsController.get(item.getWidgetIdentifier());
        //noinspection unchecked
        return (ExportController<C>) holder.getController();
    }

    @Override
    public void afterRun(OperationResult result) throws CommonException {
        support.saveReportFile(dataWriter, result);
    }

    @Override
    public @NotNull ErrorHandlingStrategyExecutor.FollowUpAction getDefaultErrorAction() {
        return ErrorHandlingStrategyExecutor.FollowUpAction.CONTINUE;
    }
}
