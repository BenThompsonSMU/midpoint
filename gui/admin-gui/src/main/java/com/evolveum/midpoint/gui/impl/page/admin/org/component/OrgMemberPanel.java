/*
 * Copyright (c) 2015-2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.admin.org.component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.xml.namespace.QName;

import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.impl.page.admin.abstractrole.component.AbstractRoleMemberPanel;
import com.evolveum.midpoint.gui.impl.page.admin.abstractrole.component.MemberOperationsHelper;
import com.evolveum.midpoint.gui.impl.page.admin.assignmentholder.FocusDetailsModels;
import com.evolveum.midpoint.prism.PrismConstants;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.application.PanelDisplay;
import com.evolveum.midpoint.web.application.PanelInstance;
import com.evolveum.midpoint.web.application.PanelType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

@PanelType(name = "orgMembers")
@PanelInstance(identifier = "orgMembers", applicableForType = OrgType.class,
        applicableForOperation = OperationTypeType.MODIFY,
        display = @PanelDisplay(label = "pageRole.members", icon = GuiStyleConstants.CLASS_GROUP_ICON, order = 60))
@PanelInstance(identifier = "orgGovernance", applicableForType = OrgType.class,
        applicableForOperation = OperationTypeType.MODIFY,
        display = @PanelDisplay(label = "pageRole.governance", icon = GuiStyleConstants.CLASS_GROUP_ICON, order = 70))
public class OrgMemberPanel extends AbstractRoleMemberPanel<OrgType> {
    private static final long serialVersionUID = 1L;

    private static final Trace LOGGER = TraceManager.getTrace(OrgMemberPanel.class);

    public OrgMemberPanel(String id, FocusDetailsModels<OrgType> model, ContainerPanelConfigurationType config) {
        super(id, model, config);
    }

    @Override
    protected ObjectQuery getActionQuery(QueryScope scope, @NotNull Collection<QName> relations) {
        if (!isSubtreeScope() || (isSubtreeScope() && !QueryScope.ALL.equals(scope))) {
            return super.getActionQuery(scope, relations);
        } else {
            String oid = getModelObject().getOid();

            ObjectReferenceType ref = ObjectTypeUtil.createObjectRef(getModelObject(), getRelationValue());
            ObjectQuery query = getPageBase().getPrismContext().queryFor(getSearchTypeClass())
                    .type(getSearchTypeClass())
                    .isChildOf(ref.asReferenceValue()).build();

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Searching members of org {} with query:\n{}", oid, query.debugDump());
            }
            return query;
        }
    }

    @Override
    protected ObjectQuery createAllMemberQuery(Collection<QName> relations) {
        return getPrismContext().queryFor(AssignmentHolderType.class)
                .item(AssignmentHolderType.F_ROLE_MEMBERSHIP_REF).ref(MemberOperationsHelper.createReferenceValuesList(getModelObject(), relations))
                .build();
    }

    @Override
    protected List<QName> getNewMemberObjectTypes() {
        List<QName> objectTypes = WebComponentUtil.createFocusTypeList();
        objectTypes.add(ResourceType.COMPLEX_TYPE);
        return objectTypes;
    }

    private Class<? extends AssignmentHolderType> getSearchTypeClass() {
        return getMemberPanelStorage().getSearch().getTypeClass();
    }

    @Override
    protected boolean reloadPageOnRefresh() {
        return "orgTreeMembers".equals(getStorageKeyTabSuffix());
    }

    @Override
    protected String getStorageKeyTabSuffix() {
        if (getPanelConfiguration() == null) {
            return "orgTreeMembers";
        }
        if ("orgMembers".equals(getPanelConfiguration().getIdentifier())) {
            return "orgMembers";
        }
        if ("orgGovernance".equals(getPanelConfiguration().getIdentifier())) {
            return "orgGovernance";
        }
        return "orgTreeMembers";
    }

    @Override
    protected @NotNull List<QName> getRelationsForRecomputeTask() {
        if (CollectionUtils.isEmpty(getSupportedRelations())) {
            return Collections.singletonList(PrismConstants.Q_ANY);
        }
        return super.getRelationsForRecomputeTask();
    }

}
