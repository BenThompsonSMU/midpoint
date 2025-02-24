/*
 * Copyright (C) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.factory.panel;

import java.util.List;
import jakarta.annotation.PostConstruct;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.apache.wicket.model.Model;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.gui.api.prism.wrapper.ItemWrapper;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.web.component.input.DropDownChoicePanel;
import com.evolveum.midpoint.web.component.input.QNameObjectTypeChoiceRenderer;
import com.evolveum.midpoint.web.component.prism.InputPanel;
import com.evolveum.midpoint.web.page.admin.configuration.component.EmptyOnChangeAjaxFormUpdatingBehavior;

/**
 * @author katkav
 */
@Component
public class DropDownChoicePanelFactory extends AbstractInputGuiComponentFactory<QName> {

    @PostConstruct
    public void register() {
        getRegistry().addToRegistry(this);
    }

    @Override
    public <IW extends ItemWrapper<?, ?>> boolean match(IW wrapper) {
        return AssignmentType.F_FOCUS_TYPE.equals(wrapper.getItemName()) || DOMUtil.XSD_QNAME.equals(wrapper.getTypeName());
    }

    @Override
    protected InputPanel getPanel(PrismPropertyPanelContext<QName> panelCtx) {
        List<QName> typesList;
        if (AssignmentType.F_FOCUS_TYPE.equals(panelCtx.getDefinitionName())
                || ItemPath.create(
                        ResourceType.F_SCHEMA_HANDLING,
                        SchemaHandlingType.F_OBJECT_TYPE,
                        ResourceObjectTypeDefinitionType.F_FOCUS,
                        ResourceObjectFocusSpecificationType.F_TYPE)
                .equivalent(panelCtx.unwrapWrapperModel().getPath().namedSegmentsOnly())) {
            typesList = WebComponentUtil.createFocusTypeList();
        } else if ((ObjectCollectionType.F_TYPE.equals(panelCtx.getDefinitionName()) || GuiObjectListViewType.F_TYPE.equals(panelCtx.getDefinitionName()))
                && panelCtx.unwrapWrapperModel().getParent().getDefinition() != null &&
                (ObjectCollectionType.class.equals(panelCtx.unwrapWrapperModel().getParent().getDefinition().getTypeClass())
                        || GuiObjectListViewType.class.equals(panelCtx.unwrapWrapperModel().getParent().getDefinition().getTypeClass()))) {
            typesList = WebComponentUtil.createContainerableTypesQnameList();
        } else {
            typesList = WebComponentUtil.createObjectTypeList();
        }

        DropDownChoicePanel<QName> typePanel = new DropDownChoicePanel<QName>(panelCtx.getComponentId(), panelCtx.getRealValueModel(),
                Model.ofList(typesList), new QNameObjectTypeChoiceRenderer(), true);
        typePanel.getBaseFormComponent().add(new EmptyOnChangeAjaxFormUpdatingBehavior());
        typePanel.setOutputMarkupId(true);
        return typePanel;
    }

    @Override
    public Integer getOrder() {
        return 10000;
    }

}
