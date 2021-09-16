/*
 * Copyright (c) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.admin.objecttemplate.component;

import com.evolveum.midpoint.gui.impl.page.admin.AbstractObjectMainPanel;
import com.evolveum.midpoint.gui.impl.page.admin.ObjectDetailsModels;
import com.evolveum.midpoint.gui.impl.page.admin.assignmentholder.AssignmentHolderDetailsModel;
import com.evolveum.midpoint.gui.impl.prism.panel.ListMappingPanel;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.application.PanelDisplay;
import com.evolveum.midpoint.web.application.PanelInstance;
import com.evolveum.midpoint.web.application.PanelType;
import com.evolveum.midpoint.web.model.PrismContainerWrapperModel;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ContainerPanelConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectTemplateType;

@PanelType(name = "objectTemplateMappings")
@PanelInstance(identifier = "objectTemplateMappings",
        applicableForType = ObjectTemplateType.class,
        display = @PanelDisplay(label = "pageObjectTemplate.mapping.title", order = 40))
public class ObjectTemplateMappingsPanel extends AbstractObjectMainPanel<ObjectTemplateType, ObjectDetailsModels<ObjectTemplateType>> {
    private static final long serialVersionUID = 1L;

    private static final Trace LOGGER = TraceManager.getTrace(ObjectTemplateMappingsPanel.class);
    private static final String ID_PANEL = "panel";

    private static final String DOT_CLASS = ObjectTemplateMappingsPanel.class.getName() + ".";

    public ObjectTemplateMappingsPanel(String id, AssignmentHolderDetailsModel<ObjectTemplateType> model, ContainerPanelConfigurationType config) {
        super(id, model, config);
    }

    @Override
    protected void initLayout() {
        add(new ListMappingPanel(ID_PANEL,
                PrismContainerWrapperModel.fromContainerWrapper(getObjectWrapperModel(), ObjectTemplateType.F_MAPPING)));
    }
}
