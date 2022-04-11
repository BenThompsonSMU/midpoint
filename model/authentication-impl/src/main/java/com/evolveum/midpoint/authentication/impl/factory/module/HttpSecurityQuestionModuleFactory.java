/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.authentication.impl.factory.module;

import com.evolveum.midpoint.authentication.impl.provider.SecurityQuestionProvider;
import com.evolveum.midpoint.authentication.api.AuthenticationChannel;
import com.evolveum.midpoint.authentication.api.util.AuthenticationModuleNameConstants;
import com.evolveum.midpoint.authentication.impl.module.authentication.ModuleAuthenticationImpl;
import com.evolveum.midpoint.authentication.api.ModuleWebSecurityConfiguration;
import com.evolveum.midpoint.authentication.impl.module.configurer.HttpSecurityQuestionsModuleWebSecurityConfigurer;
import com.evolveum.midpoint.authentication.impl.module.authentication.HttpModuleAuthentication;
import com.evolveum.midpoint.authentication.impl.module.configuration.ModuleWebSecurityConfigurationImpl;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * @author skublik
 */
@Component
public class HttpSecurityQuestionModuleFactory extends AbstractCredentialModuleFactory
        <ModuleWebSecurityConfiguration, HttpSecurityQuestionsModuleWebSecurityConfigurer<ModuleWebSecurityConfiguration>> {

    @Override
    public boolean match(AbstractAuthenticationModuleType moduleType, AuthenticationChannel authenticationChannel) {
        return moduleType instanceof HttpSecQAuthenticationModuleType;
    }

    @Override
    protected ModuleWebSecurityConfiguration createConfiguration(AbstractAuthenticationModuleType moduleType, String prefixOfSequence, AuthenticationChannel authenticationChannel) {
        ModuleWebSecurityConfigurationImpl configuration = ModuleWebSecurityConfigurationImpl.build(moduleType,prefixOfSequence);
        configuration.setSequenceSuffix(prefixOfSequence);
        return configuration;
    }

    @Override
    protected HttpSecurityQuestionsModuleWebSecurityConfigurer<ModuleWebSecurityConfiguration> createModule(ModuleWebSecurityConfiguration configuration) {
        return getObjectObjectPostProcessor().postProcess(new HttpSecurityQuestionsModuleWebSecurityConfigurer<>(configuration));
    }

    @Override
    protected AuthenticationProvider createProvider(CredentialPolicyType usedPolicy) {
        return new SecurityQuestionProvider();
    }

    @Override
    protected Class<? extends CredentialPolicyType> supportedClass() {
        return SecurityQuestionsCredentialsPolicyType.class;
    }

    @Override
    protected ModuleAuthenticationImpl createEmptyModuleAuthentication(AbstractAuthenticationModuleType moduleType, ModuleWebSecurityConfiguration configuration) {
        HttpModuleAuthentication moduleAuthentication = new HttpModuleAuthentication(AuthenticationModuleNameConstants.SECURITY_QUESTIONS);
        moduleAuthentication.setPrefix(configuration.getPrefixOfModule());
        moduleAuthentication.setCredentialName(((AbstractCredentialAuthenticationModuleType)moduleType).getCredentialName());
        moduleAuthentication.setCredentialType(supportedClass());
        moduleAuthentication.setNameOfModule(configuration.getNameOfModule());
        moduleAuthentication.setRealm(((HttpSecQAuthenticationModuleType) moduleType).getRealm());
        return moduleAuthentication;
    }

}
