/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.schema.util;

import java.util.Objects;
import java.util.*;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.schema.SchemaRegistry;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

public class SecurityPolicyUtil {

    public static final String DEFAULT_CHANNEL = SchemaConstants.CHANNEL_USER_URI;
    public static final String DEFAULT_MODULE_IDENTIFIER = "loginForm";
    public static final String DEFAULT_SEQUENCE_IDENTIFIER = "admin-gui-default";
    public static final String DEFAULT_SEQUENCE_DISPLAY_IDENTIFIER = "Default gui sequence";

    private static final List<String> DEFAULT_IGNORED_LOCAL_PATH;

    /** Constant representing no custom ignored local paths (can be null or empty collection). */
    public static final List<String> NO_CUSTOM_IGNORED_LOCAL_PATH = null;

    static {
        List<String> list = new ArrayList<>();
        list.add("/actuator");
        list.add("/actuator/health");
        DEFAULT_IGNORED_LOCAL_PATH = Collections.unmodifiableList(list);
    }

    public static NonceCredentialsPolicyType getCredentialPolicy(
            String policyName, SecurityPolicyType securityPolicy) throws SchemaException {

        CredentialsPolicyType credentialsPolicy = securityPolicy.getCredentials();
        if (credentialsPolicy == null) {
            return null;
        }

        List<NonceCredentialsPolicyType> noncePolicies = credentialsPolicy.getNonce();

        List<NonceCredentialsPolicyType> availableNoncePolicies = new ArrayList<>();
        for (NonceCredentialsPolicyType noncePolicy : noncePolicies) {
            if (Objects.equals(noncePolicy.getName(), policyName)) {
                availableNoncePolicies.add(noncePolicy);
            }
        }

        if (availableNoncePolicies.size() > 1) {
            throw new SchemaException(
                    "Found more than one nonce credentials policy. Please review your configuration");
        }

        if (availableNoncePolicies.size() == 0) {
            return null;
        }

        return availableNoncePolicies.iterator().next();
    }

    public static List<AuthenticationSequenceModuleType> getSortedModules(AuthenticationSequenceType sequence) {
        Validate.notNull(sequence);
        ArrayList<AuthenticationSequenceModuleType> modules = new ArrayList<>(sequence.getModule());
        Validate.notNull(modules);
        Comparator<AuthenticationSequenceModuleType> comparator =
                (f1, f2) -> {
                    Integer f1Order = f1.getOrder();
                    Integer f2Order = f2.getOrder();

                    if (f1Order == null) {
                        if (f2Order != null) {
                            return 1;
                        }
                        return 0;
                    }

                    if (f2Order == null) {
                        // f1Order != null already
                        return -1;
                    }
                    return Integer.compare(f1Order, f2Order);
                };
        modules.sort(comparator);
        return Collections.unmodifiableList(modules);
    }

    public static AuthenticationsPolicyType createDefaultAuthenticationPolicy(
            List<String> customIgnoredLocalPaths, SchemaRegistry schemaRegistry)
            throws SchemaException {

        PrismObjectDefinition<SecurityPolicyType> secPolicyDef =
                schemaRegistry.findObjectDefinitionByCompileTimeClass(SecurityPolicyType.class);
        @NotNull PrismObject<SecurityPolicyType> secPolicy = secPolicyDef.instantiate();
        AuthenticationsPolicyType authenticationPolicy =
                new AuthenticationsPolicyType()
                        .beginModules()
                            .beginLoginForm()
                                .identifier(DEFAULT_MODULE_IDENTIFIER)
                            .<AuthenticationModulesType>end()
                        .<AuthenticationsPolicyType>end()
                        .sequence(createDefaultSequence());
        if (customIgnoredLocalPaths == null || customIgnoredLocalPaths.isEmpty()) {
            DEFAULT_IGNORED_LOCAL_PATH.forEach(authenticationPolicy::ignoredLocalPath);
        } else {
            customIgnoredLocalPaths.forEach(authenticationPolicy::ignoredLocalPath);
        }
        secPolicy.asObjectable().authentication(authenticationPolicy);
        return secPolicy.asObjectable().getAuthentication();
    }

    public static AuthenticationSequenceType createDefaultSequence() {
        return new AuthenticationSequenceType()
                .identifier(DEFAULT_SEQUENCE_IDENTIFIER)
                .displayName(DEFAULT_SEQUENCE_DISPLAY_IDENTIFIER)
                .beginChannel()
                    ._default(true)
                    .channelId(DEFAULT_CHANNEL)
                    .urlSuffix("gui-default")
                .<AuthenticationSequenceType>end()
                .beginModule()
                    .identifier(DEFAULT_MODULE_IDENTIFIER)
                    .order(1)
                    .necessity(AuthenticationSequenceModuleNecessityType.SUFFICIENT)
                .end();
    }

    public static SelfRegistrationPolicyType getSelfRegistrationPolicy(SecurityPolicyType securityPolicyType) {
        RegistrationsPolicyType flowPolicy = securityPolicyType.getFlow();
        SelfRegistrationPolicyType selfRegistrationPolicy = null;
        if (flowPolicy != null) {
            selfRegistrationPolicy = flowPolicy.getSelfRegistration();
        }

        return selfRegistrationPolicy;
    }

    public static AuthenticationSequenceType findSequenceByIdentifier(@NotNull SecurityPolicyType securityPolicy, String identifier) {
        if (StringUtils.isEmpty(identifier)) {
            return null;
        }
        if (securityPolicy.getAuthentication() == null || CollectionUtils.isEmpty(securityPolicy.getAuthentication().getSequence())) {
            return null;
        }
        return securityPolicy
                .getAuthentication()
                .getSequence()
                .stream()
                .filter(s -> identifier.equals(s.getIdentifier()))
                .findFirst()
                .orElse(null);
    }
}
