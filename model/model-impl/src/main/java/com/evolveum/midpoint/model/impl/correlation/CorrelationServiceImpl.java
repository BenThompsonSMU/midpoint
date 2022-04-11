/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.correlation;

import static com.evolveum.midpoint.prism.PrismObject.asObjectable;

import java.util.Collection;
import java.util.Objects;

import com.evolveum.midpoint.model.api.CorrelationProperty;
import com.evolveum.midpoint.model.impl.correlator.FullCorrelationContext;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.processor.ResourceObjectTypeDefinition;
import com.evolveum.midpoint.schema.processor.ResourceSchema;

import com.evolveum.midpoint.schema.processor.ResourceSchemaFactory;

import com.evolveum.midpoint.util.annotation.Experimental;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.model.api.correlator.*;
import com.evolveum.midpoint.model.common.SystemObjectCache;
import com.evolveum.midpoint.model.impl.ModelBeans;
import com.evolveum.midpoint.model.impl.correlator.CorrelatorUtil;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ShadowUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

@Experimental
@Component
public class CorrelationServiceImpl implements CorrelationService {

    @SuppressWarnings("unused")
    private static final Trace LOGGER = TraceManager.getTrace(CorrelationServiceImpl.class);

    @Autowired ModelBeans beans;
    @Autowired CorrelatorFactoryRegistry correlatorFactoryRegistry;
    @Autowired SystemObjectCache systemObjectCache;
    @Autowired CorrelationCaseManager correlationCaseManager;
    @Autowired @Qualifier("cacheRepositoryService") RepositoryService repositoryService;

    @Override
    public CorrelationResult correlate(
            @NotNull ShadowType shadowedResourceObject, @NotNull Task task, @NotNull OperationResult result)
            throws SchemaException, ExpressionEvaluationException, CommunicationException, SecurityViolationException,
            ConfigurationException, ObjectNotFoundException {
        FullCorrelationContext fullContext = getFullCorrelationContext(shadowedResourceObject, task, result);
        CorrelatorContext<?> correlatorContext = CorrelatorContextCreator.createRootContext(fullContext);
        CorrelationContext correlationContext = createCorrelationContext(fullContext, task, result);
        return correlatorFactoryRegistry
                .instantiateCorrelator(correlatorContext, task, result)
                .correlate(correlationContext, result);
    }

    private @NotNull FullCorrelationContext getFullCorrelationContext(
            @NotNull CaseType aCase,
            @NotNull Task task,
            @NotNull OperationResult result)
            throws SchemaException, ConfigurationException, ExpressionEvaluationException, CommunicationException,
            SecurityViolationException, ObjectNotFoundException {
        return getFullCorrelationContext(
                CorrelatorUtil.getShadowFromCorrelationCase(aCase),
                task, result);
    }

    private @NotNull FullCorrelationContext getFullCorrelationContext(ShadowType shadow, Task task, OperationResult result)
            throws SchemaException, ExpressionEvaluationException, CommunicationException, SecurityViolationException,
            ConfigurationException, ObjectNotFoundException {
        String resourceOid = ShadowUtil.getResourceOidRequired(shadow);
        ResourceType resource =
                beans.provisioningService.getObject(ResourceType.class, resourceOid, null, task, result).asObjectable();

        // We expect that the shadow is classified + reasonably fresh (= not legacy), so it has kind+intent present.
        ShadowKindType kind = MiscUtil.requireNonNull(shadow.getKind(), () -> new IllegalStateException("No kind in " + shadow));
        String intent = MiscUtil.requireNonNull(shadow.getIntent(), () -> new IllegalStateException("No intent in " + shadow));
        // TODO check for "unknown" ?

        // We'll look for type definition in the future (after synchronization is integrated into it).
        ResourceSchema schema = ResourceSchemaFactory.getCompleteSchema(resource);
        ResourceObjectTypeDefinition typeDefinition = schema.findObjectTypeDefinitionRequired(kind, intent);

        for (ObjectSynchronizationType config : resource.getSynchronization().getObjectSynchronization()) {
            if (config.getKind() == kind && intent.equals(config.getIntent())) {
                return new FullCorrelationContext(
                        shadow,
                        resource,
                        typeDefinition,
                        config,
                        MiscUtil.requireNonNull(
                                config.getCorrelationDefinition().getCorrelators(),
                                () -> new IllegalStateException("No correlators in " + config)),
                        asObjectable(beans.systemObjectCache.getSystemConfiguration(result)));
            }
        }
        throw new IllegalStateException(
                "No " + kind + "/" + intent + " (kind/intent) definition in " + resource + " (for " + shadow + ")");
    }

    @Override
    public Correlator instantiateCorrelator(
            @NotNull CaseType aCase,
            @NotNull Task task,
            @NotNull OperationResult result)
            throws SchemaException, ConfigurationException, ExpressionEvaluationException, CommunicationException,
            SecurityViolationException, ObjectNotFoundException {
        FullCorrelationContext fullContext = getFullCorrelationContext(aCase, task, result);
        CorrelatorContext<?> correlatorContext = CorrelatorContextCreator.createRootContext(fullContext);
        return correlatorFactoryRegistry.instantiateCorrelator(correlatorContext, task, result);
    }

    @Override
    public @NotNull Correlator instantiateCorrelator(
            @NotNull ShadowType shadowedResourceObject,
            @NotNull Task task,
            @NotNull OperationResult result)
            throws SchemaException, ExpressionEvaluationException, CommunicationException, SecurityViolationException,
            ConfigurationException, ObjectNotFoundException {
        FullCorrelationContext fullContext = getFullCorrelationContext(shadowedResourceObject, task, result);
        CorrelatorContext<?> correlatorContext = CorrelatorContextCreator.createRootContext(fullContext);
        return correlatorFactoryRegistry.instantiateCorrelator(correlatorContext, task, result);
    }

    private CorrelationContext createCorrelationContext(FullCorrelationContext fullContext, Task task, OperationResult result)
            throws SchemaException {
        Class<ObjectType> objectTypeClass = ObjectTypes.getObjectTypeClass(
                Objects.requireNonNull(
                        fullContext.synchronizationBean.getFocusType(),
                        () -> "No focus type for " + fullContext.typeDefinition));
        return new CorrelationContext(
                fullContext.shadow,
                (FocusType) PrismContext.get().createObjectable(objectTypeClass), // TODO
                fullContext.resource,
                fullContext.typeDefinition,
                asObjectable(systemObjectCache.getSystemConfiguration(result)),
                task);
    }

    @Override
    public void completeCorrelationCase(
            @NotNull CaseType currentCase,
            @NotNull CaseCloser caseCloser,
            @NotNull Task task,
            @NotNull OperationResult result)
            throws SchemaException, ExpressionEvaluationException, CommunicationException, SecurityViolationException,
            ConfigurationException, ObjectNotFoundException {
        correlationCaseManager.completeCorrelationCase(currentCase, caseCloser, task, result);
    }

    @Override
    public Collection<CorrelationProperty> getCorrelationProperties(
            @NotNull CaseType aCase, @NotNull Task task, @NotNull OperationResult result)
            throws SchemaException, ConfigurationException, ExpressionEvaluationException, CommunicationException,
            SecurityViolationException, ObjectNotFoundException {
        FullCorrelationContext fullCorrelationContext = getFullCorrelationContext(aCase, task, result);
        CorrelatorContext<?> correlatorContext = CorrelatorContextCreator.createRootContext(fullCorrelationContext);
        return new CorrelationPropertiesCreator(correlatorContext, fullCorrelationContext, aCase)
                .createProperties();
    }

    @Override
    public @NotNull CorrelatorContext<?> createRootCorrelatorContext(
            @NotNull CompositeCorrelatorType correlators,
            @Nullable CorrelationDefinitionType correlationDefinitionBean,
            @Nullable SystemConfigurationType systemConfiguration) throws ConfigurationException, SchemaException {
        return CorrelatorContextCreator.createRootContext(correlators, correlationDefinitionBean, systemConfiguration);
    }

    @Override
    public void clearCorrelationState(@NotNull String shadowOid, @NotNull OperationResult result)
            throws ObjectNotFoundException {
        try {
            repositoryService.modifyObject(
                    ShadowType.class,
                    shadowOid,
                    PrismContext.get().deltaFor(ShadowType.class)
                            .item(ShadowType.F_CORRELATION).replace()
                            .asItemDeltas(),
                    result);
        } catch (ObjectAlreadyExistsException | SchemaException e) {
            throw SystemException.unexpected(e, "when clearing shadow correlation state");
        }
    }
}
