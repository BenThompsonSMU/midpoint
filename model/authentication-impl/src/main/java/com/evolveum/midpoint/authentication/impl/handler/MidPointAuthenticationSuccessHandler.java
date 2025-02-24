/*
 * Copyright (C) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.authentication.impl.handler;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import com.evolveum.midpoint.authentication.api.AuthModule;
import com.evolveum.midpoint.authentication.api.AuthenticationModuleState;
import com.evolveum.midpoint.authentication.api.config.MidpointAuthentication;
import com.evolveum.midpoint.authentication.api.util.AuthConstants;
import com.evolveum.midpoint.authentication.impl.factory.module.AuthModuleRegistryImpl;
import com.evolveum.midpoint.authentication.impl.module.authentication.ModuleAuthenticationImpl;
import com.evolveum.midpoint.authentication.impl.module.configuration.ModuleWebSecurityConfigurationImpl;
import com.evolveum.midpoint.authentication.impl.util.AuthSequenceUtil;
import com.evolveum.midpoint.schema.util.SecurityPolicyUtil;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AuthenticationSequenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SecurityPolicyType;

/**
 * @author skublik
 */
public class MidPointAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private static final Trace LOGGER = TraceManager.getTrace(MidPointAuthenticationSuccessHandler.class);
    @Autowired
    private AuthModuleRegistryImpl authModuleRegistry;
    private String defaultTargetUrl;

    public MidPointAuthenticationSuccessHandler() {
        setRequestCache(new HttpSessionRequestCache());
    }

    private RequestCache requestCache;

    @Override
    public void setRequestCache(RequestCache requestCache) {
        super.setRequestCache(requestCache);
        this.requestCache = requestCache;
    }

    public RequestCache getRequestCache() {
        return requestCache;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws ServletException, IOException {

        String urlSuffix = AuthConstants.DEFAULT_PATH_AFTER_LOGIN;
        String authenticatedChannel = null;
        if (authentication instanceof MidpointAuthentication) {
            MidpointAuthentication mpAuthentication = (MidpointAuthentication) authentication;
            ModuleAuthenticationImpl moduleAuthentication = (ModuleAuthenticationImpl) mpAuthentication.getProcessingModuleAuthentication();
            moduleAuthentication.setState(AuthenticationModuleState.SUCCESSFULLY);
            if (mpAuthentication.getAuthenticationChannel() != null) {
                authenticatedChannel = mpAuthentication.getAuthenticationChannel().getChannelId();
                boolean continueSequence = false;
                boolean newSecPolicy = isNewSecurityPolicyFound(mpAuthentication);
                if (newSecPolicy) {
                    SecurityPolicyType securityPolicy = ((MidPointPrincipal) mpAuthentication.getPrincipal()).getApplicableSecurityPolicy();
                    updateMidpointAuthentication(request, mpAuthentication, securityPolicy);
                    if (!isCorrectlyConfigured(securityPolicy, mpAuthentication)) {
                        moduleAuthentication.setState(AuthenticationModuleState.FAILURE);
                        getRedirectStrategy().sendRedirect(request, response, AuthConstants.DEFAULT_PATH_AFTER_LOGOUT);
                        return;
                    }
                    continueSequence = true;
                }

                if (mpAuthentication.isLast(moduleAuthentication) && !mpAuthentication.isAuthenticated()) {
                    urlSuffix = mpAuthentication.getAuthenticationChannel().getPathAfterUnsuccessfulAuthentication();
                    HttpSession session = request.getSession(false);
                    if (session != null) {
                        request.getSession().setAttribute("SPRING_SECURITY_LAST_EXCEPTION", mpAuthentication.getAuthenticationExceptionIfExsits());
                    }

                    getRedirectStrategy().sendRedirect(request, response, urlSuffix);
                    return;
                }
                if (mpAuthentication.isAuthenticated() && !continueSequence) {
                    urlSuffix = mpAuthentication.getAuthenticationChannel().getPathAfterSuccessfulAuthentication();
                    mpAuthentication.getAuthenticationChannel().postSuccessAuthenticationProcessing();
                    if (mpAuthentication.getAuthenticationChannel().isPostAuthenticationEnabled()) {
                        getRedirectStrategy().sendRedirect(request, response, urlSuffix);
                        return;
                    }
                } else {
                    urlSuffix = mpAuthentication.getAuthenticationChannel().getPathDuringProccessing();
                }
            }
            //TODO: record success?
        }

        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null && savedRequest.getRedirectUrl().contains(ModuleWebSecurityConfigurationImpl.DEFAULT_PREFIX_OF_MODULE_WITH_SLASH + "/")) {
            String target = savedRequest.getRedirectUrl().substring(0, savedRequest.getRedirectUrl().indexOf(ModuleWebSecurityConfigurationImpl.DEFAULT_PREFIX_OF_MODULE_WITH_SLASH + "/")) + urlSuffix;
            getRedirectStrategy().sendRedirect(request, response, target);
            return;
        }
        if (savedRequest != null && authenticatedChannel != null) {
            int startIndex = savedRequest.getRedirectUrl().indexOf(request.getContextPath()) + request.getContextPath().length();
            int endIndex = savedRequest.getRedirectUrl().length() - 1;
            String channelSavedRequest = null;
            if ((startIndex < endIndex)) {
                String localePath = savedRequest.getRedirectUrl().substring(startIndex, endIndex);
                channelSavedRequest = AuthSequenceUtil.searchChannelByPath(localePath);
            }
            if (!(channelSavedRequest.equals(authenticatedChannel))) {
                getRedirectStrategy().sendRedirect(request, response, urlSuffix);
                return;
            }

        } else {
            setDefaultTargetUrl(urlSuffix);
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }

    private boolean isNewSecurityPolicyFound(MidpointAuthentication mpAuthentication) {
        if (mpAuthentication.getPrincipal() == null || !(mpAuthentication.getPrincipal() instanceof MidPointPrincipal)) {
            return false;
        }
        if (mpAuthentication.isMerged()) {
            return false;
        }
        MidPointPrincipal principal = (MidPointPrincipal) mpAuthentication.getPrincipal();
        SecurityPolicyType securityPolicy = principal.getApplicableSecurityPolicy();
        if (securityPolicy == null) {
            return false;
        }
        AuthenticationSequenceType processingSequence = mpAuthentication.getSequence();
        AuthenticationSequenceType sequence = SecurityPolicyUtil.findSequenceByIdentifier(securityPolicy,
                mpAuthentication.getSequenceIdentifier());
        return sequence != null && processingSequence.getModule().size() != sequence.getModule().size();
    }

    private boolean isCorrectlyConfigured(SecurityPolicyType securityPolicy, MidpointAuthentication mpAuthentication) {
        AuthenticationSequenceType sequence = SecurityPolicyUtil.findSequenceByIdentifier(securityPolicy, mpAuthentication.getSequenceIdentifier());
        if (sequence == null) {
            return false;
        }
        return !mpAuthentication.wrongConfiguredSufficientModuleExists();
    }


    private void updateMidpointAuthentication(HttpServletRequest request, MidpointAuthentication mpAuthentication, SecurityPolicyType newSecurityPolicy) {
        AuthenticationSequenceType sequence = SecurityPolicyUtil.findSequenceByIdentifier(newSecurityPolicy,
                mpAuthentication.getSequenceIdentifier());
        mpAuthentication.setSequence(sequence);
        List<AuthModule> modules = AuthSequenceUtil.buildModuleFilters(
                authModuleRegistry, sequence, request, newSecurityPolicy.getAuthentication().getModules(),
                newSecurityPolicy.getCredentials(), mpAuthentication.getSharedObjects(), mpAuthentication.getAuthenticationChannel());
        modules.removeIf(Objects::isNull);
        mpAuthentication.setAuthModules(modules);
        mpAuthentication.setMerged(true);
    }

    @Override
    protected String getTargetUrlParameter() {
        return defaultTargetUrl;
    }

    @Override
    public void setDefaultTargetUrl(String defaultTargetUrl) {
        this.defaultTargetUrl = defaultTargetUrl;
    }

    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response) {
        if (StringUtils.isEmpty(defaultTargetUrl)) {
            return super.determineTargetUrl(request, response);
        }

        return defaultTargetUrl;
    }
}
