/*
 * Copyright (c) 2010-2013 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.schema.processor;

import static com.evolveum.midpoint.util.MiscUtil.stateCheck;

import java.util.HashSet;
import java.util.Set;
import javax.xml.namespace.QName;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.prism.Definition;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.impl.schema.PrismSchemaImpl;
import com.evolveum.midpoint.schema.constants.MidPointConstants;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LayerType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;

/**
 * Direct implementation of {@link ResourceSchema} interface.
 *
 * Definitions are stored in {@link PrismSchemaImpl#definitions}.
 * Besides that, it has no own state.
 *
 * @author semancik
 */
public class ResourceSchemaImpl extends PrismSchemaImpl implements MutableResourceSchema {

    private static final LayerType DEFAULT_LAYER = LayerType.MODEL;

    @NotNull private final LayerType currentLayer;

    ResourceSchemaImpl() {
        this(DEFAULT_LAYER);
    }

    private ResourceSchemaImpl(@NotNull LayerType currentLayer) {
        super(MidPointConstants.NS_RI);
        this.currentLayer = currentLayer;
    }

    @Override
    public MutableResourceObjectClassDefinition createObjectClassDefinition(QName typeName) {
        ResourceObjectClassDefinitionImpl objectClassDef = new ResourceObjectClassDefinitionImpl(typeName);
        add(objectClassDef);
        return objectClassDef;
    }

    @Override
    public MutableResourceSchema toMutable() {
        return this;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + " (" + getObjectClassDefinitions().size() + " classes, "
                + getObjectTypeDefinitions().size() + " types)";
    }

    @Override
    public void validate(PrismObject<ResourceType> resource) throws SchemaException {

        Set<RefinedObjectClassDefinitionKey> discrs = new HashSet<>();

        for (ResourceObjectTypeDefinition rObjectClassDefinition: getObjectTypeDefinitions()) {
            RefinedObjectClassDefinitionKey key = new RefinedObjectClassDefinitionKey(rObjectClassDefinition);
            if (discrs.contains(key)) {
                throw new SchemaException("Duplicate definition of object class "+key+" in resource schema of "+resource);
            }
            discrs.add(key);

            ResourceTypeUtil.validateObjectClassDefinition(rObjectClassDefinition, resource);
        }
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public ResourceSchemaImpl clone() {
        return cloneForLayer(currentLayer);
    }

    private ResourceSchemaImpl cloneForLayer(@NotNull LayerType layer) {
        ResourceSchemaImpl clone = new ResourceSchemaImpl(layer);
        if (layer == currentLayer) {
            super.copyContent(clone);
        } else {
            assertNoDelayedDefinitionsOnClone();
            copyAllDefinitions(layer, clone);
            // TODO what about substitutions?
        }
        return clone;
    }

    /**
     * We re-add layer-modified versions of all our definitions.
     *
     * We have to use this approach because otherwise the internal lookup structures would be hard to fill in correctly.
     */
    private void copyAllDefinitions(LayerType layer, MutableResourceSchema target) {
        for (Definition definition : getDefinitions()) {
            stateCheck(definition instanceof ResourceObjectDefinition,
                    "Non-ResourceObjectDefinition in %s: %s (%s)", this, definition, definition.getClass());
            target.add(((ResourceObjectDefinition) definition).forLayer(layer));
        }
    }

    @Override
    public ResourceSchema forLayer(@NotNull LayerType layer) {
        return cloneForLayer(layer);
    }


    /** This is just a reminder - here we should put any freezing calls to own properties, should there be any. */
    @Override
    public void performFreeze() {
        super.performFreeze();
    }

    @Override
    public @NotNull LayerType getCurrentLayer() {
        return currentLayer;
    }
}
