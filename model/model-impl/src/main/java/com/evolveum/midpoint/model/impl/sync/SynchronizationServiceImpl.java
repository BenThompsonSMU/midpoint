/*

 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.impl.sync;

import static com.evolveum.midpoint.common.SynchronizationUtils.createSynchronizationSituationAndDescriptionDelta;
import static com.evolveum.midpoint.model.impl.sync.SynchronizationServiceUtils.isLogDebug;
import static com.evolveum.midpoint.prism.PrismObject.asObjectable;
import static com.evolveum.midpoint.prism.PrismPropertyValue.getRealValue;
import static com.evolveum.midpoint.schema.GetOperationOptions.createReadOnlyCollection;
import static com.evolveum.midpoint.schema.internals.InternalsConfig.consistencyChecks;

import java.util.List;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.evolveum.midpoint.common.Clock;
import com.evolveum.midpoint.common.SynchronizationUtils;
import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.model.api.correlator.CorrelationResult;
import com.evolveum.midpoint.model.common.SystemObjectCache;
import com.evolveum.midpoint.model.common.expression.ExpressionEnvironment;
import com.evolveum.midpoint.model.common.expression.ModelExpressionThreadLocalHolder;
import com.evolveum.midpoint.model.impl.ModelBeans;
import com.evolveum.midpoint.model.impl.lens.*;
import com.evolveum.midpoint.model.impl.util.ModelImplUtils;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.*;
import com.evolveum.midpoint.prism.delta.builder.S_ItemEntry;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.provisioning.api.ResourceObjectShadowChangeDescription;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.repo.common.expression.ExpressionFactory;
import com.evolveum.midpoint.repo.common.expression.ExpressionUtil;
import com.evolveum.midpoint.schema.ResourceShadowDiscriminator;
import com.evolveum.midpoint.schema.SchemaConstantsGenerated;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.constants.ExpressionConstants;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.schema.processor.RefinedDefinitionUtil;
import com.evolveum.midpoint.schema.processor.ResourceObjectTypeDefinition;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.schema.util.ShadowUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskUtil;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * Synchronization service receives change notifications from provisioning. It
 * decides which synchronization policy to use and evaluates it (correlation,
 * confirmation, situations, reaction, ...)
 * <p>
 * Note: don't autowire this bean by implementing class, as it is
 * proxied by Spring AOP. Use the interface instead.
 *
 * TODO improve the error handling for the whole class
 *
 * @author lazyman
 * @author Radovan Semancik
 */
@Service(value = "synchronizationService")
public class SynchronizationServiceImpl implements SynchronizationService {

    private static final Trace LOGGER = TraceManager.getTrace(SynchronizationServiceImpl.class);

    private static final String CLASS_NAME_WITH_DOT = SynchronizationServiceImpl.class.getName() + ".";

    private static final String OP_SETUP_SITUATION = CLASS_NAME_WITH_DOT + "setupSituation";
    private static final String OP_NOTIFY_CHANGE = CLASS_NAME_WITH_DOT + "notifyChange";

    @Autowired private ActionManager<Action> actionManager;
    @Autowired private SynchronizationExpressionsEvaluator synchronizationExpressionsEvaluator;
    @Autowired private ContextFactory contextFactory;
    @Autowired private Clockwork clockwork;
    @Autowired private ExpressionFactory expressionFactory;
    @Autowired private SystemObjectCache systemObjectCache;
    @Autowired private PrismContext prismContext;
    @Autowired private Clock clock;
    @Autowired private ClockworkMedic clockworkMedic;
    @Autowired private ModelBeans beans;

    @Autowired
    @Qualifier("cacheRepositoryService")
    private RepositoryService repositoryService;

    @Override
    public void notifyChange(@NotNull ResourceObjectShadowChangeDescription change, Task task, OperationResult parentResult) {

        OperationResult result = parentResult.subresult(OP_NOTIFY_CHANGE)
                .addArbitraryObjectAsParam("change", change)
                .addArbitraryObjectAsContext("task", task)
                .build();

        try {
            logStart(change);
            checkConsistence(change);

            SynchronizationContext<?> syncCtx = loadSynchronizationContextFromChange(change, task, result);

            syncCtx.checkNotInMaintenance(result);

            if (shouldSkipSynchronization(syncCtx, result)) {
                return;
            }

            setupLinkedOwnerAndSituation(syncCtx, change, result);

            task.onSynchronizationStart(change.getItemProcessingIdentifier(), change.getShadowOid(), syncCtx.getSituation());

            ExecutionModeType executionMode = TaskUtil.getExecutionMode(task);
            boolean fullSync = executionMode == ExecutionModeType.FULL;
            boolean dryRun = executionMode == ExecutionModeType.DRY_RUN;

            saveSyncMetadata(syncCtx, change, fullSync, result);

            if (!dryRun) {
                reactToChange(syncCtx, change, result);
            }

            LOGGER.debug("SYNCHRONIZATION: DONE (mode '{}') for {}", executionMode, change.getShadowedResourceObject());

        } catch (SystemException ex) {
            // avoid unnecessary re-wrap
            result.recordFatalError(ex);
            throw ex;
        } catch (Exception ex) {
            result.recordFatalError(ex);
            throw new SystemException(ex);
        } finally {
            result.computeStatusIfUnknown();
        }
    }

    private void logStart(@NotNull ResourceObjectShadowChangeDescription change) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("SYNCHRONIZATION: received change notification:\n{}", DebugUtil.debugDump(change, 1));
        } else if (isLogDebug(change)) {
            LOGGER.debug("SYNCHRONIZATION: received change notification {}", change);
        }
    }

    /**
     * TODO: Consider situations when one account belongs to two different users. It should correspond to
     *  the {@link SynchronizationSituationType#DISPUTED} situation.
     */
    @Nullable
    private PrismObject<FocusType> findShadowOwner(String shadowOid, OperationResult result) throws SchemaException {
        ObjectQuery query = prismContext.queryFor(FocusType.class)
                .item(FocusType.F_LINK_REF).ref(shadowOid, null, PrismConstants.Q_ANY)
                .build();
        // TODO read-only later
        SearchResultList<PrismObject<FocusType>> owners =
                repositoryService.searchObjects(FocusType.class, query, null, result);

        if (owners.isEmpty()) {
            return null;
        }
        if (owners.size() > 1) {
            LOGGER.warn("Found {} owners for shadow oid {}, returning first owner.", owners.size(), shadowOid);
        }
        return owners.get(0);
    }

    private SynchronizationContext<FocusType> loadSynchronizationContextFromChange(
            @NotNull ResourceObjectShadowChangeDescription change, Task task, OperationResult result)
            throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException,
            CommunicationException, ConfigurationException, SecurityViolationException {

        PrismObject<ResourceType> resource =
                MiscUtil.requireNonNull(
                        change.getResource(),
                        () -> new IllegalStateException("No resource in change description: " + change));

        SynchronizationContext<FocusType> syncCtx = loadSynchronizationContext(
                change.getShadowedResourceObject(),
                change.getObjectDelta(),
                resource,
                change.getSourceChannel(),
                change.getItemProcessingIdentifier(),
                null,
                task,
                result);
        if (Boolean.FALSE.equals(change.getShadowExistsInRepo())) {
            syncCtx.setShadowExistsInRepo(false);
            // TODO shadowExistsInRepo in syncCtx perhaps should be tri-state as well
        }
        LOGGER.trace("SYNCHRONIZATION context created: {}", syncCtx);
        return syncCtx;
    }

    @Override
    public <F extends FocusType> SynchronizationContext<F> loadSynchronizationContext(
            @NotNull PrismObject<ShadowType> shadowedResourceObject,
            ObjectDelta<ShadowType> resourceObjectDelta,
            @NotNull PrismObject<ResourceType> resource,
            String sourceChanel,
            String itemProcessingIdentifier,
            PrismObject<SystemConfigurationType> explicitSystemConfiguration,
            Task task,
            OperationResult result)
            throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException,
            CommunicationException, ConfigurationException, SecurityViolationException {

        SynchronizationContext<F> syncCtx = new SynchronizationContext<>(
                shadowedResourceObject,
                resourceObjectDelta,
                resource,
                sourceChanel,
                beans,
                task,
                itemProcessingIdentifier);
        setSystemConfiguration(syncCtx, explicitSystemConfiguration, result);

        SynchronizationType synchronization = resource.asObjectable().getSynchronization();
        if (synchronization == null) {
            LOGGER.trace("No synchronization configuration, exiting");
            return syncCtx;
        }

        ObjectSynchronizationDiscriminatorType synchronizationDiscriminator =
                evaluateSynchronizationSorter(synchronization, syncCtx, task, result);
        if (synchronizationDiscriminator != null) {
            syncCtx.setForceIntentChange(true);
            LOGGER.trace("Setting synchronization situation to synchronization context: {}",
                    synchronizationDiscriminator.getSynchronizationSituation());
            syncCtx.setSituation(synchronizationDiscriminator.getSynchronizationSituation());

            // TODO This is dubious: How could be the linked owner set in syncCtx at this moment?
            F owner = syncCtx.getLinkedOwner();
            if (owner != null && alreadyLinked(owner, syncCtx.getShadowedResourceObject())) {
                LOGGER.trace("Setting linked owner in synchronization context: {}", synchronizationDiscriminator.getOwner());
                //noinspection unchecked
                syncCtx.setLinkedOwner((F) synchronizationDiscriminator.getOwner());
            }

            LOGGER.trace("Setting correlated owner in synchronization context: {}", synchronizationDiscriminator.getOwner());
            //noinspection unchecked
            syncCtx.setCorrelatedOwner((F) synchronizationDiscriminator.getOwner());
        }

        for (ObjectSynchronizationType objectSynchronization : synchronization.getObjectSynchronization()) {
            if (isPolicyApplicable(objectSynchronization, synchronizationDiscriminator, syncCtx, result)) {
                syncCtx.setObjectSynchronization(objectSynchronization);
                break;
            }
        }

        processTag(syncCtx, result);

        return syncCtx;
    }

    private <F extends FocusType> void setSystemConfiguration(SynchronizationContext<F> syncCtx,
            PrismObject<SystemConfigurationType> explicitSystemConfiguration, OperationResult result) throws SchemaException {
        if (explicitSystemConfiguration != null) {
            syncCtx.setSystemConfiguration(explicitSystemConfiguration);
        } else {
            syncCtx.setSystemConfiguration(systemObjectCache.getSystemConfiguration(result));
        }
    }

    private <F extends FocusType> void processTag(SynchronizationContext<F> syncCtx, OperationResult result)
            throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, CommunicationException,
            ConfigurationException, SecurityViolationException {
        PrismObject<ShadowType> applicableShadow = syncCtx.getShadowedResourceObject();
        if (applicableShadow.asObjectable().getTag() != null) {
            return;
        }
        ResourceObjectTypeDefinition rOcd = syncCtx.findObjectTypeDefinition();
        if (rOcd == null) {
            // We probably do not have kind/intent yet.
            return;
        }
        ResourceObjectMultiplicityType multiplicity = rOcd.getObjectMultiplicity();
        if (!RefinedDefinitionUtil.isMultiaccount(multiplicity)) {
            return;
        }
        String tag = synchronizationExpressionsEvaluator.generateTag(multiplicity, applicableShadow,
                syncCtx.getResource(), syncCtx.getSystemConfiguration(), "tag expression for " + applicableShadow, syncCtx.getTask(), result);
        LOGGER.debug("SYNCHRONIZATION: TAG generated: {}", tag);
        syncCtx.setTag(tag);
    }

    private <F extends FocusType> boolean isPolicyApplicable(ObjectSynchronizationType synchronizationPolicy,
            ObjectSynchronizationDiscriminatorType synchronizationDiscriminator, SynchronizationContext<F> syncCtx,
            OperationResult result)
            throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ConfigurationException, SecurityViolationException {
        return SynchronizationServiceUtils.isPolicyApplicable(synchronizationPolicy, synchronizationDiscriminator, expressionFactory, syncCtx, result);
    }

    private <F extends FocusType> ObjectSynchronizationDiscriminatorType evaluateSynchronizationSorter(
            @NotNull SynchronizationType synchronization, @NotNull SynchronizationContext<F> syncCtx,
            Task task, OperationResult result)
            throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, CommunicationException,
            ConfigurationException, SecurityViolationException {

        ObjectSynchronizationSorterType synchronizationSorter = synchronization.getObjectSynchronizationSorter();
        if (synchronizationSorter == null || synchronizationSorter.getExpression() == null) {
            return null;
        }

        ExpressionType classificationExpression = synchronizationSorter.getExpression();
        String desc = "synchronization divider type ";
        VariablesMap variables = ModelImplUtils.getDefaultVariablesMap(null, syncCtx.getShadowedResourceObject(), null,
                syncCtx.getResource(), syncCtx.getSystemConfiguration(), null, syncCtx.getPrismContext());
        variables.put(ExpressionConstants.VAR_CHANNEL, syncCtx.getChannel(), String.class);
        try {
            ModelExpressionThreadLocalHolder.pushExpressionEnvironment(new ExpressionEnvironment<>(task, result));
            //noinspection unchecked
            PrismPropertyDefinition<ObjectSynchronizationDiscriminatorType> discriminatorDef = prismContext.getSchemaRegistry()
                    .findPropertyDefinitionByElementName(SchemaConstantsGenerated.C_OBJECT_SYNCHRONIZATION_DISCRIMINATOR);
            PrismPropertyValue<ObjectSynchronizationDiscriminatorType> discriminator = ExpressionUtil.evaluateExpression(
                    variables, discriminatorDef, classificationExpression, syncCtx.getExpressionProfile(),
                    expressionFactory, desc, task, result);
            return getRealValue(discriminator);
        } finally {
            ModelExpressionThreadLocalHolder.popExpressionEnvironment();
        }
    }

    /**
     * Checks for common reasons to skip synchronization:
     *
     * - no applicable synchronization policy,
     * - synchronization disabled,
     * - protected resource object.
     */
    private <F extends FocusType> boolean shouldSkipSynchronization(SynchronizationContext<F> syncCtx,
            OperationResult result) throws SchemaException {
        Task task = syncCtx.getTask();

        if (!syncCtx.hasApplicablePolicy()) {
            String message = "SYNCHRONIZATION no matching policy for " + syncCtx.getShadowedResourceObject() + " ("
                    + syncCtx.getShadowedResourceObject().asObjectable().getObjectClass() + ") " + " on " + syncCtx.getResource()
                    + ", ignoring change from channel " + syncCtx.getChannel();
            LOGGER.debug(message);
            List<PropertyDelta<?>> modifications = createShadowIntentAndSynchronizationTimestampDelta(syncCtx, false); // TODO record always full sync?
            executeShadowModifications(syncCtx.getShadowedResourceObject(), modifications, task, result);
            result.recordStatus(OperationResultStatus.NOT_APPLICABLE, message);
            task.onSynchronizationExclusion(syncCtx.getItemProcessingIdentifier(), SynchronizationExclusionReasonType.NO_SYNCHRONIZATION_POLICY);
            return true;
        }

        if (!syncCtx.isSynchronizationEnabled()) {
            String message = "SYNCHRONIZATION is not enabled for " + syncCtx.getResource()
                    + " ignoring change from channel " + syncCtx.getChannel();
            LOGGER.debug(message);
            List<PropertyDelta<?>> modifications = createShadowIntentAndSynchronizationTimestampDelta(syncCtx, true); // TODO record always full sync?
            executeShadowModifications(syncCtx.getShadowedResourceObject(), modifications, task, result);
            result.recordStatus(OperationResultStatus.NOT_APPLICABLE, message);
            task.onSynchronizationExclusion(syncCtx.getItemProcessingIdentifier(), SynchronizationExclusionReasonType.SYNCHRONIZATION_DISABLED);
            return true;
        }

        if (syncCtx.isProtected()) {
            List<PropertyDelta<?>> modifications = createShadowIntentAndSynchronizationTimestampDelta(syncCtx, true); // TODO record always full sync?
            executeShadowModifications(syncCtx.getShadowedResourceObject(), modifications, task, result);
            result.recordSuccess(); // Maybe "not applicable" would be better (it is so in Synchronizer class)
            task.onSynchronizationExclusion(syncCtx.getItemProcessingIdentifier(), SynchronizationExclusionReasonType.PROTECTED);
            LOGGER.debug("SYNCHRONIZATION: DONE for protected shadow {}", syncCtx.getShadowedResourceObject());
            return true;
        }

        return false;
    }

    private <F extends FocusType> List<PropertyDelta<?>> createShadowIntentAndSynchronizationTimestampDelta(
            SynchronizationContext<F> syncCtx, boolean saveIntent) throws SchemaException {
        Validate.notNull(syncCtx.getShadowedResourceObject(), "No current nor old shadow present");
        ShadowType applicableShadow = syncCtx.getShadowedResourceObject().asObjectable();
        List<PropertyDelta<?>> modifications = SynchronizationUtils.createSynchronizationTimestampsDeltas(syncCtx.getShadowedResourceObject());
        if (saveIntent) {
            if (StringUtils.isNotBlank(syncCtx.getIntent()) && !syncCtx.getIntent().equals(applicableShadow.getIntent())) {
                PropertyDelta<String> intentDelta = prismContext.deltaFactory().property().createModificationReplaceProperty(ShadowType.F_INTENT,
                        syncCtx.getShadowedResourceObject().getDefinition(), syncCtx.getIntent());
                modifications.add(intentDelta);
            }
            if (StringUtils.isNotBlank(syncCtx.getTag()) && !syncCtx.getTag().equals(applicableShadow.getTag())) {
                PropertyDelta<String> tagDelta = prismContext.deltaFactory().property().createModificationReplaceProperty(ShadowType.F_TAG,
                        syncCtx.getShadowedResourceObject().getDefinition(), syncCtx.getTag());
                modifications.add(tagDelta);
            }
        }
        return modifications;
    }

    private void executeShadowModifications(PrismObject<? extends ShadowType> object, List<PropertyDelta<?>> modifications,
            Task task, OperationResult subResult) {
        try {
            repositoryService.modifyObject(ShadowType.class, object.getOid(), modifications, subResult);
            task.recordObjectActionExecuted(object, ChangeType.MODIFY, null);
        } catch (Throwable t) {
            task.recordObjectActionExecuted(object, ChangeType.MODIFY, t);
        }
    }

    private <F extends FocusType> boolean alreadyLinked(F focus, PrismObject<ShadowType> shadow) {
        return focus.getLinkRef().stream().anyMatch(link -> link.getOid().equals(shadow.getOid()));
    }

    private void checkConsistence(ResourceObjectShadowChangeDescription change) {
        Validate.notNull(change, "Resource object shadow change description must not be null.");
        Validate.notNull(change.getShadowedResourceObject(), "Current shadow must not be null.");
        Validate.notNull(change.getResource(), "Resource in change must not be null.");

        if (consistencyChecks) {
            change.checkConsistence();
        }
    }

    private <F extends FocusType> void setupLinkedOwnerAndSituation(SynchronizationContext<F> syncCtx,
            ResourceObjectShadowChangeDescription change, OperationResult parentResult) throws SchemaException {

        OperationResult result = parentResult.subresult(OP_SETUP_SITUATION)
                .setMinor()
                .addArbitraryObjectAsParam("syncCtx", syncCtx)
                .addArbitraryObjectAsParam("change", change)
                .build();

        LOGGER.trace("Determining situation for resource object shadow. Focus class: {}. Applicable policy: {}.",
                syncCtx.getFocusClass(), syncCtx.getPolicyName());

        try {
            findLinkedOwner(syncCtx, change, result);

            if (syncCtx.getLinkedOwner() == null || syncCtx.isCorrelatorsUpdateRequested()) {
                determineSituationWithCorrelators(syncCtx, change, result);
            } else {
                determineSituationWithoutCorrelators(syncCtx, change, result);
            }
            logSituation(syncCtx, change);
        } catch (Exception ex) {
            result.recordFatalError(ex);
            LOGGER.error("Error occurred during resource object shadow owner lookup.");
            throw new SystemException(
                    "Error occurred during resource object shadow owner lookup, reason: " + ex.getMessage(), ex);
        } finally {
            result.close();
        }
    }

    private <F extends FocusType> void findLinkedOwner(SynchronizationContext<F> syncCtx,
            ResourceObjectShadowChangeDescription change, OperationResult result) throws SchemaException {

        if (syncCtx.getLinkedOwner() != null) {
            // TODO This never occurs. Clarify!
            return;
        }

        PrismObject<FocusType> owner = findShadowOwner(change.getShadowOid(), result);
        //noinspection unchecked
        syncCtx.setLinkedOwner((F) asObjectable(owner));
    }

    private <F extends FocusType> void determineSituationWithoutCorrelators(SynchronizationContext<F> syncCtx,
            ResourceObjectShadowChangeDescription change, OperationResult result) throws ConfigurationException {

        assert syncCtx.getLinkedOwner() != null;

        checkLinkedAndCorrelatedOwnersMatch(syncCtx, result);

        if (syncCtx.getSituation() == null) {
            if (change.isDelete()) {
                syncCtx.setSituation(SynchronizationSituationType.DELETED);
            } else {
                syncCtx.setSituation(SynchronizationSituationType.LINKED);
            }
        }
    }

    private <F extends FocusType> void checkLinkedAndCorrelatedOwnersMatch(SynchronizationContext<F> syncCtx,
            OperationResult result) throws ConfigurationException {
        F linkedOwner = syncCtx.getLinkedOwner();
        F correlatedOwner = syncCtx.getCorrelatedOwner(); // may be null; or may be provided by sync sorter

        LOGGER.trace("Shadow {} has linked owner: {}, correlated owner: {}", syncCtx.getShadowedResourceObject(),
                linkedOwner, correlatedOwner);

        if (correlatedOwner != null && linkedOwner != null && !correlatedOwner.getOid().equals(linkedOwner.getOid())) {
            LOGGER.error("Cannot synchronize {}, linked owner and expected owner are not the same. "
                    + "Linked owner: {}, expected owner: {}", syncCtx.getShadowedResourceObject(), linkedOwner, correlatedOwner);
            String msg = "Cannot synchronize " + syncCtx.getShadowedResourceObject()
                    + ", linked owner and expected owner are not the same. Linked owner: " + linkedOwner
                    + ", expected owner: " + correlatedOwner;
            result.recordFatalError(msg);
            throw new ConfigurationException(msg);
        }
    }

    /**
     * Tries to match specified focus and shadow. Return true if it matches,
     * false otherwise.
     */
    @Override
    public <F extends FocusType> boolean matchUserCorrelationRule(PrismObject<ShadowType> shadowedResourceObject,
            PrismObject<F> focus, ResourceType resourceType,
            PrismObject<SystemConfigurationType> configuration, Task task, OperationResult result)
            throws ConfigurationException, SchemaException, ObjectNotFoundException,
            ExpressionEvaluationException, CommunicationException, SecurityViolationException {

        SynchronizationContext<F> synchronizationContext = loadSynchronizationContext(
                shadowedResourceObject,
                null,
                resourceType.asPrismObject(),
                task.getChannel(),
                null,
                configuration,
                task,
                result);
        return synchronizationExpressionsEvaluator.matchFocusByCorrelationRule(synchronizationContext, focus, result);
    }

    /**
     * EITHER (todo update the description):
     *
     * account is not linked to user. you have to use correlation and
     * confirmation rule to be sure user for this account doesn't exists
     * resourceShadow only contains the data that were in the repository before
     * the change. But the correlation/confirmation should work on the updated
     * data. Therefore let's apply the changes before running
     * correlation/confirmation
     *
     * OR
     *
     * We need to update the correlator state.
     */
    private <F extends FocusType> void determineSituationWithCorrelators(SynchronizationContext<F> syncCtx,
            ResourceObjectShadowChangeDescription change, OperationResult result)
            throws CommonException {

        if (change.isDelete()) {
            // account was deleted and it was not linked; there is nothing to do (not even updating the correlators)
            if (syncCtx.getSituation() == null) {
                syncCtx.setSituation(SynchronizationSituationType.DELETED);
            }
            return;
        }

        if (!syncCtx.isCorrelatorsUpdateRequested() && syncCtx.getCorrelatedOwner() != null) { // e.g. from sync sorter
            LOGGER.trace("Correlated owner present in synchronization context: {}", syncCtx.getCorrelatedOwner());
            if (syncCtx.getSituation() == null) {
                syncCtx.setSituation(SynchronizationSituationType.UNLINKED);
            }
            return;
        }

        PrismObject<? extends ShadowType> resourceObject = change.getShadowedResourceObject();
        ResourceType resource = change.getResource().asObjectable();
        setupResourceRefInShadowIfNeeded(resourceObject.asObjectable(), resource);

        evaluatePreMappings(syncCtx, result);

        if (syncCtx.isUpdatingCorrelatorsOnly()) {
            new CorrelationProcessing<>(syncCtx, beans)
                    .update(result);
            return;
        }

        CorrelationResult correlationResult =
                new CorrelationProcessing<>(syncCtx, beans)
                        .correlate(result);

        LOGGER.debug("Correlation result:\n{}", correlationResult.debugDumpLazily(1));

        SynchronizationSituationType state;
        F owner;
        switch (correlationResult.getSituation()) {
            case EXISTING_OWNER:
                state = SynchronizationSituationType.UNLINKED;
                //noinspection unchecked
                owner = (F) correlationResult.getOwner();
                break;
            case NO_OWNER:
                state = SynchronizationSituationType.UNMATCHED;
                owner = null;
                break;
            case UNCERTAIN:
            case ERROR:
                state = SynchronizationSituationType.DISPUTED;
                owner = null;
                break;
            default:
                throw new AssertionError(correlationResult.getSituation());
        }
        LOGGER.debug("Determined synchronization situation: {} with owner: {}", state, owner);

        syncCtx.setCorrelatedOwner(owner);
        if (syncCtx.getSituation() == null) {
            syncCtx.setSituation(state);
        }

        if (correlationResult.isError()) {
            // This is a very crude and preliminary error handling: we just write pending deltas to the shadow
            // (if there are any), to have a record of the unsuccessful correlation. Normally, we should do this
            // along with the other sync metadata. But the error handling in this class is not ready for it (yet).
            savePendingDeltas(syncCtx, result);
            correlationResult.throwCommonOrRuntimeExceptionIfPresent();
            throw new AssertionError("Not here");
        }
    }

    private <F extends FocusType> void evaluatePreMappings(SynchronizationContext<F> syncCtx, OperationResult result)
            throws SchemaException, ExpressionEvaluationException, SecurityViolationException, CommunicationException,
            ConfigurationException, ObjectNotFoundException {
        new PreMappingsEvaluation<>(syncCtx, beans)
                .evaluate(result);
    }

    // This is maybe not needed
    private void setupResourceRefInShadowIfNeeded(ShadowType shadow, ResourceType resource) {
        if (shadow.getResourceRef() == null) {
            ObjectReferenceType reference = new ObjectReferenceType();
            reference.setOid(resource.getOid());
            reference.setType(ResourceType.COMPLEX_TYPE);
            shadow.setResourceRef(reference);
        }
    }

    private <F extends FocusType> void logSituation(SynchronizationContext<F> syncCtx, ResourceObjectShadowChangeDescription change) {
        String syncSituationValue = syncCtx.getSituation() != null ? syncCtx.getSituation().value() : null;
        if (isLogDebug(change)) {
            LOGGER.debug("SYNCHRONIZATION: SITUATION: '{}', currentOwner={}, correlatedOwner={}",
                    syncSituationValue, syncCtx.getLinkedOwner(),
                    syncCtx.getCorrelatedOwner());
        } else {
            LOGGER.trace("SYNCHRONIZATION: SITUATION: '{}', currentOwner={}, correlatedOwner={}",
                    syncSituationValue, syncCtx.getLinkedOwner(),
                    syncCtx.getCorrelatedOwner());
        }
    }

    private <F extends FocusType> void reactToChange(SynchronizationContext<F> syncCtx,
            ResourceObjectShadowChangeDescription change, OperationResult result)
            throws ConfigurationException, ObjectNotFoundException, SchemaException, SecurityViolationException,
            ExpressionEvaluationException, CommunicationException {

        LOGGER.trace("Synchronization context:\n{}", syncCtx.debugDumpLazily(1));

        SynchronizationReactionType reaction = syncCtx.getReaction(result);
        if (reaction == null) {
            LOGGER.trace("No reaction is defined for situation {} in {}", syncCtx.getSituation(), syncCtx.getResource());
            return;
        }

        if (!isSynchronize(reaction)) {
            LOGGER.trace("Skipping clockwork run on {} for situation {} because 'synchronize' is set to false (or there are "
                    + "no actions and 'synchronize' is not set).", syncCtx.getResource(), syncCtx.getSituation());
            return;
        }

        Task task = syncCtx.getTask();

        ModelExecuteOptions options = createOptions(syncCtx, change);
        LensContext<F> lensContext = createLensContext(syncCtx, change, options, result);
        lensContext.setDoReconciliationForAllProjections(BooleanUtils.isTrue(reaction.isReconcileAll()));
        LOGGER.trace("---[ SYNCHRONIZATION context before action execution ]-------------------------\n"
                + "{}\n------------------------------------------", lensContext.debugDumpLazily());

        // there's no point in calling executeAction without context - so
        // the actions are executed only if we are doing the synchronization
        executeActions(syncCtx, lensContext, BeforeAfterType.BEFORE, task, result);

        try {

            clockworkMedic.enterModelMethod(false);
            try {
                if (change.isSimulate()) {
                    clockwork.previewChanges(lensContext, null, task, result);
                } else {
                    clockwork.run(lensContext, task, result);
                }
            } finally {
                clockworkMedic.exitModelMethod(false);
            }

        } catch (Exception e) {
            LOGGER.error("SYNCHRONIZATION: Error in synchronization on {} for situation {}: {}: {}. Change was {}",
                    syncCtx.getResource(), syncCtx.getSituation(), e.getClass().getSimpleName(), e.getMessage(), change, e);
            // what to do here? We cannot throw the error back. All that the notifyChange method
            // could do is to convert it to SystemException. But that indicates an internal error and it will
            // break whatever code called the notifyChange in the first place. We do not want that.
            // If the clockwork could not do anything with the exception then perhaps nothing can be done at all.
            // So just log the error (the error should be remembered in the result and task already)
            // and then just go on.
        }

        // note: actions "AFTER" seem to be useless here (basically they
        // modify lens context - which is relevant only if followed by
        // clockwork run)
        executeActions(syncCtx, lensContext, BeforeAfterType.AFTER, task, result);
    }

    @NotNull
    private <F extends FocusType> ModelExecuteOptions createOptions(SynchronizationContext<F> syncCtx,
            ResourceObjectShadowChangeDescription change) {

        ModelExecuteOptionsType explicitOptions = syncCtx.getExecuteOptions();
        ModelExecuteOptions options = explicitOptions != null ?
                ModelExecuteOptions.fromModelExecutionOptionsType(explicitOptions) :
                ModelExecuteOptions.create(prismContext);

        if (options.getReconcile() == null) {
            Boolean doReconciliation = syncCtx.isDoReconciliation();
            if (doReconciliation != null) {
                options.reconcile(doReconciliation);
            } else {
                // We have to do reconciliation if we have got a full shadow and no delta.
                // There is no other good way how to reflect the changes from the shadow.
                if (change.getObjectDelta() == null) {
                    options.reconcile();
                }
            }
        }

        if (options.getLimitPropagation() == null) {
            options.limitPropagation(syncCtx.isLimitPropagation());
        }

        return options;
    }

    @NotNull
    private <F extends FocusType> LensContext<F> createLensContext(SynchronizationContext<F> syncCtx,
            ResourceObjectShadowChangeDescription change, ModelExecuteOptions options,
            OperationResult result) throws ObjectNotFoundException, SchemaException {

        LensContext<F> context = contextFactory.createSyncContext(syncCtx.getFocusClass(), change);
        context.setLazyAuditRequest(true);
        context.setSystemConfiguration(syncCtx.getSystemConfiguration());
        context.setOptions(options);
        context.setItemProcessingIdentifier(syncCtx.getItemProcessingIdentifier());

        ResourceType resource = change.getResource().asObjectable();
        if (ModelExecuteOptions.isLimitPropagation(options)) {
            context.setTriggeringResourceOid(resource);
        }

        context.rememberResource(resource);

        createProjectionContext(syncCtx, change, options, context);
        createFocusContext(syncCtx, context);

        setObjectTemplate(syncCtx, context, result);

        return context;
    }

    private <F extends FocusType> void createProjectionContext(SynchronizationContext<F> syncCtx,
            ResourceObjectShadowChangeDescription change, ModelExecuteOptions options, LensContext<F> context) throws SchemaException {
        ResourceType resource = change.getResource().asObjectable();
        PrismObject<ShadowType> shadow = syncCtx.getShadowedResourceObject();
        ShadowKindType kind = getKind(shadow, syncCtx.getKind());
        String intent = getIntent(shadow, syncCtx.getIntent());
        boolean tombstone = isTombstone(change);
        ResourceShadowDiscriminator discriminator = new ResourceShadowDiscriminator(resource.getOid(), kind, intent, shadow.asObjectable().getTag(), tombstone);
        LensProjectionContext projectionContext = context.createProjectionContext(discriminator);
        projectionContext.setResource(resource);
        projectionContext.setOid(change.getShadowOid());
        projectionContext.setSynchronizationSituationDetected(syncCtx.getSituation());
        projectionContext.setShadowExistsInRepo(syncCtx.isShadowExistsInRepo());
        projectionContext.setSynchronizationSource(true);

        // insert object delta if available in change
        ObjectDelta<ShadowType> delta = change.getObjectDelta();
        if (delta != null) {
            projectionContext.setSyncDelta(delta);
        } else {
            projectionContext.setSyncAbsoluteTrigger(true);
        }

        // This will set both old and current object: and that's how it should be.
        projectionContext.setInitialObject(shadow);

        if (!tombstone && !containsIncompleteItems(shadow)) {
            projectionContext.setFullShadow(true);
        }
        projectionContext.setFresh(true);
        projectionContext.setExists(!change.isDelete()); // TODO is this correct?
        projectionContext.setDoReconciliation(ModelExecuteOptions.isReconcile(options));
    }

    private <F extends FocusType> void createFocusContext(SynchronizationContext<F> syncCtx, LensContext<F> context) {
        if (syncCtx.getLinkedOwner() != null) {
            F owner = syncCtx.getLinkedOwner();
            LensFocusContext<F> focusContext = context.createFocusContext();
            //noinspection unchecked
            focusContext.setInitialObject((PrismObject<F>) owner.asPrismObject());
        }
    }

    private <F extends FocusType> void setObjectTemplate(SynchronizationContext<F> syncCtx, LensContext<F> context, OperationResult parentResult) throws ObjectNotFoundException, SchemaException {
        if (syncCtx.getObjectTemplateRef() != null) {
            ObjectTemplateType objectTemplate = repositoryService
                    .getObject(ObjectTemplateType.class, syncCtx.getObjectTemplateRef().getOid(), createReadOnlyCollection(), parentResult)
                    .asObjectable();
            context.setFocusTemplate(objectTemplate);
            context.setFocusTemplateExternallySet(true); // we do not want to override this template e.g. when subtype changes
        }
    }

    private boolean containsIncompleteItems(PrismObject<ShadowType> shadow) {
        ShadowAttributesType attributes = shadow.asObjectable().getAttributes();
        //noinspection SimplifiableIfStatement
        if (attributes == null) {
            return false;   // strictly speaking this is right; but we perhaps should not consider this shadow as fully loaded :)
        } else {
            return ((PrismContainerValue<?>) (attributes.asPrismContainerValue())).getItems().stream()
                    .anyMatch(Item::isIncomplete);
        }
    }

    private ShadowKindType getKind(PrismObject<ShadowType> shadow, ShadowKindType objectSynchronizationKind) {
        ShadowKindType shadowKind = shadow.asObjectable().getKind();
        if (shadowKind != null) {
            return shadowKind;
        }
        return objectSynchronizationKind;
    }

    private String getIntent(PrismObject<ShadowType> shadow,
            String objectSynchronizationIntent) {
        String shadowIntent = shadow.asObjectable().getIntent();
        if (shadowIntent != null) {
            return shadowIntent;
        }
        return objectSynchronizationIntent;
    }

    // TODO is this OK? What if the dead flag is obsolete?
    private boolean isTombstone(ResourceObjectShadowChangeDescription change) {
        PrismObject<? extends ShadowType> shadow = change.getShadowedResourceObject();
        if (shadow.asObjectable().isDead() != null) {
            return shadow.asObjectable().isDead();
        } else {
            return change.isDelete();
        }
    }

    private boolean isSynchronize(SynchronizationReactionType reactionDefinition) {
        if (reactionDefinition.isSynchronize() != null) {
            return reactionDefinition.isSynchronize();
        }
        return !reactionDefinition.getAction().isEmpty();
    }

    /**
     * Saves situation, timestamps, kind and intent (if needed)
     *
     * @param full if true, we consider this synchronization to be "full", and set the appropriate flag
     * in `synchronizationSituationDescription` as well as update `fullSynchronizationTimestamp`.
     */
    private <F extends FocusType> void saveSyncMetadata(SynchronizationContext<F> syncCtx,
            ResourceObjectShadowChangeDescription change, boolean full, OperationResult result) {

        try {
            PrismObject<ShadowType> shadow = syncCtx.getShadowedResourceObject();
            ShadowType shadowBean = shadow.asObjectable();

            // new situation description
            XMLGregorianCalendar now = clock.currentTimeXMLGregorianCalendar();
            syncCtx.addShadowDeltas(
                    createSynchronizationSituationAndDescriptionDelta(
                            shadow, syncCtx.getSituation(), change.getSourceChannel(), full, now));

            S_ItemEntry builder = prismContext.deltaFor(ShadowType.class);

            if (ShadowUtil.isNotKnown(shadowBean.getKind())) {
                builder = builder.item(ShadowType.F_KIND).replace(syncCtx.getKind());
            }

            if (shouldSaveIntent(syncCtx)) {
                builder = builder.item(ShadowType.F_INTENT).replace(syncCtx.getIntent());
            }

            if (shadowBean.getTag() == null && syncCtx.getTag() != null) {
                builder = builder.item(ShadowType.F_TAG).replace(syncCtx.getTag());
            }

            syncCtx.addShadowDeltas(
                    builder.asItemDeltas());

            savePendingDeltas(syncCtx, result);
        } catch (SchemaException e) {
            throw SystemException.unexpected(e, "while preparing shadow modifications");
        }
    }

    private void savePendingDeltas(SynchronizationContext<?> syncCtx, OperationResult result) throws SchemaException {
        Task task = syncCtx.getTask();
        PrismObject<ShadowType> shadow = syncCtx.getShadowedResourceObject();
        String channel = syncCtx.getChannel();

        try {
            beans.cacheRepositoryService.modifyObject(
                    ShadowType.class,
                    shadow.getOid(),
                    syncCtx.getPendingShadowDeltas(),
                    result);
            syncCtx.clearPendingShadowDeltas();
            task.recordObjectActionExecuted(shadow, null, null, ChangeType.MODIFY, channel, null);
        } catch (ObjectNotFoundException ex) {
            task.recordObjectActionExecuted(shadow, null, null, ChangeType.MODIFY, channel, ex);
            // This may happen e.g. during some recon-livesync interactions.
            // If the shadow is gone then it is gone. No point in recording the
            // situation any more.
            LOGGER.debug(
                    "Could not update situation in account, because shadow {} does not exist any more (this may be harmless)",
                    shadow.getOid());
            syncCtx.setShadowExistsInRepo(false);
            result.getLastSubresult().setStatus(OperationResultStatus.HANDLED_ERROR);
        } catch (ObjectAlreadyExistsException | SchemaException ex) {
            task.recordObjectActionExecuted(shadow, ChangeType.MODIFY, ex);
            LoggingUtils.logException(LOGGER,
                    "### SYNCHRONIZATION # notifyChange(..): Save of synchronization situation failed: could not modify shadow "
                            + shadow.getOid() + ": " + ex.getMessage(),
                    ex);
            result.recordFatalError("Save of synchronization situation failed: could not modify shadow "
                    + shadow.getOid() + ": " + ex.getMessage(), ex);
            throw new SystemException("Save of synchronization situation failed: could not modify shadow "
                    + shadow.getOid() + ": " + ex.getMessage(), ex);
        } catch (Throwable t) {
            task.recordObjectActionExecuted(shadow, ChangeType.MODIFY, t);
            throw t;
        }
    }

    private <F extends FocusType> boolean shouldSaveIntent(SynchronizationContext<F> syncCtx) throws SchemaException {
        ShadowType shadow = syncCtx.getShadowedResourceObject().asObjectable();
        if (shadow.getIntent() == null) {
            return true;
        }

        if (SchemaConstants.INTENT_UNKNOWN.equals(shadow.getIntent())) {
            return true;
        }

        if (syncCtx.isForceIntentChange()) {
            String objectSyncIntent = syncCtx.getIntent();
            //noinspection RedundantIfStatement
            if (!MiscSchemaUtil.equalsIntent(shadow.getIntent(), objectSyncIntent)) {
                return true;
            }
        }

        return false;
    }

    private <F extends FocusType> void executeActions(@NotNull SynchronizationContext<F> syncCtx, @NotNull LensContext<F> context,
            BeforeAfterType order, Task task, OperationResult parentResult)
            throws ConfigurationException, SchemaException, ObjectNotFoundException, CommunicationException,
            SecurityViolationException, ExpressionEvaluationException {

        SynchronizationReactionType reaction = syncCtx.getReaction(parentResult);
        for (SynchronizationActionType actionDef : reaction.getAction()) {
            if (orderMatches(actionDef, order)) {
                String handlerUri = MiscUtil.requireNonNull(actionDef.getHandlerUri(),
                        () -> new ConfigurationException("Action definition in resource " + syncCtx.getResource() +
                                " doesn't contain handler URI"));

                Action action = actionManager.getActionInstance(handlerUri);
                if (action == null) {
                    LOGGER.warn("Couldn't create action with uri '{}' in resource {}, skipping action.", handlerUri,
                            syncCtx.getResource());
                    continue;
                }

                if (SynchronizationServiceUtils.isLogDebug(syncCtx)) {
                    LOGGER.debug("SYNCHRONIZATION: ACTION: Executing: {}.", action.getClass());
                } else {
                    LOGGER.trace("SYNCHRONIZATION: ACTION: Executing: {}.", action.getClass());
                }
                SynchronizationSituation<F> situation = new SynchronizationSituation<>(syncCtx.getLinkedOwner(),
                        syncCtx.getCorrelatedOwner(), syncCtx.getSituation());
                action.handle(context, situation, null, task, parentResult);
            }
        }
    }

    private boolean orderMatches(SynchronizationActionType actionDef, BeforeAfterType order) {
        return (actionDef.getOrder() == null && order == BeforeAfterType.BEFORE)
                || (actionDef.getOrder() != null && actionDef.getOrder() == order);
    }

    @Override
    public String getName() {
        return "model synchronization service";
    }
}
