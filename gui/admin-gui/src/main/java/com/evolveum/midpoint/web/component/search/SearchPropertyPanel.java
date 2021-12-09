/*
 * Copyright (C) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.component.search;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.xml.ns._public.common.audit_3.AuditEventRecordType;

import org.apache.wicket.Component;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.util.ListModel;

import com.evolveum.midpoint.gui.api.component.autocomplete.AutoCompleteTextPanel;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismReferenceDefinition;
import com.evolveum.midpoint.util.DisplayableValue;
import com.evolveum.midpoint.web.component.input.TextPanel;
import com.evolveum.midpoint.web.component.prism.InputPanel;
import com.evolveum.midpoint.web.page.admin.configuration.component.EmptyOnBlurAjaxFormUpdatingBehaviour;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LookupTableType;

import javax.xml.namespace.QName;

/**
 * @author Viliam Repan (lazyman)
 * @author lskublik
 */
public class SearchPropertyPanel<T extends Serializable> extends SearchItemPanel<AttributeSearchItem<T>> {

    private static final long serialVersionUID = 1L;

    private static final String ID_SEARCH_ITEM_FIELD = "searchItemField";

    public SearchPropertyPanel(String id, IModel<AttributeSearchItem<T>> model) {
        super(id, model);
    }

    @Override
    protected void onConfigure() {
        super.onConfigure();
        AttributeSearchItem<T> item = getModelObject();
        if (!item.isEditWhenVisible()) {
            return;
        }
        item.setEditWhenVisible(false);
    }


    protected void initSearchItemField(WebMarkupContainer searchItemContainer) {
        Component searchItemField;
        AttributeSearchItem<T> item = getModelObject();
        IModel<List<DisplayableValue<?>>> choices = null;
        switch (item.getSearchItemType()) {
            case REFERENCE:
                searchItemField = new ReferenceValueSearchPanel(ID_SEARCH_ITEM_FIELD,
                        new PropertyModel<>(getModel(), "value.value"),
                        (PrismReferenceDefinition) item.getSearchItemDefinition().getDef()){
                    @Override
                    public Boolean isItemPanelEnabled() {
                        return item.isEnabled();
                    }

                    @Override
                    protected boolean isAllowedNotFoundObjectRef() {
                        return item.getSearch().getTypeClass().equals(AuditEventRecordType.class);
                    }

                    @Override
                    protected List<QName> getAllowedRelations() {
                        if (item.getSearch().getTypeClass().equals(AuditEventRecordType.class)) {
                            return Collections.emptyList();
                        }
                        return super.getAllowedRelations();
                    }
                };
                break;
            case BOOLEAN:
                choices = (IModel) createBooleanChoices();
            case ENUM:
                if (choices == null) {
                    choices = new ListModel(item.getAllowedValues(getPageBase()));
                }
                searchItemField = WebComponentUtil.createDropDownChoices(
                        ID_SEARCH_ITEM_FIELD, new PropertyModel(getModel(), "value"), (IModel)choices, true, getPageBase());
                break;
            case DATE:
                searchItemField = new DateIntervalSearchPanel(ID_SEARCH_ITEM_FIELD,
                        new PropertyModel(getModel(), "fromDate"),
                        new PropertyModel(getModel(), "toDate"));
                break;
            case ITEM_PATH:
                searchItemField = new ItemPathSearchPanel(ID_SEARCH_ITEM_FIELD,
                        new PropertyModel(getModel(), "value.value"));
                break;
            case TEXT:
                PrismObject<LookupTableType> lookupTable = WebComponentUtil.findLookupTable(item.getSearchItemDefinition().getDef(), getPageBase());
                if (lookupTable != null) {
                    searchItemField = createAutoCompetePanel(ID_SEARCH_ITEM_FIELD, new PropertyModel<>(getModel(), "value.value"),
                            lookupTable.asObjectable());
                } else {
                    searchItemField = new TextPanel<String>(ID_SEARCH_ITEM_FIELD, new PropertyModel<>(getModel(), "value.value"));
                }
                break;
            default:
                searchItemField = new TextPanel<String>(ID_SEARCH_ITEM_FIELD, new PropertyModel<>(getModel(), "value"));
        }
        if (searchItemField instanceof InputPanel && !(searchItemField instanceof AutoCompleteTextPanel)) {
            FormComponent<?> baseFormComponent = ((InputPanel) searchItemField).getBaseFormComponent();
            baseFormComponent.add(WebComponentUtil.getSubmitOnEnterKeyDownBehavior("searchSimple"));
            baseFormComponent.add(AttributeAppender.append("style", "width: 140px; max-width: 400px !important;"));
            baseFormComponent.add(new EmptyOnBlurAjaxFormUpdatingBehaviour());
            baseFormComponent.add(new VisibleEnableBehaviour() {
                @Override
                public boolean isEnabled() {
                    return item.isEnabled();
                }

                @Override
                public boolean isVisible() {
                    return item.isVisible();
                }
            });
        }
        searchItemField.setOutputMarkupId(true);
        searchItemContainer.add(searchItemField);
    }
}
