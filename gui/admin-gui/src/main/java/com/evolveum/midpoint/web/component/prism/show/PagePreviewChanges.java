/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.component.prism.show;

import com.evolveum.midpoint.gui.api.component.tabs.PanelTab;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.model.api.ModelInteractionService;
import com.evolveum.midpoint.model.api.context.ModelContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.authentication.api.authorization.AuthorizationAction;
import com.evolveum.midpoint.authentication.api.authorization.PageDescriptor;
import com.evolveum.midpoint.authentication.api.authorization.Url;
import com.evolveum.midpoint.web.component.AjaxButton;
import com.evolveum.midpoint.web.component.TabbedPanel;
import com.evolveum.midpoint.web.component.breadcrumbs.Breadcrumb;
import com.evolveum.midpoint.web.component.form.MidpointForm;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;
import com.evolveum.midpoint.web.page.admin.PageAdmin;
import com.evolveum.midpoint.web.page.admin.PageAdminObjectDetails;
import com.evolveum.midpoint.web.page.admin.workflow.dto.EvaluatedTriggerGroupDto;
import com.evolveum.midpoint.web.util.OnePageParameterEncoder;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AbstractRoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PolicyRuleEnforcerPreviewOutputType;

import org.apache.wicket.RestartResponseException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.tabs.ITab;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@PageDescriptor(
        urls = {
                @Url (mountUrl = "/admin/previewChanges", matchUrlForSecurity = "/admin/previewChanges"),
        },
        encoder = OnePageParameterEncoder.class,
        action = {
            @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_PREVIEW_CHANGES_URL, label = "PageAdmin.auth.previewChanges.label", description = "PageAdmin.auth.previewChanges.description")
        })
public class PagePreviewChanges<O extends ObjectType> extends PageAdmin {
    private static final long serialVersionUID = 1L;

    private static final String ID_TABBED_PANEL = "tabbedPanel";
    private static final String ID_CONTINUE_EDITING = "continueEditing";
    private static final String ID_SAVE = "save";

    private static final Trace LOGGER = TraceManager.getTrace(PagePreviewChanges.class);

    private Map<PrismObject<O>, ModelContext<O>> modelContextMap;
    private ModelInteractionService modelInteractionService;

    private PageBase previousPage;

    public PagePreviewChanges() {
        throw new RestartResponseException(getApplication().getHomePage());
    }

    public PagePreviewChanges(Map<PrismObject<O>, ModelContext<O>> modelContextMap, ModelInteractionService modelInteractionService, PageBase previousPage) {
        this.modelContextMap = modelContextMap;
        this.modelInteractionService = modelInteractionService;
        this.previousPage = previousPage;
    }

    @Override
    protected void onInitialize(){
        super.onInitialize();
        initLayout();
    }


    private void initLayout() {
        Form mainForm = new MidpointForm("mainForm");
        mainForm.setMultiPart(true);
        add(mainForm);

        List<ITab> tabs = createTabs();
        TabbedPanel<ITab> previewChangesTabbedPanel = WebComponentUtil.createTabPanel(ID_TABBED_PANEL, this, tabs, null);
        previewChangesTabbedPanel.setOutputMarkupId(true);
        mainForm.add(previewChangesTabbedPanel);

        initButtons(mainForm);
    }

    private void initButtons(Form mainForm) {
        AjaxButton cancel = new AjaxButton(ID_CONTINUE_EDITING, createStringResource("PagePreviewChanges.button.continueEditing")) {
            @Override
            public void onClick(AjaxRequestTarget target) {
                cancelPerformed(target);
            }
        };
        mainForm.add(cancel);

        AjaxButton save = new AjaxButton(ID_SAVE, createStringResource("PagePreviewChanges.button.save")) {
            @Override
            public void onClick(AjaxRequestTarget target) {
                savePerformed(target);
            }
        };
        //save.add(new EnableBehaviour(() -> violationsEmpty()));           // does not work as expected (MID-4252)

        save.add(new VisibleBehaviour(() -> violationsEmpty()));            // so hiding the button altogether
        mainForm.add(save);
    }

    //TODO relocate the logic from the loop to some util method, code repeats in PreviewChangesTabPanel
    private boolean violationsEmpty() {
        for (ModelContext<O> modelContext : modelContextMap.values()) {
            PolicyRuleEnforcerPreviewOutputType enforcements = modelContext != null
                    ? modelContext.getPolicyRuleEnforcerPreviewOutput()
                    : null;
            List<EvaluatedTriggerGroupDto> triggerGroups = enforcements != null
                    ? Collections.singletonList(EvaluatedTriggerGroupDto.initializeFromRules(enforcements.getRule(), false, null))
                    : Collections.emptyList();
            if (!EvaluatedTriggerGroupDto.isEmpty(triggerGroups)){
                return false;
            }
        }
        return true;
    }

    private List<ITab> createTabs(){
        List<ITab> tabs = new ArrayList<>();
        modelContextMap.forEach((object, modelContext) -> {

            tabs.add(
                    new PanelTab(getTabPanelTitleModel(object)){

                        private static final long serialVersionUID = 1L;

                        @Override
                        public WebMarkupContainer createPanel(String panelId) {
                            return new PreviewChangesTabPanel(panelId, Model.of(modelContext));
                        }
                    });
        });
        return tabs;
    }

    private IModel<String> getTabPanelTitleModel(PrismObject<? extends ObjectType> object){
        return Model.of(WebComponentUtil.getEffectiveName(object, AbstractRoleType.F_DISPLAY_NAME));
    }


    private void cancelPerformed(AjaxRequestTarget target) {
        if (previousPage != null) {
            setResponsePage(previousPage);
            return;
        }
        redirectBack();
    }

    private void savePerformed(AjaxRequestTarget target) {
        if (previousPage != null) {
            setResponsePage(previousPage);

            if (previousPage instanceof PageAdminObjectDetails) {
                ((PageAdminObjectDetails) previousPage).setSaveOnConfigure(true);
            } else {
                error("Couldn't save changes - unexpected referring page: " + previousPage);
            }
        } else {
            Breadcrumb bc = redirectBack();
            error("Couldn't save changes - no instance for referring page; breadcrumb is " + bc);
        }
    }
}
