/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmodel;

import java.util.Collection;
import java.util.UUID;
import javax.xml.namespace.QName;

import com.querydsl.core.Tuple;
import com.querydsl.sql.ColumnMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.evolveum.midpoint.repo.sqale.MObjectTypeMapping;
import com.evolveum.midpoint.repo.sqale.SqaleTransformerContext;
import com.evolveum.midpoint.repo.sqlbase.JdbcSession;
import com.evolveum.midpoint.repo.sqlbase.SqlTransformerContext;
import com.evolveum.midpoint.repo.sqlbase.mapping.QueryTableMapping;
import com.evolveum.midpoint.repo.sqlbase.mapping.SqlTransformer;
import com.evolveum.midpoint.repo.sqlbase.querydsl.FlexibleRelationalPathBase;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;

public abstract class SqaleTransformerBase<S, Q extends FlexibleRelationalPathBase<R>, R>
        implements SqlTransformer<S, Q, R> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final SqaleTransformerContext transformerContext;
    protected final QueryTableMapping<S, Q, R> mapping;

    /**
     * Constructor uses {@link SqlTransformerContext} type even when it really is
     * {@link SqaleTransformerContext}, but this way we can cast it just once here; otherwise cast
     * would be needed in each implementation of {@link QueryTableMapping#createTransformer)}.
     * (Alternative is to parametrize {@link QueryTableMapping} with various {@link SqlTransformer}
     * types which is not convenient at all. This little downcast is low price to pay.)
     */
    protected SqaleTransformerBase(
            SqlTransformerContext transformerContext,
            QueryTableMapping<S, Q, R> mapping) {
        this.transformerContext = (SqaleTransformerContext) transformerContext;
        this.mapping = mapping;
    }

    public R toRowObject(S schemaObject, JdbcSession jdbcSession) {
        throw new UnsupportedOperationException("Not supported on object and abstract types");
    }

    @Override
    public S toSchemaObject(R row) {
        throw new UnsupportedOperationException("Use toSchemaObject(Tuple,...)");
    }

    /**
     * Transforms row Tuple containing {@link R} under entity path and extension columns.
     */
    @Override
    public S toSchemaObject(Tuple tuple, Q entityPath,
            Collection<SelectorOptions<GetOperationOptions>> options)
            throws SchemaException {
        S schemaObject = toSchemaObject(tuple.get(entityPath));
        processExtensionColumns(schemaObject, tuple, entityPath);
        return schemaObject;
    }

    @SuppressWarnings("unused")
    protected void processExtensionColumns(S schemaObject, Tuple tuple, Q entityPath) {
        // empty by default, can be overridden
    }

    /**
     * Returns {@link ObjectReferenceType} with specified oid, proper type based on
     * {@link MObjectTypeMapping} and, optionally, target name/description.
     * Returns {@code null} if OID is null.
     * Fails if OID is not null and {@code repoObjectType} is null.
     */
    @Nullable
    protected ObjectReferenceType objectReferenceType(
            @Nullable String oid, MObjectTypeMapping repoObjectType, String targetName) {
        if (oid == null) {
            return null;
        }
        if (repoObjectType == null) {
            throw new IllegalArgumentException(
                    "NULL object type provided for object reference with OID " + oid);
        }

        return new ObjectReferenceType()
                .oid(oid)
                .type(transformerContext.schemaClassToQName(repoObjectType.getSchemaType()))
                .description(targetName)
                .targetName(targetName);
    }

    /**
     * Returns {@link MObjectTypeMapping} from ordinal Integer or specified default value.
     */
    protected @NotNull MObjectTypeMapping objectTypeMapping(
            @Nullable Integer repoObjectTypeId, @NotNull MObjectTypeMapping defaultValue) {
        return repoObjectTypeId != null
                ? MObjectTypeMapping.fromCode(repoObjectTypeId)
                : defaultValue;
    }

    /**
     * Returns nullable {@link MObjectTypeMapping} from ordinal Integer.
     * If null is returned it will not fail immediately unlike {@link MObjectTypeMapping#fromCode(int)}.
     * This is practical for eager argument resolution for
     * {@link #objectReferenceType(String, MObjectTypeMapping, String)}.
     * Null may still be OK if OID is null as well - which means no reference.
     */
    protected @Nullable MObjectTypeMapping objectTypeMapping(
            @Nullable Integer repoObjectTypeId) {
        return repoObjectTypeId != null
                ? MObjectTypeMapping.fromCode(repoObjectTypeId)
                : null;
    }

    /**
     * Trimming the value to the column size from column metadata (must be specified).
     */
    protected @Nullable String trim(
            @Nullable String value, @NotNull ColumnMetadata columnMetadata) {
        if (!columnMetadata.hasSize()) {
            throw new IllegalArgumentException(
                    "trimString with column metadata without specified size: " + columnMetadata);
        }
        return MiscUtil.trimString(value, columnMetadata.getSize());
    }

    /** Returns ID for cached URI (represented by QName) without going ot database. */
    protected Integer resolveToId(QName qName) {
        return qName != null
                ? resolveToId(QNameUtil.qNameToUri(transformerContext.normalizeRelation(qName)))
                : null;
    }

    /** Returns ID for cached URI without going ot database. */
    protected Integer resolveToId(String uri) {
        return transformerContext.resolveToId(uri);
    }

    /** Returns ID for URI (represented by QName) creating new cache row in DB as needed. */
    protected Integer processCachedUri(QName qName, JdbcSession jdbcSession) {
        return qName != null
                ? processCachedUri(
                QNameUtil.qNameToUri(transformerContext.normalizeRelation(qName)),
                jdbcSession)
                : null;
    }

    /** Returns ID for URI creating new cache row in DB as needed. */
    protected Integer processCachedUri(String uri, JdbcSession jdbcSession) {
        return transformerContext.processCachedUri(uri, jdbcSession);
    }

    protected @Nullable UUID oidToUUid(@Nullable String oid) {
        return oid != null ? UUID.fromString(oid) : null;
    }

    protected int schemaTypeToCode(QName schemaType) {
        return MObjectTypeMapping.fromSchemaType(
                transformerContext.qNameToSchemaClass(schemaType)).code();
    }
}
