/*
 * Copyright (c) 2011 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2011 [name of copyright owner]
 */
package com.evolveum.midpoint.model.controller.handler;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBElement;

import org.apache.commons.lang.StringUtils;

import com.evolveum.midpoint.common.Utils;
import com.evolveum.midpoint.common.patch.PatchXml;
import com.evolveum.midpoint.common.result.OperationResult;
import com.evolveum.midpoint.model.controller.ModelController;
import com.evolveum.midpoint.model.controller.ModelControllerImpl;
import com.evolveum.midpoint.model.controller.ModelUtils;
import com.evolveum.midpoint.model.controller.SchemaHandler;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.exception.ObjectNotFoundException;
import com.evolveum.midpoint.schema.exception.SystemException;
import com.evolveum.midpoint.schema.util.JAXBUtil;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_1.AccountConstructionType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.AccountShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectModificationType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyReferenceListType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceObjectShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.SchemaHandlingType.AccountType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.SystemConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.SystemObjectsType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.UserTemplateType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.UserType;

/**
 * 
 * @author lazyman
 * 
 */
public class BasicHandler {

	private static final Trace LOGGER = TraceManager.getTrace(BasicHandler.class);
	private ModelController modelController;
	private ProvisioningService provisioning;
	private RepositoryService repository;
	private SchemaHandler schemaHandler;

	public BasicHandler(ModelController modelController, ProvisioningService provisioning,
			RepositoryService repository, SchemaHandler schemaHandler) {
		this.modelController = modelController;
		this.provisioning = provisioning;
		this.repository = repository;
		this.schemaHandler = schemaHandler;
	}

	protected ModelController getModelController() {
		return modelController;
	}

	protected ProvisioningService getProvisioning() {
		return provisioning;
	}

	protected RepositoryService getRepository() {
		return repository;
	}

	protected SchemaHandler getSchemaHandler() {
		return schemaHandler;
	}

	protected SystemConfigurationType getSystemConfiguration(OperationResult result)
			throws ObjectNotFoundException {
		OperationResult configResult = result.createSubresult(ModelControllerImpl.GET_SYSTEM_CONFIGURATION);
		SystemConfigurationType systemConfiguration = null;
		try {
			systemConfiguration = getObject(SystemConfigurationType.class,
					SystemObjectsType.SYSTEM_CONFIGURATION.value(),
					ModelUtils.createPropertyReferenceListType("defaultUserTemplate"), result);
			configResult.recordSuccess();
		} catch (ObjectNotFoundException ex) {
			configResult.recordFatalError("Couldn't get system configuration.", ex);
			throw ex;
		}

		return systemConfiguration;
	}

	protected UserType processUserTemplateForUser(UserType user, UserTemplateType userTemplate,
			OperationResult result) {
		OperationResult subResult = result.createSubresult(ModelControllerImpl.PROCESS_USER_TEMPLATE);
		subResult.addParams(new String[] { "user", "userTemplate" }, user, userTemplate);
		if (userTemplate == null) {
			subResult.recordSuccess();
			return user;
		}

		try {
			user = schemaHandler.processPropertyConstructions(user, userTemplate, subResult);
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER,
					"Couldn't process property construction from template {} on user {}", ex,
					userTemplate.getName(), user.getName());
		}
		processUserTemplateAccount(user, userTemplate, subResult);
		subResult.computeStatus("Couldn't finish process user template.");

		return user;
	}

	@SuppressWarnings("unchecked")
	protected void processUserTemplateAccount(UserType user, UserTemplateType userTemplate,
			OperationResult result) {
		for (AccountConstructionType construction : userTemplate.getAccountConstruction()) {
			OperationResult subResult = result.createSubresult(ModelControllerImpl.CREATE_ACCOUNT);
			subResult.addParams(new String[] { "user", "userTemplate" }, user, userTemplate);
			try {
				ObjectReferenceType resourceRef = construction.getResourceRef();
				ResourceType resource = getObject(ResourceType.class, resourceRef.getOid(),
						new PropertyReferenceListType(), subResult);

				AccountType accountType = ModelUtils.getAccountTypeFromHandling(construction.getType(),
						resource);

				AccountShadowType account = new AccountShadowType();
				account.setAttributes(new ResourceObjectShadowType.Attributes());
				account.setObjectClass(accountType.getObjectClass());
				account.setName(resource.getName() + "-" + user.getName());
				account.setResourceRef(resourceRef);
				account.setActivation(user.getActivation());

				ObjectModificationType changes = processOutboundSchemaHandling(user, account, subResult);
				if (changes != null) {
					PatchXml patchXml = new PatchXml();
					String accountXml = patchXml.applyDifferences(changes, account);
					account = ((JAXBElement<AccountShadowType>) JAXBUtil.unmarshal(accountXml)).getValue();
				}

				String accountOid = getModelController().addObject(account, subResult);
				user.getAccountRef().add(ModelUtils.createReference(accountOid, ObjectTypes.ACCOUNT));
			} catch (Exception ex) {
				LoggingUtils.logException(LOGGER, "Couldn't process account construction '{}' for user {}",
						ex, construction.getType(), user.getName());
				subResult.recordFatalError("Something went terribly wrong.", ex);
				result.recordWarning("Couldn't process account construction '" + construction.getType()
						+ "'.", ex);
			} finally {
				subResult.computeStatus("Couldn't process account construction '" + construction.getType()
						+ "'.");
			}
		}
	}

	@SuppressWarnings("unchecked")
	public <T extends ObjectType> T getObject(Class<T> clazz, String oid, PropertyReferenceListType resolve,
			OperationResult result) throws ObjectNotFoundException {
		T object = null;
		try {
			ObjectType objectType = null;
			if (ObjectTypes.isClassManagedByProvisioning(clazz)) {
				objectType = getProvisioning().getObject(clazz, oid, resolve, result);
			} else {
				objectType = getRepository().getObject(clazz, oid, resolve, result);
			}
			if (!clazz.isInstance(objectType)) {
				throw new ObjectNotFoundException("Bad object type returned for referenced oid '" + oid
						+ "'. Expected '" + clazz + "', but was '"
						+ (objectType == null ? "null" : objectType.getClass()) + "'.");
			} else {
				object = (T) objectType;
			}

			resolveObjectAttributes(object, resolve, result);
		} catch (SystemException ex) {
			throw ex;
		} catch (ObjectNotFoundException ex) {
			throw ex;
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "Couldn't get object with oid {}, expected type was {}.", ex,
					oid, clazz);
			throw new SystemException("Couldn't get object with oid '" + oid + "'.", ex);
		} finally {
			result.computeStatus("Couldn't get object with oid '" + oid + "'.");
			LOGGER.debug(result.dump());
		}

		return object;
	}

	// TODO: change to protected
	public ObjectModificationType processOutboundSchemaHandling(UserType user,
			ResourceObjectShadowType object, OperationResult result) {
		ObjectModificationType change = null;
		if (user != null) {
			try {
				change = getSchemaHandler().processOutboundHandling(user, (ResourceObjectShadowType) object,
						result);
			} catch (Exception ex) {
				LoggingUtils.logException(LOGGER, "Couldn't process outbound schema handling for {}", ex,
						object.getName());
			}
		} else {
			LOGGER.debug("Skipping outbound schema handling processing for {} (no user defined).",
					new Object[] { object.getName() });
		}

		return change;
	}

	// TODO: change to protected
	public void resolveObjectAttributes(ObjectType object, PropertyReferenceListType resolve,
			OperationResult result) {
		if (object == null) {
			return;
		}

		if (object instanceof UserType) {
			resolveUserAttributes((UserType) object, resolve, result);
		} else if (object instanceof AccountShadowType) {
			resolveAccountAttributes((AccountShadowType) object, resolve, result);
		}
	}

	private void resolveUserAttributes(UserType user, PropertyReferenceListType resolve,
			OperationResult result) {
		if (!Utils.haveToResolve("Account", resolve)) {
			return;
		}

		List<ObjectReferenceType> refToBeDeleted = new ArrayList<ObjectReferenceType>();
		for (ObjectReferenceType accountRef : user.getAccountRef()) {
			OperationResult subResult = result.createSubresult(ModelControllerImpl.RESOLVE_USER_ATTRIBUTES);
			subResult.addParams(new String[] { "user", "accountRef" }, user, accountRef);
			try {
				AccountShadowType account = getObject(AccountShadowType.class, accountRef.getOid(), resolve,
						subResult);
				user.getAccount().add(account);
				refToBeDeleted.add(accountRef);
				subResult.recordSuccess();
			} catch (Exception ex) {
				LoggingUtils.logException(LOGGER, "Couldn't resolve account with oid {}", ex,
						accountRef.getOid());
				subResult.recordFatalError(
						"Couldn't resolve account with oid '" + accountRef.getOid() + "'.", ex);
			} finally {
				subResult.computeStatus("Couldn't resolve account with oid '" + accountRef.getOid() + "'.");
			}
		}
		user.getAccountRef().removeAll(refToBeDeleted);
	}

	private void resolveAccountAttributes(AccountShadowType account, PropertyReferenceListType resolve,
			OperationResult result) {
		if (!Utils.haveToResolve("Resource", resolve)) {
			return;
		}

		ObjectReferenceType reference = account.getResourceRef();
		if (reference == null || StringUtils.isEmpty(reference.getOid())) {
			LOGGER.debug("Skipping resolving resource for account {}, resource reference is null or "
					+ "doesn't contain oid.", new Object[] { account.getName() });
			return;
		}
		OperationResult subResult = result.createSubresult(ModelControllerImpl.RESOLVE_ACCOUNT_ATTRIBUTES);
		subResult.addParams(new String[] { "account", "resolve" }, account, resolve);
		try {
			ResourceType resource = getObject(ResourceType.class, account.getResourceRef().getOid(), resolve,
					result);
			account.setResource(resource);
			account.setResourceRef(null);
			subResult.recordSuccess();
		} catch (Exception ex) {
			LoggingUtils
					.logException(LOGGER, "Couldn't resolve resource with oid {}", ex, reference.getOid());
			subResult
					.recordFatalError("Couldn't resolve resource with oid '" + reference.getOid() + "'.", ex);
		} finally {
			subResult.computeStatus("Couldn't resolve resource with oid '" + reference.getOid() + "'.");
		}
	}
}
