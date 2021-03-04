/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmodel.object;

import java.time.Instant;
import java.util.UUID;

import com.evolveum.midpoint.prism.polystring.PolyString;

/**
 * Querydsl "row bean" type related to {@link QObject}.
 */
public class MObject {

    public UUID oid;
    // objectType is read-only, it must be null before insert/updates of the whole M-bean
    public Integer objectType;
    public String nameNorm;
    public String nameOrig;
    public byte[] fullObject;
    public UUID tenantRefTargetOid;
    public Integer tenantRefTargetType;
    public Integer tenantRefRelationId;
    public String lifecycleState;
    public Integer version;
    public byte[] ext;
    // metadata
    public UUID creatorRefTargetOid;
    public Integer creatorRefTargetType;
    public Integer creatorRefRelationId;
    public Integer createChannelId;
    public Instant createTimestamp;
    public UUID modifierRefTargetOid;
    public Integer modifierRefTargetType;
    public Integer modifierRefRelationId;
    public Integer modifyChannelId;
    public Instant modifyTimestamp;

    public PolyString getName() {
        return new PolyString(nameOrig, nameNorm);
    }
}
