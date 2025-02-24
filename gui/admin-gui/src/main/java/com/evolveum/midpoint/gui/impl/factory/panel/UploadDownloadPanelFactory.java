/*
 * Copyright (C) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.factory.panel;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import jakarta.annotation.PostConstruct;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.prism.wrapper.ItemWrapper;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.web.component.input.UploadDownloadPanel;
import com.evolveum.midpoint.web.component.prism.InputPanel;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;

/**
 * @author katkav
 */
//FIXME serializable
@Component
public class UploadDownloadPanelFactory<T> extends AbstractInputGuiComponentFactory<T> implements Serializable {

    @PostConstruct
    public void register() {
        getRegistry().addToRegistry(this);
    }

    @Override
    public <IW extends ItemWrapper<?, ?>> boolean match(IW wrapper) {
        return DOMUtil.XSD_BASE64BINARY.equals(wrapper.getTypeName());
    }

    @Override
    protected InputPanel getPanel(PrismPropertyPanelContext<T> panelCtx) {
        UploadDownloadPanel panel = new UploadDownloadPanel(panelCtx.getComponentId(), false) { //getModelService().getObject().isReadonly()

            private static final long serialVersionUID = 1L;

            @Override
            public InputStream getStream() {
                T object = panelCtx.getRealValueModel().getObject();
                if (object instanceof String) {
                    return new ByteArrayInputStream(((String) object).getBytes());
                }
                return object != null ? new ByteArrayInputStream((byte[]) object) : new ByteArrayInputStream(new byte[0]);
            }

            @Override
            public void updateValue(byte[] file) {
                panelCtx.getRealValueModel().setObject((T) file);
            }

            @Override
            public void uploadFileFailed(AjaxRequestTarget target) {
                super.uploadFileFailed(target);
                target.add(((PageBase) getPage()).getFeedbackPanel());
            }

            @Override
            public List<String> getAllowedUploadContentTypes() {
                ItemPath path = panelCtx.getValueWrapperModel().getObject().getParent().getPath();

                if (Objects.equals(path, ItemPath.create(FocusType.F_JPEG_PHOTO))) {
                    return Arrays.asList("image/*");
                }

                return super.getAllowedUploadContentTypes();
            }
        };

        return panel;
    }
}
