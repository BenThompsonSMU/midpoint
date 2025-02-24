/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.authentication.impl.factory.module;

import com.evolveum.midpoint.authentication.api.AuthenticationChannel;
import com.evolveum.midpoint.authentication.impl.module.authentication.FocusIdentificationModuleAuthentication;
import com.evolveum.midpoint.authentication.impl.module.authentication.HintAuthenticationModuleAuthentication;
import com.evolveum.midpoint.authentication.impl.module.authentication.ModuleAuthenticationImpl;
import com.evolveum.midpoint.authentication.impl.module.configuration.LoginFormModuleWebSecurityConfiguration;
import com.evolveum.midpoint.authentication.impl.module.configurer.FocusIdentificationModuleWebSecurityConfigurer;
import com.evolveum.midpoint.authentication.impl.module.configurer.HintModuleWebSecurityConfigurer;
import com.evolveum.midpoint.authentication.impl.provider.FocusIdentificationProvider;
import com.evolveum.midpoint.authentication.impl.provider.HintAuthenticationProvider;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.stereotype.Component;

/**
 * @author skublik
 */
@Component
public class HintAuthenticationModuleFactoryImpl extends AbstractCredentialModuleFactory
        <LoginFormModuleWebSecurityConfiguration, HintModuleWebSecurityConfigurer<LoginFormModuleWebSecurityConfiguration>> {

    @Override
    public boolean match(AbstractAuthenticationModuleType moduleType, AuthenticationChannel authenticationChannel) {
        return moduleType instanceof HintAuthenticationModuleType;
    }

    @Override
    protected LoginFormModuleWebSecurityConfiguration createConfiguration(AbstractAuthenticationModuleType moduleType, String prefixOfSequence, AuthenticationChannel authenticationChannel) {
        LoginFormModuleWebSecurityConfiguration configuration = LoginFormModuleWebSecurityConfiguration.build(moduleType,prefixOfSequence);
        configuration.setSequenceSuffix(prefixOfSequence);
        return configuration;
    }

    @Override
    protected HintModuleWebSecurityConfigurer<LoginFormModuleWebSecurityConfiguration> createModule(
            LoginFormModuleWebSecurityConfiguration configuration) {
        return  getObjectObjectPostProcessor().postProcess(new HintModuleWebSecurityConfigurer<>(configuration));
    }

    @Override
    protected AuthenticationProvider createProvider(CredentialPolicyType usedPolicy) {
        return new HintAuthenticationProvider();
    }

    @Override
    protected Class<? extends CredentialPolicyType> supportedClass() {
        return null;
    }

    @Override
    protected ModuleAuthenticationImpl createEmptyModuleAuthentication(AbstractAuthenticationModuleType moduleType,
            LoginFormModuleWebSecurityConfiguration configuration, AuthenticationSequenceModuleType sequenceModule) {
        HintAuthenticationModuleAuthentication moduleAuthentication = new HintAuthenticationModuleAuthentication(sequenceModule);
        moduleAuthentication.setPrefix(configuration.getPrefixOfModule());
        moduleAuthentication.setCredentialName(((AbstractCredentialAuthenticationModuleType)moduleType).getCredentialName());
        moduleAuthentication.setCredentialType(supportedClass());
        moduleAuthentication.setNameOfModule(configuration.getModuleIdentifier());
        return moduleAuthentication;
    }


}
