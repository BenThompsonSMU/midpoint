/*
 * Copyright (c) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.login;

import com.evolveum.midpoint.authentication.api.authorization.PageDescriptor;
import com.evolveum.midpoint.authentication.api.authorization.Url;
import com.evolveum.midpoint.authentication.api.config.MidpointAuthentication;
import com.evolveum.midpoint.authentication.api.config.ModuleAuthentication;
import com.evolveum.midpoint.authentication.api.util.AuthConstants;
import com.evolveum.midpoint.authentication.api.util.AuthUtil;
import com.evolveum.midpoint.authentication.api.util.AuthenticationModuleNameConstants;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.util.SecurityPolicyUtil;
import com.evolveum.midpoint.web.component.form.MidpointForm;
import com.evolveum.midpoint.web.component.prism.DynamicFormPanel;
import com.evolveum.midpoint.web.page.error.PageError;
import com.evolveum.midpoint.web.security.util.SecurityUtils;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusIdentificationAuthenticationModuleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ModuleItemConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SecurityPolicyType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import com.evolveum.prism.xml.ns._public.types_3.ItemPathType;

import com.github.openjson.JSONArray;
import com.github.openjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.HiddenField;
import org.apache.wicket.markup.html.form.RequiredTextField;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.stream.Collectors;

@PageDescriptor(urls = {
        @Url(mountUrl = "/focusIdentification", matchUrlForSecurity = "/focusIdentification")
}, permitAll = true, loginPage = true, authModule = AuthenticationModuleNameConstants.FOCUS_IDENTIFICATION)
public class PageFocusIdentification extends PageAuthenticationBase {
    private static final long serialVersionUID = 1L;


    private static final String ID_MAIN_FORM = "mainForm";
    private static final String ID_ATTRIBUTE_VALUES = "attributeValues";
    private static final String ID_ATTRIBUTE_NAME = "attributeName";
    private static final String ID_ATTRIBUTE_VALUE = "attributeValue";
    private static final String ID_BACK_BUTTON = "back";
    private static final String ID_CSRF_FIELD = "csrfField";

    LoadableModel<List<ItemPathType>> attributesPathModel;
    private LoadableDetachableModel<UserType> userModel;
    IModel<String> attrValuesModel;

    public PageFocusIdentification() {
    }

    @Override
    protected void initModels() {
        attrValuesModel = Model.of();
        userModel = new LoadableDetachableModel<>() {
            @Override
            protected UserType load() {
                return new UserType();
            }
        };
        attributesPathModel = new LoadableModel<>(false) {
            private static final long serialVersionUID = 1L;

            @Override
            protected List<ItemPathType> load() {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (!(authentication instanceof MidpointAuthentication)) {
                    getSession().error(getString("No midPoint authentication is found"));
                    throw new RestartResponseException(PageError.class);
                }
                MidpointAuthentication mpAuthentication = (MidpointAuthentication) authentication;
                ModuleAuthentication moduleAuthentication = mpAuthentication.getProcessingModuleAuthentication();
                if (moduleAuthentication == null
                        && !AuthenticationModuleNameConstants.FOCUS_IDENTIFICATION.equals(moduleAuthentication.getModuleTypeName())) {
                    getSession().error(getString("No authentication module is found"));
                    throw new RestartResponseException(PageError.class);
                }
                if (StringUtils.isEmpty(moduleAuthentication.getModuleIdentifier())) {
                    getSession().error(getString("No module identifier is defined"));
                    throw new RestartResponseException(PageError.class);
                }
                FocusIdentificationAuthenticationModuleType module = getModuleByIdentifier(moduleAuthentication.getModuleIdentifier());
                if (module == null) {
                    getSession().error(getString("No module with identifier \"" + moduleAuthentication.getModuleIdentifier() + "\" is found"));
                    throw new RestartResponseException(PageError.class);
                }
                List<ModuleItemConfigurationType> itemConfigs = module.getItem();
                return itemConfigs.stream()
                        .map(config -> config.getPath())
                        .collect(Collectors.toList());
            }
        };
    }

    private FocusIdentificationAuthenticationModuleType getModuleByIdentifier(String moduleIdentifier) {
        if (StringUtils.isEmpty(moduleIdentifier)) {
            return null;
        }
        UserType user = userModel.getObject();
        SecurityPolicyType securityPolicy = resolveUserSecurityPolicy(user);
        if (securityPolicy.getAuthentication() == null || securityPolicy.getAuthentication().getModules() == null) {
            return null;
        }
        return securityPolicy.getAuthentication().getModules().getFocusIdentification()
                .stream()
                .filter(m -> moduleIdentifier.equals(m.getIdentifier()))
                .findFirst()
                .orElse(null);
    }

    @Override
    protected void initCustomLayout() {
        MidpointForm<?> form = new MidpointForm<>(ID_MAIN_FORM);
        form.add(AttributeModifier.replace("action", this::getUrlProcessingLogin));
        add(form);

        WebMarkupContainer csrfField = SecurityUtils.createHiddenInputForCsrf(ID_CSRF_FIELD);
        form.add(csrfField);

        HiddenField<String> verified = new HiddenField<>(ID_ATTRIBUTE_VALUES, attrValuesModel);
        verified.setOutputMarkupId(true);
        form.add(verified);

        initAttributesLayout(form);

        initButtons(form);


    }

    private void initAttributesLayout(MidpointForm<?> form) {

        Label attributeNameLabel = new Label(ID_ATTRIBUTE_NAME, resolveAttributeLabel(attributesPathModel));
        form.add(attributeNameLabel);

        RequiredTextField<String> attributeValue = new RequiredTextField<>(ID_ATTRIBUTE_VALUE, Model.of());
        attributeValue.setOutputMarkupId(true);
        attributeValue.add(new AjaxFormComponentUpdatingBehavior("blur") {
            @Override
            protected void onUpdate(AjaxRequestTarget ajaxRequestTarget) {
                updateAttributeValues(ajaxRequestTarget);
            }
        });
        attributeValue.add(WebComponentUtil.getBlurOnEnterKeyDownBehavior());
        form.add(attributeValue);
    }

    private void updateAttributeValues(AjaxRequestTarget ajaxRequestTarget) {
        attrValuesModel.setObject(generateAttributeValuesString());
        ajaxRequestTarget.add(getHiddenField());
    }

    private String resolveAttributeLabel(IModel<List<ItemPathType>> path) {
        if (path == null) {
            return "";
        }
        List<ItemPathType> itemPaths = path.getObject();
        return itemPaths.stream()
                .map(p -> translateAttribute(p))
                .collect(Collectors.joining(" or "));
    }

    private String translateAttribute(ItemPathType itemPath) {
        ItemDefinition<?> def = userModel.getObject().asPrismObject().getDefinition().findItemDefinition(itemPath.getItemPath());
        return WebComponentUtil.getItemDefinitionDisplayName(def);
    }

    private void initButtons(MidpointForm form) {
        form.add(createBackButton(ID_BACK_BUTTON));
    }

    private Component getVerifiedField() {
        return  get(ID_MAIN_FORM).get(ID_ATTRIBUTE_VALUE);
    }

    private Component getHiddenField() {
        return  get(ID_MAIN_FORM).get(ID_ATTRIBUTE_VALUES);
    }

    @Override
    protected ObjectQuery createStaticFormQuery() {
        String username = "";
        return getPrismContext().queryFor(UserType.class).item(UserType.F_NAME)
                .eqPoly(username).matchingNorm().build();
    }

    @Override
    protected DynamicFormPanel<UserType> getDynamicForm() {
        return null;
    }

    private String getUrlProcessingLogin() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof MidpointAuthentication) {
            MidpointAuthentication mpAuthentication = (MidpointAuthentication) authentication;
            ModuleAuthentication moduleAuthentication = mpAuthentication.getProcessingModuleAuthentication();
            if (moduleAuthentication != null
                    && AuthenticationModuleNameConstants.FOCUS_IDENTIFICATION.equals(moduleAuthentication.getModuleTypeName())){
                String prefix = moduleAuthentication.getPrefix();
                return AuthUtil.stripSlashes(prefix) + "/spring_security_login";
            }
        }

        String key = "web.security.flexAuth.unsupported.auth.type";
        error(getString(key));
        return "/midpoint/spring_security_login";
    }

    private String generateAttributeValuesString() {
        JSONArray attrValues = new JSONArray();
        attributesPathModel.getObject().forEach(entry -> {
            JSONObject json  = new JSONObject();
            json.put(AuthConstants.ATTR_VERIFICATION_J_PATH, entry.toString());
            json.put(AuthConstants.ATTR_VERIFICATION_J_VALUE, getVerifiedField().getDefaultModelObjectAsString());
            attrValues.put(json);
        });
        if (attrValues.length() == 0) {
            return null;
        }
        return attrValues.toString();
    }

    @Override
    protected IModel<String> getLoginPanelTitleModel() {
        return createStringResource("PageFocusIdentification.title");
    }

    @Override
    protected IModel<String> getLoginPanelDescriptionModel() {
        return createStringResource("PageFocusIdentification.description");
    }

}
