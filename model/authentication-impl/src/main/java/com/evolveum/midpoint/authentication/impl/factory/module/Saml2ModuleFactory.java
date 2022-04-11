/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.authentication.impl.factory.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.servlet.Filter;
import javax.servlet.ServletRequest;

import com.evolveum.midpoint.authentication.impl.module.authentication.RemoteModuleAuthenticationImpl;
import com.evolveum.midpoint.authentication.impl.provider.Saml2Provider;
import com.evolveum.midpoint.authentication.impl.util.AuthModuleImpl;
import com.evolveum.midpoint.authentication.api.IdentityProvider;
import com.evolveum.midpoint.authentication.api.AuthModule;
import com.evolveum.midpoint.authentication.api.AuthenticationChannel;
import com.evolveum.midpoint.authentication.impl.module.authentication.ModuleAuthenticationImpl;
import com.evolveum.midpoint.authentication.impl.module.configurer.SamlModuleWebSecurityConfigurer;
import com.evolveum.midpoint.authentication.impl.module.authentication.Saml2ModuleAuthenticationImpl;
import com.evolveum.midpoint.authentication.impl.module.configuration.SamlAdditionalConfiguration;
import com.evolveum.midpoint.authentication.impl.module.configuration.SamlModuleWebSecurityConfiguration;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.saml2.provider.service.servlet.filter.Saml2WebSsoAuthenticationRequestFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * @author skublik
 */
@Component
public class Saml2ModuleFactory extends RemoteModuleFactory {

    private static final Trace LOGGER = TraceManager.getTrace(Saml2ModuleFactory.class);

    @Override
    public boolean match(AbstractAuthenticationModuleType moduleType, AuthenticationChannel authenticationChannel) {
        return moduleType instanceof Saml2AuthenticationModuleType;
    }

    @Override
    public AuthModule createModuleFilter(AbstractAuthenticationModuleType moduleType, String sequenceSuffix, ServletRequest request,
                                         Map<Class<?>, Object> sharedObjects, AuthenticationModulesType authenticationsPolicy, CredentialsPolicyType credentialPolicy, AuthenticationChannel authenticationChannel) throws Exception {
        if (!(moduleType instanceof Saml2AuthenticationModuleType)) {
            LOGGER.error("This factory support only Saml2AuthenticationModuleType, but modelType is " + moduleType);
            return null;
        }

        isSupportedChannel(authenticationChannel);

        SamlModuleWebSecurityConfiguration.setProtector(getProtector());
        SamlModuleWebSecurityConfiguration configuration = SamlModuleWebSecurityConfiguration.build((Saml2AuthenticationModuleType)moduleType, sequenceSuffix, getPublicUrlPrefix(request), request);
        configuration.setSequenceSuffix(sequenceSuffix);
        configuration.addAuthenticationProvider(getObjectObjectPostProcessor().postProcess(new Saml2Provider()));

        SamlModuleWebSecurityConfigurer<SamlModuleWebSecurityConfiguration> module = getObjectObjectPostProcessor().postProcess(
                new SamlModuleWebSecurityConfigurer<>(configuration));
        HttpSecurity http = getNewHttpSecurity(module);
        setSharedObjects(http, sharedObjects);

        ModuleAuthenticationImpl moduleAuthentication = createEmptyModuleAuthentication(configuration, request);
        moduleAuthentication.setFocusType(moduleType.getFocusType());
        SecurityFilterChain filter = http.build();
        for (Filter f : filter.getFilters()){
            if (f instanceof Saml2WebSsoAuthenticationRequestFilter) {
                ((Saml2WebSsoAuthenticationRequestFilter) f).setRedirectMatcher(new AntPathRequestMatcher(module.getPrefix()
                        + RemoteModuleAuthenticationImpl.AUTHENTICATION_REQUEST_PROCESSING_URL_SUFFIX_WITH_REG_ID));
                break;
            }
        }
        return AuthModuleImpl.build(filter, configuration, moduleAuthentication);
    }

    public ModuleAuthenticationImpl createEmptyModuleAuthentication(SamlModuleWebSecurityConfiguration configuration, ServletRequest request) {
        Saml2ModuleAuthenticationImpl moduleAuthentication = new Saml2ModuleAuthenticationImpl();
        List<IdentityProvider> providers = new ArrayList<>();
        configuration.getRelyingPartyRegistrationRepository().forEach(
                p -> {
                    String authRequestPrefixUrl = request.getServletContext().getContextPath() + configuration.getPrefixOfModule()
                            + RemoteModuleAuthenticationImpl.AUTHENTICATION_REQUEST_PROCESSING_URL_SUFFIX_WITH_REG_ID;
                    SamlAdditionalConfiguration config = configuration.getAdditionalConfiguration().get(p.getRegistrationId());
                    IdentityProvider mp = new IdentityProvider()
                                .setLinkText(config.getLinkText())
                                .setRedirectLink(authRequestPrefixUrl.replace("{registrationId}", p.getRegistrationId()));
                        providers.add(mp);
                }
        );
        moduleAuthentication.setProviders(providers);
        moduleAuthentication.setAdditionalConfiguration(configuration.getAdditionalConfiguration());
        moduleAuthentication.setNameOfModule(configuration.getNameOfModule());
        moduleAuthentication.setPrefix(configuration.getPrefixOfModule());
        return moduleAuthentication;
    }
}
