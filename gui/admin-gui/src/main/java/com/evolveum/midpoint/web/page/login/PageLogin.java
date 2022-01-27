/*
 * Copyright (c) 2010-2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.web.page.login;

import com.evolveum.midpoint.authentication.api.ModuleWebSecurityConfiguration;
import com.evolveum.midpoint.authentication.api.authorization.PageDescriptor;
import com.evolveum.midpoint.authentication.api.authorization.Url;
import com.evolveum.midpoint.authentication.api.config.MidpointAuthentication;
import com.evolveum.midpoint.authentication.api.config.ModuleAuthentication;
import com.evolveum.midpoint.authentication.api.util.AuthUtil;
import com.evolveum.midpoint.authentication.api.util.AuthenticationModuleNameConstants;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.SecurityPolicyUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.form.MidpointForm;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.page.forgetpassword.PageForgotPassword;
import com.evolveum.midpoint.web.security.util.SecurityUtils;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * @author mserbak
 * @author lskublik
 */
@PageDescriptor(urls = {
        @Url(mountUrl = "/login", matchUrlForSecurity = "/login")
}, permitAll = true, loginPage = true, authModule = AuthenticationModuleNameConstants.LOGIN_FORM)
public class PageLogin extends AbstractPageLogin {
    private static final long serialVersionUID = 1L;

    private static final Trace LOGGER = TraceManager.getTrace(PageLogin.class);

    private static final String ID_FORGET_PASSWORD = "forgetpassword";
    private static final String ID_SELF_REGISTRATION = "selfRegistration";
    private static final String ID_CSRF_FIELD = "csrfField";
    private static final String ID_FORM = "form";

    private static final String DOT_CLASS = PageLogin.class.getName() + ".";
    protected static final String OPERATION_LOAD_RESET_PASSWORD_POLICY = DOT_CLASS + "loadPasswordResetPolicy";
    private static final String OPERATION_LOAD_REGISTRATION_POLICY = DOT_CLASS + "loadRegistrationPolicy";

    private LoadableDetachableModel<SecurityPolicyType> securityPolicyModel;

    public PageLogin() {

        securityPolicyModel = new LoadableDetachableModel<>() {
            @Override
            protected SecurityPolicyType load() {
                Task task = createAnonymousTask(OPERATION_LOAD_RESET_PASSWORD_POLICY);
                OperationResult parentResult = new OperationResult(OPERATION_LOAD_RESET_PASSWORD_POLICY);
                try {
                    return getModelInteractionService().getSecurityPolicy((PrismObject<? extends FocusType>) null, task, parentResult);
                } catch (CommonException e) {
                    LOGGER.warn("Cannot read credentials policy: " + e.getMessage(), e);
                }
                return null;
            }
        };
    }

    private SecurityPolicyType getSecurityPolicy() {
        return securityPolicyModel.getObject();
    }

    @Override
    protected void initCustomLayer() {
        MidpointForm form = new MidpointForm(ID_FORM);
        form.add(AttributeModifier.replace("action", new IModel<String>() {
            @Override
            public String getObject() {
                return getUrlProcessingLogin();
            }
        }));
        add(form);

        BookmarkablePageLink<String> link = new BookmarkablePageLink<>(ID_FORGET_PASSWORD, PageForgotPassword.class);

        link.add(new VisibleEnableBehaviour() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isVisible() {
                SecurityPolicyType finalSecurityPolicy = getSecurityPolicy();
                if (finalSecurityPolicy == null) {
                    return false;
                }

                if (finalSecurityPolicy != null && finalSecurityPolicy.getCredentialsReset() != null
                        && StringUtils.isNotBlank(finalSecurityPolicy.getCredentialsReset().getAuthenticationSequenceName())) {
                    AuthenticationSequenceType sequence = SecurityUtils.getSequenceByName(finalSecurityPolicy.getCredentialsReset().getAuthenticationSequenceName(), finalSecurityPolicy.getAuthentication());
                    if (sequence != null
                            && (sequence.getChannel() == null || StringUtils.isBlank(sequence.getChannel().getUrlSuffix()))){
                        return false;
                    }
                }

                CredentialsPolicyType creds = finalSecurityPolicy.getCredentials();

                // TODO: Not entirely correct. This means we have reset somehow configured, but not necessarily enabled.
                if (creds != null
                        && ((creds.getSecurityQuestions() != null
                        && creds.getSecurityQuestions().getQuestionNumber() != null) || (finalSecurityPolicy.getCredentialsReset() != null))) {
                    return true;
                }

                return false;
            }
        });
        SecurityPolicyType securityPolicy = getSecurityPolicy();
        if (securityPolicy != null && securityPolicy.getCredentialsReset() != null
                && StringUtils.isNotBlank(securityPolicy.getCredentialsReset().getAuthenticationSequenceName())) {
            AuthenticationSequenceType sequence = SecurityUtils.getSequenceByName(securityPolicy.getCredentialsReset().getAuthenticationSequenceName(), securityPolicy.getAuthentication());
            if (sequence != null) {

                if (sequence.getChannel() == null || StringUtils.isBlank(sequence.getChannel().getUrlSuffix())) {
                    String message = "Sequence with name " + securityPolicy.getCredentialsReset().getAuthenticationSequenceName() + " doesn't contain urlSuffix";
                    LOGGER.error(message, new IllegalArgumentException(message));
                    error(message);
                }
                link.add(AttributeModifier.replace("href", new IModel<String>() {
                    @Override
                    public String getObject() {
                        return "./" + ModuleWebSecurityConfiguration.DEFAULT_PREFIX_OF_MODULE + "/" + sequence.getChannel().getUrlSuffix();
                    }
                }));
            }
        }
        form.add(link);

        BookmarkablePageLink<String> registration = new BookmarkablePageLink<>(ID_SELF_REGISTRATION, PageSelfRegistration.class);
        registration.add(new VisibleEnableBehaviour() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isVisible() {
                OperationResult parentResult = new OperationResult(OPERATION_LOAD_REGISTRATION_POLICY);

                RegistrationsPolicyType registrationPolicies = null;
                try {
                    Task task = createAnonymousTask(OPERATION_LOAD_REGISTRATION_POLICY);
                    registrationPolicies = getModelInteractionService().getFlowPolicy(null, task, parentResult);

                } catch (CommonException e) {
                    LOGGER.warn("Cannot read credentials policy: " + e.getMessage(), e);
                }

                boolean linkIsVisible = false;
                if (registrationPolicies != null
                        && registrationPolicies.getSelfRegistration() != null) {
                    linkIsVisible = true;
                }

                return linkIsVisible;
            }
        });
        if (securityPolicy != null) {
            SelfRegistrationPolicyType selfRegistrationPolicy = SecurityPolicyUtil.getSelfRegistrationPolicy(securityPolicy);
            if (selfRegistrationPolicy != null
                    && StringUtils.isNotBlank(selfRegistrationPolicy.getAdditionalAuthenticationName())) {
                AuthenticationSequenceType sequence = SecurityUtils.getSequenceByName(selfRegistrationPolicy.getAdditionalAuthenticationName(),
                        securityPolicy.getAuthentication());
                if (sequence != null) {
                    registration.add(AttributeModifier.replace("href", new IModel<String>() {
                        @Override
                        public String getObject() {
                            return "./" + ModuleWebSecurityConfiguration.DEFAULT_PREFIX_OF_MODULE + "/" + sequence.getChannel().getUrlSuffix();
                        }
                    }));
                }
            }
        }
        form.add(registration);

        WebMarkupContainer csrfField = SecurityUtils.createHiddenInputForCsrf(ID_CSRF_FIELD);
        form.add(csrfField);
    }

    private String getUrlProcessingLogin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof MidpointAuthentication) {
            MidpointAuthentication mpAuthentication = (MidpointAuthentication) authentication;
            ModuleAuthentication moduleAuthentication = mpAuthentication.getProcessingModuleAuthentication();
            if (moduleAuthentication != null
                    && (AuthenticationModuleNameConstants.LOGIN_FORM.equals(moduleAuthentication.getNameOfModuleType())
                    || AuthenticationModuleNameConstants.LDAP.equals(moduleAuthentication.getNameOfModuleType()))){
                String prefix = moduleAuthentication.getPrefix();
                return AuthUtil.stripSlashes(prefix) + "/spring_security_login";
            }
        }

        return "./spring_security_login";
    }

    @Override
    protected void onDetach() {
        super.onDetach();
        securityPolicyModel.detach();
    }
}
