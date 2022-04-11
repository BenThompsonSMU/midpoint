/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.api.context;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.model.api.ProgressInformation;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.DeltaSetTriple;
import com.evolveum.midpoint.schema.ObjectTreeDeltas;
import com.evolveum.midpoint.schema.ResourceShadowDiscriminator;
import com.evolveum.midpoint.schema.expression.ExpressionProfile;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author semancik
 *
 */
public interface ModelContext<F extends ObjectType> extends Serializable, DebugDumpable {

    String getRequestIdentifier();

    ModelState getState();

    ModelElementContext<F> getFocusContext();

    @NotNull
    ModelElementContext<F> getFocusContextRequired();

    @NotNull
    Collection<? extends ModelProjectionContext> getProjectionContexts();

    ModelProjectionContext findProjectionContext(ResourceShadowDiscriminator rat);

    ModelExecuteOptions getOptions();

    @NotNull
    PartialProcessingOptionsType getPartialProcessingOptions();

    Class<F> getFocusClass();

    void reportProgress(ProgressInformation progress);

    DeltaSetTriple<? extends EvaluatedAssignment<?>> getEvaluatedAssignmentTriple();

    @NotNull
    Stream<? extends EvaluatedAssignment<?>> getEvaluatedAssignmentsStream();

    @NotNull
    Collection<? extends EvaluatedAssignment<?>> getNonNegativeEvaluatedAssignments();

    @NotNull
    Collection<? extends EvaluatedAssignment<?>> getAllEvaluatedAssignments();

    ObjectTemplateType getFocusTemplate();

    PrismObject<SystemConfigurationType> getSystemConfiguration();  // beware, may be null - use only as a performance optimization

    String getChannel();

    int getAllChanges() throws SchemaException;

    // For diagnostic purposes (this is more detailed than rule-related part of LensContext debugDump,
    // while less detailed than that part of detailed LensContext debugDump).
    default String dumpAssignmentPolicyRules(int indent) {
        return dumpAssignmentPolicyRules(indent, false);
    }

    String dumpAssignmentPolicyRules(int indent, boolean alsoMessages);

    default String dumpFocusPolicyRules(int indent) {
        return dumpFocusPolicyRules(indent, false);
    }

    String dumpFocusPolicyRules(int indent, boolean alsoMessages);

    Map<String, Collection<Containerable>> getHookPreviewResultsMap();

    @NotNull
    <T> List<T> getHookPreviewResults(@NotNull Class<T> clazz);

    @Nullable
    PolicyRuleEnforcerPreviewOutputType getPolicyRuleEnforcerPreviewOutput();

    boolean isPreview();

    @NotNull
    ObjectTreeDeltas<F> getTreeDeltas();

    Collection<ResourceShadowDiscriminator> getHistoricResourceObjects();

    Long getSequenceCounter(String sequenceOid);

    void setSequenceCounter(String sequenceOid, long counter);

    String getTaskTreeOid(Task task, OperationResult result);

    ExpressionProfile getPrivilegedExpressionProfile();
}
