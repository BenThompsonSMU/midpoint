/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.web.page.admin.reports;

import com.evolveum.midpoint.authentication.api.authorization.AuthorizationAction;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.authentication.api.authorization.PageDescriptor;
import com.evolveum.midpoint.authentication.api.authorization.Url;
import com.evolveum.midpoint.authentication.api.util.AuthConstants;
import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.component.MainObjectListPanel;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIconBuilder;
import com.evolveum.midpoint.gui.impl.page.admin.report.PageReport;
import com.evolveum.midpoint.web.application.*;
import com.evolveum.midpoint.web.component.data.column.ColumnMenuAction;
import com.evolveum.midpoint.web.component.data.column.ColumnUtils;
import com.evolveum.midpoint.web.component.form.MidpointForm;
import com.evolveum.midpoint.web.component.menu.cog.ButtonInlineMenuItem;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItemAction;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.component.util.SelectableBeanImpl;
import com.evolveum.midpoint.web.page.admin.PageAdmin;
import com.evolveum.midpoint.web.session.UserProfileStorage;
import com.evolveum.midpoint.web.util.OnePageParameterEncoder;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import java.util.ArrayList;
import java.util.List;


/**
 * @author lazyman
 */
@PageDescriptor(
        urls = {
            @Url(mountUrl = "/admin/reports", matchUrlForSecurity = "/admin/reports")
        },
        action = {
        @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_REPORTS_ALL_URL,
                label = AuthConstants.AUTH_CONFIGURATION_ALL_LABEL,
                description = AuthConstants.AUTH_CONFIGURATION_ALL_DESCRIPTION),
        @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_REPORTS_URL,
                label = "PageReports.auth.reports.label",
                description = "PageReports.auth.reports.description")})
@CollectionInstance(identifier = "allReports", applicableForType = ReportType.class, applicableForOperation = OperationTypeType.MODIFY,
        display = @PanelDisplay(label = "PageAdmin.menu.top.reports.list", singularLabel = "ObjectType.report", icon = GuiStyleConstants.CLASS_REPORT_ICON))
public class PageReports extends PageAdmin {
    private static final long serialVersionUID = 1L;

    private static final String ID_MAIN_FORM = "mainForm";
    private static final String ID_TABLE = "table";

    public PageReports() {
        super();
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        initLayout();
    }

    private void initLayout() {
        Form mainForm = new MidpointForm(ID_MAIN_FORM);
        add(mainForm);

        MainObjectListPanel<ReportType> table = new MainObjectListPanel<ReportType>(ID_TABLE, ReportType.class) {
            private static final long serialVersionUID = 1L;

            @Override
            protected UserProfileStorage.TableId getTableId() {
                return UserProfileStorage.TableId.PAGE_REPORTS;
            }

            @Override
            protected List<IColumn<SelectableBean<ReportType>, String>> createDefaultColumns() {
                return ColumnUtils.getDefaultObjectColumns();
            }

            @Override
            protected IColumn<SelectableBean<ReportType>, String> createCheckboxColumn() {
                return null;
            }

            @Override
            protected List<InlineMenuItem> createInlineMenu() {
                return PageReports.this.createInlineMenu();
            }

        };
        table.setOutputMarkupId(true);
        mainForm.add(table);

    }

    private List<InlineMenuItem> createInlineMenu(){
        List<InlineMenuItem> menu = new ArrayList<>();
        ButtonInlineMenuItem runButton = new ButtonInlineMenuItem(createStringResource("PageReports.button.run")) {
            private static final long serialVersionUID = 1L;

            @Override
            public InlineMenuItemAction initAction() {
                return new ColumnMenuAction<SelectableBeanImpl<ReportType>>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        ReportType report = getRowModel().getObject().getValue();
                        com.evolveum.midpoint.web.page.admin.reports.PageReport.runReportPerformed(
                                target, report.asPrismObject(), PageReports.this);
                    }
                };
            }

            @Override
            public CompositedIconBuilder getIconCompositedBuilder() {
                return getDefaultCompositedIconBuilder(GuiStyleConstants.CLASS_START_MENU_ITEM);
            }

            @Override
            public boolean isHeaderMenuItem() {
                return false;
            }
        };

        runButton.setVisibilityChecker((rowModel, isHeader) -> !isImportReport((IModel<SelectableBean<ReportType>>)rowModel));

        menu.add(runButton);

        ButtonInlineMenuItem importButton = new ButtonInlineMenuItem(createStringResource("PageReports.button.import")) {
            private static final long serialVersionUID = 1L;

            @Override
            public InlineMenuItemAction initAction() {
                return new ColumnMenuAction<SelectableBeanImpl<ReportType>>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        ReportType report = getRowModel().getObject().getValue();
                        com.evolveum.midpoint.web.page.admin.reports.PageReport.importReportPerformed(
                                target, report.asPrismObject(), PageReports.this);
                    }
                };
            }

            @Override
            public CompositedIconBuilder getIconCompositedBuilder() {
                return getDefaultCompositedIconBuilder(GuiStyleConstants.CLASS_UPLOAD);
            }

            @Override
            public boolean isHeaderMenuItem() {
                return false;
            }
        };

        importButton.setVisibilityChecker((rowModel, isHeader) -> isImportReport((IModel<SelectableBean<ReportType>>)rowModel));

        menu.add(importButton);
        menu.add(new ButtonInlineMenuItem(createStringResource("PageReports.button.configure")) {
            private static final long serialVersionUID = 1L;

            @Override
            public InlineMenuItemAction initAction() {
                return new ColumnMenuAction<SelectableBeanImpl<ReportType>>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        ReportType reportObject = getRowModel().getObject().getValue();
                        configurePerformed(target, reportObject);
                    }
                };
            }

            @Override
            public CompositedIconBuilder getIconCompositedBuilder(){
                return getDefaultCompositedIconBuilder(GuiStyleConstants.CLASS_EDIT_MENU_ITEM);
            }

            @Override
            public boolean isHeaderMenuItem(){
                return false;
            }
        });
        menu.add(new ButtonInlineMenuItem(createStringResource("PageReports.button.showOutput")) {
            private static final long serialVersionUID = 1L;

            @Override
            public InlineMenuItemAction initAction() {
                return new ColumnMenuAction<SelectableBeanImpl<ReportType>>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        ReportType reportObject = getRowModel().getObject().getValue();
                        PageParameters pageParameters = new PageParameters();
                        pageParameters.add(OnePageParameterEncoder.PARAMETER, reportObject.getOid());
                        navigateToNext(PageCreatedReports.class, pageParameters);
                    }
                };
            }

            @Override
            public CompositedIconBuilder getIconCompositedBuilder(){
                return getDefaultCompositedIconBuilder("fa fa-files-o");
            }

            @Override
            public boolean isHeaderMenuItem(){
                return false;
            }
        });
        return menu;
    }

    private boolean isImportReport(IModel<SelectableBean<ReportType>> rowModel) {
        ReportType report = rowModel.getObject().getValue();
        return WebComponentUtil.isImportReport(report);
    }

    private void configurePerformed(AjaxRequestTarget target, ReportType report) {
        PageParameters params = new PageParameters();
        params.add(OnePageParameterEncoder.PARAMETER, report.getOid());
        navigateToNext(PageReport.class, params);
    }
}
