/*
 * Copyright (c) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.component.search;

import javax.xml.datatype.XMLGregorianCalendar;

import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.web.component.DateInput;
import com.evolveum.midpoint.web.component.input.DatePanel;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.web.component.DateLabelComponent;

/**
 * @author honchar
 */
public class DateIntervalSearchPanel extends PopoverSearchPanel {

    private static final long serialVersionUID = 1L;

    private final IModel<XMLGregorianCalendar> fromDateModel;
    private final IModel<XMLGregorianCalendar> toDateModel;

    public DateIntervalSearchPanel(String id, IModel<XMLGregorianCalendar> fromDateModel, IModel<XMLGregorianCalendar> toDateModel) {
        super(id);
        this.fromDateModel = fromDateModel;
        this.toDateModel = toDateModel;
    }

    @Override
    protected PopoverSearchPopupPanel createPopupPopoverPanel(String id) {
        return new DateIntervalSearchPopupPanel(id, fromDateModel, toDateModel) {

            private static final long serialVersionUID = 1L;

            @Override
            protected void confirmPerformed(AjaxRequestTarget target) {
                DatePanel fromDatePanel = getFromDatePanel();
                if (fromDatePanel != null) {
                    fromDateModel.setObject(MiscUtil.asXMLGregorianCalendar(((DateInput)fromDatePanel.getBaseFormComponent()).computeDateTime()));
                }
                target.add(DateIntervalSearchPanel.this);
            }

            @Override
            protected boolean isInterval() {
                return DateIntervalSearchPanel.this.isInterval();
            }
        };
    }

    @Override
    public IModel<String> getTextValue() {
        return () -> {
            StringBuilder sb = new StringBuilder();
            if (fromDateModel != null && fromDateModel.getObject() != null) {
                sb.append(WebComponentUtil.getLocalizedDate(fromDateModel.getObject(), DateLabelComponent.SHORT_SHORT_STYLE));
            }
            if (sb.length() > 0 && toDateModel != null && toDateModel.getObject() != null) {
                sb.append("-");
            }
            if (toDateModel != null && toDateModel.getObject() != null) {
                sb.append(WebComponentUtil.getLocalizedDate(toDateModel.getObject(), DateLabelComponent.SHORT_SHORT_STYLE));
            }
            return sb.toString();
        };
    }

    protected boolean isInterval() {
        return true;
    }

}
