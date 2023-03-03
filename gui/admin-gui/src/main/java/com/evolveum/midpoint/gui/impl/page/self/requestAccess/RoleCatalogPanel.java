/*
 * Copyright (c) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.self.requestAccess;

import java.util.*;
import java.util.stream.Collectors;
import javax.xml.namespace.QName;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.Component;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.ISortableDataProvider;
import org.apache.wicket.extensions.markup.html.repeater.data.table.PropertyColumn;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.IResource;
import org.apache.wicket.util.string.Strings;

import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.component.Badge;
import com.evolveum.midpoint.gui.api.component.ObjectBrowserPanel;
import com.evolveum.midpoint.gui.api.component.Toggle;
import com.evolveum.midpoint.gui.api.component.TogglePanel;
import com.evolveum.midpoint.gui.api.component.result.Toast;
import com.evolveum.midpoint.gui.api.component.wizard.WizardModel;
import com.evolveum.midpoint.gui.api.component.wizard.WizardStepPanel;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.gui.impl.component.data.provider.ObjectDataProvider;
import com.evolveum.midpoint.gui.impl.component.menu.listGroup.CustomListGroupMenuItem;
import com.evolveum.midpoint.gui.impl.component.menu.listGroup.ListGroupMenu;
import com.evolveum.midpoint.gui.impl.component.menu.listGroup.ListGroupMenuItem;
import com.evolveum.midpoint.gui.impl.component.menu.listGroup.ListGroupMenuPanel;
import com.evolveum.midpoint.gui.impl.component.search.Search;
import com.evolveum.midpoint.gui.impl.component.search.SearchBuilder;
import com.evolveum.midpoint.gui.impl.component.search.panel.SearchPanel;
import com.evolveum.midpoint.gui.impl.component.tile.*;
import com.evolveum.midpoint.gui.impl.page.self.PageRequestAccess;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.OrgFilter;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.security.api.SecurityUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.data.column.AjaxLinkPanel;
import com.evolveum.midpoint.web.component.data.column.CheckBoxHeaderColumn;
import com.evolveum.midpoint.web.component.data.column.RoundedIconColumn;
import com.evolveum.midpoint.web.component.util.EnableBehaviour;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.component.util.SerializableBiConsumer;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.session.UserProfileStorage;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.ItemPathType;

/**
 * Created by Viliam Repan (lazyman).
 */
public class RoleCatalogPanel extends WizardStepPanel<RequestAccess> implements AccessRequestStep {

    private static final long serialVersionUID = 1L;

    public static final String STEP_ID = "catalog";

    private static final Trace LOGGER = TraceManager.getTrace(RoleCatalogPanel.class);

    private static final String DOT_CLASS = RoleCatalogPanel.class.getName() + ".";
    private static final String OPERATION_LOAD_ROLE_CATALOG_MENU = DOT_CLASS + "loadRoleCatalogMenu";
    private static final String OPERATION_LOAD_USER = DOT_CLASS + "loadUser";

    private static final RoleCatalogViewType DEFAULT_VIEW = RoleCatalogViewType.TILE;

    private static final String ID_VIEW_TOGGLE = "viewToggle";
    private static final String ID_MENU = "menu";
    private static final String ID_TILES = "tilesTable";
    private static final String ID_TABLE_FOOTER_FRAGMENT = "tableFooterFragment";
    private static final String ID_ADD_SELECTED = "addSelected";
    private static final String ID_ADD_ALL = "addAll";

    private final PageBase page;

    private IModel<Search> searchModel;

    private IModel<ListGroupMenu<RoleCatalogQueryItem>> menuModel;

    private IModel<ObjectReferenceType> teammateModel;

    private IModel<RoleCatalogQuery> queryModel;

    public RoleCatalogPanel(IModel<RequestAccess> model, PageBase page) {
        super(model);

        this.page = page;
    }

    @Override
    public String getStepId() {
        return STEP_ID;
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();

        initModels();
        initLayout();
    }

    @Override
    protected void onBeforeRender() {
        if (getModelObject().getRelation() == null) {
            PageParameters params = new PageParameters();
            params.set(WizardModel.PARAM_STEP, RelationPanel.STEP_ID);

            throw new RestartResponseException(new PageRequestAccess(params, getWizard()));
        }

        super.onBeforeRender();
    }

    @Override
    public IModel<List<Badge>> getTitleBadges() {
        return () -> {
            String text;

            int count = getModelObject().getPersonOfInterest().size();
            if (isRequestingForMyself()) {
                text = count > 1 ? getString("RoleCatalogPanel.badgeMyselfAndOthers", count - 1) : getString("RoleCatalogPanel.badgeMyself");
            } else {
                text = getString("RoleCatalogPanel.badgeOthers", count);
            }

            return List.of(new Badge("badge badge-info", text));
        };
    }

    private boolean isRequestingForMyself() {
        String principalOid = SecurityUtil.getPrincipalOidIfAuthenticated();
        RequestAccess request = getModelObject();
        return request.getPersonOfInterest().stream().anyMatch(o -> Objects.equals(principalOid, o.getOid()));
    }

    @Override
    public IModel<String> getTitle() {
        return createStringResource("RoleCatalogPanel.title");
    }

    @Override
    public PrismContext getPrismContext() {
        return page.getPrismContext();
    }

    private void updateFalseQuery(RoleCatalogQuery query) {
        ObjectQuery oq = getPrismContext()
                .queryFor(RoleType.class)
                .none()
                .build();

        query.setQuery(oq);
        query.setType(RoleType.class);
    }

    private void updateQueryFromOrgRef(RoleCatalogQuery query, ObjectReferenceType ref, boolean scopeOne) {
        ObjectQuery oq = getPrismContext()
                .queryFor(AbstractRoleType.class)
                .isInScopeOf(ref.getOid(), scopeOne ? OrgFilter.Scope.ONE_LEVEL : OrgFilter.Scope.SUBTREE)
                .asc(AbstractRoleType.F_NAME)
                .build();

        query.setQuery(oq);
        query.setType(AbstractRoleType.class);
    }

    private void updateQueryForRolesOfTeammate(RoleCatalogQuery query, String userOid) {
        if (userOid == null) {
            updateFalseQuery(query);
            return;
        }

        query.setType(AbstractRoleType.class);

        if (getPageBase().isNativeRepo()) {
            ObjectQuery oq = getPrismContext().queryFor(AbstractRoleType.class)
                    .referencedBy(UserType.class, ItemPath.create(UserType.F_ASSIGNMENT, AssignmentType.F_TARGET_REF))
                    .id(userOid)
                    .and().not().type(ArchetypeType.class)
                    .build();

            query.setQuery(oq);
            return;
        }

        // searching for user assignments targets in two steps for non-native repository (doesn't support referencedBy)
        Task task = page.createSimpleTask(OPERATION_LOAD_USER);
        OperationResult result = task.getResult();
        try {
            PrismObject<UserType> user = WebModelServiceUtils.loadObject(UserType.class, userOid, page, task, result);
            if (user == null) {
                updateFalseQuery(query);
                return;
            }

            String[] oids = user.asObjectable().getAssignment().stream()
                    .filter(a -> a.getTargetRef() != null)
                    .map(a -> a.getTargetRef().getOid())
                    .toArray(String[]::new);

            ObjectQuery oq = getPrismContext().queryFor(AbstractRoleType.class)
                    .id(oids)
                    .and().not().type(ArchetypeType.class)
                    .build();
            query.setQuery(oq);

            result.computeStatusIfUnknown();
        } catch (Exception ex) {
            result.recordFatalError("Couldn't load user", ex);
        }

        if (!WebComponentUtil.isSuccessOrHandledError(result)) {
            page.showResult(result);
        }
    }

    private void updateQueryFromCollectionRef(RoleCatalogQuery query, ObjectReferenceType collectionRef) {
        if (collectionRef == null) {
            updateFalseQuery(query);
            return;
        }

        PrismObject<ObjectCollectionType> collection = WebModelServiceUtils.loadObject(collectionRef, page);
        if (collection == null) {
            page.error(page.getString("RoleCatalogPanel.message.loadObjectCollectionError", WebComponentUtil.getName(collectionRef)));
            updateFalseQuery(query);
            return;
        }

        ObjectCollectionType objectCollection = collection.asObjectable();

        try {
            QName type = objectCollection.getType();
            if (type == null) {
                type = AbstractRoleType.COMPLEX_TYPE;
            }
            ObjectTypes ot = ObjectTypes.getObjectTypeFromTypeQName(type);

            ObjectFilter filter = page.getQueryConverter().createObjectFilter(ot.getClassDefinition(), objectCollection.getFilter());
            ObjectQuery oq = getPrismContext()
                    .queryFor(ot.getClassDefinition())
                    .filter(filter)
                    .asc(AbstractRoleType.F_NAME)
                    .build();

            query.setQuery(oq);
            query.setType(ot.getClassDefinition());
        } catch (Exception ex) {
            LOGGER.debug("Couldn't create search filter", ex);
            page.error(page.getString("RoleCatalogPanel.message.searchFilterError", ex.getMessage()));

            updateFalseQuery(query);
        }
    }

    private void updateQueryFromCollectionIdentifier(RoleCatalogQuery query, String collectionIdentifier) {
        StaticObjectCollection collection = StaticObjectCollection.findCollection(collectionIdentifier);
        if (collection == null) {
            updateFalseQuery(query);
            return;
        }

        ObjectQuery oq = getPrismContext()
                .queryFor(collection.getType())
                .asc(ObjectType.F_NAME)
                .build();

        query.setQuery(oq);
        query.setType((Class<? extends AbstractRoleType>) collection.getType());
    }

    private void initModels() {
        teammateModel = Model.of((ObjectReferenceType) null);

        RoleCatalogQuery query = new RoleCatalogQuery();
        updateFalseQuery(query);
        queryModel = Model.of(query);

        searchModel = new LoadableModel<>(false) {

            @Override
            public Search getObject() {
                Search search = super.getObject();

                Class<? extends ObjectType> type = queryModel.getObject().getType();
                // make sure we'll return search object that was created for proper ObjectType class
                if (!Objects.equals(type, search.getTypeClass())) {
                    reset();

                    search = super.getObject();
                }

                return search;
            }

            @Override
            protected Search load() {
                Class<? extends ObjectType> type = queryModel.getObject().getType();

                SearchBuilder searchBuilder = new SearchBuilder(type)
                        .modelServiceLocator(page);

                return searchBuilder.build();
            }
        };

        menuModel = new LoadableModel<>(false) {

            @Override
            protected ListGroupMenu<RoleCatalogQueryItem> load() {
                ListGroupMenu<RoleCatalogQueryItem> menu = loadRoleCatalogMenu();

                ListGroupMenuItem<RoleCatalogQueryItem> active = menu.getActiveMenu();
                if (active == null) {
                    active = menu.activateFirstAvailableItem();
                }
                updateQueryModel(active);

                return menu;
            }
        };
    }

    private void initLayout() {
        setOutputMarkupId(true);

        ObjectDataProvider provider = new ObjectDataProvider(this, searchModel) {

            @Override
            protected ObjectQuery getCustomizeContentQuery() {
                // make sure menuModel was loaded
                menuModel.getObject();

                RoleCatalogQuery catalogQuery = queryModel.getObject();
                ObjectQuery query = catalogQuery.getQuery();
                query = query.clone();

                Class<? extends AbstractRoleType> type = catalogQuery.getType();

                ObjectFilter assignableRolesFilter = getModelObject().getAssignableRolesFilter(page, type);
                if (assignableRolesFilter != null) {
                    query.addFilter(assignableRolesFilter);
                }

                return query;
            }
        };
        Collection<SelectorOptions<GetOperationOptions>> options = getPageBase().getOperationOptionsBuilder()
                .item(FocusType.F_JPEG_PHOTO).retrieve()
                .build();
        provider.setOptions(options);

        TileTablePanel<CatalogTile<SelectableBean<ObjectType>>, SelectableBean<ObjectType>> tilesTable =
                new TileTablePanel<>(ID_TILES, createViewToggleModel(), UserProfileStorage.TableId.PAGE_REQUEST_ACCESS_ROLE_CATALOG) {

                    @Override
                    protected List<IColumn<SelectableBean<ObjectType>, String>> createColumns() {
                        return RoleCatalogPanel.this.createColumns();
                    }

                    @Override
                    protected Component createHeader(String id) {
                        Component header = super.createHeader(id);
                        if (header instanceof SearchPanel) {
                            // mt-2 added because search panel now uses *-sm classes and it doesn't match rest of the layout
                            header.add(AttributeAppender.append("class", "mt-2"));
                        }

                        return header;
                    }

                    @Override
                    protected WebMarkupContainer createTableButtonToolbar(String id) {
                        Fragment fragment = new Fragment(id, ID_TABLE_FOOTER_FRAGMENT, RoleCatalogPanel.this);
                        fragment.add(new AjaxLink<>(ID_ADD_SELECTED) {

                            @Override
                            public void onClick(AjaxRequestTarget target) {
                                List<ObjectType> selected = provider.getSelectedData();
                                addItemsPerformed(target, selected);
                            }
                        });

                        fragment.add(new AjaxLink<>(ID_ADD_ALL) {

                            @Override
                            public void onClick(AjaxRequestTarget target) {
                                addAllItemsPerformed(target);
                            }
                        });

                        return fragment;
                    }

                    @Override
                    protected CatalogTile createTileObject(SelectableBean<ObjectType> object) {
                        ObjectType obj = object.getValue();
                        PrismObject prism = obj != null ? obj.asPrismObject() : null;
                        String icon = WebComponentUtil.createDefaultColoredIcon(prism.getValue().getTypeName());

                        CatalogTile<SelectableBean<ObjectType>> t = new CatalogTile<>(icon, WebComponentUtil.getDisplayNameOrName(prism));
                        t.setDescription(object.getValue().getDescription());
                        t.setValue(object);

                        RoundedIconPanel.State checkState = computeCheckState(obj.getOid());
                        t.setCheckState(checkState);

                        String checkTitle = computeCheckTitle(obj.getOid());
                        t.setCheckTitle(checkTitle);

                        return t;
                    }

                    @Override
                    protected Component createTile(String id, IModel<CatalogTile<SelectableBean<ObjectType>>> model) {
                        return new CatalogTilePanel<>(id, model) {

                            @Override
                            protected void onAdd(AjaxRequestTarget target) {
                                SelectableBean<ObjectType> bean = model.getObject().getValue();
                                addItemsPerformed(target, Arrays.asList(bean.getValue()));
                            }

                            @Override
                            protected void onDetails(AjaxRequestTarget target) {
                                SelectableBean<ObjectType> bean = model.getObject().getValue();
                                itemDetailsPerformed(target, bean.getValue());
                            }

                            @Override
                            protected void onClick(AjaxRequestTarget target) {
                                // no selection to be done
                            }

                            @Override
                            protected Component createAddButton(String id) {
                                Component details = super.createAddButton(id);
                                WebComponentUtil.addDisabledClassBehavior(details);

                                details.add(new EnableBehaviour(() -> {
                                    ObjectType object = model.getObject().getValue().getValue();

                                    RequestAccess access = RoleCatalogPanel.this.getModelObject();
                                    return access.getTemplateAssignments().stream()
                                            .noneMatch(a -> Objects.equals(object.getOid(), a.getTargetRef().getOid()));
                                }));
                                return details;
                            }

                            @Override
                            protected IModel<IResource> createPreferredImage(IModel<CatalogTile<SelectableBean<ObjectType>>> model) {
                                return createImage(() -> model.getObject().getValue().getValue());
                            }
                        };
                    }

                    @Override
                    protected ISortableDataProvider createProvider() {
                        return provider;
                    }

                    @Override
                    protected String getTileCssClasses() {
                        return "col-12 col-md-6 col-lg-4 col-xxl-2";
                    }

                    @Override
                    protected IModel<Search> createSearchModel() {
                        return searchModel;
                    }
                };
        add(tilesTable);

        IModel<List<Toggle<ViewToggle>>> items = new LoadableModel<>(false) {

            @Override
            protected List<Toggle<ViewToggle>> load() {
                RoleCatalogType config = getRoleCatalogConfiguration();
                List<RoleCatalogViewType> allowedViews = config.getAllowedViews();

                ViewToggle toggle = tilesTable.getViewToggleModel().getObject();
                List<Toggle<ViewToggle>> list = new ArrayList<>();

                if (allowedViews.isEmpty() || allowedViews.contains(RoleCatalogViewType.TABLE)) {
                    Toggle<ViewToggle> asList = new Toggle<>("fa-solid fa-table-list", null);
                    asList.setActive(ViewToggle.TABLE == toggle);
                    asList.setValue(ViewToggle.TABLE);
                    list.add(asList);
                }

                if (allowedViews.isEmpty() || allowedViews.contains(RoleCatalogViewType.TILE)) {
                    Toggle<ViewToggle> asTile = new Toggle<>("fa-solid fa-table-cells", null);
                    asTile.setActive(ViewToggle.TILE == toggle);
                    asTile.setValue(ViewToggle.TILE);
                    list.add(asTile);
                }

                return list;
            }
        };

        TogglePanel<ViewToggle> viewToggle = new TogglePanel<>(ID_VIEW_TOGGLE, items) {

            @Override
            protected void itemSelected(AjaxRequestTarget target, IModel<Toggle<ViewToggle>> item) {
                super.itemSelected(target, item);

                tilesTable.getViewToggleModel().setObject(item.getObject().getValue());
                target.add(RoleCatalogPanel.this);
            }
        };
        viewToggle.add(new VisibleEnableBehaviour(() -> items.getObject().size() > 1));
        add(viewToggle);

        ListGroupMenuPanel menu = new ListGroupMenuPanel(ID_MENU, menuModel) {

            @Override
            protected void onMenuClickPerformed(AjaxRequestTarget target, ListGroupMenuItem item) {
                super.onMenuClickPerformed(target, item);

                if (!item.isActive()) {
                    // we've clicked on menu that has submenus
                    return;
                }

                RoleCatalogPanel.this.onMenuClickPerformed(target, item);
            }
        };
        add(menu);
    }

    private void updateQueryModel(ListGroupMenuItem<RoleCatalogQueryItem> item) {
        RoleCatalogQuery query = queryModel.getObject();

        RoleCatalogQueryItem rcq = item != null ? item.getValue() : null;

        if (rcq == null) {
            updateFalseQuery(query);
            return;
        }

        if (rcq.rolesOfTeammate()) {
            ObjectReferenceType userRef = teammateModel.getObject();
            String userOid = userRef != null ? userRef.getOid() : null;
            updateQueryForRolesOfTeammate(query, userOid);
            return;
        }

        if (rcq.orgRef() != null) {
            updateQueryFromOrgRef(query, rcq.orgRef(), rcq.scopeOne());
            return;
        }

        RoleCollectionViewType collection = rcq.collection();
        if (collection == null) {
            updateFalseQuery(query);
            return;
        }

        if (collection.getCollectionRef() != null) {
            updateQueryFromCollectionRef(query, collection.getCollectionRef());
            return;
        } else if (collection.getCollectionIdentifier() != null) {
            updateQueryFromCollectionIdentifier(query, collection.getCollectionIdentifier());
            return;
        }

        updateFalseQuery(query);
    }

    private void onMenuClickPerformed(AjaxRequestTarget target, ListGroupMenuItem<RoleCatalogQueryItem> item) {
        updateQueryModel(item);

        RoleCatalogQuery query = queryModel.getObject();

        Search search = searchModel.getObject();
        if (!Objects.equals(search.getTypeClass(), query.getType())) {
            SearchBuilder searchBuilder = new SearchBuilder(query.getType())
                    .modelServiceLocator(page);

            searchModel.setObject(searchBuilder.build());
        }

        target.add(get(ID_TILES));
    }

    private IModel<ViewToggle> createViewToggleModel() {
        return new LoadableModel<>(false) {

            @Override
            protected ViewToggle load() {
                RoleCatalogType config = getRoleCatalogConfiguration();
                RoleCatalogViewType view = config.getDefaultView();

                List<RoleCatalogViewType> allowedViews = config.getAllowedViews();
                if (view == null) {
                    view = DEFAULT_VIEW;
                }

                if (!allowedViews.isEmpty() && !allowedViews.contains(view)) {
                    return findDefaultViewToggle(allowedViews.get(0));
                }

                switch (view) {
                    case TABLE:
                        return ViewToggle.TABLE;
                    case TILE:
                    default:
                        return ViewToggle.TILE;
                }
            }
        };
    }

    private ViewToggle findDefaultViewToggle(RoleCatalogViewType view) {
        if (view == null) {
            return ViewToggle.TILE;
        }

        switch (view) {
            case TABLE:
                return ViewToggle.TABLE;
            case TILE:
            default:
                return ViewToggle.TILE;
        }
    }

    private RoleCatalogType getRoleCatalogConfiguration() {
        AccessRequestType config = getAccessRequestConfiguration(page);
        RoleCatalogType catalog = null;
        if (config != null) {
            catalog = config.getRoleCatalog();
        }

        return catalog != null ? catalog : new RoleCatalogType();
    }

    private ListGroupMenu<RoleCatalogQueryItem> loadRoleCatalogMenu() {
        RoleCatalogType roleCatalog = getRoleCatalogConfiguration();

        ListGroupMenu<RoleCatalogQueryItem> menu = new ListGroupMenu<>();
        List<ListGroupMenuItem<RoleCatalogQueryItem>> menuItems = menu.getItems();

        ObjectReferenceType ref = roleCatalog.getRoleCatalogRef();
        menuItems.addAll(loadMenuFromOrgTree(ref));

        List<RoleCollectionViewType> collections = roleCatalog.getCollection();
        menuItems.addAll(createMenuFromRoleCollections(collections));

        if (BooleanUtils.isNotFalse(roleCatalog.isShowRolesOfTeammate())) {
            CustomListGroupMenuItem<RoleCatalogQueryItem> rolesOfTeamMate = new CustomListGroupMenuItem<>("RoleCatalogPanel.rolesOfTeammate") {

                @Override
                public Component createMenuItemPanel(String id, IModel<ListGroupMenuItem<RoleCatalogQueryItem>> model,
                        SerializableBiConsumer<AjaxRequestTarget, ListGroupMenuItem<RoleCatalogQueryItem>> onClickHandler) {

                    return new RoleOfTeammateMenuPanel<>(id, model, teammateModel) {

                        @Override
                        protected void onClickPerformed(AjaxRequestTarget target, ListGroupMenuItem item) {
                            onClickHandler.accept(target, item);
                        }

                        @Override
                        protected void onManualSelectionPerformed(AjaxRequestTarget target) {
                            RoleCatalogPanel.this.onManualSelectionPerformed(target, getModelObject());
                        }

                        @Override
                        protected void onSelectionUpdate(AjaxRequestTarget target, ObjectReferenceType newSelection) {
                            onMenuClickPerformed(target, getModelObject());
                        }
                    };
                }
            };
            rolesOfTeamMate.setIconCss("fa-solid fa-user-group");
            RoleCatalogQueryItem rcq = new RoleCatalogQueryItem();
            rcq.rolesOfTeammate(true);
            rolesOfTeamMate.setValue(rcq);

            menu.getItems().add(rolesOfTeamMate);
        }

        return menu;
    }

    private void onManualSelectionPerformed(AjaxRequestTarget target, ListGroupMenuItem<RoleCatalogQueryItem> item) {
        ObjectBrowserPanel<UserType> panel = new ObjectBrowserPanel<>(page.getMainPopupBodyId(), UserType.class,
                List.of(UserType.COMPLEX_TYPE), false, page) {

            private static final long serialVersionUID = 1L;

            @Override
            protected void onSelectPerformed(AjaxRequestTarget target, UserType user) {
                ObjectReferenceType ref = null;
                if (user != null) {
                    ref = new ObjectReferenceType()
                            .oid(user.getOid())
                            .type(UserType.COMPLEX_TYPE)
                            .targetName(WebComponentUtil.getDisplayNameOrName(user.asPrismObject()));
                }

                teammateModel.setObject(ref);

                onMenuClickPerformed(target, item);

                target.add(RoleCatalogPanel.this.get(ID_MENU));

                page.hideMainPopup(target);
            }
        };
        page.showMainPopup(panel, target);
    }

    private List<ListGroupMenuItem<RoleCatalogQueryItem>> createMenuFromRoleCollections(List<RoleCollectionViewType> collections) {
        List<ListGroupMenuItem<RoleCatalogQueryItem>> items = new ArrayList<>();
        boolean defaultFound = false;
        for (RoleCollectionViewType collection : collections) {
            try {
                String name = null;
                RoleCatalogQueryItem rcq = new RoleCatalogQueryItem();
                rcq.collection(collection);

                ObjectReferenceType collectionRef = collection.getCollectionRef();
                if (collectionRef != null) {
                    PrismObject<ObjectCollectionType> objectCollection = WebModelServiceUtils.loadObject(collectionRef, page);

                    name = WebComponentUtil.getDisplayNameOrName(objectCollection, true);
                }

                String collectionIdentifier = collection.getCollectionIdentifier();
                if (StringUtils.isNotEmpty(collectionIdentifier)) {
                    StaticObjectCollection staticCollection = StaticObjectCollection.findCollection(collectionIdentifier);
                    if (staticCollection != null) {
                        name = getString(staticCollection);
                    }
                }

                if (name == null) {
                    continue;
                }

                ListGroupMenuItem<RoleCatalogQueryItem> item = new ListGroupMenuItem<>(name);
                item.setIconCss(GuiStyleConstants.CLASS_OBJECT_COLLECTION_ICON);
                item.setValue(rcq);

                if (!defaultFound && BooleanUtils.isTrue(collection.isDefault())) {
                    item.setActive(true);
                    defaultFound = true;
                }

                items.add(item);
            } catch (Exception ex) {
                LOGGER.debug("Couldn't load object collection as role catalog menu item", ex);
            }
        }

        return items;
    }

    private List<ListGroupMenuItem<RoleCatalogQueryItem>> loadMenuFromOrgTree(ObjectReferenceType ref) {
        return loadMenuFromOrgTree(ref, 1, 3);
    }

    private List<ListGroupMenuItem<RoleCatalogQueryItem>> loadMenuFromOrgTree(ObjectReferenceType ref, int currentLevel, int maxLevel) {
        if (ref == null) {
            return new ArrayList<>();
        }

        if (currentLevel > maxLevel) {
            return new ArrayList<>();
        }

        QName type = ref.getType() != null ? ref.getType() : OrgType.COMPLEX_TYPE;
        ObjectTypes ot = ObjectTypes.getObjectTypeFromTypeQName(type);

        ObjectQuery query = getPrismContext()
                .queryFor(ot.getClassDefinition())
                .isInScopeOf(ref.getOid(), OrgFilter.Scope.ONE_LEVEL)
                .asc(ObjectType.F_NAME)
                .build();

        Task task = page.createSimpleTask(OPERATION_LOAD_ROLE_CATALOG_MENU);
        OperationResult result = task.getResult();

        List<ListGroupMenuItem<RoleCatalogQueryItem>> list = new ArrayList<>();
        try {
            List<PrismObject<ObjectType>> objects = WebModelServiceUtils.searchObjects(ot.getClassDefinition(), query, result, page);
            for (PrismObject o : objects) {
                String name = WebComponentUtil.getDisplayNameOrName(o, true);
                ListGroupMenuItem<RoleCatalogQueryItem> menu = new ListGroupMenuItem<>(name);
                menu.setIconCss(GuiStyleConstants.CLASS_OBJECT_ORG_ICON);
                menu.setValue(new RoleCatalogQueryItem()
                        .orgRef(new ObjectReferenceType().oid(o.getOid()).type(o.getDefinition().getTypeName()))
                        .scopeOne(currentLevel < maxLevel));

                menu.setItemsModel(new LoadableModel<>(false) {
                    @Override
                    protected List<ListGroupMenuItem<RoleCatalogQueryItem>> load() {
                        ObjectReferenceType parentRef = new ObjectReferenceType()
                                .oid(o.getOid())
                                .targetName(o.getName().getOrig())
                                .type(o.getDefinition().getTypeName());

                        return loadMenuFromOrgTree(parentRef, currentLevel + 1, maxLevel);
                    }
                });
                list.add(menu);
            }
        } catch (Exception ex) {
            LOGGER.debug("Couldn't load menu using role catalog reference to org. structure, reason: " + ex.getMessage(), ex);
        }

        return list;
    }

    private IModel<IResource> createImage(IModel<ObjectType> model) {
        return new LoadableModel<>(false) {
            @Override
            protected IResource load() {
                ObjectType object = model.getObject();

                return WebComponentUtil.createJpegPhotoResource((FocusType) object);
            }
        };
    }

    private RoundedIconPanel.State computeCheckState(String roleOid) {
        RequestAccess ra = getModelObject();
        if (ra.isAssignedToAll(roleOid)) {
            return RoundedIconPanel.State.FULL;
        }

        if (ra.isAssignedToNone(roleOid)) {
            return RoundedIconPanel.State.NONE;
        }

        return RoundedIconPanel.State.PARTIAL;
    }

    private String computeCheckTitle(String roleOid) {
        RequestAccess ra = getModelObject();
        if (ra.isAssignedToAll(roleOid)) {
            return getString("RoleCatalogPanel.tileFullCheckState");
        }

        if (ra.isAssignedToNone(roleOid)) {
            return null;
        }

        return getString("RoleCatalogPanel.tilePartialCheckState");
    }

    private List<IColumn<SelectableBean<ObjectType>, String>> createColumns() {
        List<IColumn<SelectableBean<ObjectType>, String>> columns = new ArrayList<>();

        columns.add(new CheckBoxHeaderColumn());
        columns.add(new RoundedIconColumn<>(null) {

            @Override
            protected IModel<IResource> createPreferredImage(IModel<SelectableBean<ObjectType>> model) {
                return RoleCatalogPanel.this.createImage(() -> model.getObject().getValue());
            }

            @Override
            protected DisplayType createDisplayType(IModel<SelectableBean<ObjectType>> model) {
                ObjectType obj = model.getObject().getValue();
                String icon = WebComponentUtil.createDefaultBlackIcon(obj.asPrismContainerValue().getTypeName());

                return new DisplayType()
                        .icon(new IconType()
                                .cssClass(icon));
            }
        });
        columns.add(new AbstractColumn<>(createStringResource("ObjectType.name")) {
            @Override
            public void populateItem(Item<ICellPopulator<SelectableBean<ObjectType>>> item, String id, IModel<SelectableBean<ObjectType>> row) {
                item.add(AttributeAppender.append("class", "align-middle"));
                item.add(new LabelWithCheck(id,
                        () -> WebComponentUtil.getDisplayNameOrName(row.getObject().getValue().asPrismObject()),
                        () -> computeCheckState(row.getObject().getValue().getOid()),
                        () -> computeCheckTitle(row.getObject().getValue().getOid())));
            }
        });
        columns.add(new PropertyColumn(createStringResource("ObjectType.description"), "value.description"));

        columns.add(new AbstractColumn<>(null) {

            @Override
            public void populateItem(Item<ICellPopulator<SelectableBean<ObjectType>>> item, String id, IModel<SelectableBean<ObjectType>> model) {
                item.add(new AjaxLinkPanel(id, createStringResource("RoleCatalogPanel.details")) {

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        itemDetailsPerformed(target, model.getObject().getValue());
                    }
                });
            }
        });

        return columns;
    }

    private ContainerPanelConfigurationType createDefaultContainerPanelConfiguration(QName type) {
        ContainerPanelConfigurationType c = new ContainerPanelConfigurationType();
        c.identifier("sample-panel");
        c.type(type);
        c.panelType("formPanel");
        VirtualContainersSpecificationType vcs =
                c.beginContainer()
                        .beginDisplay()
                        .label("RoleCatalogPanel.details")
                        .end();
        vcs.identifier("sample-container");
        vcs.beginItem().path(new ItemPathType(ItemPath.create(ObjectType.F_DESCRIPTION))).end();

        return c;
    }

    private void itemDetailsPerformed(AjaxRequestTarget target, ObjectType object) {
        QName type = ObjectTypes.getObjectType(object.getClass()).getTypeQName();

        ContainerPanelConfigurationType config;

        ListGroupMenuItem<RoleCatalogQueryItem> selectedMenu = menuModel.getObject().getActiveMenu();

        RoleCatalogQueryItem item = selectedMenu != null ? selectedMenu.getValue() : null;
        if (item == null || item.collection() == null) {
            config = createDefaultContainerPanelConfiguration(type);
        } else {
            RoleCollectionViewType view = item.collection();
            config = view.getDetails() != null ? view.getDetails() : createDefaultContainerPanelConfiguration(type);
        }

        List<ContainerPanelConfigurationType> finalConfig = new ArrayList<>();
        if (!config.getPanel().isEmpty()) {
            finalConfig.addAll(config.getPanel());
        } else {
            finalConfig.add(config);
        }

        CatalogItemDetailsPanel panel = new CatalogItemDetailsPanel(() -> finalConfig, Model.of(object)) {

            @Override
            protected void addPerformed(AjaxRequestTarget target, IModel<ObjectType> model) {
                addItemsPerformed(target, List.of(model.getObject()));

                page.getMainPopup().close(target);
            }

            @Override
            protected void closePerformed(AjaxRequestTarget target, IModel<ObjectType> model) {
                page.getMainPopup().close(target);
            }
        };

        page.showMainPopup(panel, target);
    }

    private void addAllItemsPerformed(AjaxRequestTarget target) {
        TileTablePanel tiles = (TileTablePanel) get(ID_TILES);
        ObjectDataProvider provider = (ObjectDataProvider) tiles.getProvider();
        List<SelectableBean<ObjectType>> objects = provider.getAvailableData();

        if (objects == null) {
            page.warn(getString("RoleCatalogPanel.noItemsAvailable"));
            target.add(page.getFeedbackPanel());
            return;
        }

        List<ObjectType> items = objects.stream().map(s -> s.getValue()).collect(Collectors.toList());

        addItemsPerformed(target, items);
    }

    private AssignmentType createNewAssignment(ObjectType object, QName relation) {
        AssignmentType a = new AssignmentType();
        ObjectReferenceType targetRef = new ObjectReferenceType()
                .targetName(WebComponentUtil.getDisplayNameOrName(object.asPrismObject()))
                .type(ObjectTypes.getObjectType(object.getClass()).getTypeQName())
                .oid(object.getOid())
                .relation(relation);
        a.targetRef(targetRef);

        return a;
    }

    private void addItemsPerformed(AjaxRequestTarget target, List<ObjectType> selected) {
        if (CollectionUtils.isEmpty(selected)) {
            new Toast()
                    .info()
                    .title(getString("RoleCatalogPanel.message.noItemAdded"))
                    .icon("fas fa-cart-shopping")
                    .autohide(true)
                    .delay(5_000)
                    .body(getString("RoleCatalogPanel.message.selectItemToBeAdded")).show(target);
            return;
        }
        RequestAccess requestAccess = getModelObject();
        QName relation = requestAccess.getRelation();

        List<AssignmentType> newAssignments = selected.stream().map(o -> createNewAssignment(o, relation)).collect(Collectors.toList());
        requestAccess.addAssignments(newAssignments);

        getPageBase().reloadShoppingCartIcon(target);
        target.add(getWizard().getHeader());
        target.add(get(ID_TILES));

        String msg;
        if (selected.size() > 1) {
            msg = getString("RoleCatalogPanel.multipleAdded", selected.size());
        } else {
            String name = WebComponentUtil.getDisplayNameOrName(selected.get(0).asPrismObject());
            msg = getString("RoleCatalogPanel.singleAdded",
                    Strings.escapeMarkup(name, false, true));
        }

        new Toast()
                .success()
                .title(getString("RoleCatalogPanel.itemAdded"))
                .icon("fas fa-cart-shopping")
                .autohide(true)
                .delay(5_000)
                .body(msg).show(target);
    }

    @Override
    public VisibleEnableBehaviour getNextBehaviour() {
        return new VisibleEnableBehaviour(() -> !getModelObject().getShoppingCartAssignments().isEmpty());
    }
}
