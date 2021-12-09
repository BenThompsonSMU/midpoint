/*
 * Copyright (C) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.component.search;

import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.web.component.data.column.AjaxLinkPanel;

import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectCollectionType;

import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

/**
 * @author lskublik
 */
public class SearchObjectCollectionPanel extends SearchItemPanel<ObjectCollectionSearchItem> {

    private static final long serialVersionUID = 1L;

    private static final String ID_CLICKABLE_NAME= "clickableName";

    public SearchObjectCollectionPanel(String id, IModel<ObjectCollectionSearchItem> model) {
        super(id, model);
    }

    protected void initSearchItemField(WebMarkupContainer searchItemContainer) {
        IModel<String> nameModel = super.createLabelModel();
        String oid = null;
        ObjectCollectionSearchItem item = getModelObject();
        if (item != null && item.getObjectCollectionView().getCollection() != null
                && item.getObjectCollectionView().getCollection().getCollectionRef() != null) {
            oid = item.getObjectCollectionView().getCollection().getCollectionRef().getOid();
        }
        String finalOid = oid;
        AjaxLinkPanel ajaxLinkPanel = new AjaxLinkPanel(ID_CLICKABLE_NAME, nameModel) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                WebComponentUtil.dispatchToObjectDetailsPage(ObjectCollectionType.class, finalOid, this, true);
            }

            @Override
            public boolean isEnabled() {
                return StringUtils.isNotEmpty(finalOid) && WebComponentUtil.isAuthorized(ObjectCollectionType.class);
            }
        };
        ajaxLinkPanel.setOutputMarkupId(true);
        searchItemContainer.add(ajaxLinkPanel);
    }

    @Override
    protected IModel<String> createLabelModel() {
        ObjectCollectionSearchItem item = getModelObject();
        if (item == null) {
            return Model.of();
        }
        String name = item.getName();
        if (name == null) {
            return getPageBase().createStringResource("SearchObjectCollectionPanel.name.default");
        }
        if (item.getObjectCollectionView().getFilter() != null) {
            return getPageBase().createStringResource("SearchObjectCollectionPanel.name.withBasicFilter");
        }
        return Model.of();
    }

    protected boolean canRemoveSearchItem() {
        return false;
    }
}
