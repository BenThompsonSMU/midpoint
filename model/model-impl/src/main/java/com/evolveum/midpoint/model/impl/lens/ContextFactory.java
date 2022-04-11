/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.impl.lens;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

import com.evolveum.midpoint.schema.processor.ShadowCoordinatesQualifiedObjectDelta;
import com.evolveum.midpoint.prism.ConsistencyCheckScope;
import com.evolveum.midpoint.schema.ResourceShadowDiscriminator;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.apache.commons.lang.Validate;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.crypto.Protector;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.provisioning.api.ResourceObjectShadowChangeDescription;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.internals.InternalsConfig;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;

/**
 * @author semancik
 *
 */
@Component
public class ContextFactory {

    @Autowired PrismContext prismContext;
    @Autowired private ProvisioningService provisioningService;
    @Autowired Protector protector;

    public <F extends ObjectType> LensContext<F> createContext(
            Collection<ObjectDelta<? extends ObjectType>> deltas, ModelExecuteOptions options, Task task, OperationResult result)
            throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException,
            ExpressionEvaluationException {

        ObjectDelta<F> focusDelta = null;
        Collection<ObjectDelta<ShadowType>> projectionDeltas = new ArrayList<>(deltas.size());
        ObjectDelta<? extends ObjectType> confDelta = null;
        Class<F> focusClass = null;
        // Sort deltas to focus and projection deltas, check if the classes are correct;
        for (ObjectDelta<? extends ObjectType> delta: deltas) {
            Class<? extends ObjectType> typeClass = delta.getObjectTypeClass();
            Validate.notNull(typeClass, "Object type class is null in "+delta);
            if (isFocalClass(typeClass)) {
                if (confDelta != null) {
                    throw new IllegalArgumentException("Mixed configuration and focus deltas in one executeChanges invocation");
                }

                focusClass = (Class<F>) typeClass;
                if (!delta.isAdd() && delta.getOid() == null) {
                    throw new IllegalArgumentException("Delta "+delta+" does not have an OID");
                }
                if (InternalsConfig.consistencyChecks) {
                    // Focus delta has to be complete now with all the definition already in place
                    delta.checkConsistence(false, true, true, ConsistencyCheckScope.THOROUGH);
                } else {
                    delta.checkConsistence(ConsistencyCheckScope.MANDATORY_CHECKS_ONLY);        // TODO is this necessary? Perhaps it would be sufficient to check on model/repo entry
                }
                if (focusDelta != null) {
                    throw new IllegalStateException("More than one focus delta used in model operation");
                }
                // Make sure we clone request delta here. Clockwork will modify the delta (e.g. normalize it).
                // And we do not want to touch request delta. It may even be immutable.
                focusDelta = (ObjectDelta<F>) delta.clone();
            } else if (isProjectionClass(typeClass)) {
                if (confDelta != null) {
                    throw new IllegalArgumentException("Mixed configuration and projection deltas in one executeChanges invocation");
                }
                // Make sure we clone request delta here. Clockwork will modify the delta (e.g. normalize it).
                // And we do not want to touch request delta. It may even be immutable.
                ObjectDelta<ShadowType> projectionDelta = (ObjectDelta<ShadowType>) delta.clone();
                projectionDeltas.add(projectionDelta);
            } else {
                if (confDelta != null) {
                    throw new IllegalArgumentException("More than one configuration delta in a single executeChanges invocation");
                }
                // Make sure we clone request delta here. Clockwork will modify the delta (e.g. normalize it).
                // And we do not want to touch request delta. It may even be immutable.
                confDelta = delta.clone();
            }
        }

        if (confDelta != null) {
            //noinspection unchecked
            focusClass = (Class<F>) confDelta.getObjectTypeClass();
        }

        if (focusClass == null) {
            focusClass = determineFocusClass();
        }
        LensContext<F> context = new LensContext<>(focusClass);
        context.setChannel(task.getChannel());
        context.setOptions(options);
        context.setDoReconciliationForAllProjections(ModelExecuteOptions.isReconcile(options));

        if (confDelta != null) {
            LensFocusContext<F> focusContext = context.createFocusContext();
            //noinspection unchecked
            focusContext.setPrimaryDelta((ObjectDelta<F>) confDelta);

        } else {

            if (focusDelta != null) {
                LensFocusContext<F> focusContext = context.createFocusContext();
                focusContext.setPrimaryDelta(focusDelta);
            }

            for (ObjectDelta<ShadowType> projectionDelta: projectionDeltas) {
                LensProjectionContext projectionContext = context.createProjectionContext();
                if (context.isDoReconciliationForAllProjections()) {
                    projectionContext.setDoReconciliation(true);
                }
                projectionContext.setPrimaryDelta(projectionDelta);

                // We are little bit more liberal regarding projection deltas.
                // If the deltas represent shadows we tolerate missing attribute definitions.
                // We try to add the definitions by calling provisioning
                provisioningService.applyDefinition(projectionDelta, task, result);

                if (projectionDelta instanceof ShadowCoordinatesQualifiedObjectDelta) {
                    projectionContext.setResourceShadowDiscriminator(
                            new ResourceShadowDiscriminator(
                                    ((ShadowCoordinatesQualifiedObjectDelta<ShadowType>)projectionDelta).getCoordinates()));
                } else {
                    if (!projectionDelta.isAdd() && projectionDelta.getOid() == null) {
                        throw new IllegalArgumentException("Delta "+projectionDelta+" does not have an OID");
                    }
                }
            }

        }

        // This forces context reload before the next projection
        context.rot("context initialization");

        if (InternalsConfig.consistencyChecks) context.checkConsistence();

        context.finishBuild();
        return context;
    }


    public <F extends ObjectType, O extends ObjectType> LensContext<F> createRecomputeContext(
            @NotNull PrismObject<O> object, ModelExecuteOptions options, @NotNull Task task, @NotNull OperationResult result)
            throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException,
            ExpressionEvaluationException {
        Class<O> typeClass = Objects.requireNonNull(object.getCompileTimeClass(), "no object class");
        LensContext<F> context;
        if (AssignmentHolderType.class.isAssignableFrom(typeClass)) {
            //noinspection unchecked
            context = createRecomputeFocusContext((Class<F>)typeClass, (PrismObject<F>) object, options, task, result);
        } else if (ShadowType.class.isAssignableFrom(typeClass)) {
            //noinspection unchecked
            context = createRecomputeProjectionContext((PrismObject<ShadowType>) object, options, task, result);
        } else {
            throw new IllegalArgumentException("Cannot create recompute context for "+object);
        }
        context.setOptions(options);
        context.setLazyAuditRequest(true);
        return context;
    }

    private <F extends ObjectType> LensContext<F> createRecomputeFocusContext(
            Class<F> focusType, PrismObject<F> focus, ModelExecuteOptions options, Task task, OperationResult result) {
        LensContext<F> lensContext = new LensContext<>(focusType);
        LensFocusContext<F> focusContext = lensContext.createFocusContext();
        focusContext.setInitialObject(focus);
        focusContext.setOid(focus.getOid());
        lensContext.setChannel(SchemaConstants.CHANNEL_RECOMPUTE_URI);
        lensContext.setDoReconciliationForAllProjections(ModelExecuteOptions.isReconcile(options));
        return lensContext;
    }

    public <F extends ObjectType> LensContext<F> createRecomputeProjectionContext(
            @NotNull PrismObject<ShadowType> shadow, ModelExecuteOptions options, Task task, OperationResult result)
            throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException,
            ExpressionEvaluationException {
        provisioningService.applyDefinition(shadow, task, result);
        LensContext<F> lensContext = new LensContext<>(null);
        LensProjectionContext projectionContext = lensContext.createProjectionContext();
        projectionContext.setInitialObject(shadow);
        projectionContext.setOid(shadow.getOid());
        projectionContext.setDoReconciliation(ModelExecuteOptions.isReconcile(options));
        lensContext.setChannel(SchemaConstants.CHANNEL_RECOMPUTE_URI);
        return lensContext;
    }

     /**
     * Creates empty lens context for synchronization purposes, filling in only the very basic metadata (such as channel).
     */
    public <F extends ObjectType> LensContext<F> createSyncContext(Class<F> focusClass, ResourceObjectShadowChangeDescription change) {
        LensContext<F> context = new LensContext<>(focusClass);
        context.setChannel(change.getSourceChannel());
        return context;
    }

    public static <F extends ObjectType> Class<F> determineFocusClass() {
        // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        return (Class<F>) UserType.class;
    }

    private static <T extends ObjectType> Class<T> checkProjectionClass(Class<T> oldProjectionClass, Class<T> newProjectionClass) {
        if (oldProjectionClass == null) {
            return newProjectionClass;
        } else {
            if (oldProjectionClass != oldProjectionClass) {
                throw new IllegalArgumentException("Mixed projection classes in the deltas, got both "+oldProjectionClass+" and "+oldProjectionClass);
            }
            return oldProjectionClass;
        }
    }

    private static <T extends ObjectType> boolean isFocalClass(Class<T> aClass) {
        return FocusType.class.isAssignableFrom(aClass);
    }

    private boolean isProjectionClass(Class<? extends ObjectType> aClass) {
        return ShadowType.class.isAssignableFrom(aClass);
    }

}
