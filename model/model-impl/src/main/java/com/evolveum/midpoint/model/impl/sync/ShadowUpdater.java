/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.sync;

import static com.evolveum.midpoint.common.SynchronizationUtils.createSynchronizationSituationDelta;
import static com.evolveum.midpoint.common.SynchronizationUtils.createSynchronizationSituationDescriptionDelta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.common.SynchronizationUtils;
import com.evolveum.midpoint.model.impl.ModelBeans;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.provisioning.api.ShadowSimulationData;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.schema.util.ShadowUtil;
import com.evolveum.midpoint.task.api.SimulationTransaction;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowKindType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;

/**
 * Offloads {@link SynchronizationServiceImpl} from duties related to updating synchronization/correlation metadata.
 *
 * Accumulates deltas in {@link #deltas} list (along with applying them to the current shadow) and writes them into
 * repository - or to simulation result - when the {@link #commit(OperationResult)} method is called.
 *
 * Besides that, provides the business methods that update the operational data in the shadow.
 */
class ShadowUpdater {

    private static final Trace LOGGER = TraceManager.getTrace(ShadowUpdater.class);

    @NotNull private final SynchronizationContext<?> syncCtx;
    @NotNull private final ModelBeans beans;
    @NotNull private final List<ItemDelta<?, ?>> deltas = new ArrayList<>();

    ShadowUpdater(@NotNull SynchronizationContext<?> syncCtx, @NotNull ModelBeans beans) {
        this.syncCtx = syncCtx;
        this.beans = beans;
    }

    ShadowUpdater updateAllSyncMetadataRespectingMode() throws SchemaException {
        assert syncCtx.isComplete();

        XMLGregorianCalendar now = beans.clock.currentTimeXMLGregorianCalendar();

        if (syncCtx.isExecutionFullyPersistent()) {
            updateSyncSituation();
            updateSyncSituationDescription(now);
            updateBasicSyncTimestamp(now); // this is questionable, but the same behavior is in LinkUpdater class
        }
        updateCoordinatesIfUnknown();

        return this;
    }

    private void updateSyncSituation() throws SchemaException {
        applyShadowDelta(
                createSynchronizationSituationDelta(syncCtx.getSituation()));
    }

    private void updateSyncSituationDescription(XMLGregorianCalendar now) throws SchemaException {
        applyShadowDelta(
                createSynchronizationSituationDescriptionDelta(
                        syncCtx.getShadowedResourceObject(),
                        syncCtx.getSituation(),
                        now,
                        syncCtx.getChannel(),
                        syncCtx.isFullMode()));
    }

    ShadowUpdater updateFullSyncTimestamp(XMLGregorianCalendar now) throws SchemaException {
        applyShadowDelta(
                SynchronizationUtils.createFullSynchronizationTimestampDelta(now));
        return this;
    }

    private void updateBasicSyncTimestamp(XMLGregorianCalendar now) throws SchemaException {
        applyShadowDelta(
                SynchronizationUtils.createSynchronizationTimestampDelta(now));
    }

    ShadowUpdater updateBothSyncTimestamps() throws SchemaException {
        XMLGregorianCalendar now = beans.clock.currentTimeXMLGregorianCalendar();
        updateBasicSyncTimestamp(now);
        updateFullSyncTimestamp(now);
        return this;
    }

    /**
     * Updates kind/intent if some of them are null/empty. This is used if synchronization is skipped.
     */
    ShadowUpdater updateCoordinatesIfMissing() throws SchemaException {
        assert syncCtx.isComplete();
        return updateCoordinates(false);
    }

    /**
     * Updates kind/intent if some of them are null/empty/unknown. This is used if synchronization is NOT skipped.
     *
     * TODO why the difference from {@link #updateCoordinatesIfMissing()}? Is there any reason for it?
     * TODO this behavior should be made configurable
     */
    private void updateCoordinatesIfUnknown() throws SchemaException {
        assert syncCtx.isComplete();
        updateCoordinates(true);
    }

    private ShadowUpdater updateCoordinates(boolean overwriteUnknownValues) throws SchemaException {
        assert syncCtx.isComplete();
        SynchronizationContext.Complete<?> completeCtx = (SynchronizationContext.Complete<?>) syncCtx;

        ShadowType shadow = completeCtx.getShadowedResourceObject();
        ShadowKindType shadowKind = shadow.getKind();
        String shadowIntent = shadow.getIntent();
        String shadowTag = shadow.getTag();
        ShadowKindType ctxKind = completeCtx.getTypeIdentification().getKind();
        String ctxIntent = completeCtx.getTypeIdentification().getIntent();
        String ctxTag = completeCtx.getTag();

        boolean typeEmpty = shadowKind == null || StringUtils.isBlank(shadowIntent);
        boolean typeNotKnown = ShadowUtil.isNotKnown(shadowKind) || ShadowUtil.isNotKnown(shadowIntent);

        // Are we going to update the kind/intent?
        boolean updateType =
                syncCtx.isForceClassificationUpdate() // typically when sorter is used
                        || typeEmpty
                        || typeNotKnown && overwriteUnknownValues;

        if (updateType) {
            // Before 4.6, the kind was updated unconditionally, only intent was driven by "force intent change" flag.
            // This is now changed to treat kind+intent as a single data item.
            if (ctxKind != shadowKind) {
                deltas.add(
                        PrismContext.get().deltaFor(ShadowType.class)
                                .item(ShadowType.F_KIND).replace(ctxKind)
                                .asItemDelta());
            }
            if (!ctxIntent.equals(shadowIntent)) {
                deltas.add(
                        PrismContext.get().deltaFor(ShadowType.class)
                                .item(ShadowType.F_INTENT).replace(ctxIntent)
                                .asItemDelta());
            }
        }

        if (StringUtils.isNotBlank(ctxTag) && !ctxTag.equals(shadowTag)) {
            deltas.add(
                    PrismContext.get().deltaFor(ShadowType.class)
                            .item(ShadowType.F_TAG).replace(ctxTag)
                            .asItemDelta());
        }

        return this;
    }

    @NotNull List<ItemDelta<?, ?>> getDeltas() {
        return deltas;
    }

    void applyShadowDeltas(@NotNull Collection<? extends ItemDelta<?, ?>> deltas) throws SchemaException {
        for (ItemDelta<?, ?> delta : deltas) {
            applyShadowDelta(delta);
        }
    }

    private void applyShadowDelta(ItemDelta<?, ?> delta) throws SchemaException {
        deltas.add(delta);
        delta.applyTo(syncCtx.getShadowedResourceObject().asPrismObject());
    }

    void commit(OperationResult result) {
        if (deltas.isEmpty()) {
            return;
        }
        try {
            if (syncCtx.getTask().areShadowChangesSimulated()) {
                commitToSimulation(result);
            } else {
                commitToRepository(result);
            }
            recordModificationExecuted(null);
        } catch (Throwable t) {
            recordModificationExecuted(t);
            throw t;
        }
        deltas.clear();
    }

    private void commitToSimulation(OperationResult result) {
        Task task = syncCtx.getTask();
        ShadowType shadow = syncCtx.getShadowedResourceObject();
        SimulationTransaction simulationTransaction = task.getSimulationTransaction();
        if (simulationTransaction == null) {
            LOGGER.debug("Ignoring simulation data because there is no simulation transaction: {}: {}", shadow, deltas);
        } else {
            simulationTransaction.writeSimulationData(
                    ShadowSimulationData.of(shadow, deltas), task, result);
        }
    }

    private void commitToRepository(OperationResult result) {
        try {
            beans.cacheRepositoryService.modifyObject(ShadowType.class, syncCtx.getShadowOid(), deltas, result);
        } catch (ObjectNotFoundException ex) {
            recordModificationExecuted(ex);
            // This may happen e.g. during some recon-livesync interactions.
            // If the shadow is gone then it is gone. No point in recording the
            // situation any more.
            LOGGER.debug("Could not update synchronization metadata in account, because shadow {} does not "
                    + "exist any more (this may be harmless)", syncCtx.getShadowOid());
            syncCtx.setShadowExistsInRepo(false);
            result.getLastSubresult().setStatus(OperationResultStatus.HANDLED_ERROR);
        } catch (ObjectAlreadyExistsException | SchemaException ex) {
            recordModificationExecuted(ex);
            String message = String.format(
                    "Save of synchronization metadata failed: could not modify shadow %s: %s",
                    syncCtx.getShadowOid(), ex.getMessage());
            LoggingUtils.logException(LOGGER, "### SYNCHRONIZATION # notifyChange(..): {}", ex, message);
            result.recordFatalError(message, ex);
            throw new SystemException(message, ex);
        }
    }

    private void recordModificationExecuted(Throwable t) {
        syncCtx.getTask().recordObjectActionExecuted(
                syncCtx.getShadowedResourceObject().asPrismObject(),
                null,
                null,
                ChangeType.MODIFY,
                syncCtx.getChannel(),
                t);
    }
}
