/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.security.util;

import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;

import com.evolveum.midpoint.gui.impl.component.menu.LeftMenuAuthzUtil;

import com.evolveum.midpoint.gui.impl.page.login.PageLogin;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.web.application.Url;
import com.evolveum.midpoint.web.page.error.PageError401;
import com.evolveum.midpoint.web.page.login.*;

import com.github.openjson.JSONArray;
import com.github.openjson.JSONObject;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.MarkupStream;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Response;
import org.apache.wicket.request.cycle.RequestCycle;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.web.csrf.CsrfToken;

import com.evolveum.midpoint.authentication.api.authorization.AuthorizationAction;
import com.evolveum.midpoint.authentication.api.authorization.PageDescriptor;
import com.evolveum.midpoint.authentication.api.util.AuthUtil;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.impl.component.menu.LeftMenuAuthzUtil;
import com.evolveum.midpoint.web.component.menu.MainMenuItem;
import com.evolveum.midpoint.web.component.menu.MenuItem;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AuthenticationSequenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AuthenticationsPolicyType;

/**
 * @author lazyman
 * @author lskublik
 */
public class SecurityUtils {

    public static final String DEFAULT_LOGOUT_PATH = "/logout";

    public static boolean isMenuAuthorized(MainMenuItem item) {
        Class<?> clazz = item.getPageClass();
        return clazz == null || isPageAuthorized(clazz);
    }

    public static boolean isMenuAuthorized(MenuItem item) {
        Class<? extends WebPage> clazz = item.getPageClass();
        List<String> authz = LeftMenuAuthzUtil.getAuthorizationsForPage(clazz);
        if (CollectionUtils.isNotEmpty(authz)) {
            return WebComponentUtil.isAuthorized(authz);
        }
        return isPageAuthorized(clazz);
    }

    public static boolean isCollectionMenuAuthorized(MenuItem item) {
        Class<? extends WebPage> clazz = item.getPageClass();
        List<String> authz = LeftMenuAuthzUtil.getAuthorizationsForView(clazz);
        if (CollectionUtils.isNotEmpty(authz)) {
            return WebComponentUtil.isAuthorized(authz);
        }
        return isPageAuthorized(clazz);
    }

    public static boolean isPageAuthorized(Class<?> page) {
        if (page == null) {
            return false;
        }

        PageDescriptor descriptor = page.getAnnotation(PageDescriptor.class);
        if (descriptor == null) {
            return false;
        }

        AuthorizationAction[] actions = descriptor.action();
        List<String> list = new ArrayList<>();
        for (AuthorizationAction action : actions) {
            list.add(action.actionUri());
        }

        return WebComponentUtil.isAuthorized(list.toArray(new String[0]));
    }

    public static List<String> getPageAuthorizations(Class<?> page) {
        List<String> list = new ArrayList<>();
        if (page == null) {
            return list;
        }

        PageDescriptor descriptor = page.getAnnotation(PageDescriptor.class);
        if (descriptor == null) {
            return list;
        }

        AuthorizationAction[] actions = descriptor.action();
        for (AuthorizationAction action : actions) {
            list.add(action.actionUri());
        }
        return list;
    }

    public static WebMarkupContainer createHiddenInputForCsrf(String id) {
        WebMarkupContainer field = new WebMarkupContainer(id) {

            @Override
            public void onComponentTagBody(MarkupStream markupStream, ComponentTag openTag) {
                super.onComponentTagBody(markupStream, openTag);

                appendHiddenInputForCsrf(getResponse());
            }
        };
        field.setRenderBodyOnly(true);

        return field;
    }

    public static void appendHiddenInputForCsrf(Response resp) {
        CsrfToken csrfToken = getCsrfToken();
        if (csrfToken == null) {
            return;
        }

        String parameterName = csrfToken.getParameterName();
        String value = csrfToken.getToken();

        resp.write("<input type=\"hidden\" name=\"" + parameterName + "\" value=\"" + value + "\"/>");
    }

    public static CsrfToken getCsrfToken() {
        Request req = RequestCycle.get().getRequest();
        HttpServletRequest httpReq = (HttpServletRequest) req.getContainerRequest();

        return (CsrfToken) httpReq.getAttribute("_csrf");
    }

    public static AuthenticationSequenceType getSequenceByName(String name, AuthenticationsPolicyType authenticationPolicy) {
        if (authenticationPolicy == null || authenticationPolicy.getSequence() == null
                || authenticationPolicy.getSequence().isEmpty()) {
            return null;
        }

        Validate.notBlank(name, "Name for searching of sequence is blank");
        for (AuthenticationSequenceType sequence : authenticationPolicy.getSequence()) {
            if (sequence != null) {
                if (name.equals(sequence.getName())) {
                    if (sequence.getModule() == null || sequence.getModule().isEmpty()) {
                        return null;
                    }
                    return sequence;
                }
            }
        }
        return null;
    }

    public static String getPathForLogoutWithContextPath(String contextPath, @NotNull String prefix) {
        return StringUtils.isNotEmpty(contextPath)
                ? "/" + AuthUtil.stripSlashes(contextPath) + getPathForLogout(prefix)
                : getPathForLogout(prefix);
    }

    private static String getPathForLogout(@NotNull String prefix) {
        return "/" + AuthUtil.stripSlashes(prefix) + DEFAULT_LOGOUT_PATH;
    }
}
