/*
 * Copyright (C) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.factory.panel;

import org.apache.wicket.model.IModel;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.gui.api.component.autocomplete.AppenderAutocompletePanel;
import com.evolveum.midpoint.gui.api.prism.wrapper.ItemWrapper;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismPropertyWrapper;
import com.evolveum.midpoint.gui.impl.prism.wrapper.PrismPropertyValueWrapper;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.web.component.prism.InputPanel;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ClassLoggerConfigurationType;

@Component
public abstract class LoggingAppenderPanelFactory extends AbstractInputGuiComponentFactory<String> {

    @Override
    protected InputPanel getPanel(PrismPropertyPanelContext<String> panelCtx) {
        return new AppenderAutocompletePanel(panelCtx.getComponentId(), panelCtx.getRealValueModel(), (IModel<PrismPropertyValueWrapper<String>>) panelCtx.getValueWrapperModel());
    }

    @Override
    public <IW extends ItemWrapper<?, ?>> boolean match(IW wrapper) {
        return wrapper instanceof PrismPropertyWrapper
                && QNameUtil.match(wrapper.getItemName(), ClassLoggerConfigurationType.F_APPENDER);
    }


    @Override
    public Integer getOrder() {
        return 90;
    }
}
