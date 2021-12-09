/*
 * Copyright (c) 2018-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.component;

import java.util.ArrayList;
import java.util.List;

import com.evolveum.midpoint.gui.api.component.MainObjectListPanel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.impl.page.admin.assignmentholder.PageAssignmentHolderDetails;
import com.evolveum.midpoint.gui.impl.page.admin.user.PageUser;
import com.evolveum.midpoint.web.component.*;

import com.evolveum.midpoint.web.component.util.VisibleBehaviour;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ContainerPanelConfigurationType;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;

import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerWrapper;
import com.evolveum.midpoint.gui.api.util.WebPrismUtil;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIconBuilder;
import com.evolveum.midpoint.model.api.AssignmentObjectRelation;
import com.evolveum.midpoint.model.api.authentication.CompiledObjectCollectionView;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.web.component.data.BoxedTablePanel;
import com.evolveum.midpoint.web.component.data.ISelectableDataProvider;
import com.evolveum.midpoint.web.component.data.column.ColumnMenuAction;
import com.evolveum.midpoint.web.component.menu.cog.ButtonInlineMenuItem;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItemAction;
import com.evolveum.midpoint.web.component.objectdetails.AssignmentHolderTypeMainPanel;
import com.evolveum.midpoint.web.component.objectdetails.FocusMainPanel;
import com.evolveum.midpoint.web.component.prism.ValueStatus;
import com.evolveum.midpoint.web.component.search.*;
import com.evolveum.midpoint.web.component.util.MultivalueContainerListDataProvider;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.session.PageStorage;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentHolderType;

/**
 * @author skublik
 */

public abstract class MultivalueContainerListPanel<C extends Containerable>
        extends ContainerableListPanel<C, PrismContainerValueWrapper<C>> {

    private static final long serialVersionUID = 1L;

    public MultivalueContainerListPanel(String id, Class<C> type) {
        super(id, type);
    }

    public MultivalueContainerListPanel(String id, Class<C> type, ContainerPanelConfigurationType config) {
        super(id, type, null, config);
    }

    @Override
    protected Search createSearch(Class<C> type) {
        PrismContainerDefinition<C> containerDefinition = getTypeDefinitionForSearch();
        return SearchFactory.createContainerSearch(createTypeSearchItem(type, containerDefinition), getTypeDefinitionForSearch(),
                getDefaultSearchItem(), initSearchableItems(containerDefinition), getPageBase(), false);
    }

    protected PrismContainerDefinition<C> getTypeDefinitionForSearch() {
        return getPrismContext().getSchemaRegistry().findContainerDefinitionByCompileTimeClass(getType());
    }

    private ContainerTypeSearchItem<C> createTypeSearchItem(Class<C> type, PrismContainerDefinition<C> containerDefinition) {
        return new ContainerTypeSearchItem<>(new SearchValue<>(type, containerDefinition == null ? getType().getTypeName() : containerDefinition.getDisplayName()));
    }

    protected List<AbstractSearchItemDefinition> initSearchableItems(PrismContainerDefinition<C> containerDef){
        return null;
    }

    protected ItemPath getDefaultSearchItem() {
        return null;
    }

    @Override
    protected ISelectableDataProvider<C, PrismContainerValueWrapper<C>> createProvider() {
        return new MultivalueContainerListDataProvider<>(MultivalueContainerListPanel.this, getSearchModel(), new PropertyModel<>(getContainerModel(), "values")) {

            @Override
            protected PageStorage getPageStorage() {
                return MultivalueContainerListPanel.this.getPageStorage();
            }
        };
    }

    @Override
    protected List<Component> createToolbarButtonsList(String idButton) {
        List<Component> bar = new ArrayList<>();

        AjaxIconButton newObjectButton = new AjaxIconButton(idButton, new Model<>(GuiStyleConstants.CLASS_ADD_NEW_OBJECT),
                createStringResource("MainObjectListPanel.newObject")) {

            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                newItemPerformed(target, null);
            }
        };
        newObjectButton.add(AttributeAppender.append("class", "btn btn-default btn-sm"));
        newObjectButton.add(new VisibleBehaviour(() -> isCreateNewObjectVisible()));
        bar.add(newObjectButton);
        return bar;
    }

    protected void newItemPerformed(AjaxRequestTarget target, AssignmentObjectRelation relationSepc) {

    }

    public List<PrismContainerValueWrapper<C>> getSelectedItems() {
        BoxedTablePanel<PrismContainerValueWrapper<C>> itemsTable = getTable();
        @SuppressWarnings("unchecked") ISelectableDataProvider<C, PrismContainerValueWrapper<C>> itemsProvider = (ISelectableDataProvider<C, PrismContainerValueWrapper<C>>) itemsTable.
                getDataTable().getDataProvider();
        return itemsProvider.getSelectedObjects();
    }

    public void reloadSavePreviewButtons(AjaxRequestTarget target){
        FocusMainPanel mainPanel = findParent(FocusMainPanel.class);
        if (mainPanel != null) {
            mainPanel.reloadSavePreviewButtons(target);
        }
    }

    public List<PrismContainerValueWrapper<C>> getPerformedSelectedItems(IModel<PrismContainerValueWrapper<C>> rowModel) {
        List<PrismContainerValueWrapper<C>> performedItems = new ArrayList<>();
        List<PrismContainerValueWrapper<C>> listItems = getSelectedItems();
        if((listItems!= null && !listItems.isEmpty()) || rowModel != null) {
            if(rowModel == null) {
                performedItems.addAll(listItems);
                listItems.forEach(itemConfigurationTypeContainerValueWrapper -> itemConfigurationTypeContainerValueWrapper.setSelected(false));
            } else {
                performedItems.add(rowModel.getObject());
                rowModel.getObject().setSelected(false);
            }
        }
        return performedItems;
    }

    //TODO generalize for properties
    public PrismContainerValueWrapper<C> createNewItemContainerValueWrapper(
            PrismContainerValue<C> newItem,
            PrismContainerWrapper<C> model, AjaxRequestTarget target) {

        return WebPrismUtil.createNewValueWrapper(model, newItem, getPageBase(), target);
    }

    protected abstract void editItemPerformed(AjaxRequestTarget target, IModel<PrismContainerValueWrapper<C>> rowModel, List<PrismContainerValueWrapper<C>> listItems);

    public List<InlineMenuItem> getDefaultMenuActions() {
        List<InlineMenuItem> menuItems = new ArrayList<>();
        menuItems.add(new ButtonInlineMenuItem(createStringResource("pageAdminFocus.button.delete")) {
            private static final long serialVersionUID = 1L;

            public CompositedIconBuilder getIconCompositedBuilder(){
                return getDefaultCompositedIconBuilder(GuiStyleConstants.CLASS_DELETE_MENU_ITEM);
            }

            @Override
            public InlineMenuItemAction initAction() {
                return createDeleteColumnAction();
            }
        });

        menuItems.add(new ButtonInlineMenuItem(createStringResource("PageBase.button.edit")) {
            private static final long serialVersionUID = 1L;

            @Override
            public CompositedIconBuilder getIconCompositedBuilder(){
                return getDefaultCompositedIconBuilder(GuiStyleConstants.CLASS_EDIT_MENU_ITEM);
            }

            @Override
            public InlineMenuItemAction initAction() {
                return createEditColumnAction();
            }
        });
        return menuItems;
    }


    public <AH extends AssignmentHolderType> PrismObject<AH> getFocusObject(){
        AssignmentHolderTypeMainPanel mainPanel = findParent(AssignmentHolderTypeMainPanel.class);
        if (mainPanel != null) {
            return mainPanel.getObjectWrapper().getObject();
        }
        PageBase pageBase = getPageBase();
        if (pageBase != null && pageBase instanceof PageAssignmentHolderDetails) {
            PageAssignmentHolderDetails pageAssignmentHolderDetails = (PageAssignmentHolderDetails) pageBase;
            return (PrismObject<AH>) pageAssignmentHolderDetails.getPrismObject();
        }
        return null;
    }

    public ColumnMenuAction<PrismContainerValueWrapper<C>> createDeleteColumnAction() {
        return new ColumnMenuAction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                if (getRowModel() == null) {
                    deleteItemPerformed(target, getSelectedItems());
                } else {
                    List<PrismContainerValueWrapper<C>> toDelete = new ArrayList<>();
                    toDelete.add(getRowModel().getObject());
                    deleteItemPerformed(target, toDelete);
                }
            }
        };
    }

    public ColumnMenuAction<PrismContainerValueWrapper<C>> createEditColumnAction() {
        return new ColumnMenuAction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                editItemPerformed(target, getRowModel(), getSelectedItems());
            }
        };
    }

    public void deleteItemPerformed(AjaxRequestTarget target, List<PrismContainerValueWrapper<C>> toDelete){
        if (toDelete == null || toDelete.isEmpty()){
            warn(createStringResource("MultivalueContainerListPanel.message.noItemsSelected").getString());
            target.add(getPageBase().getFeedbackPanel());
            return;
        }
        toDelete.forEach(value -> {
            if (value.getStatus() == ValueStatus.ADDED) {
                PrismContainerWrapper<C> wrapper = getContainerModel() != null ?
                        getContainerModel().getObject() : null;
                if (wrapper != null) {
                    wrapper.getValues().remove(value);
                }
            } else {
                value.setStatus(ValueStatus.DELETED);
            }
            value.setSelected(false);
        });
        refreshTable(target);
        reloadSavePreviewButtons(target);
    }

    protected abstract boolean isCreateNewObjectVisible();

    public boolean isListPanelVisible(){
        return true;
    }

    protected IModel<String> createStyleClassModelForNewObjectIcon() {
        return new IModel<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public String getObject() {
                return "btn btn-success btn-sm";
            }
        };
    }

    protected abstract IModel<PrismContainerWrapper<C>> getContainerModel();

    @Override
    protected IColumn<PrismContainerValueWrapper<C>, String> createIconColumn() {
        return null;
    }

    @Override
    protected IColumn<PrismContainerValueWrapper<C>, String> createCheckboxColumn() {
        return null;
    }

    @Override
    protected C getRowRealValue(PrismContainerValueWrapper<C> rowModelObject) {
        if (rowModelObject == null) {
            return null;
        }

        return rowModelObject.getRealValue();
    }

}
