/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.authentication.impl.factory.module;

import com.evolveum.midpoint.authentication.impl.provider.PasswordProvider;
import com.evolveum.midpoint.authentication.api.AuthenticationChannel;
import com.evolveum.midpoint.authentication.impl.module.authentication.ModuleAuthenticationImpl;
import com.evolveum.midpoint.authentication.impl.module.configurer.LoginFormModuleWebSecurityConfigurer;
import com.evolveum.midpoint.authentication.impl.module.authentication.LoginFormModuleAuthenticationImpl;
import com.evolveum.midpoint.authentication.impl.module.configuration.LoginFormModuleWebSecurityConfiguration;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * @author skublik
 */
@Component
public class LoginFormModuleFactoryImpl extends AbstractCredentialModuleFactory
        <LoginFormModuleWebSecurityConfiguration, LoginFormModuleWebSecurityConfigurer<LoginFormModuleWebSecurityConfiguration>> {

    @Override
    public boolean match(AbstractAuthenticationModuleType moduleType, AuthenticationChannel authenticationChannel) {
        return moduleType instanceof LoginFormAuthenticationModuleType;
    }

    @Override
    protected LoginFormModuleWebSecurityConfiguration createConfiguration(AbstractAuthenticationModuleType moduleType, String prefixOfSequence, AuthenticationChannel authenticationChannel) {
        LoginFormModuleWebSecurityConfiguration configuration = LoginFormModuleWebSecurityConfiguration.build(moduleType,prefixOfSequence);
        configuration.setSequenceSuffix(prefixOfSequence);
        return configuration;
    }

    @Override
    protected LoginFormModuleWebSecurityConfigurer<LoginFormModuleWebSecurityConfiguration> createModule(LoginFormModuleWebSecurityConfiguration configuration) {
        return  getObjectObjectPostProcessor().postProcess(new LoginFormModuleWebSecurityConfigurer<>(configuration));
    }

    @Override
    protected AuthenticationProvider createProvider(CredentialPolicyType usedPolicy) {
        return new PasswordProvider();
    }

    @Override
    protected Class<? extends CredentialPolicyType> supportedClass() {
        return PasswordCredentialsPolicyType.class;
    }

    @Override
    protected ModuleAuthenticationImpl createEmptyModuleAuthentication(AbstractAuthenticationModuleType moduleType,
            LoginFormModuleWebSecurityConfiguration configuration) {
        LoginFormModuleAuthenticationImpl moduleAuthentication = new LoginFormModuleAuthenticationImpl();
        moduleAuthentication.setPrefix(configuration.getPrefixOfModule());
        moduleAuthentication.setCredentialName(((AbstractCredentialAuthenticationModuleType)moduleType).getCredentialName());
        moduleAuthentication.setCredentialType(supportedClass());
        moduleAuthentication.setNameOfModule(configuration.getNameOfModule());
        return moduleAuthentication;
    }

}
