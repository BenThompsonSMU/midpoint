/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.api.correlator;

import com.evolveum.midpoint.model.api.CorrelationProperty;

import com.evolveum.midpoint.util.annotation.Experimental;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.*;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Supports correlation.
 */
@Experimental
public interface CorrelationService {

    /**
     * Instantiates the correlator for given correlation case.
     */
    Correlator instantiateCorrelator(
            @NotNull CaseType aCase,
            @NotNull Task task,
            @NotNull OperationResult result)
            throws SchemaException, ConfigurationException, ExpressionEvaluationException, CommunicationException,
            SecurityViolationException, ObjectNotFoundException;

    /**
     * Instantiates the correlator for given shadow.
     *
     * TODO consider removal (seems to be unused)
     */
    @NotNull Correlator instantiateCorrelator(
            @NotNull ShadowType shadow,
            @NotNull Task task,
            @NotNull OperationResult result)
            throws SchemaException, ExpressionEvaluationException, CommunicationException, SecurityViolationException,
            ConfigurationException, ObjectNotFoundException;

    /**
     * Completes given correlation case.
     *
     * Preconditions:
     *
     * - case is freshly fetched,
     * - case is a correlation one
     */
    void completeCorrelationCase(
            @NotNull CaseType currentCase,
            @NotNull CaseCloser closeCaseInRepository,
            @NotNull Task task,
            @NotNull OperationResult result)
            throws SchemaException, ExpressionEvaluationException, CommunicationException, SecurityViolationException,
            ConfigurationException, ObjectNotFoundException;

    /**
     * Returns properties relevant for the correlation, e.g. to be shown in GUI.
     */
    Collection<CorrelationProperty> getCorrelationProperties(
            @NotNull CaseType aCase,
            @NotNull Task task,
            @NotNull OperationResult result)
            throws SchemaException, ConfigurationException, ExpressionEvaluationException, CommunicationException,
            SecurityViolationException, ObjectNotFoundException;

    /**
     * Creates the root correlator context for given configuration.
     */
    @NotNull CorrelatorContext<?> createRootCorrelatorContext(
            @NotNull CompositeCorrelatorType correlators,
            @Nullable CorrelationDefinitionType correlationDefinitionBean,
            @Nullable SystemConfigurationType systemConfiguration) throws ConfigurationException, SchemaException;

    /**
     * Clears the correlation state of a shadow.
     *
     * Does not do unlinking (if the shadow is linked)!
     */
    void clearCorrelationState(@NotNull String shadowOid, @NotNull OperationResult result) throws ObjectNotFoundException;

    /**
     * Executes the correlation for a given shadow.
     */
    @VisibleForTesting
    CorrelationResult correlate(
            @NotNull ShadowType shadow,
            @NotNull Task task,
            @NotNull OperationResult result)
            throws SchemaException, ExpressionEvaluationException, CommunicationException, SecurityViolationException,
            ConfigurationException, ObjectNotFoundException;

    @FunctionalInterface
    interface CaseCloser {
        /** Closes the case in repository. */
        void closeCaseInRepository(OperationResult result) throws ObjectNotFoundException;
    }
}
