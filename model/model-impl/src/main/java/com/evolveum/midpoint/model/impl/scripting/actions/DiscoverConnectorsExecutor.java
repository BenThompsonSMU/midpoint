/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.scripting.actions;

import com.evolveum.midpoint.model.impl.scripting.PipelineData;
import com.evolveum.midpoint.model.impl.scripting.ExecutionContext;
import com.evolveum.midpoint.schema.statistics.Operation;
import com.evolveum.midpoint.util.exception.ScriptExecutionException;
import com.evolveum.midpoint.model.api.PipelineItem;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.ReferenceDelta;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ConnectorHostType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ConnectorType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.model.scripting_3.ActionExpressionType;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.util.*;

/**
 * Executes "discover-connectors" action.
 *
 * There is no static (typed) definition of this action yet.
 * Also, this code is not refactored yet.
 */
@Component
public class DiscoverConnectorsExecutor extends BaseActionExecutor {

    private static final Trace LOGGER = TraceManager.getTrace(DiscoverConnectorsExecutor.class);

    private static final String NAME = "discover-connectors";
    private static final String PARAM_REBIND_RESOURCES = "rebindResources";

    @PostConstruct
    public void init() {
        actionExecutorRegistry.register(NAME, this);
    }

    @Override
    public PipelineData execute(ActionExpressionType expression, PipelineData input, ExecutionContext context,
            OperationResult globalResult) throws ScriptExecutionException, SchemaException, ConfigurationException,
            ObjectNotFoundException, CommunicationException, SecurityViolationException, ExpressionEvaluationException {

        boolean rebind = expressionHelper.getArgumentAsBoolean(expression.getParameter(), PARAM_REBIND_RESOURCES, input, context, false, PARAM_REBIND_RESOURCES, globalResult);

        PipelineData output = PipelineData.createEmpty();

        for (PipelineItem item: input.getData()) {
            PrismValue value = item.getValue();
            OperationResult result = operationsHelper.createActionResult(item, this, globalResult);
            context.checkTaskStop();
            if (value instanceof PrismObjectValue && ((PrismObjectValue) value).asObjectable() instanceof ConnectorHostType) {
                PrismObject<ConnectorHostType> connectorHostTypePrismObject = ((PrismObjectValue) value).asPrismObject();
                Set<ConnectorType> newConnectors;
                Operation op = operationsHelper.recordStart(context, connectorHostTypePrismObject.asObjectable());
                Throwable exception = null;
                try {
                    newConnectors = modelService.discoverConnectors(connectorHostTypePrismObject.asObjectable(), context.getTask(), result);
                    operationsHelper.recordEnd(context, op, null, result);
                } catch (CommunicationException | SecurityViolationException | SchemaException | ConfigurationException | ObjectNotFoundException | ExpressionEvaluationException | RuntimeException e) {
                    operationsHelper.recordEnd(context, op, e, result);
                    exception = processActionException(e, NAME, value, context);
                    newConnectors = Collections.emptySet();
                }
                context.println((exception != null ? "Attempted to discover " : "Discovered " + newConnectors.size())
                        + " new connector(s) from " + connectorHostTypePrismObject + exceptionSuffix(exception));
                for (ConnectorType connectorType : newConnectors) {
                    output.addValue(connectorType.asPrismObject().getValue(), item.getResult(), item.getVariables());
                }
                try {
                    if (rebind) {
                        rebindConnectors(newConnectors, context, result);
                    }
                } catch (ScriptExecutionException e) {
                    //noinspection ThrowableNotThrown
                    processActionException(e, NAME, value, context); // TODO better message
                }
            } else {
                //noinspection ThrowableNotThrown
                processActionException(new ScriptExecutionException("Input item is not a PrismObject<ConnectorHost>"), NAME, value, context);
            }
            operationsHelper.trimAndCloneResult(result, item.getResult());
        }
        return output; // TODO configurable output (either connector hosts or discovered connectors)
    }

    private void rebindConnectors(Set<ConnectorType> newConnectors, ExecutionContext context, OperationResult result) throws ScriptExecutionException {
        Map<String,String> rebindMap = new HashMap<>();
        for (ConnectorType connectorType : newConnectors) {
            determineConnectorMappings(rebindMap, connectorType, context, result);
        }
        LOGGER.trace("Connector rebind map: {}", rebindMap);
        rebindResources(rebindMap, context, result);
    }

    private void rebindResources(Map<String, String> rebindMap, ExecutionContext context, OperationResult result) throws ScriptExecutionException {
        List<PrismObject<ResourceType>> resources;
        try {
            resources = modelService.searchObjects(ResourceType.class, null, null, null, result);
        } catch (SchemaException|ConfigurationException|ObjectNotFoundException|CommunicationException|SecurityViolationException|ExpressionEvaluationException e) {
            throw new ScriptExecutionException("Couldn't list resources: " + e.getMessage(), e);
        }
        for (PrismObject<ResourceType> resource : resources) {
            if (resource.asObjectable().getConnectorRef() != null) {
                String connectorOid = resource.asObjectable().getConnectorRef().getOid();
                String newOid = rebindMap.get(connectorOid);
                if (newOid != null) {
                    String msg = "resource " + resource + " from connector " + connectorOid + " to new one: " + newOid;
                    LOGGER.info("Rebinding {}", msg);
                    ReferenceDelta refDelta = prismContext.deltaFactory().reference()
                            .createModificationReplace(ResourceType.F_CONNECTOR_REF, resource.getDefinition(), newOid);
                    ObjectDelta<ResourceType> objDelta = prismContext.deltaFactory().object()
                            .createModifyDelta(resource.getOid(), refDelta, ResourceType.class
                            );
                    operationsHelper.applyDelta(objDelta, context, result);
                    context.println("Rebound " + msg);
                }
            }
        }
    }

    private void determineConnectorMappings(Map<String,String> rebindMap, ConnectorType connectorType, ExecutionContext context, OperationResult result) throws ScriptExecutionException {

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Finding obsolete versions for connector: {}", connectorType.asPrismObject().debugDump());
        }

        ObjectQuery query = prismContext.queryFor(ConnectorType.class)
                .item(SchemaConstants.C_CONNECTOR_FRAMEWORK).eq(connectorType.getFramework())
                .and().item(SchemaConstants.C_CONNECTOR_CONNECTOR_TYPE).eq(connectorType.getConnectorType())
                .build();
        List<PrismObject<ConnectorType>> foundConnectors;
        try {
            foundConnectors = modelService.searchObjects(ConnectorType.class, query, null, null, result);
        } catch (SchemaException|ConfigurationException|ObjectNotFoundException|CommunicationException|SecurityViolationException|ExpressionEvaluationException e) {
            throw new ScriptExecutionException("Couldn't get connectors of type: " + connectorType.getConnectorType() + ": " + e.getMessage(), e);
        }

        for (PrismObject<ConnectorType> foundConnector : foundConnectors) {
            ConnectorType foundConnectorType = foundConnector.asObjectable();
            // TODO temporary hack. fix it after MID-3355 is implemented.
            if (connectorType.getConnectorHostRef() != null && connectorType.getConnectorHostRef().asReferenceValue().getObject() != null) {
                String connectorHostOid = connectorType.getConnectorHostRef().getOid();
                connectorType.getConnectorHostRef().asReferenceValue().setObject(null);
                connectorType.getConnectorHostRef().setOid(connectorHostOid);
            }
            if (connectorType.getConnectorHostRef().equals(foundConnectorType.getConnectorHostRef()) &&
                    foundConnectorType.getConnectorVersion() != null &&
                    !foundConnectorType.getConnectorVersion().equals(connectorType.getConnectorVersion())) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Found obsolete connector:\n{}", foundConnectorType.asPrismObject().debugDump(1));
                }
                rebindMap.put(foundConnectorType.getOid(), connectorType.getOid());
            }
        }
    }

    @Override
    String getActionName() {
        return NAME;
    }
}
