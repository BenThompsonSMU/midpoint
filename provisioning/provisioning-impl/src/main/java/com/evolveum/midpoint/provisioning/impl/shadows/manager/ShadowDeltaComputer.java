/*
 * Copyright (c) 2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.provisioning.impl.shadows.manager;

import com.evolveum.midpoint.common.Clock;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.match.MatchingRule;
import com.evolveum.midpoint.prism.match.MatchingRuleRegistry;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.provisioning.impl.ProvisioningContext;
import com.evolveum.midpoint.provisioning.util.ProvisioningUtil;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.processor.ResourceAttributeDefinition;
import com.evolveum.midpoint.schema.processor.ResourceObjectDefinition;
import com.evolveum.midpoint.schema.util.ShadowUtil;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CachingMetadataType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CachingStategyType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowLifecycleStateType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.namespace.QName;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 *  Computes deltas to be applied to repository shadows.
 *  This functionality grew too large to deserve special implementation class.
 *
 *  In the future we might move more functionality here and rename this class.
 */
@Component
public class ShadowDeltaComputer {

    private static final Trace LOGGER = TraceManager.getTrace(ShadowDeltaComputer.class);

    @Autowired private Clock clock;
    @Autowired private MatchingRuleRegistry matchingRuleRegistry;
    @Autowired private PrismContext prismContext;

    @NotNull
    ObjectDelta<ShadowType> computeShadowDelta(@NotNull ProvisioningContext ctx,
            @NotNull PrismObject<ShadowType> repoShadow, PrismObject<ShadowType> resourceObject,
            ObjectDelta<ShadowType> resourceObjectDelta, ShadowLifecycleStateType shadowState)
            throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException,
            ExpressionEvaluationException {

        ObjectDelta<ShadowType> computedShadowDelta = repoShadow.createModifyDelta();

        CachingStategyType cachingStrategy = ProvisioningUtil.getCachingStrategy(ctx);
        Collection<QName> incompleteCacheableItems = new HashSet<>();

        processAttributes(ctx, repoShadow, resourceObject, resourceObjectDelta,
                cachingStrategy, incompleteCacheableItems, computedShadowDelta);

        addShadowNameDelta(repoShadow, resourceObject, computedShadowDelta);
        addAuxiliaryObjectClassDelta(repoShadow, resourceObject, computedShadowDelta);
        addExistsDelta(shadowState, computedShadowDelta);

        if (cachingStrategy == CachingStategyType.NONE) {
            addClearCachingMetadataDelta(repoShadow, computedShadowDelta);
        } else if (cachingStrategy == CachingStategyType.PASSIVE) {
            addCachedActivationDeltas(repoShadow, resourceObject, computedShadowDelta);
            addCachingMetadataDelta(incompleteCacheableItems, computedShadowDelta);
        } else {
            throw new ConfigurationException("Unknown caching strategy "+cachingStrategy);
        }
        return computedShadowDelta;
    }

    private void addShadowNameDelta(PrismObject<ShadowType> repoShadow, PrismObject<ShadowType> resourceObject,
            ObjectDelta<ShadowType> computedShadowDelta) throws SchemaException {
        PolyString resourceObjectName = ShadowUtil.determineShadowName(resourceObject);
        PolyString repoShadowName = repoShadow.getName();
        if (resourceObjectName != null && !resourceObjectName.equalsOriginalValue(repoShadowName)) {
            PropertyDelta<?> shadowNameDelta = prismContext.deltaFactory().property()
                    .createModificationReplaceProperty(ShadowType.F_NAME, repoShadow.getDefinition(), resourceObjectName);
            computedShadowDelta.addModification(shadowNameDelta);
        }
    }

    private void addAuxiliaryObjectClassDelta(PrismObject<ShadowType> repoShadow, PrismObject<ShadowType> resourceObject,
            ObjectDelta<ShadowType> computedShadowDelta) {
        PropertyDelta<QName> auxOcDelta = ItemUtil.diff(
                repoShadow.findProperty(ShadowType.F_AUXILIARY_OBJECT_CLASS),
                resourceObject.findProperty(ShadowType.F_AUXILIARY_OBJECT_CLASS));
        if (auxOcDelta != null) {
            computedShadowDelta.addModification(auxOcDelta);
        }
    }

    private void addExistsDelta(ShadowLifecycleStateType shadowState, ObjectDelta<ShadowType> computedShadowDelta) {
        // Resource object obviously exists in this case. However, we do not want to mess with isExists flag in some
        // situations (e.g. in CORPSE state) as this existence may be just a quantum illusion.
        if (shadowState == ShadowLifecycleStateType.CONCEIVED || shadowState == ShadowLifecycleStateType.GESTATING) {
            PropertyDelta<Boolean> existsDelta = computedShadowDelta.createPropertyModification(ShadowType.F_EXISTS);
            existsDelta.setRealValuesToReplace(true);
            computedShadowDelta.addModification(existsDelta);
        }
    }

    private void addClearCachingMetadataDelta(@NotNull PrismObject<ShadowType> repoShadow, ObjectDelta<ShadowType> computedShadowDelta) {
        if (repoShadow.asObjectable().getCachingMetadata() != null) {
            computedShadowDelta.addModificationReplaceProperty(ShadowType.F_CACHING_METADATA);
        }
    }

    private void addCachingMetadataDelta(Collection<QName> incompleteCacheableItems, ObjectDelta<ShadowType> computedShadowDelta) {
        if (incompleteCacheableItems.isEmpty()) {
            CachingMetadataType cachingMetadata = new CachingMetadataType();
            cachingMetadata.setRetrievalTimestamp(clock.currentTimeXMLGregorianCalendar());
            computedShadowDelta.addModificationReplaceProperty(ShadowType.F_CACHING_METADATA, cachingMetadata);
        } else {
            LOGGER.trace("Shadow has incomplete cacheable items; will not update caching timestamp: {}", incompleteCacheableItems);
        }
    }

    private void addCachedActivationDeltas(PrismObject<ShadowType> repoShadow, PrismObject<ShadowType> resourceObject,
            ObjectDelta<ShadowType> computedShadowDelta) {
        compareUpdateProperty(SchemaConstants.PATH_ACTIVATION_ADMINISTRATIVE_STATUS, repoShadow, resourceObject, computedShadowDelta);
        compareUpdateProperty(SchemaConstants.PATH_ACTIVATION_VALID_FROM, repoShadow, resourceObject, computedShadowDelta);
        compareUpdateProperty(SchemaConstants.PATH_ACTIVATION_VALID_TO, repoShadow, resourceObject, computedShadowDelta);
        compareUpdateProperty(SchemaConstants.PATH_ACTIVATION_LOCKOUT_STATUS, repoShadow, resourceObject, computedShadowDelta);
    }

    private <T> void compareUpdateProperty(ItemPath itemPath, PrismObject<ShadowType> repoShadow, PrismObject<ShadowType> resourceObject,
            ObjectDelta<ShadowType> computedShadowDelta) {
        PrismProperty<T> currentProperty = resourceObject.findProperty(itemPath);
        PrismProperty<T> oldProperty = repoShadow.findProperty(itemPath);
        PropertyDelta<T> itemDelta = ItemUtil.diff(oldProperty, currentProperty);
        if (itemDelta != null && !itemDelta.isEmpty()) {
            computedShadowDelta.addModification(itemDelta);
        }
    }

    private void processAttributes(
            ProvisioningContext ctx,
            PrismObject<ShadowType> repoShadow,
            PrismObject<ShadowType> resourceObject,
            ObjectDelta<ShadowType> resourceObjectDelta,
            CachingStategyType cachingStrategy,
            Collection<QName> incompleteCacheableAttributes,
            ObjectDelta<ShadowType> computedShadowDelta)
            throws SchemaException, ConfigurationException, ExpressionEvaluationException, ObjectNotFoundException,
            CommunicationException {

        PrismContainer<Containerable> resourceObjectAttributes = resourceObject.findContainer(ShadowType.F_ATTRIBUTES);
        PrismContainer<Containerable> repoShadowAttributes = repoShadow.findContainer(ShadowType.F_ATTRIBUTES);
        ResourceObjectDefinition ocDef = ctx.computeCompositeObjectDefinition(resourceObject);

        // For complete attributes we can proceed as before: take resourceObjectAttributes as authoritative.
        // If not obtained from the resource, they were created from object delta anyway.
        // However, for incomplete (e.g. index-only) attributes we have to rely on object delta, if present.
        // TODO clean this up! MID-5834

        for (Item<?, ?> currentResourceAttrItem: resourceObjectAttributes.getValue().getItems()) {
            if (currentResourceAttrItem instanceof PrismProperty<?>) {
                //noinspection unchecked
                PrismProperty<Object> currentResourceAttrProperty = (PrismProperty<Object>) currentResourceAttrItem;
                ResourceAttributeDefinition<?> attrDef =
                        ocDef.findAttributeDefinitionRequired(currentResourceAttrProperty.getElementName());
                if (ProvisioningUtil.shouldStoreAttributeInShadow(ocDef, attrDef.getItemName(), cachingStrategy)) {
                    if (!currentResourceAttrItem.isIncomplete()) {
                        processResourceAttribute(computedShadowDelta, repoShadowAttributes, currentResourceAttrProperty, attrDef);
                    } else {
                        incompleteCacheableAttributes.add(attrDef.getItemName());
                        if (resourceObjectDelta != null) {
                            LOGGER.trace(
                                    "Resource attribute {} is incomplete but a delta does exist: we'll update the shadow "
                                            + "using the delta", attrDef.getItemName());
                        } else {
                            LOGGER.trace(
                                    "Resource attribute {} is incomplete and object delta is not present: will not update the"
                                            + " shadow with its content", attrDef.getItemName());
                        }
                    }
                } else {
                    LOGGER.trace("Skipping resource attribute because it's not going to be stored in shadow: {}",
                            attrDef.getItemName());
                }
            } else {
                LOGGER.warn("Skipping resource attribute because it's not a PrismProperty (huh?): {}", currentResourceAttrItem);
            }
        }

        for (Item<?, ?> oldRepoItem: repoShadowAttributes.getValue().getItems()) {
            if (oldRepoItem instanceof PrismProperty<?>) {
                //noinspection unchecked
                PrismProperty<Object> oldRepoAttrProperty = (PrismProperty<Object>) oldRepoItem;
                ResourceAttributeDefinition<?> attrDef = ocDef.findAttributeDefinition(oldRepoAttrProperty.getElementName());
                PrismProperty<Object> currentAttribute = resourceObjectAttributes.findProperty(oldRepoAttrProperty.getElementName());
                // note: incomplete attributes with no values are not here: they are found in resourceObjectAttributes container
                if (attrDef == null || !ProvisioningUtil.shouldStoreAttributeInShadow(ocDef, attrDef.getItemName(), cachingStrategy) ||
                        currentAttribute == null) {
                    // No definition for this property it should not be there or no current value: remove it from the shadow
                    PropertyDelta<Object> oldRepoAttrPropDelta = oldRepoAttrProperty.createDelta();
                    oldRepoAttrPropDelta.addValuesToDelete(PrismValueCollectionsUtil.cloneCollection(oldRepoAttrProperty.getValues()));
                    computedShadowDelta.addModification(oldRepoAttrPropDelta);
                }
            } else {
                LOGGER.warn("Skipping repo shadow attribute because it's not a PrismProperty (huh?): {}", oldRepoItem);
            }
        }

        if (resourceObjectDelta != null && !incompleteCacheableAttributes.isEmpty()) {
            LOGGER.trace("Found incomplete cacheable attributes: {} while resource object delta is known. "
                    + "We'll update them using the delta.", incompleteCacheableAttributes);
            for (ItemDelta<?, ?> modification : resourceObjectDelta.getModifications()) {
                if (modification.getPath().startsWith(ShadowType.F_ATTRIBUTES)) {
                    if (QNameUtil.contains(incompleteCacheableAttributes, modification.getElementName())) {
                        LOGGER.trace(" - using: {}", modification);
                        computedShadowDelta.addModification(modification.clone());
                    }
                }
            }
            incompleteCacheableAttributes.clear(); // So we are OK regarding this. We can update caching timestamp.
        }
    }

    private void processResourceAttribute(ObjectDelta<ShadowType> computedShadowDelta,
            PrismContainer<Containerable> oldRepoAttributes, PrismProperty<Object> currentResourceAttrProperty,
            ResourceAttributeDefinition<?> attrDef)
            throws SchemaException {
        MatchingRule<Object> matchingRule = matchingRuleRegistry.getMatchingRule(attrDef.getMatchingRuleQName(), attrDef.getTypeName());
        PrismProperty<Object> oldRepoAttributeProperty = oldRepoAttributes.findProperty(attrDef.getItemName());
        if (oldRepoAttributeProperty == null) {
            PropertyDelta<Object> attrAddDelta = currentResourceAttrProperty.createDelta();
            List<PrismPropertyValue<Object>> valuesOnResource = currentResourceAttrProperty.getValues();
            if (attrDef.isIndexOnly()) {
                // We don't know what is in the repository. We simply want to replace everything with the current values.
                setNormalizedValuesToReplace(attrAddDelta, valuesOnResource, matchingRule);
            } else {
                // This is a brutal hack: For extension attributes the ADD operation is slow when using large # of
                // values to add. So let's do REPLACE instead (this is OK if there are no existing values).
                // TODO Move this logic to repository. Here it is only for PoC purposes.
                if (valuesOnResource.size() >= 100) {
                    setNormalizedValuesToReplace(attrAddDelta, valuesOnResource, matchingRule);
                } else {
                    for (PrismPropertyValue<?> pVal : valuesOnResource) {
                        attrAddDelta.addRealValuesToAdd(matchingRule.normalize(pVal.getValue()));
                    }
                }
            }
            computedShadowDelta.addModification(attrAddDelta);
        } else {
            if (attrDef.isSingleValue()) {
                Object currentResourceRealValue = currentResourceAttrProperty.getRealValue();
                Object currentResourceNormalizedRealValue = matchingRule.normalize(currentResourceRealValue);
                if (!Objects.equals(currentResourceNormalizedRealValue, oldRepoAttributeProperty.getRealValue())) {
                    PropertyDelta<Object> delta;
                    if (currentResourceNormalizedRealValue != null) {
                        delta = computedShadowDelta.addModificationReplaceProperty(currentResourceAttrProperty.getPath(),
                                currentResourceNormalizedRealValue);
                    } else {
                        delta = computedShadowDelta.addModificationReplaceProperty(currentResourceAttrProperty.getPath());
                    }
                    delta.setDefinition(currentResourceAttrProperty.getDefinition());
                }
            } else {
                PrismProperty<Object> normalizedCurrentResourceAttrProperty = currentResourceAttrProperty.clone();
                for (PrismPropertyValue<Object> pVal : normalizedCurrentResourceAttrProperty.getValues()) {
                    pVal.setValue(matchingRule.normalize(pVal.getValue()));
                }
                PropertyDelta<Object> attrDiff = oldRepoAttributeProperty.diff(normalizedCurrentResourceAttrProperty);
                if (attrDiff != null && !attrDiff.isEmpty()) {
                    attrDiff.setParentPath(ShadowType.F_ATTRIBUTES);
                    computedShadowDelta.addModification(attrDiff);
                }
            }
        }
    }

    private void setNormalizedValuesToReplace(PropertyDelta<Object> attrAddDelta, List<PrismPropertyValue<Object>> currentValues,
            MatchingRule<Object> matchingRule) throws SchemaException {
        Object[] currentValuesNormalized = new Object[currentValues.size()];
        for (int i = 0; i < currentValues.size(); i++) {
            currentValuesNormalized[i] = matchingRule.normalize(currentValues.get(i).getValue());
        }
        attrAddDelta.setRealValuesToReplace(currentValuesNormalized);
    }
}
