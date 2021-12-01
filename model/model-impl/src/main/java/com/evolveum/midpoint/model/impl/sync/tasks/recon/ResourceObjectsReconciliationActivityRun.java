/*
 * Copyright (c) 2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.sync.tasks.recon;

import java.util.Collection;

import com.evolveum.midpoint.repo.common.activity.run.ActivityRunException;

import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.model.impl.sync.tasks.Synchronizer;
import com.evolveum.midpoint.repo.common.activity.run.ActivityRunInstantiationContext;
import com.evolveum.midpoint.repo.common.activity.run.ActivityReportingCharacteristics;
import com.evolveum.midpoint.repo.common.activity.run.processing.ItemProcessingRequest;
import com.evolveum.midpoint.repo.common.activity.run.buckets.ItemDefinitionProvider;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.RunningTask;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FetchErrorReportingMethodType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;

/**
 * Execution of resource objects reconciliation (the main part of reconciliation).
 */
public final class ResourceObjectsReconciliationActivityRun
        extends PartialReconciliationActivityRun {

    private Synchronizer synchronizer;

    ResourceObjectsReconciliationActivityRun(
            @NotNull ActivityRunInstantiationContext<ReconciliationWorkDefinition, ReconciliationActivityHandler> context,
            String shortNameCapitalized) {
        super(context, shortNameCapitalized);
        setInstanceReady();
    }

    @Override
    public void beforeRun(OperationResult result) throws CommonException, ActivityRunException {
        super.beforeRun(result);
        synchronizer = createSynchronizer();
    }

    @Override
    public @NotNull ActivityReportingCharacteristics createReportingCharacteristics() {
        return new ActivityReportingCharacteristics()
                .actionsExecutedStatisticsSupported(true)
                .synchronizationStatisticsSupported(true);
    }

    private Synchronizer createSynchronizer() {
        return new Synchronizer(
                resourceObjectClass.getResource(),
                resourceObjectClass.getObjectClassDefinitionRequired(),
                objectsFilter,
                getModelBeans().eventDispatcher,
                SchemaConstants.CHANNEL_RECON,
                isPreview(),
                false);
    }

    // Ignoring configured search options. TODO ok?
    @Override
    public Collection<SelectorOptions<GetOperationOptions>> customizeSearchOptions(
            Collection<SelectorOptions<GetOperationOptions>> configuredOptions, OperationResult result) {
        // This is necessary to give ItemProcessingGatekeeper a chance to "see" errors in preprocessing.
        // At the same time, it ensures that an exception in preprocessing does not kill the whole searchObjectsIterative call.
        return getBeans().schemaService.getOperationOptionsBuilder()
                .errorReportingMethod(FetchErrorReportingMethodType.FETCH_RESULT)
                .build();
    }

    @Override
    public ItemDefinitionProvider createItemDefinitionProvider() {
        return resourceObjectClass.createItemDefinitionProvider();
    }

    @Override
    public boolean processItem(@NotNull ShadowType object,
            @NotNull ItemProcessingRequest<ShadowType> request, RunningTask workerTask, OperationResult result)
            throws CommonException, ActivityRunException {
        synchronizer.synchronize(object.asPrismObject(), request.getIdentifier(), workerTask, result);
        return true;
    }

    @VisibleForTesting
    public long getResourceReconCount() {
        return transientRunStatistics.getItemsProcessed();
    }

    @VisibleForTesting
    public long getResourceReconErrors() {
        return transientRunStatistics.getErrors();
    }
}
