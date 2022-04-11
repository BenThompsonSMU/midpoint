/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.correlator;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.model.api.correlator.CorrelationContext;
import com.evolveum.midpoint.model.impl.util.ModelImplUtils;
import com.evolveum.midpoint.schema.constants.ExpressionConstants;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

public class CorrelatorUtil {

    public static <F extends ObjectType> void addCandidates(List<F> allCandidates, List<F> candidates, Trace logger) {
        for (F candidate : candidates) {
            if (!containsOid(allCandidates, candidate.getOid())) {
                logger.trace("Found candidate owner {}", candidate);
                allCandidates.add(candidate);
            } else {
                logger.trace("Candidate owner {} already processed", candidate);
            }
        }
    }

    public static <F extends ObjectType> boolean containsOid(List<F> allCandidates, String oid) {
        for (F existing : allCandidates) {
            if (existing.getOid().equals(oid)) {
                return true;
            }
        }
        return false;
    }

    public static VariablesMap getVariablesMap(
            ObjectType focus,
            ShadowType resourceObject,
            CorrelationContext correlationContext) {
        VariablesMap variables = ModelImplUtils.getDefaultVariablesMap(
                focus,
                resourceObject,
                correlationContext.getResource(),
                correlationContext.getSystemConfiguration());
        variables.put(ExpressionConstants.VAR_CORRELATION_CONTEXT, correlationContext, CorrelationContext.class);
        variables.put(ExpressionConstants.VAR_CORRELATOR_STATE,
                correlationContext.getCorrelatorState(), AbstractCorrelatorStateType.class);
        return variables;
    }

    public static @NotNull ShadowType getShadowFromCorrelationCase(@NotNull CaseType aCase) throws SchemaException {
        return MiscUtil.requireNonNull(
                MiscUtil.castSafely(
                        ObjectTypeUtil.getObjectFromReference(aCase.getTargetRef()),
                        ShadowType.class),
                () -> new IllegalStateException("No shadow object in " + aCase));
    }

    public static @NotNull FocusType getPreFocusFromCorrelationCase(@NotNull CaseType aCase) throws SchemaException {
        CaseCorrelationContextType correlationContext =
                MiscUtil.requireNonNull(
                        aCase.getCorrelationContext(),
                        () -> new IllegalStateException("No correlation context in " + aCase));
        return MiscUtil.requireNonNull(
                MiscUtil.castSafely(
                        ObjectTypeUtil.getObjectFromReference(correlationContext.getPreFocusRef()),
                        FocusType.class),
                () -> new IllegalStateException("No pre-focus object in " + aCase));
    }
}
