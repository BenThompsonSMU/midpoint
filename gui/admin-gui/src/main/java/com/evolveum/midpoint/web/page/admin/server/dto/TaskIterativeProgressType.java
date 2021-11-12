/*
 * Copyright (c) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.page.admin.server.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.evolveum.midpoint.schema.util.task.TaskPartPerformanceInformation;
import com.evolveum.midpoint.schema.util.task.TaskPartProgressInformation;
import com.evolveum.midpoint.schema.util.task.TaskPerformanceInformation;

import org.apache.wicket.model.StringResourceModel;

import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.web.page.admin.server.TaskDisplayUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.wicket.chartjs.*;

public class TaskIterativeProgressType implements Serializable {

    public static final String F_SUCCESS_BOX = "successBox";
    public static final String F_FAILED_BOX = "failedBox";
    public static final String F_SKIP_BOX = "skipBox";
    public static final String F_CURRENT_ITEMS = "currentItems";
    public static final String F_PROGRESS = "progress";
    public static final String F_TITLE = "title";
    public static final String F_WALLCLOCK_THROUGHPUT = "wallClockThroughput";

    private ProcessedItemSetType successProcessedItemSetType;
    private ProcessedItemSetType failureProcessedItemSetType;
    private ProcessedItemSetType skippedProcessedItemSetType;

    private List<ProcessedItemDto> currentItems = new ArrayList<>();

    private PieChartConfiguration progress;
//    private String title = "";
//    private String wallClockThroughput;

    TaskPartPerformanceInformation performanceInformation;

    public TaskIterativeProgressType(IterativeTaskPartItemsProcessingInformationType processingInfoType, TaskType taskType) {
        for (ProcessedItemSetType processedItem : processingInfoType.getProcessed()) {
            QualifiedItemProcessingOutcomeType outcome = processedItem.getOutcome();
            if (outcome == null) {
                continue;
            }
            parseItemForOutcome(outcome.getOutcome(), processedItem);
        }
        for (ProcessedItemType currentItem : processingInfoType.getCurrent()) {
            currentItems.add(new ProcessedItemDto(currentItem));
        }

        createChartConfiguration();
        performanceInformation = createPerformanceInformation(taskType, processingInfoType.getPartUri());
    }

    private void parseItemForOutcome(ItemProcessingOutcomeType outcome, ProcessedItemSetType processedItem) {
        switch (outcome) {
            case SUCCESS:
                this.successProcessedItemSetType = processedItem;
                break;
            case FAILURE:
                this.failureProcessedItemSetType = processedItem;
                break;
            case SKIP:
                this.skippedProcessedItemSetType = processedItem;
                break;
        }
    }

    public TaskInfoBoxType getSuccessBox() {
        return createInfoBoxType("success", successProcessedItemSetType, "bg-green", "fa fa-check");
    }

    public TaskInfoBoxType getFailedBox() {
        return createInfoBoxType("failure", failureProcessedItemSetType, "bg-red", "fa fa-close");
    }

    public TaskInfoBoxType getSkipBox() {
        return createInfoBoxType("skip", skippedProcessedItemSetType, "bg-gray", "fe fe-skip-step-object");
    }

    private TaskInfoBoxType createInfoBoxType(String title, ProcessedItemSetType processedsetType, String background, String icon) {
        if (processedsetType == null || processedsetType.getLastItem() == null) {
            return null;
        }
        ProcessedItemType processedItem = processedsetType.getLastItem();
        TaskInfoBoxType taskInfoBoxType = createInfoBoxType(createInfoBoxMessage(title, processedsetType), processedItem, background, icon);
        return taskInfoBoxType;
    }

    private String createInfoBoxMessage(String result, ProcessedItemSetType processedItemSetType) {
        return getString("TaskIterativeProgress.box.title." + result, getFormattedDate(processedItemSetType));
    }

    private TaskInfoBoxType createInfoBoxType(String title, ProcessedItemType processedItem, String background, String icon) {
        TaskInfoBoxType infoBoxType = new TaskInfoBoxType(background, icon, title);
        infoBoxType.setNumber(processedItem.getName());

        Long end = WebComponentUtil.getTimestampAsLong(processedItem.getEndTimestamp(), true);
        Long start = WebComponentUtil.getTimestampAsLong(processedItem.getStartTimestamp(), true);

        infoBoxType.setDuration(end - start);

        infoBoxType.setErrorMessage(processedItem.getMessage());
        return infoBoxType;
    }

    private String getFormattedDate(ProcessedItemSetType processedSetItem) {
        if (processedSetItem == null) {
            return null;
        }
        ProcessedItemType processedItem = processedSetItem.getLastItem();
        Long end = WebComponentUtil.getTimestampAsLong(processedItem.getEndTimestamp(), true);
        return WebComponentUtil.formatDate(end == 0 ? processedItem.getStartTimestamp() : processedItem.getEndTimestamp());
    }

    private TaskPartPerformanceInformation createPerformanceInformation(TaskType taskType, String partUri) {
        TaskPerformanceInformation taskPerformanceInformation = TaskPerformanceInformation.fromTaskTree(taskType);
        return taskPerformanceInformation.getParts().get(partUri);
    }

    public String getTitle() {
        if (performanceInformation != null && performanceInformation.getPartUri() != null) {
            return getString("TaskIterativeProgress.part." + performanceInformation.getPartUri(), performanceInformation.getItemsProcessed());
        }
        return getString("TaskOperationStatisticsPanel.processingInfo",
                performanceInformation == null ? "" : performanceInformation.getItemsProcessed());
    }

    public String getWallClockThroughput() {
        if (containsPerfInfo()) {
            return getString("TaskIterativeProgress.wallClock.throughput", performanceInformation.getAverageWallClockTime(), performanceInformation.getThroughput());
        }
        return null;
    }

    private boolean containsPerfInfo() {
        return performanceInformation != null && performanceInformation.getAverageWallClockTime() != null && performanceInformation.getThroughput() != null;
    }

    public int getTotalCount() {
        int success = getCount(successProcessedItemSetType);
        int failure = getCount(failureProcessedItemSetType);
        int skipped = getCount(skippedProcessedItemSetType);

        return success + failure + skipped;
    }

    private int getCount(ProcessedItemSetType item) {
        if (item == null) {
            return 0;
        }

        Integer count = item.getCount();
        if (count == null) {
            return 0;
        }

        return count;
    }

    private void createChartConfiguration() {
        progress = new PieChartConfiguration();

        ChartData chartData = new ChartData();
        chartData.addDataset(createDataset());

        chartData.addLabel(getString("TaskIterativeProgress.success", getCount(successProcessedItemSetType)));
        chartData.addLabel(getString("TaskIterativeProgress.failure", getCount(failureProcessedItemSetType)));
        chartData.addLabel(getString("TaskIterativeProgress.skip", getCount(skippedProcessedItemSetType)));

        progress.setData(chartData);

        progress.setOptions(createChartOptions());
    }

    private String getString(String key, Object... params) {
        StringResourceModel stringModel = new StringResourceModel(key).setDefaultValue(key).setParameters(params);
        return stringModel.getString();
    }

    private ChartDataset createDataset() {
        ChartDataset dataset = new ChartDataset();
        dataset.addData(getCount(successProcessedItemSetType));
        dataset.addData(getCount(failureProcessedItemSetType));
        dataset.addData(getCount(skippedProcessedItemSetType));

        dataset.addBackgroudColor("rgba(73, 171, 101)");
        dataset.addBackgroudColor("rgba(168, 44, 44)");
        dataset.addBackgroudColor("rgba(145, 145, 145)");
        return dataset;
    }

    private ChartOptions createChartOptions() {
        ChartOptions options = new ChartOptions();
        options.setAnimation(createAnimation());
        options.setLegend(createChartLegend());
        return options;
    }

    private ChartAnimationOption createAnimation() {
        ChartAnimationOption animationOption = new ChartAnimationOption();
        animationOption.setDuration(0);
        return animationOption;
    }

    private ChartLegendOption createChartLegend() {
        ChartLegendOption legend = new ChartLegendOption();
        legend.setPosition("right");
        ChartLegendLabel label = new ChartLegendLabel();
        label.setBoxWidth(15);
        legend.setLabels(label);
        return legend;
    }

    public boolean existPerformanceInformation() {
        return performanceInformation != null;
    }
}
