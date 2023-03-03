/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.authentication.impl.evaluator;

import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.model.api.context.SecurityQuestionsAuthenticationContext;
import com.evolveum.midpoint.security.api.ConnectionEnvironment;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.security.api.SecurityUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

@Component("securityQuestionsAuthenticationEvaluator")
public class SecurityQuestionAuthenticationEvaluatorImpl
        extends AuthenticationEvaluatorImpl<SecurityQuestionsCredentialsType, SecurityQuestionsAuthenticationContext> {

    @Override
    protected void checkEnteredCredentials(ConnectionEnvironment connEnv,
            SecurityQuestionsAuthenticationContext authCtx) {
        if (MapUtils.isEmpty(authCtx.getQuestionAnswerMap())) {
//            recordPasswordAuthenticationFailure(authCtx, connEnv, null, "no stored password value", true);

            recordPasswordAuthenticationFailure(authCtx.getUsername(), null, connEnv, null, "empty answers for security questions provided");
//            recordAuthenticationBehavior(authCtx.getUsername(), null, connEnv, "empty answers for security questions provided", authCtx.getPrincipalType(), false);
            throw new BadCredentialsException("web.security.provider.securityQuestion.bad");
        }

        Map<String, String> enteredQuestionAnswer = authCtx.getQuestionAnswerMap();
        boolean allBlank = false;
        for (String enteredAnswers : enteredQuestionAnswer.values()) {
            if (StringUtils.isBlank(enteredAnswers)) {
                allBlank = true;
            }
        }

        if (allBlank) {
            recordPasswordAuthenticationFailure(authCtx.getUsername(), null, connEnv, null, "all empty");
//            recordAuthenticationBehavior(authCtx.getUsername(), null, connEnv, "empty password provided", authCtx.getPrincipalType(), false);
            throw new BadCredentialsException("web.security.provider.password.encoding");
        }
    }

    @Override
    protected boolean supportsAuthzCheck() {
        return true;
    }

    @Override
    protected SecurityQuestionsCredentialsType getCredential(CredentialsType credentials) {
        return credentials.getSecurityQuestions();
    }

    @Override
    protected void validateCredentialNotNull(ConnectionEnvironment connEnv,
            @NotNull MidPointPrincipal principal, SecurityQuestionsCredentialsType credential) {
        List<SecurityQuestionAnswerType> securityQuestionsAnswers = credential.getQuestionAnswer();

        if (securityQuestionsAnswers == null || securityQuestionsAnswers.isEmpty()) {
            recordPasswordAuthenticationFailure(principal.getUsername(), principal, connEnv, null, "no stored security questions");
//            recordAuthenticationBehavior(principal.getUsername(),principal, connEnv, "no stored security questions", principal.getFocus().getClass(),false);
            throw new AuthenticationCredentialsNotFoundException("web.security.provider.securityQuestion.bad");
        }

    }

    @Override
    protected boolean passwordMatches(
            ConnectionEnvironment connEnv, @NotNull MidPointPrincipal principal,
            SecurityQuestionsCredentialsType passwordType, SecurityQuestionsAuthenticationContext authCtx) {

        SecurityQuestionsCredentialsPolicyType policy = authCtx.getPolicy();
        Integer iNumberOfQuestions = policy.getQuestionNumber();
        int numberOfQuestions = iNumberOfQuestions != null ? iNumberOfQuestions : 0;

        Map<String, String> enteredQuestionsAnswers = authCtx.getQuestionAnswerMap();
        if (numberOfQuestions > enteredQuestionsAnswers.size()) {
            return false;
        }

        List<SecurityQuestionAnswerType> questionsAnswers = passwordType.getQuestionAnswer();
        int matched = 0;
        for (SecurityQuestionAnswerType questionAnswer : questionsAnswers) {
            String enteredAnswer = enteredQuestionsAnswers.get(questionAnswer.getQuestionIdentifier());
            if (StringUtils.isNotBlank(enteredAnswer)) {
                if (decryptAndMatch(connEnv, principal, questionAnswer.getQuestionAnswer(), enteredAnswer)) {
                    matched++;
                }
            }
        }

        return matched > 0 && matched >= numberOfQuestions;

    }

    @Override
    protected CredentialPolicyType getEffectiveCredentialPolicy(
            SecurityPolicyType securityPolicy, SecurityQuestionsAuthenticationContext authnCtx) {
        SecurityQuestionsCredentialsPolicyType policy = authnCtx.getPolicy();
        if (policy == null) {
            policy = SecurityUtil.getEffectiveSecurityQuestionsCredentialsPolicy(securityPolicy);
        }
        authnCtx.setPolicy(policy);
        return policy;
    }

    @Override
    protected boolean supportsActivation() {
        return true;
    }

}
