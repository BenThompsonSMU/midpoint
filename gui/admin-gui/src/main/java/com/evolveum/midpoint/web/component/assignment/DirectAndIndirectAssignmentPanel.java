/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.component.assignment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.evolveum.midpoint.web.component.search.AbstractSearchItemDefinition;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.prism.wrapper.AssignmentValueWrapper;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerWrapper;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismObjectWrapper;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.GuiDisplayTypeUtil;
import com.evolveum.midpoint.gui.impl.component.data.column.AbstractItemWrapperColumn.ColumnType;
import com.evolveum.midpoint.gui.impl.component.data.column.PrismPropertyWrapperColumn;
import com.evolveum.midpoint.gui.impl.component.data.column.PrismReferenceWrapperColumn;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.web.component.data.column.AjaxLinkColumn;
import com.evolveum.midpoint.web.component.data.column.IconColumn;
import com.evolveum.midpoint.web.component.search.SearchFactory;
import com.evolveum.midpoint.web.component.search.SearchItemDefinition;
import com.evolveum.midpoint.web.model.PrismContainerWrapperModel;
import com.evolveum.midpoint.web.page.admin.PageAdminFocus;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * @author lskublik
 */
public class DirectAndIndirectAssignmentPanel<AH extends AssignmentHolderType> extends AssignmentPanel<AH> {
    private static final long serialVersionUID = 1L;

    private LoadableModel<List<PrismContainerValueWrapper<AssignmentType>>> allAssignmentModel = null;

    public DirectAndIndirectAssignmentPanel(String id, IModel<PrismContainerWrapper<AssignmentType>> assignmentContainerWrapperModel){
        super(id, assignmentContainerWrapperModel);
    }

    public DirectAndIndirectAssignmentPanel(String id, LoadableModel<PrismObjectWrapper<AH>> assignmentContainerWrapperModel, ContainerPanelConfigurationType config) {
        super(id, PrismContainerWrapperModel.fromContainerWrapper(assignmentContainerWrapperModel, AssignmentHolderType.F_ASSIGNMENT), config);
    }

    @Override
    protected IModel<List<PrismContainerValueWrapper<AssignmentType>>> loadValuesModel() {
        PageBase pageBase = getPageBase();
        if (pageBase instanceof PageAdminFocus) {
            if (allAssignmentModel == null) {
                allAssignmentModel = new LoadableModel<>() {

                    @Override
                    protected List<PrismContainerValueWrapper<AssignmentType>> load() {
                        return (List) ((PageAdminFocus<?>) pageBase).showAllAssignmentsPerformed(getModel());
                    }
                };
            }
            return allAssignmentModel;
        } else {
            return super.loadValuesModel();
        }
    }

    @Override
    protected List<IColumn<PrismContainerValueWrapper<AssignmentType>, String>> initBasicColumns() {
        List<IColumn<PrismContainerValueWrapper<AssignmentType>, String>> columns = new ArrayList<>();

        columns.add(new IconColumn<>(Model.of("")) {

            private static final long serialVersionUID = 1L;

            @Override
            protected DisplayType getIconDisplayType(IModel<PrismContainerValueWrapper<AssignmentType>> rowModel) {
                AssignmentType assignment = rowModel.getObject().getRealValue();
                if (assignment != null && assignment.getTargetRef() != null && StringUtils.isNotEmpty(assignment.getTargetRef().getOid())) {
                    List<ObjectType> targetObjectList = WebComponentUtil.loadReferencedObjectList(Collections.singletonList(assignment.getTargetRef()), OPERATION_LOAD_ASSIGNMENTS_TARGET_OBJ,
                            DirectAndIndirectAssignmentPanel.this.getPageBase());
                    if (CollectionUtils.isNotEmpty(targetObjectList) && targetObjectList.size() == 1) {
                        ObjectType targetObject = targetObjectList.get(0);
                        DisplayType displayType = GuiDisplayTypeUtil.getArchetypePolicyDisplayType(targetObject.asPrismObject(), DirectAndIndirectAssignmentPanel.this.getPageBase());
                        if (displayType != null) {
                            String disabledStyle;
                            if (targetObject instanceof FocusType) {
                                disabledStyle = WebComponentUtil.getIconEnabledDisabled(((FocusType) targetObject).asPrismObject());
                                if (displayType.getIcon() != null && StringUtils.isNotEmpty(displayType.getIcon().getCssClass()) &&
                                        disabledStyle != null) {
                                    displayType.getIcon().setCssClass(displayType.getIcon().getCssClass() + " " + disabledStyle);
                                    displayType.getIcon().setColor("");
                                }
                            }
                            return displayType;
                        }
                    }
                }
                return GuiDisplayTypeUtil.createDisplayType(WebComponentUtil.createDefaultBlackIcon(
                        AssignmentsUtil.getTargetType(rowModel.getObject().getRealValue())));
            }

        });

        columns.add(new AjaxLinkColumn<>(createStringResource("DirectAndIndirectAssignmentPanel.column.name")) {
            private static final long serialVersionUID = 1L;

            @Override
            protected IModel<String> createLinkModel(IModel<PrismContainerValueWrapper<AssignmentType>> rowModel) {
                String name = AssignmentsUtil.getName(rowModel.getObject(), getParentPage());
                if (StringUtils.isEmpty(name)) {
                    ObjectReferenceType ref;
                    if (rowModel.getObject().getRealValue().getConstruction() != null) {
                        ref = rowModel.getObject().getRealValue().getConstruction().getResourceRef();
                    } else {
                        ref = rowModel.getObject().getRealValue().getTargetRef();
                    }
                    name = WebComponentUtil.getDisplayNameOrName(ref);
                }
                return Model.of(name);
            }

            @Override
            public void onClick(AjaxRequestTarget target, IModel<PrismContainerValueWrapper<AssignmentType>> rowModel) {
                ObjectReferenceType ref;
                if (rowModel.getObject().getRealValue().getConstruction() != null) {
                    ref = rowModel.getObject().getRealValue().getConstruction().getResourceRef();
                } else {
                    ref = rowModel.getObject().getRealValue().getTargetRef();
                }
                if (ref != null) {
                    try {
                        WebComponentUtil.dispatchToObjectDetailsPage(ref, getPageBase(), true);
                    } catch (Exception e) {
                        getPageBase().error("Cannot determine details page for " + ref);
                        target.add(getPageBase().getFeedbackPanel());
                    }
                }
            }
        });

        columns.add(new AbstractColumn<>(createStringResource("DirectAndIndirectAssignmentPanel.column.type")) {
            @Override
            public void populateItem(Item<ICellPopulator<PrismContainerValueWrapper<AssignmentType>>> cellItem,
                    String componentId, final IModel<PrismContainerValueWrapper<AssignmentType>> rowModel) {
                AssignmentValueWrapper object = (AssignmentValueWrapper) rowModel.getObject();
                cellItem.add(new Label(componentId, (IModel<String>) () -> object.isDirectAssignment() ?
                        createStringResource("DirectAndIndirectAssignmentPanel.type.direct").getString() :
                        createStringResource("DirectAndIndirectAssignmentPanel.type.indirect").getString()));
                ObjectType assignmentParent = object.getAssignmentParent();
                if (assignmentParent != null) {
                    cellItem.add(AttributeModifier.replace("title",
                            createStringResource("DirectAndIndirectAssignmentPanel.tooltip.indirect.parent", assignmentParent.getName()).getString()));
                }
            }

            @Override
            public String getCssClass() {
                return "col-md-1";
            }
        });
        columns.add(new PrismPropertyWrapperColumn<AssignmentType, String>(getModel(), AssignmentType.F_DESCRIPTION, ColumnType.STRING, getPageBase()){
            @Override
            protected boolean isHelpTextVisible(boolean helpTextVisible) {
                return false;
            }
        });
        columns.add(new PrismReferenceWrapperColumn<AssignmentType, ObjectReferenceType>(getModel(), AssignmentType.F_TENANT_REF, ColumnType.STRING, getPageBase()));
        columns.add(new PrismReferenceWrapperColumn<AssignmentType, ObjectReferenceType>(getModel(), AssignmentType.F_ORG_REF, ColumnType.STRING, getPageBase()));
        columns.add(new PrismPropertyWrapperColumn<AssignmentType, String>(getModel(), ItemPath.create(AssignmentType.F_CONSTRUCTION, ConstructionType.F_KIND), ColumnType.STRING, getPageBase()));
        columns.add(new PrismPropertyWrapperColumn<AssignmentType, String>(getModel(), ItemPath.create(AssignmentType.F_CONSTRUCTION, ConstructionType.F_INTENT), ColumnType.STRING, getPageBase()));

        columns.add(new AbstractColumn<>(
                createStringResource("AbstractRoleAssignmentPanel.relationLabel")) {
            @Override
            public void populateItem(Item<ICellPopulator<PrismContainerValueWrapper<AssignmentType>>> item, String componentId, IModel<PrismContainerValueWrapper<AssignmentType>> assignmentModel) {
                item.add(new Label(componentId, WebComponentUtil.getRelationLabelValue(assignmentModel.getObject(), getPageBase())));
            }

            @Override
            public String getCssClass() {
                return "col-md-1";
            }
        });
        return columns;
    }

    @Override
    protected List<AbstractSearchItemDefinition> createSearchableItems(PrismContainerDefinition<AssignmentType> containerDef) {
        List<AbstractSearchItemDefinition> defs = new ArrayList<>();
        SearchFactory.addSearchRefDef(containerDef, AssignmentType.F_TARGET_REF, defs, AreaCategoryType.ADMINISTRATION, getPageBase());
        return defs;
    }

    @Override
    protected void refreshTable(AjaxRequestTarget ajaxRequestTarget) {
        if (allAssignmentModel != null) {
            allAssignmentModel.reset();
        }
        super.refreshTable(ajaxRequestTarget);

    }

}
