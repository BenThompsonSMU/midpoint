/*
 * Copyright (c) 2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.component.assignment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismObjectWrapper;

import com.evolveum.midpoint.web.component.search.AbstractSearchItemDefinition;
import com.evolveum.midpoint.web.model.PrismContainerWrapperModel;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerWrapper;
import com.evolveum.midpoint.gui.impl.component.data.column.AbstractItemWrapperColumn.ColumnType;
import com.evolveum.midpoint.gui.impl.component.data.column.PrismContainerWrapperColumn;
import com.evolveum.midpoint.gui.impl.component.data.column.PrismPropertyWrapperColumn;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.search.SearchFactory;
import com.evolveum.midpoint.web.component.search.SearchItemDefinition;
import com.evolveum.midpoint.web.session.UserProfileStorage;
import com.evolveum.midpoint.web.session.UserProfileStorage.TableId;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

//@PanelType(name = "policyRuleAssignments")
//@PanelInstance(identifier = "policyRuleAssignments",
//        applicableFor = AbstractRoleType.class,
//        childOf = AssignmentHolderAssignmentPanel.class)
//@PanelDisplay(label = "Policy rule", icon = GuiStyleConstants.CLASS_POLICY_RULES_ICON, order = 60)
public class PolicyRulesPanel<AR extends AbstractRoleType> extends AssignmentPanel<AR> {

    private static final long serialVersionUID = 1L;

    private static final Trace LOGGER = TraceManager.getTrace(PolicyRulesPanel.class);

    public PolicyRulesPanel(String id, IModel<PrismContainerWrapper<AssignmentType>> assignmentContainerWrapperModel, ContainerPanelConfigurationType config){
        super(id, assignmentContainerWrapperModel, config);

    }

    public PolicyRulesPanel(String id, LoadableModel<PrismObjectWrapper<AR>> assignmentContainerWrapperModel, ContainerPanelConfigurationType config) {
        super(id, PrismContainerWrapperModel.fromContainerWrapper(assignmentContainerWrapperModel, AssignmentHolderType.F_ASSIGNMENT), config);
    }

    protected List<IColumn<PrismContainerValueWrapper<AssignmentType>, String>> initColumns() {
        List<IColumn<PrismContainerValueWrapper<AssignmentType>, String>> columns = new ArrayList<>();


        columns.add(new PrismContainerWrapperColumn<>(getModel(), ItemPath.create(AssignmentType.F_POLICY_RULE, PolicyRuleType.F_POLICY_CONSTRAINTS), getPageBase()));

        columns.add(new PrismPropertyWrapperColumn<>(getModel(), ItemPath.create(AssignmentType.F_POLICY_RULE, PolicyRuleType.F_POLICY_SITUATION), ColumnType.STRING, getPageBase()));

        columns.add(new PrismContainerWrapperColumn<>(getModel(), ItemPath.create(AssignmentType.F_POLICY_RULE, PolicyRuleType.F_POLICY_ACTIONS), getPageBase()));

        columns.add(new PrismPropertyWrapperColumn<>(getModel(), AssignmentType.F_ORDER, ColumnType.STRING, getPageBase()));

        return columns;
    }

    @Override
    protected TableId getTableId() {
        return UserProfileStorage.TableId.POLICY_RULES_TAB_TABLE;
    }

    @Override
    protected void newAssignmentClickPerformed(AjaxRequestTarget target) {
        PrismContainerValue<AssignmentType> newAssignment = getModelObject().getItem().createNewValue();
        AssignmentType assignmentType = newAssignment.asContainerable();
        try {
            newAssignment.findOrCreateContainer(AssignmentType.F_POLICY_RULE);
            assignmentType.setPolicyRule(new PolicyRuleType());
        } catch (SchemaException e) {
            LOGGER.error("Cannot create policy rule assignment: {}", e.getMessage(), e);
            getSession().error("Cannot create policyRule assignment.");
            target.add(getPageBase().getFeedbackPanel());
            return;
        }
        PrismContainerValueWrapper<AssignmentType> newAssignmentWrapper = getMultivalueContainerListPanel().createNewItemContainerValueWrapper(newAssignment, getModelObject(), target);
        getMultivalueContainerListPanel().itemDetailsPerformed(target, Collections.singletonList(newAssignmentWrapper));
    }

    @Override
    protected ObjectQuery getCustomizeQuery() {
        return getParentPage().getPrismContext().queryFor(AssignmentType.class)
                .exists(AssignmentType.F_POLICY_RULE).build();
    }

    @Override
    protected List<AbstractSearchItemDefinition> createSearchableItems(PrismContainerDefinition<AssignmentType> containerDef) {
        List<AbstractSearchItemDefinition> defs = new ArrayList<>();

        SearchFactory.addSearchPropertyDef(containerDef, ItemPath.create(AssignmentType.F_ACTIVATION, ActivationType.F_ADMINISTRATIVE_STATUS), defs);
        SearchFactory.addSearchPropertyDef(containerDef, ItemPath.create(AssignmentType.F_ACTIVATION, ActivationType.F_EFFECTIVE_STATUS), defs);
        SearchFactory.addSearchPropertyDef(containerDef, ItemPath.create(AssignmentType.F_POLICY_RULE, PolicyRuleType.F_NAME), defs, "AssignmentPanel.search.policyRule.name");
        SearchFactory.addSearchRefDef(containerDef,
                ItemPath.create(AssignmentType.F_POLICY_RULE, PolicyRuleType.F_POLICY_CONSTRAINTS,
                        PolicyConstraintsType.F_EXCLUSION, ExclusionPolicyConstraintType.F_TARGET_REF), defs, AreaCategoryType.POLICY, getPageBase());

        defs.addAll(SearchFactory.createExtensionDefinitionList(containerDef));

        return defs;
    }

//    @Override
//    protected ItemVisibility getTypedContainerVisibility(ItemWrapper<?, ?> wrapper) {
//        if (QNameUtil.match(ConstructionType.COMPLEX_TYPE, wrapper.getTypeName())){
//            return ItemVisibility.HIDDEN;
//        }
//
//        if (QNameUtil.match(PersonaConstructionType.COMPLEX_TYPE, wrapper.getTypeName())){
//            return ItemVisibility.HIDDEN;
//        }
//
//        if (QNameUtil.match(AssignmentType.F_ORG_REF, wrapper.getItemName())){
//            return ItemVisibility.HIDDEN;
//        }
//
//        if (ItemPath.create(AssignmentHolderType.F_ASSIGNMENT, AssignmentType.F_TARGET_REF).equivalent(wrapper.getPath().namedSegmentsOnly())){
//            return ItemVisibility.HIDDEN;
//        }
//
//        if (ItemPath.create(AbstractRoleType.F_INDUCEMENT, AssignmentType.F_TARGET_REF).equivalent(wrapper.getPath().namedSegmentsOnly())){
//            return ItemVisibility.HIDDEN;
//        }
//
//        if (QNameUtil.match(AssignmentType.F_TENANT_REF, wrapper.getItemName())){
//            return ItemVisibility.HIDDEN;
//        }
//        return ItemVisibility.AUTO;
//    }
}
