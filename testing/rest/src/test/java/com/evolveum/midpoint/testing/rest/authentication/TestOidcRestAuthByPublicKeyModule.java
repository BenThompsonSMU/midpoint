/*
 * Copyright (c) 2016-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.testing.rest.authentication;

import com.evolveum.midpoint.common.rest.MidpointAbstractProvider;
import com.evolveum.midpoint.common.rest.MidpointJsonProvider;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SystemObjectsType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;

import org.apache.cxf.jaxrs.client.WebClient;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.representations.AccessTokenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.Test;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileInputStream;

import static org.testng.AssertJUnit.assertNotNull;

public class TestOidcRestAuthByPublicKeyModule extends TestAbstractOidcRestModule {

    private static final File KEYCLOAK_PUBLIC_KEY_CONFIGURATION = new File(BASE_AUTHENTICATION_DIR,"keycloak-public-key.json");

    @Autowired
    protected MidpointJsonProvider jsonProvider;

    private AuthzClient authzClient;

    @Override
    protected String getAcceptHeader() {
        return MediaType.APPLICATION_JSON;
    }

    @Override
    protected String getContentType() {
        return MediaType.APPLICATION_JSON;
    }

    @Override
    protected MidpointAbstractProvider getProvider() {
        return jsonProvider;
    }

    @Override
    public void initSystem(Task initTask, OperationResult result) throws Exception {
        super.initSystem(initTask, result);
        authzClient = AuthzClient.create(new FileInputStream(KEYCLOAK_PUBLIC_KEY_CONFIGURATION));
    }

    @Override
    public AuthzClient getAuthzClient() {
        return authzClient;
    }

    @Override
    protected void assertForAuthByPublicKey(Response response) {
        assertSuccess(response);
    }

    @Override
    protected void assertForAuthByHMac(Response response) {
        assertUnsuccess(response);
    }
}
