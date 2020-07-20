/*
 * Copyright (C) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sql.pure.querymodel;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;

import java.sql.Types;
import java.time.Instant;

import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.ColumnMetadata;
import com.querydsl.sql.ForeignKey;
import com.querydsl.sql.PrimaryKey;

import com.evolveum.midpoint.repo.sql.pure.FlexibleRelationalPathBase;
import com.evolveum.midpoint.repo.sql.pure.querymodel.beans.MAuditDelta;
import com.evolveum.midpoint.repo.sql.pure.querymodel.beans.MAuditEventRecord;

/**
 * Querydsl query type for M_AUDIT_EVENT table.
 */
@SuppressWarnings("unused")
public class QAuditEventRecord extends FlexibleRelationalPathBase<MAuditEventRecord> {

    private static final long serialVersionUID = -229589301;

    public static final String TABLE_NAME = "m_audit_event";

    // column metadata constants, we don't care about the indexes, better to remove them
    public static final ColumnMetadata ID =
            ColumnMetadata.named("ID").ofType(Types.BIGINT).withSize(19).notNull();
    public static final ColumnMetadata CHANNEL =
            ColumnMetadata.named("CHANNEL").ofType(Types.VARCHAR).withSize(255);
    public static final ColumnMetadata ATTORNEY_NAME =
            ColumnMetadata.named("ATTORNEYNAME").ofType(Types.VARCHAR).withSize(255);
    public static final ColumnMetadata ATTORNEY_OID =
            ColumnMetadata.named("ATTORNEYOID").ofType(Types.VARCHAR).withSize(36);
    public static final ColumnMetadata EVENT_IDENTIFIER =
            ColumnMetadata.named("EVENTIDENTIFIER").ofType(Types.VARCHAR).withSize(255);
    public static final ColumnMetadata EVENT_STAGE =
            ColumnMetadata.named("EVENTSTAGE").ofType(Types.INTEGER).withSize(10);
    public static final ColumnMetadata EVENT_TYPE =
            ColumnMetadata.named("EVENTTYPE").ofType(Types.INTEGER).withSize(10);
    public static final ColumnMetadata HOST_IDENTIFIER =
            ColumnMetadata.named("HOSTIDENTIFIER").ofType(Types.VARCHAR).withSize(255);
    public static final ColumnMetadata INITIATOR_NAME =
            ColumnMetadata.named("INITIATORNAME").ofType(Types.VARCHAR).withSize(255);
    public static final ColumnMetadata INITIATOR_OID =
            ColumnMetadata.named("INITIATOROID").ofType(Types.VARCHAR).withSize(36);
    public static final ColumnMetadata INITIATOR_TYPE =
            ColumnMetadata.named("INITIATORTYPE").ofType(Types.INTEGER).withSize(10);
    public static final ColumnMetadata MESSAGE =
            ColumnMetadata.named("MESSAGE").ofType(Types.VARCHAR).withSize(1024);
    public static final ColumnMetadata NODE_IDENTIFIER =
            ColumnMetadata.named("NODEIDENTIFIER").ofType(Types.VARCHAR).withSize(255);
    public static final ColumnMetadata OUTCOME =
            ColumnMetadata.named("OUTCOME").ofType(Types.INTEGER).withSize(10);
    public static final ColumnMetadata PARAMETER =
            ColumnMetadata.named("PARAMETER").ofType(Types.VARCHAR).withSize(255);
    public static final ColumnMetadata REMOTE_HOST_ADDRESS =
            ColumnMetadata.named("REMOTEHOSTADDRESS").ofType(Types.VARCHAR).withSize(255);
    public static final ColumnMetadata REQUEST_IDENTIFIER =
            ColumnMetadata.named("REQUESTIDENTIFIER").ofType(Types.VARCHAR).withSize(255);
    public static final ColumnMetadata RESULT =
            ColumnMetadata.named("RESULT").ofType(Types.VARCHAR).withSize(255);
    public static final ColumnMetadata SESSION_IDENTIFIER =
            ColumnMetadata.named("SESSIONIDENTIFIER").ofType(Types.VARCHAR).withSize(255);
    public static final ColumnMetadata TARGET_NAME =
            ColumnMetadata.named("TARGETNAME").ofType(Types.VARCHAR).withSize(255);
    public static final ColumnMetadata TARGET_OID =
            ColumnMetadata.named("TARGETOID").ofType(Types.VARCHAR).withSize(36);
    public static final ColumnMetadata TARGET_OWNER_NAME =
            ColumnMetadata.named("TARGETOWNERNAME").ofType(Types.VARCHAR).withSize(255);
    public static final ColumnMetadata TARGET_OWNER_OID =
            ColumnMetadata.named("TARGETOWNEROID").ofType(Types.VARCHAR).withSize(36);
    public static final ColumnMetadata TARGET_OWNER_TYPE =
            ColumnMetadata.named("TARGETOWNERTYPE").ofType(Types.INTEGER).withSize(10);
    public static final ColumnMetadata TARGET_TYPE =
            ColumnMetadata.named("TARGETTYPE").ofType(Types.INTEGER).withSize(10);
    public static final ColumnMetadata TASK_IDENTIFIER =
            ColumnMetadata.named("TASKIDENTIFIER").ofType(Types.VARCHAR).withSize(255);
    public static final ColumnMetadata TASK_OID =
            ColumnMetadata.named("TASKOID").ofType(Types.VARCHAR).withSize(255);
    public static final ColumnMetadata TIMESTAMP =
            ColumnMetadata.named("TIMESTAMPVALUE").ofType(Types.TIMESTAMP).withSize(23).withDigits(10);

    // columns and relations
    public final NumberPath<Long> id = createLong("id", ID);
    public final StringPath attorneyName = createString("attorneyName", ATTORNEY_NAME);
    public final StringPath attorneyOid = createString("attorneyOid", ATTORNEY_OID);
    public final StringPath channel = createString("channel", CHANNEL);
    public final StringPath eventIdentifier = createString("eventIdentifier", EVENT_IDENTIFIER);
    public final NumberPath<Integer> eventStage = createInteger("eventStage", EVENT_STAGE);
    public final NumberPath<Integer> eventType = createInteger("eventType", EVENT_TYPE);
    public final StringPath hostIdentifier = createString("hostIdentifier", HOST_IDENTIFIER);
    public final StringPath initiatorName = createString("initiatorName", INITIATOR_NAME);
    public final StringPath initiatorOid = createString("initiatorOid", INITIATOR_OID);
    public final NumberPath<Integer> initiatorType = createInteger("initiatorType", INITIATOR_TYPE);
    public final StringPath message = createString("message", MESSAGE);
    public final StringPath nodeIdentifier = createString("nodeIdentifier", NODE_IDENTIFIER);
    public final NumberPath<Integer> outcome = createInteger("outcome", OUTCOME);
    public final StringPath parameter = createString("parameter", PARAMETER);
    public final StringPath remoteHostAddress =
            createString("remoteHostAddress", REMOTE_HOST_ADDRESS);
    public final StringPath requestIdentifier =
            createString("requestIdentifier", REQUEST_IDENTIFIER);
    public final StringPath result = createString("result", RESULT);
    public final StringPath sessionIdentifier =
            createString("sessionIdentifier", SESSION_IDENTIFIER);
    public final StringPath targetName = createString("targetName", TARGET_NAME);
    public final StringPath targetOid = createString("targetOid", TARGET_OID);
    public final StringPath targetOwnerName = createString("targetOwnerName", TARGET_OWNER_NAME);
    public final StringPath targetOwnerOid = createString("targetOwnerOid", TARGET_OWNER_OID);
    public final NumberPath<Integer> targetOwnerType =
            createInteger("targetOwnerType", TARGET_OWNER_TYPE);
    public final NumberPath<Integer> targetType = createInteger("targetType", TARGET_TYPE);
    public final StringPath taskIdentifier = createString("taskIdentifier", TASK_IDENTIFIER);
    public final StringPath taskOid = createString("taskOid", TASK_OID);
    public final DateTimePath<Instant> timestamp = createInstant("timestamp", TIMESTAMP);

    public final PrimaryKey<MAuditEventRecord> constraint85c = createPrimaryKey(id);
    public final ForeignKey<QAuditItem> auditItemFk = createInvForeignKey(id, "RECORD_ID");
    public final ForeignKey<QAuditPropertyValue> auditPropValueFk = createInvForeignKey(id, "RECORD_ID");
    public final ForeignKey<MAuditDelta> auditDeltaFk = createInvForeignKey(id, "RECORD_ID");
    public final ForeignKey<QAuditRefValue> auditRefValueFk = createInvForeignKey(id, "RECORD_ID");
    public final ForeignKey<QAuditResource> auditResourceFk = createInvForeignKey(id, "RECORD_ID");

    public QAuditEventRecord(String variable) {
        this(variable, "PUBLIC", TABLE_NAME);
    }

    public QAuditEventRecord(String variable, String schema, String table) {
        super(MAuditEventRecord.class, forVariable(variable), schema, table);
    }
}
