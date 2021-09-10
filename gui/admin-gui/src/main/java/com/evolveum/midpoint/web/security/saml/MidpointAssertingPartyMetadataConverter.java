/*
 * Copyright (c) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.web.security.saml;

import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import com.evolveum.midpoint.web.security.module.configuration.SamlMidpointAdditionalConfiguration;

import net.shibboleth.utilities.java.support.xml.ParserPool;
import org.opensaml.core.config.ConfigurationService;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.config.XMLObjectProviderRegistry;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.ext.saml2alg.SigningMethod;
import org.opensaml.saml.saml2.metadata.*;
import org.opensaml.security.credential.UsageType;
import org.opensaml.xmlsec.keyinfo.KeyInfoSupport;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.springframework.security.saml2.Saml2Exception;
import org.springframework.security.saml2.core.OpenSamlInitializationService;
import org.springframework.security.saml2.core.Saml2X509Credential;

public class MidpointAssertingPartyMetadataConverter {

    static {
        OpenSamlInitializationService.initialize();
    }

    private final XMLObjectProviderRegistry registry;

    private final ParserPool parserPool;

    public MidpointAssertingPartyMetadataConverter() {
        this.registry = ConfigurationService.get(XMLObjectProviderRegistry.class);
        this.parserPool = this.registry.getParserPool();
    }

    public RelyingPartyRegistration.Builder convert(InputStream inputStream, SamlMidpointAdditionalConfiguration.Builder additionalConfigurationBuilder) {
        EntityDescriptor descriptor = entityDescriptor(inputStream);
        IDPSSODescriptor idpssoDescriptor = descriptor.getIDPSSODescriptor(SAMLConstants.SAML20P_NS);
        if (idpssoDescriptor == null) {
            throw new Saml2Exception("Metadata response is missing the necessary IDPSSODescriptor element");
        }
        List<Saml2X509Credential> verification = new ArrayList<>();
        List<Saml2X509Credential> encryption = new ArrayList<>();
        for (KeyDescriptor keyDescriptor : idpssoDescriptor.getKeyDescriptors()) {
            if (keyDescriptor.getUse().equals(UsageType.SIGNING)) {
                List<X509Certificate> certificates = certificates(keyDescriptor);
                for (X509Certificate certificate : certificates) {
                    verification.add(Saml2X509Credential.verification(certificate));
                }
            }
            if (keyDescriptor.getUse().equals(UsageType.ENCRYPTION)) {
                List<X509Certificate> certificates = certificates(keyDescriptor);
                for (X509Certificate certificate : certificates) {
                    encryption.add(Saml2X509Credential.encryption(certificate));
                }
            }
            if (keyDescriptor.getUse().equals(UsageType.UNSPECIFIED)) {
                List<X509Certificate> certificates = certificates(keyDescriptor);
                for (X509Certificate certificate : certificates) {
                    verification.add(Saml2X509Credential.verification(certificate));
                    encryption.add(Saml2X509Credential.encryption(certificate));
                }
            }
        }
        if (verification.isEmpty()) {
            throw new Saml2Exception(
                    "Metadata response is missing verification certificates, necessary for verifying SAML assertions");
        }
        RelyingPartyRegistration.Builder builder = RelyingPartyRegistration.withRegistrationId(descriptor.getEntityID())
                .assertingPartyDetails((party) -> party.entityId(descriptor.getEntityID())
                        .wantAuthnRequestsSigned(Boolean.TRUE.equals(idpssoDescriptor.getWantAuthnRequestsSigned()))
                        .verificationX509Credentials((c) -> c.addAll(verification))
                        .encryptionX509Credentials((c) -> c.addAll(encryption)));
        List<SigningMethod> signingMethods = signingMethods(idpssoDescriptor);
        for (SigningMethod method : signingMethods) {
            builder.assertingPartyDetails(
                    (party) -> party.signingAlgorithms((algorithms) -> algorithms.add(method.getAlgorithm())));
        }
        Saml2MessageBinding authBinding = null;
        for (SingleSignOnService singleSignOnService : idpssoDescriptor.getSingleSignOnServices()) {
            if (singleSignOnService.getBinding().equals(Saml2MessageBinding.POST.getUrn())) {
                authBinding = Saml2MessageBinding.POST;
            }
            else if (singleSignOnService.getBinding().equals(Saml2MessageBinding.REDIRECT.getUrn())) {
                authBinding = Saml2MessageBinding.REDIRECT;
            }
            else {
                continue;
            }
            Saml2MessageBinding finalAuthBinding = authBinding;
            builder.assertingPartyDetails(
                    (party) -> party.singleSignOnServiceLocation(singleSignOnService.getLocation())
                            .singleSignOnServiceBinding(finalAuthBinding));
            break;
        }
        if (authBinding == null) {
            throw new Saml2Exception(
                    "Metadata response is missing a SingleSignOnService, necessary for sending AuthnRequests");
        }
//        for (SingleLogoutService singleLogoutService : idpssoDescriptor.getSingleLogoutServices()) {
//            Saml2MessageBinding binding;
//            if (singleLogoutService.getBinding().equals(Saml2MessageBinding.POST.getUrn())) {
//                binding = Saml2MessageBinding.POST;
//            }
//            else if (singleLogoutService.getBinding().equals(Saml2MessageBinding.REDIRECT.getUrn())) {
//                binding = Saml2MessageBinding.REDIRECT;
//            }
//            else {
//                continue;
//            }
//
//            additionalConfigurationBuilder.logoutBinding(binding).logoutDestination(singleLogoutService.getLocation());
//            return builder;
//        }
//        throw new Saml2Exception(
//                "Metadata response is missing a SingleLogoutService, necessary for sending LogoutRequests");
        return builder;
    }

    private List<X509Certificate> certificates(KeyDescriptor keyDescriptor) {
        try {
            return KeyInfoSupport.getCertificates(keyDescriptor.getKeyInfo());
        }
        catch (CertificateException ex) {
            throw new Saml2Exception(ex);
        }
    }

    private List<SigningMethod> signingMethods(IDPSSODescriptor idpssoDescriptor) {
        Extensions extensions = idpssoDescriptor.getExtensions();
        List<SigningMethod> result = signingMethods(extensions);
        if (!result.isEmpty()) {
            return result;
        }
        EntityDescriptor descriptor = (EntityDescriptor) idpssoDescriptor.getParent();
        extensions = descriptor.getExtensions();
        return signingMethods(extensions);
    }

    private EntityDescriptor entityDescriptor(InputStream inputStream) {
        Document document = document(inputStream);
        Element element = document.getDocumentElement();
        Unmarshaller unmarshaller = this.registry.getUnmarshallerFactory().getUnmarshaller(element);
        if (unmarshaller == null) {
            throw new Saml2Exception("Unsupported element of type " + element.getTagName());
        }
        try {
            XMLObject object = unmarshaller.unmarshall(element);
            if (object instanceof EntitiesDescriptor) {
                return ((EntitiesDescriptor) object).getEntityDescriptors().get(0);
            }
            if (object instanceof EntityDescriptor) {
                return (EntityDescriptor) object;
            }
        }
        catch (Exception ex) {
            throw new Saml2Exception(ex);
        }
        throw new Saml2Exception("Unsupported element of type " + element.getTagName());
    }

    private Document document(InputStream inputStream) {
        try {
            return this.parserPool.parse(inputStream);
        }
        catch (Exception ex) {
            throw new Saml2Exception(ex);
        }
    }

    private <T> List<T> signingMethods(Extensions extensions) {
        if (extensions != null) {
            return (List<T>) extensions.getUnknownXMLObjects(SigningMethod.DEFAULT_ELEMENT_NAME);
        }
        return new ArrayList<>();
    }

}
