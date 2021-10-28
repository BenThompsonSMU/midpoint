/*
 * Copyright (c) 2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.impl.lens.projector;

import java.util.*;

import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.AddDeleteReplace;
import com.evolveum.midpoint.prism.delta.ContainerDelta;
import com.evolveum.midpoint.prism.equivalence.ParameterizedEquivalenceStrategy;
import com.evolveum.midpoint.schema.internals.InternalsConfig;
import com.evolveum.midpoint.schema.internals.TestingPaths;
import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentHolderType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.evolveum.midpoint.util.MiscUtil.emptyIfNull;

/**
 * Smart set of assignment values that keep track whether the assignment is new, old or changed.
 *
 * This information is used for various reasons. We specifically distinguish between assignments in objectCurrent and objectOld
 * to be able to reliably detect phantom adds: a phantom add is an assignment that is both in OLD and CURRENT objects. This is
 * important in waves greater than 0, where objectCurrent is already updated with existing assignments. (See MID-2422.)
 *
 * Each assignment is present here only once. For example, if it's present in old and current object,
 * and also referenced by a delta, it is still here represented only as a single entry.
 *
 * @author Radovan Semancik
 */
public class SmartAssignmentCollection<F extends AssignmentHolderType> implements Iterable<SmartAssignmentElement>, DebugDumpable {

    /**
     * Collection of SmartAssignmentElements (assignment value + origin), indexed by assignment value.
     */
    private Map<SmartAssignmentKey, SmartAssignmentElement> aMap;

    /**
     * Map from assignment ID to SmartAssignmentElements.
     */
    private Map<Long, SmartAssignmentElement> idMap;

    /**
     * Fills-in this collection from given sources and computes isNew in origins afterwards.
     */
    public void collect(PrismObject<F> objectCurrent, PrismObject<F> objectOld,
            ContainerDelta<AssignmentType> currentAssignmentDelta,
            Collection<AssignmentType> virtualAssignments,
            PrismContext prismContext) throws SchemaException {

        PrismContainer<AssignmentType> assignmentContainerOld = getAssignmentContainer(objectOld);
        PrismContainer<AssignmentType> assignmentContainerCurrent = getAssignmentContainer(objectCurrent);

        if (aMap == null) {
            int initialCapacity = computeInitialCapacity(assignmentContainerCurrent, currentAssignmentDelta, virtualAssignments);
            aMap = new HashMap<>(initialCapacity);
            idMap = new HashMap<>(initialCapacity);
        }

        collectAssignments(assignmentContainerCurrent, Mode.CURRENT, false);
        collectAssignments(assignmentContainerOld, Mode.OLD, false);

        // TODO what if assignment is both virtual and in delta? It will have virtual flag set to true... MID-6404
        collectVirtualAssignments(virtualAssignments);

        collectDeltaValuesAndComputeIsNew(assignmentContainerCurrent, currentAssignmentDelta, prismContext);
    }

    private void collectDeltaValuesAndComputeIsNew(PrismContainer<AssignmentType> assignmentContainerCurrent, ContainerDelta<AssignmentType> currentAssignmentDelta, PrismContext prismContext) throws SchemaException {
        if (currentAssignmentDelta != null) {
            if (currentAssignmentDelta.isReplace()) {
                allValues().forEach(v -> v.getOrigin().setNew(false));
                PrismContainer<AssignmentType> assignmentContainerNew =
                        computeAssignmentContainerNew(assignmentContainerCurrent, currentAssignmentDelta, prismContext);
                collectAssignments(assignmentContainerNew, Mode.NEW, true);
            } else {
                // For performance reasons it is better to process only changes than to process
                // the whole new assignment set (that can have hundreds of assignments)
                collectAssignmentsFromAddDeleteDelta(currentAssignmentDelta);
            }
            computeIsNew();
        }
    }

    @Nullable
    private PrismContainer<AssignmentType> getAssignmentContainer(PrismObject<F> object) {
        return object != null ? object.findContainer(FocusType.F_ASSIGNMENT) : null;
    }

    @NotNull
    private PrismContainer<AssignmentType> computeAssignmentContainerNew(PrismContainer<AssignmentType> currentContainer,
            ContainerDelta<AssignmentType> delta, PrismContext prismContext) throws SchemaException {
        PrismContainer<AssignmentType> newContainer;
        if (currentContainer == null) {
            newContainer = prismContext.getSchemaRegistry()
                    .findContainerDefinitionByCompileTimeClass(AssignmentType.class).instantiate();
        } else {
            newContainer = currentContainer.clone();
        }
        delta.applyToMatchingPath(newContainer, ParameterizedEquivalenceStrategy.DEFAULT_FOR_DELTA_APPLICATION);
        return newContainer;
    }

    private void collectAssignments(PrismContainer<AssignmentType> assignmentContainer, Mode mode, boolean inDelta) throws SchemaException {
        if (assignmentContainer != null) {
            for (PrismContainerValue<AssignmentType> assignmentCVal : assignmentContainer.getValues()) {
                collectAssignment(assignmentCVal, mode, false, null, false, inDelta);
            }
        }
    }

    private void collectVirtualAssignments(Collection<AssignmentType> forcedAssignments) throws SchemaException {
        for (AssignmentType assignment : emptyIfNull(forcedAssignments)) {
            //noinspection unchecked
            collectAssignment(assignment.asPrismContainerValue(), Mode.CURRENT, true, null, false, false);
        }
    }

    private void collectAssignmentsFromAddDeleteDelta(ContainerDelta<AssignmentType> assignmentDelta) throws SchemaException {
        collectAssignmentsFromDeltaSet(assignmentDelta.getValuesToAdd(), AddDeleteReplace.ADD);
        collectAssignmentsFromDeltaSet(assignmentDelta.getValuesToDelete(), AddDeleteReplace.DELETE);
    }

    private void collectAssignmentsFromDeltaSet(Collection<PrismContainerValue<AssignmentType>> assignments,
            AddDeleteReplace deltaSet) throws SchemaException {
        for (PrismContainerValue<AssignmentType> assignmentCVal: emptyIfNull(assignments)) {
            boolean doNotCreateNew = deltaSet == AddDeleteReplace.DELETE;
            collectAssignment(assignmentCVal, Mode.IN_ADD_OR_DELETE_DELTA, false, deltaSet, doNotCreateNew, true);
        }
    }

    /**
     * @param doNotCreateNew If an assignment does not exist, please DO NOT create it.
     * This flag is used for "delta delete" assignments that do not exist in object current.
     *
     * This also means that delete delta assignments must be processed last.
     */
    private void collectAssignment(PrismContainerValue<AssignmentType> assignmentCVal, Mode mode, boolean virtual,
            AddDeleteReplace deltaSet, boolean doNotCreateNew, boolean inDelta) throws SchemaException {

        @NotNull SmartAssignmentElement element;

        if (assignmentCVal.isEmpty()) {
            // Special lookup for empty elements.
            // Changed assignments may be "light", i.e. they may contain just the identifier.
            // Make sure that we always have the full assignment data.
            if (assignmentCVal.getId() != null) {
                element = idMap.get(assignmentCVal.getId());
                if (element == null) {
                    // deleting non-existing assignment. Safe to ignore? Yes.
                    return;
                }
            } else {
                throw new SchemaException("Attempt to change empty assignment without ID");
            }
        } else {
            SmartAssignmentElement existingElement = lookup(assignmentCVal);
            if (existingElement != null) {
                element = existingElement;
            } else if (doNotCreateNew) {
                // Deleting non-existing assignment.
                return;
            } else {
                element = put(assignmentCVal, virtual);
            }
        }

        element.updateOrigin(mode, deltaSet, inDelta);
    }

    private SmartAssignmentElement put(PrismContainerValue<AssignmentType> assignmentCVal, boolean virtual) {
        SmartAssignmentElement element = new SmartAssignmentElement(assignmentCVal, virtual);
        aMap.put(element.getKey(), element);
        if (assignmentCVal.getId() != null) {
            idMap.put(assignmentCVal.getId(), element);
        }
        return element;
    }

    private SmartAssignmentElement lookup(PrismContainerValue<AssignmentType> assignmentCVal) {
        if (assignmentCVal.getId() != null) {
            // A shortcut. But also important for deltas that specify correct id, but the value
            // does not match exactly (e.g. small differences in relation, e.g. null vs default)
            return idMap.get(assignmentCVal.getId());
        } else {
            return aMap.get(new SmartAssignmentKey(assignmentCVal));
        }
    }

    private int computeInitialCapacity(PrismContainer<AssignmentType> assignmentContainerCurrent,
            ContainerDelta<AssignmentType> assignmentDelta, Collection<AssignmentType> forcedAssignments) {
        return (assignmentContainerCurrent != null ? assignmentContainerCurrent.size() : 0)
                + (assignmentDelta != null ? assignmentDelta.size() : 0)
                + (forcedAssignments != null ? forcedAssignments.size() : 0);
    }

    @NotNull
    @Override
    public Iterator<SmartAssignmentElement> iterator() {
        if (InternalsConfig.getTestingPaths() == TestingPaths.REVERSED) {
            Collection<SmartAssignmentElement> values = allValues();
            List<SmartAssignmentElement> valuesList = new ArrayList<>(values.size());
            valuesList.addAll(values);
            Collections.reverse(valuesList);
            return valuesList.iterator();
        } else {
            return allValues().iterator();
        }
    }

    @Override
    public String debugDump(int indent) {
        StringBuilder sb = new StringBuilder();
        DebugUtil.indentDebugDump(sb, indent);
        sb.append("SmartAssignmentCollection: ");
        if (aMap == null) {
            sb.append("uninitialized");
        } else {
            sb.append(aMap.size()).append(" items");
            for (SmartAssignmentElement element: allValues()) {
                sb.append("\n");
                sb.append(element.debugDump(indent + 1));
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "SmartAssignmentCollection(" + allValues() + ")";
    }

    private void computeIsNew() {
        allValues().forEach(v -> v.getOrigin().computeIsNew());
    }

    @NotNull
    private Collection<SmartAssignmentElement> allValues() {
        return aMap.values();
    }

    enum Mode {
        CURRENT, OLD, NEW, IN_ADD_OR_DELETE_DELTA
    }
}
