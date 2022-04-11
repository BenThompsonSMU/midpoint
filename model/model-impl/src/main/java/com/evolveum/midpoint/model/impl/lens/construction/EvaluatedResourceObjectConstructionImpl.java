/*
 * Copyright (c) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.lens.construction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.model.impl.lens.LensUtil;
import com.evolveum.midpoint.prism.util.ObjectDeltaObject;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.model.api.context.AssignmentPath;
import com.evolveum.midpoint.model.api.context.EvaluatedResourceObjectConstruction;
import com.evolveum.midpoint.model.common.mapping.MappingImpl;
import com.evolveum.midpoint.model.impl.lens.LensProjectionContext;
import com.evolveum.midpoint.model.impl.lens.projector.mappings.NextRecompute;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.schema.ResourceShadowDiscriminator;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.*;

import org.jetbrains.annotations.Nullable;

/**
 * Evaluated construction of a resource object.
 *
 * More such objects can stem from single {@link ResourceObjectConstruction} in the presence of multiaccounts.
 *
 * The evaluation itself is delegated to {@link ConstructionEvaluation} class that, in turn, delegates
 * to {@link AttributeEvaluation} and {@link AssociationEvaluation}. However, these classes shouldn't be
 * publicly visible.
 */
public abstract class EvaluatedResourceObjectConstructionImpl<AH extends AssignmentHolderType, ROC extends ResourceObjectConstruction<AH, ?>>
        implements EvaluatedAbstractConstruction<AH>, EvaluatedResourceObjectConstruction {

    private static final Trace LOGGER = TraceManager.getTrace(EvaluatedResourceObjectConstructionImpl.class);

    private static final String OP_EVALUATE = EvaluatedResourceObjectConstructionImpl.class.getName() + ".evaluate";

    /**
     * Parent construction to which this EvaluatedConstruction belongs.
     */
    @NotNull protected final ROC construction;

    /**
     * Specification of the resource object.
     */
    @NotNull protected final ResourceShadowDiscriminator rsd;

    /**
     * Mappings for the resource object attributes.
     */
    @NotNull protected final Collection<MappingImpl<? extends PrismPropertyValue<?>, ? extends PrismPropertyDefinition<?>>>
            attributeMappings = new ArrayList<>();

    /**
     * Mappings for the resource object associations.
     */
    @NotNull protected final Collection<MappingImpl<PrismContainerValue<ShadowAssociationType>, PrismContainerDefinition<ShadowAssociationType>>> associationMappings = new ArrayList<>();

    /**
     * Projection context for the resource object.
     * For assigned constructions it is filled-in on evaluation start (and it might not exist).
     * For plain constructions it is filled-in on creation; and it always exists.
     */
    private LensProjectionContext projectionContext;

    /**
     * Construction evaluation state. It is factored out into separate class to allow many of its fields to be final.
     * (It would not be possible if it was part of this class.)
     */
    protected transient ConstructionEvaluation<AH, ROC> evaluation;

    /**
     * Precondition: {@link ResourceObjectConstruction} is already evaluated and not ignored (has resource).
     */
    EvaluatedResourceObjectConstructionImpl(@NotNull final ROC construction,
            @NotNull final ResourceShadowDiscriminator rsd) {
        this.construction = construction;
        this.rsd = rsd;
    }

    //region Trivia
    @Override
    public @NotNull ROC getConstruction() {
        return construction;
    }

    public ResourceShadowDiscriminator getResourceShadowDiscriminator() {
        return rsd;
    }

    @Override
    public @NotNull PrismObject<ResourceType> getResource() {
        // We assume that for assigned constructions with missing resource we never come here.
        return construction.getResource().asPrismObject();
    }

    @Override
    public @NotNull ShadowKindType getKind() {
        return Objects.requireNonNullElse( // TODO is this OK?
                rsd.getKind(),
                ShadowKindType.ACCOUNT);
    }

    @Override
    public String getIntent() {
        return rsd.getIntent();
    }

    @Override
    public String getTag() {
        return rsd.getTag();
    }

    @Override
    public boolean isDirectlyAssigned() {
        AssignmentPath assignmentPath = getAssignmentPath();
        return assignmentPath == null || assignmentPath.size() == 1;
    }

    @Override
    public AssignmentPath getAssignmentPath() {
        return construction.getAssignmentPath();
    }

    @Override
    public boolean isWeak() {
        return construction.isWeak();
    }

    protected @Nullable LensProjectionContext getProjectionContext() {
        return projectionContext;
    }

    protected void setProjectionContext(LensProjectionContext projectionContext) {
        this.projectionContext = projectionContext;
    }

    protected String getHumanReadableConstructionDescription() {
        return "construction for (" + (construction.getResolvedResource() != null ? construction.getResolvedResource().resource : null)
                + "/" + getKind() + "/" + getIntent() + "/" + getTag() + ") in " + construction.getSource();
    }

    @Override
    public String debugDump(int indent) {
        StringBuilder sb = new StringBuilder();
        DebugUtil.debugDumpLabelLn(sb, this.getClass().getSimpleName(), indent);
        DebugUtil.debugDumpWithLabelShortDumpLn(sb, "discriminator", rsd, indent + 1);
        // We do not want to dump construction here. This can lead to cycles.
        // We usually dump EvaluatedConstruction in a Construction dump anyway, therefore the context should be quite clear.
        DebugUtil.debugDumpWithLabelToString(sb, "projectionContext", projectionContext, indent + 1);
        if (!attributeMappings.isEmpty()) {
            sb.append("\n");
            DebugUtil.debugDumpLabel(sb, "attribute mappings", indent + 1);
            for (MappingImpl<?, ?> mapping : attributeMappings) {
                sb.append("\n");
                sb.append(mapping.debugDump(indent + 2));
            }
        }
        if (!associationMappings.isEmpty()) {
            sb.append("\n");
            DebugUtil.debugDumpLabel(sb, "association mappings", indent + 1);
            for (MappingImpl<?, ?> mapping : associationMappings) {
                sb.append("\n");
                sb.append(mapping.debugDump(indent + 2));
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" +
                "discriminator=" + rsd +
                ", construction=" + construction +
                ", projectionContext='" + projectionContext +
                ')';
    }
    //endregion

    //region Mappings management
    public @NotNull Collection<MappingImpl<? extends PrismPropertyValue<?>, ? extends PrismPropertyDefinition<?>>> getAttributeMappings() {
        return attributeMappings;
    }

    @VisibleForTesting
    public MappingImpl<? extends PrismPropertyValue<?>, ? extends PrismPropertyDefinition<?>> getAttributeMapping(QName attrName) {
        for (MappingImpl<? extends PrismPropertyValue<?>, ? extends PrismPropertyDefinition<?>> myVc : getAttributeMappings()) {
            if (myVc.getItemName().equals(attrName)) {
                return myVc;
            }
        }
        return null;
    }

    protected void addAttributeMapping(MappingImpl<PrismPropertyValue<?>, PrismPropertyDefinition<?>> mapping) {
        attributeMappings.add(mapping);
    }

    public @NotNull Collection<MappingImpl<PrismContainerValue<ShadowAssociationType>, PrismContainerDefinition<ShadowAssociationType>>> getAssociationMappings() {
        return associationMappings;
    }

    protected void addAssociationMapping(
            MappingImpl<PrismContainerValue<ShadowAssociationType>, PrismContainerDefinition<ShadowAssociationType>> mapping) {
        associationMappings.add(mapping);
    }
    //endregion

    //region Mappings evaluation
    public NextRecompute evaluate(Task task, OperationResult parentResult) throws CommunicationException, ObjectNotFoundException,
            SchemaException, SecurityViolationException, ConfigurationException, ExpressionEvaluationException {
        if (evaluation != null) {
            throw new IllegalStateException("Attempting to evaluate an EvaluatedConstruction twice: " + this);
        }
        OperationResult result = parentResult.subresult(OP_EVALUATE)
                .addParam("resourceShadowDiscriminator", rsd.toHumanReadableDescription())
                .setMinor()
                .build();
        if (result.isTracingAny(ResourceObjectConstructionEvaluationTraceType.class)) {
            ResourceObjectConstructionEvaluationTraceType trace =
                    new ResourceObjectConstructionEvaluationTraceType(PrismContext.get());
            trace.setConstruction(construction.constructionBean);
            trace.setResourceShadowDiscriminator(LensUtil.createDiscriminatorBean(rsd, construction.lensContext));
            if (construction.assignmentPath != null && result.isTracingNormal(ResourceObjectConstructionEvaluationTraceType.class)) {
                trace.setAssignmentPath(construction.assignmentPath.toAssignmentPathType(false));
            }
            result.addTrace(trace);
        }
        try {
            initializeProjectionContext();
            if (projectionContext != null && !projectionContext.isCurrentForProjection()) {
                LOGGER.trace("Skipping evaluation of construction for {} because this projection context is not current"
                        + " (already completed or wrong wave)", projectionContext.getHumanReadableName());
                result.recordNotApplicable();
                return null;
            } else {
                evaluation = new ConstructionEvaluation<>(this, task, result);
                evaluation.evaluate();
                return evaluation.getNextRecompute();
            }
        } catch (Throwable t) {
            result.recordFatalError(t);
            throw t;
        } finally {
            result.computeStatusIfUnknown();
        }
    }

    /**
     * Sets up the projection context. The implementation differs for assigned and plain constructions.
     */
    protected abstract void initializeProjectionContext();

    /**
     * Collects attributes that are to be evaluated. Again, the exact mechanism is implementation-specific.
     */
    protected abstract List<AttributeEvaluation<AH>> getAttributesToEvaluate(ConstructionEvaluation<AH, ?> constructionEvaluation) throws SchemaException;

    /**
     * Collects associations that are to be evaluated.
     */
    protected abstract List<AssociationEvaluation<AH>> getAssociationsToEvaluate(ConstructionEvaluation<AH, ?> constructionEvaluation) throws SchemaException;
    //endregion

    //region Resource object loading

    /**
     * Checks whether we are obliged to load the full shadow.
     * @return non-null if we have to
     */
    String getFullShadowLoadReason(MappingType outboundMappingBean) {
        if (projectionContext == null) {
            return null;
        }
        if (projectionContext.isFullShadow()) {
            return null;
        }
        if (projectionContext.isDelete()) {
            return null;
        }
        MappingStrengthType strength = outboundMappingBean.getStrength();
        if (strength == MappingStrengthType.STRONG) {
            return "strong outbound mapping";
        } else if (strength == MappingStrengthType.WEAK) {
            return "weak outbound mapping";
        } else if (outboundMappingBean.getTarget() != null && outboundMappingBean.getTarget().getSet() != null) {
            return "outbound mapping target set specified";
        } else {
            return null;
        }
    }

    /**
     * Executes the loading itself.
     */
    ObjectDeltaObject<ShadowType> loadFullShadow(String reason, Task task, OperationResult result)
            throws CommunicationException, ObjectNotFoundException, SchemaException,
            SecurityViolationException, ConfigurationException, ExpressionEvaluationException {
        construction.loadFullShadow(projectionContext, reason, task, result);
        return projectionContext.getObjectDeltaObject();
    }
    //endregion

}
