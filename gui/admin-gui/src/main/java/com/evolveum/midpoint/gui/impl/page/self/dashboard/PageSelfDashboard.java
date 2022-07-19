/*
 * Copyright (C) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.self.dashboard;

import com.evolveum.midpoint.authentication.api.authorization.AuthorizationAction;
import com.evolveum.midpoint.authentication.api.authorization.PageDescriptor;
import com.evolveum.midpoint.authentication.api.authorization.Url;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.impl.component.ContainerableListPanel;
import com.evolveum.midpoint.gui.impl.component.search.Search;
import com.evolveum.midpoint.model.api.authentication.CompiledObjectCollectionView;
import com.evolveum.midpoint.model.common.util.DefaultColumnUtils;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.PrismConstants;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.SecurityContextAwareCallable;
import com.evolveum.midpoint.web.component.data.ISelectableDataProvider;
import com.evolveum.midpoint.web.component.data.column.ColumnUtils;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;
import com.evolveum.midpoint.web.component.util.CallableResult;
import com.evolveum.midpoint.web.component.util.ContainerListDataProvider;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;
import com.evolveum.midpoint.web.page.admin.cases.CaseWorkItemsPanel;
import com.evolveum.midpoint.web.page.admin.home.component.AsyncDashboardPanel;
import com.evolveum.midpoint.web.page.self.PageSelf;

import com.evolveum.midpoint.web.session.PageStorage;
import com.evolveum.midpoint.web.session.UserProfileStorage;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.wicket.Component;
import org.apache.wicket.extensions.markup.html.repeater.data.sort.SortOrder;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.IModel;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@PageDescriptor(
        urls = {
//                @Url(mountUrl = "/self", matchUrlForSecurity = "/self"),
                @Url(mountUrl = "/self/dashboardNew")
        },
        action = {
                @AuthorizationAction(actionUri = PageSelf.AUTH_SELF_ALL_URI,
                        label = PageSelf.AUTH_SELF_ALL_LABEL,
                        description = PageSelf.AUTH_SELF_ALL_DESCRIPTION),
                @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_SELF_DASHBOARD_URL,
                        label = "PageSelfDashboard.auth.dashboard.label",
                        description = "PageSelfDashboard.auth.dashboard.description")
        })
public class PageSelfDashboard extends PageSelf {

    private static final Trace LOGGER = TraceManager.getTrace(PageSelfDashboard.class);
    private static final String ID_SEARCH_PANEL = "searchPanel";
    private static final String ID_LINKS_PANEL = "linksPanel";
    private static final String ID_LINK_ITEM = "linkItem";
    private static final String ID_OBJECT_COLLECTION_VIEW_WIDGETS_PANEL = "objectCollectionViewWidgetsPanel";
    private static final String ID_OBJECT_COLLECTION_VIEW_WIDGET = "objectCollectionViewWidget";
    private static final String DOT_CLASS = PageSelfDashboard.class.getName() + ".";
    private static final String OPERATION_LOAD_OBJECTS = DOT_CLASS + "loadObjectCollectionViewObjects";

    public PageSelfDashboard() {
        initLayout();
    }

    private void initLayout() {
        DashboardSearchPanel dashboardSearchPanel = new DashboardSearchPanel(ID_SEARCH_PANEL);
        List<String> searchPanelActions = Arrays.asList(AuthorizationConstants.AUTZ_UI_USERS_ALL_URL,
                AuthorizationConstants.AUTZ_UI_USERS_URL, AuthorizationConstants.AUTZ_UI_RESOURCES_ALL_URL,
                AuthorizationConstants.AUTZ_UI_RESOURCES_URL, AuthorizationConstants.AUTZ_UI_TASKS_ALL_URL,
                AuthorizationConstants.AUTZ_UI_TASKS_URL);
        dashboardSearchPanel.add(new VisibleBehaviour(() -> {
//            UserInterfaceElementVisibilityType visibility = getComponentVisibility(PredefinedDashboardWidgetId.SEARCH);
//            return WebComponentUtil.getElementVisibility(visibility, searchPanelActions);
            return true;
        }));
        add(dashboardSearchPanel);

        ListView<RichHyperlinkType> linksPanel = new ListView<>(ID_LINKS_PANEL, () -> loadLinksList()) {

            private static final long serialVersionUID = 1L;

            @Override
            protected void populateItem(ListItem<RichHyperlinkType> item) {
                item.add(new DashboardLinkComponent(ID_LINK_ITEM, item.getModel()));
            }
        };
        linksPanel.setOutputMarkupId(true);
        linksPanel.add(new VisibleBehaviour(() -> {
//            UserInterfaceElementVisibilityType visibility = getComponentVisibility(PredefinedDashboardWidgetId.SHORTCUTS);
//            return WebComponentUtil.getElementVisibility(visibility);
            return true;
        }));
        add(linksPanel);

        ListView<CompiledObjectCollectionView> viewWidgetsPanel = new ListView<>(ID_OBJECT_COLLECTION_VIEW_WIDGETS_PANEL, () -> loadObjectCollectionViewList()) {

            private static final long serialVersionUID = 1L;

            @Override
            protected void populateItem(ListItem<CompiledObjectCollectionView> item) {
                item.add(initViewPanel(item.getModelObject()));
            }
        };
        viewWidgetsPanel.setOutputMarkupId(true);
        viewWidgetsPanel.add(new VisibleBehaviour(() -> {
//            UserInterfaceElementVisibilityType visibility = getComponentVisibility(PredefinedDashboardWidgetId.SHORTCUTS);
//            return WebComponentUtil.getElementVisibility(visibility);
            return true;
        }));
        add(viewWidgetsPanel);
     }

    private List<RichHyperlinkType> loadLinksList() {
        return ((PageBase) getPage()).getCompiledGuiProfile().getUserDashboardLink();
    }

    private List<CompiledObjectCollectionView> loadObjectCollectionViewList() {
        return new ArrayList<>();
    }

    private <C extends Containerable> AsyncDashboardPanel<Object, List<C>> initViewPanel(CompiledObjectCollectionView view) {
        AsyncDashboardPanel<Object, List<C>> viewPanel = new AsyncDashboardPanel<>(ID_OBJECT_COLLECTION_VIEW_WIDGET,
                () -> WebComponentUtil.getTranslatedPolyString(WebComponentUtil.getCollectionLabel(view.getDisplay())),
                WebComponentUtil.getIconCssClass(view.getDisplay()), null) {

            private static final long serialVersionUID = 1L;
            private ContainerListDataProvider<C> provider = null;

            private void initProvider() {
                provider = new ContainerListDataProvider<>(this, null) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected PageStorage getPageStorage() {
                        return null;
                    }

                    @Override
                    public ObjectQuery getQuery() {
                        OperationResult result = new OperationResult(OPERATION_LOAD_OBJECTS);
                        ObjectFilter collectionFilter = WebComponentUtil.evaluateExpressionsInFilter(view.getFilter(),
                                result, PageSelfDashboard.this);    //todo only this filter? getDomainFilter? get filter from collection?
                        return getPrismContext().queryFactory().createQuery(collectionFilter);
                    }

                };
            }

            @Override
            protected SecurityContextAwareCallable<CallableResult<List<PrismContainerValueWrapper<C>>>> createCallable(
                    Authentication auth, IModel callableParameterModel) {

                return new SecurityContextAwareCallable<>(
                        getSecurityContextManager(), auth) {

                    @Override
                    public CallableResult<List<PrismContainerValueWrapper<C>>> callWithContextPrepared() {
                        if (provider == null) {
                            initProvider();
                        }
                        return loadObjects(provider);
                    }
                };
            }

            @Override
            protected Component getMainComponent(String markupId) {
                ContainerableListPanel<C, PrismContainerValueWrapper<C>> workItemsPanel =
                        new ContainerableListPanel<>(markupId, view.getTargetClass(PrismContext.get())) {

                            @Override
                            protected void onBeforeRender() {
                                super.onBeforeRender();

                                getTable().setShowAsCard(false);
                            }

                            @Override
                            protected List<IColumn<PrismContainerValueWrapper<C>, String>> createDefaultColumns() {
                                if (CollectionUtils.isNotEmpty(view.getColumns())) {
                                    return getViewColumnsTransformed(view.getColumns());
                                }
                                GuiObjectListViewType defaultView = DefaultColumnUtils.getDefaultView(view.getTargetClass(PrismContext.get()));
                                if (defaultView == null) {
                                    return null;
                                }
                                return getViewColumnsTransformed(defaultView.getColumn());
                            }

                            @Override
                            protected List<InlineMenuItem> createInlineMenu() {
                                return null;
                            }

                            @Override
                            protected ISelectableDataProvider<C, PrismContainerValueWrapper<C>> createProvider() {
                                if (provider == null) {
                                    initProvider();
                                }
                                return provider;
                            }

                            @Override
                            public List<C> getSelectedRealObjects() {
                                return getSelectedObjects().stream().map(o -> o.getRealValue()).collect(Collectors.toList());
                            }

                            @Override
                            protected UserProfileStorage.TableId getTableId() {
                                return null;
                            }

                            @Override
                            protected boolean hideFooterIfSinglePage() {
                                return true;
                            }

                            @Override
                            protected boolean isHeaderVisible() {
                                return false;
                            }

                            @Override
                            protected IColumn createCheckboxColumn() {
                                return null;
                            }

                            @Override
                            protected IColumn createIconColumn() {
                                return null; //todo fix
                            }

                            @Override
                            protected IColumn createNameColumn(IModel<String> displayModel, GuiObjectColumnType customColumn, ItemPath itemPath, ExpressionType expression) {
                                return null;
                            }

                            @Override
                            protected String getStorageKey() {
                                return "";
                            }

                            @Override
                            protected C getRowRealValue(PrismContainerValueWrapper<C> rowModelObject) {
                                if (rowModelObject == null) {
                                    return null;
                                }
                                return rowModelObject.getRealValue();
                            }
                        };
                workItemsPanel.setOutputMarkupId(true);
                return workItemsPanel;
            }
        };
        viewPanel.add(new VisibleBehaviour(() -> {
            return true; //todo visibility of widget
        }));
        return viewPanel;
    }

    private <C extends Containerable> CallableResult<List<PrismContainerValueWrapper<C>>> loadObjects(ContainerListDataProvider<C> provider) {
        CallableResult<List<PrismContainerValueWrapper<C>>> callableResult = new CallableResult<>();
        List<PrismContainerValueWrapper<C>> list = new ArrayList<>();
        callableResult.setValue(list);

        Task task = createSimpleTask(OPERATION_LOAD_OBJECTS);
        OperationResult result = task.getResult();
        callableResult.setResult(result);

       provider.internalIterator(0, provider.size()).forEachRemaining(list::add);

        result.recordSuccessIfUnknown();
        result.recomputeStatus();

        return callableResult;
    }

}
