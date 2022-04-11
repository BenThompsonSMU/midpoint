/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.api.correlator;

import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.util.DebugUtil;

import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceObjectOwnerOptionType;

import java.util.List;

/**
 * Here can the user script in the correlator request the manual resolution. (Plus provide additional information.)
 *
 * TODO Future of this class is uncertain.
 */
public class ManualCorrelationContext implements DebugDumpable {

    /**
     * Was the manual correlation requested by the user code?
     */
    private boolean requested;

    /**
     * Explicit list of potential matches provided by the user code.
     * TODO
     */
    private List<ResourceObjectOwnerOptionType> potentialMatches;

    public boolean isRequested() {
        return requested;
    }

    public void setRequested(boolean requested) {
        this.requested = requested;
    }

    public List<ResourceObjectOwnerOptionType> getPotentialMatches() {
        return potentialMatches;
    }

    public void setPotentialMatches(List<ResourceObjectOwnerOptionType> potentialMatches) {
        this.potentialMatches = potentialMatches;
    }

    @Override
    public String debugDump(int indent) {
        StringBuilder sb = DebugUtil.createTitleStringBuilderLn(getClass(), indent);
        DebugUtil.debugDumpWithLabel(sb, "requested", requested, indent + 1);
        return sb.toString();
    }
}
