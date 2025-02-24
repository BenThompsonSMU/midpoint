/*
 * Copyright (C) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.objectType;

import com.evolveum.midpoint.gui.api.component.wizard.TileEnum;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.impl.component.wizard.WizardPanelHelper;
import com.evolveum.midpoint.gui.impl.page.admin.assignmentholder.AssignmentHolderDetailsModel;
import com.evolveum.midpoint.gui.impl.page.admin.resource.ResourceDetailsModel;

import com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.ResourceWizardChoicePanel;

import com.evolveum.midpoint.gui.impl.util.GuiDisplayNameUtil;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceObjectTypeDefinitionType;

import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.jetbrains.annotations.NotNull;

public abstract class ResourceObjectTypeWizardPreviewPanel
        extends ResourceWizardChoicePanel<ResourceObjectTypeWizardPreviewPanel.ResourceObjectTypePreviewTileType> {

    private final WizardPanelHelper<ResourceObjectTypeDefinitionType, ResourceDetailsModel> helper;

    public ResourceObjectTypeWizardPreviewPanel(
            String id,
            WizardPanelHelper<ResourceObjectTypeDefinitionType, ResourceDetailsModel> helper) {
        super(id, helper.getDetailsModel(), ResourceObjectTypePreviewTileType.class);
        this.helper = helper;
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        add(AttributeAppender.append("class", "col-xxl-10 col-12 gap-3 m-auto"));
    }

    public enum ResourceObjectTypePreviewTileType implements TileEnum {

        BASIC("fa fa-circle"),
        PREVIEW_DATA("fa fa-magnifying-glass"),
        ATTRIBUTE_MAPPING("fa fa-retweet"),
        SYNCHRONIZATION_CONFIG("fa fa-arrows-rotate"),
        CORRELATION_CONFIG("fa fa-code-branch"),
        CAPABILITIES_CONFIG("fa fa-atom"),
        ACTIVATION("fa fa-toggle-off"),
        CREDENTIALS("fa fa-key"),
        ASSOCIATIONS("fa fa-shield");

        private final String icon;

        ResourceObjectTypePreviewTileType(String icon) {
            this.icon = icon;
        }

        @Override
        public String getIcon() {
            return icon;
        }
    }

    @Override
    protected IModel<String> getExitLabel() {
        return getPageBase().createStringResource("ResourceObjectTypeWizardPreviewPanel.exit");
    }

    protected IModel<PrismContainerValueWrapper<ResourceObjectTypeDefinitionType>> getValueModel() {
        return helper.getValueModel();
    }

    @Override
    protected @NotNull IModel<String> getBreadcrumbLabel() {
        return new LoadableDetachableModel<>() {
            @Override
            protected String load() {
                return GuiDisplayNameUtil.getDisplayName(getValueModel().getObject().getRealValue());
            }
        };
    }

    @Override
    protected IModel<String> getSubTextModel() {
        return getPageBase().createStringResource("ResourceObjectTypeWizardPreviewPanel.subText");
    }

    @Override
    protected IModel<String> getTextModel() {
        return getPageBase().createStringResource("ResourceObjectTypeWizardPreviewPanel.text");
    }
}
