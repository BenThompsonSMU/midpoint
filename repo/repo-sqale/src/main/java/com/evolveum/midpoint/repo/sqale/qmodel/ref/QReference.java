/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmodel.ref;

import java.sql.Types;

import com.querydsl.core.types.dsl.EnumPath;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.sql.ColumnMetadata;
import com.querydsl.sql.PrimaryKey;

import com.evolveum.midpoint.repo.sqlbase.querydsl.FlexibleRelationalPathBase;
import com.evolveum.midpoint.repo.sqlbase.querydsl.UuidPath;

/**
 * Querydsl query type for {@value #TABLE_NAME} table that contains all persisted object references.
 * This actually points to super-table, concrete tables are partitioned by {@link MReferenceType}.
 */
public class QReference extends FlexibleRelationalPathBase<MReference> {

    private static final long serialVersionUID = -466419569179455042L;

    public static final String TABLE_NAME = "m_reference";

    public static final ColumnMetadata OWNER_OID =
            ColumnMetadata.named("owner_oid").ofType(UuidPath.UUID_TYPE).notNull();
    public static final ColumnMetadata REFERENCE_TYPE =
            ColumnMetadata.named("referenceType").ofType(Types.OTHER).notNull();
    public static final ColumnMetadata TARGET_OID =
            ColumnMetadata.named("targetOid").ofType(UuidPath.UUID_TYPE).notNull();
    public static final ColumnMetadata TARGET_TYPE =
            ColumnMetadata.named("targetType").ofType(Types.INTEGER).notNull();
    public static final ColumnMetadata RELATION_ID =
            ColumnMetadata.named("relation_id").ofType(Types.INTEGER).notNull();

    public final UuidPath ownerOid = createUuid("ownerOid", OWNER_OID);
    public final EnumPath<MReferenceType> referenceType =
            createEnum("referenceType", MReferenceType.class, REFERENCE_TYPE);
    public final UuidPath targetOid = createUuid("targetOid", TARGET_OID);
    public final NumberPath<Integer> targetType = createInteger("targetType", TARGET_TYPE);
    public final NumberPath<Integer> relationId = createInteger("relationId", RELATION_ID);

    public final PrimaryKey<MReference> pk =
            createPrimaryKey(ownerOid, referenceType, relationId, targetOid);

    public QReference(String variable) {
        this(variable, DEFAULT_SCHEMA_NAME, TABLE_NAME);
    }

    public QReference(String variable, String schema, String table) {
        super(MReference.class, variable, schema, table);
    }
}
