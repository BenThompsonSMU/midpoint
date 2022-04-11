/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.authentication.impl.factory.module;

import com.evolveum.midpoint.authentication.impl.provider.MailNonceProvider;
import com.evolveum.midpoint.authentication.api.AuthenticationChannel;
import com.evolveum.midpoint.authentication.impl.module.authentication.ModuleAuthenticationImpl;
import com.evolveum.midpoint.authentication.api.ModuleWebSecurityConfiguration;
import com.evolveum.midpoint.authentication.impl.module.configurer.MailNonceFormModuleWebSecurityConfigurer;
import com.evolveum.midpoint.authentication.impl.module.authentication.MailNonceModuleAuthenticationImpl;
import com.evolveum.midpoint.authentication.impl.module.configuration.ModuleWebSecurityConfigurationImpl;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * @author skublik
 */
@Component
public class MailNonceModuleFactory extends AbstractCredentialModuleFactory
        <ModuleWebSecurityConfiguration, MailNonceFormModuleWebSecurityConfigurer<ModuleWebSecurityConfiguration>> {

    @Override
    public boolean match(AbstractAuthenticationModuleType moduleType, AuthenticationChannel authenticationChannel) {
        return moduleType instanceof MailNonceAuthenticationModuleType;
    }

    @Override
    protected ModuleWebSecurityConfiguration createConfiguration(AbstractAuthenticationModuleType moduleType, String prefixOfSequence, AuthenticationChannel authenticationChannel) {
        ModuleWebSecurityConfigurationImpl configuration = ModuleWebSecurityConfigurationImpl.build(moduleType,prefixOfSequence);
        configuration.setSequenceSuffix(prefixOfSequence);
        configuration.setSpecificLoginUrl(authenticationChannel.getSpecificLoginUrl());
        return configuration;
    }

    @Override
    protected MailNonceFormModuleWebSecurityConfigurer<ModuleWebSecurityConfiguration> createModule(ModuleWebSecurityConfiguration configuration) {
        return  getObjectObjectPostProcessor().postProcess(new MailNonceFormModuleWebSecurityConfigurer<>(configuration));
    }

    //TODO
    @Override
    protected AuthenticationProvider createProvider(CredentialPolicyType usedPolicy) {
        return new MailNonceProvider();
    }

    @Override
    protected Class<? extends CredentialPolicyType> supportedClass() {
        return NonceCredentialsPolicyType.class;
    }

    @Override
    protected ModuleAuthenticationImpl createEmptyModuleAuthentication(AbstractAuthenticationModuleType moduleType,
                                                                   ModuleWebSecurityConfiguration configuration) {
        MailNonceModuleAuthenticationImpl moduleAuthentication = new MailNonceModuleAuthenticationImpl();
        moduleAuthentication.setPrefix(configuration.getPrefixOfModule());
        moduleAuthentication.setCredentialName(((AbstractCredentialAuthenticationModuleType)moduleType).getCredentialName());
        moduleAuthentication.setCredentialType(supportedClass());
        moduleAuthentication.setNameOfModule(configuration.getNameOfModule());
        return moduleAuthentication;
    }

    @Override
    protected void isSupportedChannel(AuthenticationChannel authenticationChannel) {
        //supported for all modules
    }

}
