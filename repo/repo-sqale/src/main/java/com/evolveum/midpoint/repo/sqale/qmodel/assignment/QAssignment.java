/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmodel.assignment;

import java.sql.Types;
import java.time.Instant;

import com.querydsl.core.types.dsl.ArrayPath;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.ColumnMetadata;

import com.evolveum.midpoint.repo.sqale.qmodel.common.QContainer;
import com.evolveum.midpoint.repo.sqlbase.querydsl.UuidPath;

/**
 * Querydsl query type for {@value #TABLE_NAME} table.
 */
@SuppressWarnings("unused")
public class QAssignment extends QContainer<MAssignment> {

    private static final long serialVersionUID = 7068031681581618788L;

    public static final String TABLE_NAME = "m_assignment";

    public static final ColumnMetadata OWNER_TYPE =
            ColumnMetadata.named("owner_type").ofType(Types.INTEGER);
    public static final ColumnMetadata ASSIGNMENT_OWNER =
            ColumnMetadata.named("assignmentOwner").ofType(Types.INTEGER);
    public static final ColumnMetadata LIFECYCLE_STATE =
            ColumnMetadata.named("lifecycleState").ofType(Types.VARCHAR).withSize(255);
    public static final ColumnMetadata ORDER_VALUE =
            ColumnMetadata.named("orderValue").ofType(Types.INTEGER);
    public static final ColumnMetadata ORG_REF_TARGET_OID =
            ColumnMetadata.named("orgRef_targetOid").ofType(UuidPath.UUID_TYPE);
    public static final ColumnMetadata ORG_REF_TARGET_TYPE =
            ColumnMetadata.named("orgRef_targetType").ofType(Types.INTEGER);
    public static final ColumnMetadata ORG_REF_RELATION_ID =
            ColumnMetadata.named("orgRef_relation_id").ofType(Types.INTEGER);
    public static final ColumnMetadata TARGET_REF_TARGET_OID =
            ColumnMetadata.named("targetRef_targetOid").ofType(UuidPath.UUID_TYPE);
    public static final ColumnMetadata TARGET_REF_TARGET_TYPE =
            ColumnMetadata.named("targetRef_targetType").ofType(Types.INTEGER);
    public static final ColumnMetadata TARGET_REF_RELATION_ID =
            ColumnMetadata.named("targetRef_relation_id").ofType(Types.INTEGER);
    public static final ColumnMetadata TENANT_REF_TARGET_OID =
            ColumnMetadata.named("tenantRef_targetOid").ofType(UuidPath.UUID_TYPE);
    public static final ColumnMetadata TENANT_REF_TARGET_TYPE =
            ColumnMetadata.named("tenantRef_targetType").ofType(Types.INTEGER);
    public static final ColumnMetadata TENANT_REF_RELATION_ID =
            ColumnMetadata.named("tenantRef_relation_id").ofType(Types.INTEGER);
    public static final ColumnMetadata EXT_ID =
            ColumnMetadata.named("extId").ofType(Types.INTEGER);
    // TODO UUID or not? our control or outside?
    public static final ColumnMetadata EXT_OID =
            ColumnMetadata.named("extOid").ofType(Types.VARCHAR).withSize(36);
    public static final ColumnMetadata EXT =
            ColumnMetadata.named("ext").ofType(Types.BINARY);
    // construction columns
    public static final ColumnMetadata RESOURCE_REF_TARGET_OID =
            ColumnMetadata.named("resourceRef_targetOid").ofType(UuidPath.UUID_TYPE);
    public static final ColumnMetadata RESOURCE_REF_TARGET_TYPE =
            ColumnMetadata.named("resourceRef_targetType").ofType(Types.INTEGER);
    public static final ColumnMetadata RESOURCE_REF_RELATION_ID =
            ColumnMetadata.named("resourceRef_relation_id").ofType(Types.INTEGER);
    // activation columns
    public static final ColumnMetadata ADMINISTRATIVE_STATUS =
            ColumnMetadata.named("administrativeStatus").ofType(Types.INTEGER);
    public static final ColumnMetadata EFFECTIVE_STATUS =
            ColumnMetadata.named("effectiveStatus").ofType(Types.INTEGER);
    public static final ColumnMetadata ENABLE_TIMESTAMP =
            ColumnMetadata.named("enableTimestamp").ofType(Types.TIMESTAMP_WITH_TIMEZONE);
    public static final ColumnMetadata DISABLE_TIMESTAMP =
            ColumnMetadata.named("disableTimestamp").ofType(Types.TIMESTAMP_WITH_TIMEZONE);
    public static final ColumnMetadata DISABLE_REASON =
            ColumnMetadata.named("disableReason").ofType(Types.VARCHAR).withSize(255);
    public static final ColumnMetadata VALIDITY_STATUS =
            ColumnMetadata.named("validityStatus").ofType(Types.INTEGER);
    public static final ColumnMetadata VALID_FROM =
            ColumnMetadata.named("validFrom").ofType(Types.TIMESTAMP_WITH_TIMEZONE);
    public static final ColumnMetadata VALID_TO =
            ColumnMetadata.named("validTo").ofType(Types.TIMESTAMP_WITH_TIMEZONE);
    public static final ColumnMetadata VALIDITY_CHANGE_TIMESTAMP =
            ColumnMetadata.named("validityChangeTimestamp").ofType(Types.TIMESTAMP_WITH_TIMEZONE);
    public static final ColumnMetadata ARCHIVE_TIMESTAMP =
            ColumnMetadata.named("archiveTimestamp").ofType(Types.TIMESTAMP_WITH_TIMEZONE);
    // metadata columns
    public static final ColumnMetadata CREATOR_REF_TARGET_OID =
            ColumnMetadata.named("creatorRef_targetOid").ofType(UuidPath.UUID_TYPE);
    public static final ColumnMetadata CREATOR_REF_TARGET_TYPE =
            ColumnMetadata.named("creatorRef_targetType").ofType(Types.INTEGER);
    public static final ColumnMetadata CREATOR_REF_RELATION_ID =
            ColumnMetadata.named("creatorRef_relation_id").ofType(Types.INTEGER);
    public static final ColumnMetadata CREATE_CHANNEL_ID =
            ColumnMetadata.named("createChannel_id").ofType(Types.INTEGER);
    public static final ColumnMetadata CREATE_TIMESTAMP =
            ColumnMetadata.named("createTimestamp").ofType(Types.TIMESTAMP_WITH_TIMEZONE);
    public static final ColumnMetadata MODIFIER_REF_TARGET_OID =
            ColumnMetadata.named("modifierRef_targetOid").ofType(UuidPath.UUID_TYPE);
    public static final ColumnMetadata MODIFIER_REF_TARGET_TYPE =
            ColumnMetadata.named("modifierRef_targetType").ofType(Types.INTEGER);
    public static final ColumnMetadata MODIFIER_REF_RELATION_ID =
            ColumnMetadata.named("modifierRef_relation_id").ofType(Types.INTEGER);
    public static final ColumnMetadata MODIFY_CHANNEL_ID =
            ColumnMetadata.named("modifyChannel_id").ofType(Types.INTEGER);
    public static final ColumnMetadata MODIFY_TIMESTAMP =
            ColumnMetadata.named("modifyTimestamp").ofType(Types.TIMESTAMP_WITH_TIMEZONE);

    // attributes

    public final NumberPath<Integer> ownerType = createInteger("ownerType", OWNER_TYPE);
    public final NumberPath<Integer> assignmentOwner =
            createInteger("assignmentOwner", ASSIGNMENT_OWNER);
    public final StringPath lifecycleState = createString("lifecycleState", LIFECYCLE_STATE);
    public final NumberPath<Integer> orderValue = createInteger("orderValue", ORDER_VALUE);
    public final UuidPath orgRefTargetOid =
            createUuid("orgRefTargetOid", ORG_REF_TARGET_OID);
    public final NumberPath<Integer> orgRefTargetType =
            createInteger("orgRefTargetType", ORG_REF_TARGET_TYPE);
    public final NumberPath<Integer> orgRefRelationId =
            createInteger("orgRefRelationId", ORG_REF_RELATION_ID);
    public final UuidPath targetRefTargetOid =
            createUuid("targetRefTargetOid", TARGET_REF_TARGET_OID);
    public final NumberPath<Integer> targetRefTargetType =
            createInteger("targetRefTargetType", TARGET_REF_TARGET_TYPE);
    public final NumberPath<Integer> targetRefRelationId =
            createInteger("targetRefRelationId", TARGET_REF_RELATION_ID);
    public final UuidPath tenantRefTargetOid =
            createUuid("tenantRefTargetOid", TENANT_REF_TARGET_OID);
    public final NumberPath<Integer> tenantRefTargetType =
            createInteger("tenantRefTargetType", TENANT_REF_TARGET_TYPE);
    public final NumberPath<Integer> tenantRefRelationId =
            createInteger("tenantRefRelationId", TENANT_REF_RELATION_ID);
    public final NumberPath<Integer> extId = createInteger("extId", EXT_ID);
    public final StringPath extOid = createString("extOid", EXT_OID);
    public final ArrayPath<byte[], Byte> ext = createByteArray("ext", EXT); // TODO is byte[] the right type?
    // construction attributes
    public final UuidPath resourceRefTargetOid =
            createUuid("resourceRefTargetOid", RESOURCE_REF_TARGET_OID);
    public final NumberPath<Integer> resourceRefTargetType =
            createInteger("resourceRefTargetType", RESOURCE_REF_TARGET_TYPE);
    public final NumberPath<Integer> resourceRefRelationId =
            createInteger("resourceRefRelationId", RESOURCE_REF_RELATION_ID);
    // activation attributes
    public final NumberPath<Integer> administrativeStatus =
            createInteger("administrativeStatus", ADMINISTRATIVE_STATUS);
    public final NumberPath<Integer> effectiveStatus =
            createInteger("effectiveStatus", EFFECTIVE_STATUS);
    public final DateTimePath<Instant> enableTimestamp =
            createInstant("enableTimestamp", ENABLE_TIMESTAMP);
    public final DateTimePath<Instant> disableTimestamp =
            createInstant("disableTimestamp", DISABLE_TIMESTAMP);
    public final StringPath disableReason = createString("disableReason", DISABLE_REASON);
    public final NumberPath<Integer> validityStatus =
            createInteger("validityStatus", VALIDITY_STATUS);
    public final DateTimePath<Instant> validFrom = createInstant("validFrom", VALID_FROM);
    public final DateTimePath<Instant> validTo = createInstant("validTo", VALID_TO);
    public final DateTimePath<Instant> validityChangeTimestamp =
            createInstant("validityChangeTimestamp", VALIDITY_CHANGE_TIMESTAMP);
    public final DateTimePath<Instant> archiveTimestamp =
            createInstant("archiveTimestamp", ARCHIVE_TIMESTAMP);
    // metadata attributes
    public final UuidPath creatorRefTargetOid =
            createUuid("creatorRefTargetOid", CREATOR_REF_TARGET_OID);
    public final NumberPath<Integer> creatorRefTargetType =
            createInteger("creatorRefTargetType", CREATOR_REF_TARGET_TYPE);
    public final NumberPath<Integer> creatorRefRelationId =
            createInteger("creatorRefRelationId", CREATOR_REF_RELATION_ID);
    public final NumberPath<Integer> createChannelId =
            createInteger("createChannelId", CREATE_CHANNEL_ID);
    public final DateTimePath<Instant> createTimestamp =
            createInstant("createTimestamp", CREATE_TIMESTAMP);
    public final UuidPath modifierRefTargetOid =
            createUuid("modifierRefTargetOid", MODIFIER_REF_TARGET_OID);
    public final NumberPath<Integer> modifierRefTargetType =
            createInteger("modifierRefTargetType", MODIFIER_REF_TARGET_TYPE);
    public final NumberPath<Integer> modifierRefRelationId =
            createInteger("modifierRefRelationId", MODIFIER_REF_RELATION_ID);
    public final NumberPath<Integer> modifyChannelId =
            createInteger("modifyChannelId", MODIFY_CHANNEL_ID);
    public final DateTimePath<Instant> modifyTimestamp =
            createInstant("modifyTimestamp", MODIFY_TIMESTAMP);

    public QAssignment(String variable) {
        this(variable, DEFAULT_SCHEMA_NAME, TABLE_NAME);
    }

    public QAssignment(String variable, String schema, String table) {
        super(MAssignment.class, variable, schema, table);
    }
}
