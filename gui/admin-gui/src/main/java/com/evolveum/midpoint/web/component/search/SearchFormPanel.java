/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.web.component.search;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.web.component.form.MidpointForm;

/**
 * @author Viliam Repan (lazyman)
 */
public class SearchFormPanel<C extends Containerable> extends BasePanel<Search<C>> {

    private static final String ID_SEARCH = "search";
    private static final String ID_SEARCH_FORM = "searchForm";

    public SearchFormPanel(String id, IModel<Search<C>> model) {
        super(id, model);

        initLayout();
    }

    protected void initLayout() {
        final Form searchForm = new MidpointForm(ID_SEARCH_FORM);
        add(searchForm);
        searchForm.setOutputMarkupId(true);

        SearchPanel<C> search = new SearchPanel<>(ID_SEARCH, getModel()) {
            private static final long serialVersionUID = 1L;

            @Override
            public void searchPerformed(AjaxRequestTarget target) {
                SearchFormPanel.this.searchPerformed(target);
            }

            @Override
            protected void saveSearch(Search search, AjaxRequestTarget target) {
                SearchFormPanel.this.saveSearch(search, target);
            }
        };
        searchForm.add(search);
    }

    protected void searchPerformed(AjaxRequestTarget target) {

    }

    protected void saveSearch(Search search, AjaxRequestTarget target) {
    }
}
