/*
 * Copyright (c) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.web.page.admin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.gui.api.util.GuiDisplayTypeUtil;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.util.string.StringValue;

import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIcon;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIconBuilder;
import com.evolveum.midpoint.gui.impl.component.icon.IconCssStyle;
import com.evolveum.midpoint.gui.impl.util.ObjectCollectionViewUtil;
import com.evolveum.midpoint.model.api.AssignmentObjectRelation;
import com.evolveum.midpoint.model.api.authentication.CompiledObjectCollectionView;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.authentication.api.authorization.AuthorizationAction;
import com.evolveum.midpoint.authentication.api.authorization.PageDescriptor;
import com.evolveum.midpoint.authentication.api.authorization.Url;
import com.evolveum.midpoint.web.component.CompositedIconButtonDto;
import com.evolveum.midpoint.web.component.MultiCompositedButtonPanel;
import com.evolveum.midpoint.web.component.MultiFunctinalButtonDto;
import com.evolveum.midpoint.web.util.OnePageParameterEncoder;

@PageDescriptor(
        urls = {
                @Url(mountUrl = "/admin/template", matchUrlForSecurity = "/admin/template")
        },
        encoder = OnePageParameterEncoder.class,
        action = {
                @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_USERS_ALL_URL,
                        label = "PageAdminUsers.auth.usersAll.label",
                        description = "PageAdminUsers.auth.usersAll.description"),
                @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_USER_URL,
                        label = "PageUser.auth.user.label",
                        description = "PageUser.auth.user.description")
        })
public class PageCreateFromTemplate extends PageAdmin {

    private static final String ID_TEMPLATE = "template";

    public PageCreateFromTemplate(PageParameters pageParameters) {
        super(pageParameters);
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        initLayout();
    }

    @Override
    protected IModel<String> createPageTitleModel() {
        return createStringResource("PageCreateFromTemplate." + getType().getLocalPart() + ".title");
    }

    private void initLayout() {
        MultiCompositedButtonPanel buttonsPanel = new MultiCompositedButtonPanel(ID_TEMPLATE, new PropertyModel<>(loadButtonDescriptions(), MultiFunctinalButtonDto.F_ADDITIONAL_BUTTONS)) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void buttonClickPerformed(AjaxRequestTarget target, AssignmentObjectRelation relationSpec, CompiledObjectCollectionView collectionViews, Class<? extends WebPage> page) {
                List<ObjectReferenceType> archetypeRef = ObjectCollectionViewUtil.getArchetypeReferencesList(collectionViews);
                try {
                    WebComponentUtil.initNewObjectWithReference(getPageBase(),
                            getType(),
                            archetypeRef);
                } catch (SchemaException ex) {
                    getPageBase().getFeedbackMessages().error(PageCreateFromTemplate.this, ex.getUserFriendlyMessage());
                    target.add(getPageBase().getFeedbackPanel());
                }
            }

        };
        add(buttonsPanel);
    }

    protected LoadableModel<MultiFunctinalButtonDto> loadButtonDescriptions() {
        return new LoadableModel<>(false) {

            @Override
            protected MultiFunctinalButtonDto load() {
                List<CompositedIconButtonDto> additionalButtons = new ArrayList<>();

                Collection<CompiledObjectCollectionView> compiledObjectCollectionViews = getCompiledGuiProfile().findAllApplicableArchetypeViews(getType(), OperationTypeType.ADD);

                if (CollectionUtils.isNotEmpty(compiledObjectCollectionViews)) {
                    compiledObjectCollectionViews.forEach(collection -> {
                        CompositedIconButtonDto buttonDesc = new CompositedIconButtonDto();
                        buttonDesc.setCompositedIcon(createCompositedIcon(collection));
                        buttonDesc.setOrCreateDefaultAdditionalButtonDisplayType(collection.getDisplay());
                        buttonDesc.setCollectionView(collection);
                        additionalButtons.add(buttonDesc);
                    });
                }

                if (isGenericNewButtonVisible()) {
                    CompositedIconButtonDto defaultButton = new CompositedIconButtonDto();
                    DisplayType defaultButtonDisplayType = getDefaultButtonDisplayType();
                    defaultButton.setAdditionalButtonDisplayType(defaultButtonDisplayType);

                    CompositedIconBuilder defaultButtonIconBuilder = new CompositedIconBuilder();
                    defaultButtonIconBuilder.setBasicIcon(WebComponentUtil.getIconCssClass(defaultButtonDisplayType), IconCssStyle.IN_ROW_STYLE)
                            .appendColorHtmlValue(WebComponentUtil.getIconColor(defaultButtonDisplayType));

                    defaultButton.setCompositedIcon(defaultButtonIconBuilder.build());
                    additionalButtons.add(defaultButton);
                }

                MultiFunctinalButtonDto multifunctionalButton = new MultiFunctinalButtonDto();
                multifunctionalButton.setAdditionalButtons(additionalButtons);
                return multifunctionalButton;
            }
        };

    }

    //TODO copied from MainObjectListPanel
    private CompositedIcon createCompositedIcon(CompiledObjectCollectionView collectionView) {
        DisplayType additionalButtonDisplayType = GuiDisplayTypeUtil.getNewObjectDisplayTypeFromCollectionView(collectionView, PageCreateFromTemplate.this);
        CompositedIconBuilder builder = new CompositedIconBuilder();

        builder.setBasicIcon(WebComponentUtil.getIconCssClass(additionalButtonDisplayType), IconCssStyle.IN_ROW_STYLE)
                .appendColorHtmlValue(WebComponentUtil.getIconColor(additionalButtonDisplayType));

        return builder.build();
    }

    private DisplayType getDefaultButtonDisplayType() {
        String iconCssStyle = WebComponentUtil.createDefaultBlackIcon(getType());

        String sb = createStringResource("MainObjectListPanel.newObject").getString()
                + " "
                + createStringResource("ObjectTypeLowercase." + getType().getLocalPart()).getString();
        DisplayType display = GuiDisplayTypeUtil.createDisplayType(iconCssStyle, "", sb);
        display.setLabel(WebComponentUtil.createPolyFromOrigString(
                getType().getLocalPart(), "ObjectType." + getType().getLocalPart()));
        return display;
    }

    protected boolean isGenericNewButtonVisible() {
        if (QNameUtil.match(ReportType.COMPLEX_TYPE, getType())) {
            return false;
        }
        return true;
    }

    private QName getType() {
        StringValue restType = getPageParameters().get("type");
        if (restType == null || restType.toString() == null) {
            throw redirectBackViaRestartResponseException();
        }
        return ObjectTypes.getTypeQNameFromRestType(restType.toString());
    }
}
