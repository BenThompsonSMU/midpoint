/*
 * Copyright (c) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.page.admin.resources.content;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.evolveum.midpoint.web.application.Url;

import org.apache.wicket.RestartResponseException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.tabs.ITab;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.util.string.StringValue;

import com.evolveum.midpoint.gui.api.component.tabs.PanelTab;
import com.evolveum.midpoint.gui.api.factory.wrapper.PrismObjectWrapperFactory;
import com.evolveum.midpoint.gui.api.factory.wrapper.WrapperContext;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.prism.ItemStatus;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismObjectWrapper;
import com.evolveum.midpoint.gui.api.prism.wrapper.ShadowWrapper;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.application.AuthorizationAction;
import com.evolveum.midpoint.web.application.PageDescriptor;
import com.evolveum.midpoint.web.component.AjaxButton;
import com.evolveum.midpoint.web.component.AjaxSubmitButton;
import com.evolveum.midpoint.web.component.AjaxTabbedPanel;
import com.evolveum.midpoint.web.component.dialog.ConfirmationPanel;
import com.evolveum.midpoint.web.component.form.MidpointForm;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.page.admin.PageAdmin;
import com.evolveum.midpoint.web.page.admin.configuration.PageDebugView;
import com.evolveum.midpoint.web.page.admin.resources.PageResources;
import com.evolveum.midpoint.web.page.admin.resources.ShadowDetailsTabPanel;
import com.evolveum.midpoint.web.page.admin.resources.ShadowSummaryPanel;
import com.evolveum.midpoint.web.util.OnePageParameterEncoder;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;

/**
 * @author lazyman
 */
//TODO unify with PageAdminObjectDetails!
@PageDescriptor(
        urls = {
                @Url(mountUrl = "/admin/resources/account")
        },
        encoder = OnePageParameterEncoder.class, action = {
        @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_RESOURCES_ALL_URL,
                label = "PageAdminResources.auth.resourcesAll.label",
                description = "PageAdminResources.auth.resourcesAll.description"),
        @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_RESOURCES_ACCOUNT_URL,
                label = "PageAccount.auth.resourcesAccount.label",
                description = "PageAccount.auth.resourcesAccount.description")})
public class PageAccount extends PageAdmin {

    private static final long serialVersionUID = 1L;

    private static final Trace LOGGER = TraceManager.getTrace(PageAccount.class);
    private static final String DOT_CLASS = PageAccount.class.getName() + ".";
    private static final String OPERATION_LOAD_ACCOUNT = DOT_CLASS + "loadAccount";
    private static final String OPERATION_SAVE_ACCOUNT = DOT_CLASS + "saveAccount";

    private static final String ID_MAIN_FORM = "mainForm";
    private static final String ID_SUMMARY = "summary";
    private static final String ID_TAB_PANEL = "tabPanel";
    private static final String ID_PROTECTED_MESSAGE = "protectedMessage";
    private static final String ID_SAVE = "save";
    private static final String ID_BACK = "back";
    private static final String ID_EDIT_XML = "editXml";

    public static final String PARAMETER_SELECTED_TAB = "tab";

    private LoadableModel<ShadowWrapper> accountModel;

    public PageAccount(final PageParameters parameters) {
        accountModel = new LoadableModel<>(false) {

            private static final long serialVersionUID = 1L;

            @Override
            protected ShadowWrapper load() {
                return loadAccount(parameters);
            }
        };
    }

    @Override
    protected void onInitialize(){
        super.onInitialize();
        initLayout();
    }

    private ShadowWrapper loadAccount(PageParameters parameters) {
        Task task = createSimpleTask(OPERATION_LOAD_ACCOUNT);
        OperationResult result = task.getResult();

        Collection<SelectorOptions<GetOperationOptions>> options = getOperationOptionsBuilder()
                .item(ShadowType.F_RESOURCE_REF).resolve()
                .build();
        StringValue oid = parameters != null ? parameters.get(OnePageParameterEncoder.PARAMETER) : null;
        if (oid == null) {
            getSession().error(getString("pageAccount.message.cantEditAccount"));
            showResult(result);
            throw new RestartResponseException(PageResources.class);
        }
        PrismObject<ShadowType> account = WebModelServiceUtils.loadObject(ShadowType.class, oid.toString(), options,
                PageAccount.this, task, result);

        if (account == null) {
            getSession().error(getString("pageAccount.message.cantEditAccount"));
            showResult(result);
            throw new RestartResponseException(PageResources.class);
        }

        PrismObjectWrapperFactory<ShadowType> owf = getRegistry().getObjectWrapperFactory(account.getDefinition());
        WrapperContext context = new WrapperContext(task, result);
        context.setShowEmpty(false);
        ShadowWrapper wrapper;
        try {
            wrapper = (ShadowWrapper) owf.createObjectWrapper(account, ItemStatus.NOT_CHANGED, context);
            //TODO: fetch result???
        } catch (SchemaException e) {
            LOGGER.error("Cannot create wrapper for shadow {}", account);
            result.recordFatalError(getString("PageAccount.message.loadAccount.fatalError"));
            throw new RestartResponseException(PageResources.class);
        }

        return wrapper;
    }

    private void initLayout() {
        add(new ShadowSummaryPanel(ID_SUMMARY, Model.of(accountModel.getObject().getObject().asObjectable()), WebComponentUtil.getSummaryPanelSpecification(ShadowType.class, getCompiledGuiProfile())));


        WebMarkupContainer protectedMessage = new WebMarkupContainer(ID_PROTECTED_MESSAGE);
        protectedMessage.add(new VisibleEnableBehaviour() {

            private static final long serialVersionUID = 1L;

            @Override
            public boolean isVisible() {
                ShadowWrapper wrapper = accountModel.getObject();
                return wrapper.isProtected();
            }
        });
        add(protectedMessage);

        MidpointForm mainForm = new MidpointForm(ID_MAIN_FORM);
        mainForm.setMultiPart(true);
        add(mainForm);

        mainForm.add(createTabsPanel(mainForm));

        initButtons(mainForm);
    }


    private AjaxTabbedPanel<ITab> createTabsPanel(MidpointForm<PrismObjectWrapper<ShadowType>> form) {
        List<ITab> tabs = new ArrayList<>();

        tabs.add(new PanelTab(createStringResource("PageAccount.tab.details")) {
            private static final long serialVersionUID = 1L;

            @Override
            public WebMarkupContainer createPanel(String panelId) {
                return new ShadowDetailsTabPanel(panelId, (LoadableModel) accountModel);
            }
        });

        AjaxTabbedPanel<ITab> tabPanel = new AjaxTabbedPanel<>(ID_TAB_PANEL, tabs) {

            private static final long serialVersionUID = 1L;

            @Override
            protected void onTabChange(int index) {
                updateBreadcrumbParameters(PARAMETER_SELECTED_TAB, index);
            }
        };
        tabPanel.setOutputMarkupId(true);
        return tabPanel;
    }


    private void initButtons(Form mainForm) {
        AjaxSubmitButton save = new AjaxSubmitButton(ID_SAVE, createStringResource("pageAccount.button.save")) {

            private static final long serialVersionUID = 1L;

            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                savePerformed(target);
            }

            @Override
            protected void onError(AjaxRequestTarget target) {
                target.add(getFeedbackPanel());
            }
        };
        save.add(new VisibleEnableBehaviour() {

            private static final long serialVersionUID = 1L;

            @Override
            public boolean isVisible() {
                ShadowWrapper wrapper = accountModel.getObject();
                return !wrapper.isProtected();
            }
        });
        mainForm.add(save);

        AjaxButton back = new AjaxButton(ID_BACK, createStringResource("pageAccount.button.back")) {

            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                cancelPerformed(target);
            }
        };
        mainForm.add(back);

        AjaxButton editXmlButton = new AjaxButton(ID_EDIT_XML, createStringResource("AbstractObjectMainPanel.editXmlButton")) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                ConfirmationPanel confirmationPanel = new ConfirmationPanel(getMainPopupBodyId(),
                        createStringResource("AbstractObjectMainPanel.confirmEditXmlRedirect")) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void yesPerformed(AjaxRequestTarget target) {
                        PageParameters parameters = new PageParameters();
                        parameters.add(PageDebugView.PARAM_OBJECT_ID, accountModel.getObject().getOid());
                        parameters.add(PageDebugView.PARAM_OBJECT_TYPE, "ShadowType");
                        navigateToNext(PageDebugView.class, parameters);
                    }

                };

                showMainPopup(confirmationPanel, target);
            }
        };
        editXmlButton.add(new VisibleEnableBehaviour() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isVisible() {
                return WebComponentUtil.isAuthorized(AuthorizationConstants.AUTZ_UI_CONFIGURATION_URL,
                                AuthorizationConstants.AUTZ_UI_CONFIGURATION_DEBUG_URL) &&
                        !accountModel.getObject().isReadOnly();
            }
        });
        mainForm.add(editXmlButton);
    }

    @Override
    protected IModel<String> createPageTitleModel() {
        return new LoadableModel<>(false) {

            private static final long serialVersionUID = 1L;

            @Override
            protected String load() {
                PrismObject<ShadowType> account = accountModel.getObject().getObject();
                String accName = WebComponentUtil.getName(account);

                ResourceType resource = (ResourceType) account.asObjectable().getResourceRef().asReferenceValue().getObject().asObjectable();
                String name = WebComponentUtil.getName(resource);

                //TODO: refactor
                return createStringResourceStatic("PageAccount.title", accName, name).getString();
            }
        };
    }

    private void savePerformed(AjaxRequestTarget target) {
        LOGGER.debug("Saving account changes.");

        OperationResult result = new OperationResult(OPERATION_SAVE_ACCOUNT);
        try {
            WebComponentUtil.revive(accountModel, getPrismContext());
            PrismObjectWrapper<ShadowType> wrapper = accountModel.getObject();
            ObjectDelta<ShadowType> delta = wrapper.getObjectDelta();
            if (delta == null) {
                return;
            }
            if (delta.getPrismContext() == null) {
                getPrismContext().adopt(delta);
            }
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Account delta computed from form:\n{}", delta.debugDump(3));
            }

            if (delta.isEmpty()) {
                return;
            }
            WebComponentUtil.encryptCredentials(delta, true, getMidpointApplication());

            Task task = createSimpleTask(OPERATION_SAVE_ACCOUNT);
            Collection<ObjectDelta<? extends ObjectType>> deltas = new ArrayList<>();
            deltas.add(delta);

            getModelService().executeChanges(deltas, null, task, result);
            result.recomputeStatus();
        } catch (Exception ex) {
            result.recordFatalError(getString("PageAccount.message.savePerformed.fatalError"), ex);
            LoggingUtils.logUnexpectedException(LOGGER, "Couldn't save account", ex);
        }

        if (!result.isSuccess()) {
            showResult(result);
            target.add(getFeedbackPanel());
        } else {
            showResult(result);

            redirectBack();
        }
    }

    private void cancelPerformed(AjaxRequestTarget target) {
        redirectBack();
    }
}
