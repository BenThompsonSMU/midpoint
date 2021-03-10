/*
 * Copyright (c) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.impl.lens.projector;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.common.Clock;
import com.evolveum.midpoint.model.impl.ModelBeans;
import com.evolveum.midpoint.model.impl.lens.LensContext;
import com.evolveum.midpoint.model.impl.lens.LensProjectionContext;
import com.evolveum.midpoint.model.impl.lens.construction.PlainResourceObjectConstruction;
import com.evolveum.midpoint.model.impl.lens.construction.PlainResourceObjectConstructionBuilder;
import com.evolveum.midpoint.model.impl.lens.projector.mappings.NextRecompute;
import com.evolveum.midpoint.prism.OriginType;
import com.evolveum.midpoint.prism.util.ObjectDeltaObject;
import com.evolveum.midpoint.schema.ResourceShadowDiscriminator;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentHolderType;

/**
 * Processor that evaluates values of the outbound mappings. It does not create the deltas yet. It just collects the
 * evaluated mappings in account context.
 *
 * @author Radovan Semancik
 */
@Component
public class OutboundProcessor {

    private static final Trace LOGGER = TraceManager.getTrace(OutboundProcessor.class);

    @Autowired private Clock clock;
    @Autowired private ModelBeans modelBeans;

    <AH extends AssignmentHolderType>
    void processOutbound(LensContext<AH> context, LensProjectionContext projCtx, Task task, OperationResult result) throws SchemaException,
            ExpressionEvaluationException, ObjectNotFoundException, CommunicationException, ConfigurationException, SecurityViolationException {

        ResourceShadowDiscriminator discr = projCtx.getResourceShadowDiscriminator();
        if (projCtx.isDelete()) {
            LOGGER.trace("Processing outbound expressions for {} skipped, DELETE account delta", discr);
            // No point in evaluating outbound
            return;
        }

        LOGGER.trace("Processing outbound expressions for {} starting", discr);

        // Each projection is evaluated in a single wave only. So we take into account all changes of focus from wave 0 to this one.
        ObjectDeltaObject<AH> focusOdoAbsolute = context.getFocusContext().getObjectDeltaObjectAbsolute();

        PlainResourceObjectConstructionBuilder<AH> builder = new PlainResourceObjectConstructionBuilder<AH>()
                .projectionContext(projCtx)
                .source(projCtx.getResource())
                .lensContext(context)
                .now(clock.currentTimeXMLGregorianCalendar()) // todo
                .originType(OriginType.ASSIGNMENTS) // fixme
                .valid(true);

        PlainResourceObjectConstruction<AH> outboundConstruction = builder.build();

        outboundConstruction.setFocusOdoAbsolute(focusOdoAbsolute);
        NextRecompute nextRecompute = outboundConstruction.evaluate(task, result);

        projCtx.setEvaluatedPlainConstruction(outboundConstruction);
        if (nextRecompute != null) {
            nextRecompute.createTrigger(context.getFocusContext());
        }

        context.recompute();
        context.checkConsistenceIfNeeded();
    }
}
