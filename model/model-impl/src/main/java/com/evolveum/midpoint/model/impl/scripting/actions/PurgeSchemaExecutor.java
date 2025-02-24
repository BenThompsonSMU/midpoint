/*
 * Copyright (c) 2010-2014 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.scripting.actions;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Component;

import com.evolveum.midpoint.model.api.PipelineItem;
import com.evolveum.midpoint.util.exception.ScriptExecutionException;
import com.evolveum.midpoint.model.api.util.ResourceUtils;
import com.evolveum.midpoint.model.impl.scripting.ExecutionContext;
import com.evolveum.midpoint.model.impl.scripting.PipelineData;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.model.scripting_3.ActionExpressionType;
import com.evolveum.midpoint.xml.ns._public.model.scripting_3.PurgeSchemaActionExpressionType;

/**
 * Executes "purge-schema" action.
 */
@Component
public class PurgeSchemaExecutor extends AbstractObjectBasedActionExecutor<ResourceType> {

    private static final String NAME = "purge-schema";

    @PostConstruct
    public void init() {
        actionExecutorRegistry.register(NAME, PurgeSchemaActionExpressionType.class, this);
    }

    @Override
    public PipelineData execute(ActionExpressionType expression, PipelineData input, ExecutionContext context,
            OperationResult globalResult) throws ScriptExecutionException {

        PipelineData output = PipelineData.createEmpty();

        iterateOverObjects(input, context, globalResult,
                (object, item, result) ->
                        purge(object, item, output, context, result),
                (object, exception) ->
                        context.println("Failed to purge schema in " + object + exceptionSuffix(exception))
        );

        return output;
    }

    private void purge(PrismObject<? extends ResourceType> resource, PipelineItem item, PipelineData output,
            ExecutionContext context, OperationResult result) throws ScriptExecutionException,
            ExpressionEvaluationException, SchemaException, CommunicationException, ObjectAlreadyExistsException,
            PolicyViolationException, SecurityViolationException, ConfigurationException, ObjectNotFoundException {

        ResourceUtils.deleteSchema(resource, modelService, prismContext, context.getTask(), result);
        context.println("Purged schema information from " + resource);

        // It is questionable if noFetch should be used here. But it was so for a number of years.
        // (Actually, the resource was fetched because of model operation used to implement
        // deleteSchema method. So there is a complete version in the repository anyway.)
        PrismObject<ResourceType> resourceAfter = operationsHelper.getObject(ResourceType.class,
                resource.getOid(), true, context, result);
        output.addValue(resourceAfter.getValue(), item.getResult(), item.getVariables());
    }

    @Override
    Class<ResourceType> getObjectType() {
        return ResourceType.class;
    }

    @Override
    String getActionName() {
        return NAME;
    }
}
