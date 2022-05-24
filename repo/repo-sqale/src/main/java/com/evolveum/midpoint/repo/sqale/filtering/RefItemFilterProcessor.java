/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.filtering;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import javax.xml.namespace.QName;

import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.EnumPath;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.prism.PrismConstants;
import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.RefFilter;
import com.evolveum.midpoint.repo.sqale.SqaleQueryContext;
import com.evolveum.midpoint.repo.sqale.qmodel.common.QUri;
import com.evolveum.midpoint.repo.sqale.qmodel.object.MObjectType;
import com.evolveum.midpoint.repo.sqale.qmodel.object.QObject;
import com.evolveum.midpoint.repo.sqlbase.RepositoryException;
import com.evolveum.midpoint.repo.sqlbase.SqlQueryContext;
import com.evolveum.midpoint.repo.sqlbase.filtering.item.ItemValueFilterProcessor;
import com.evolveum.midpoint.repo.sqlbase.querydsl.FlexibleRelationalPathBase;
import com.evolveum.midpoint.repo.sqlbase.querydsl.UuidPath;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

/**
 * Filter processor for reference item paths embedded in table as three columns.
 * OID is represented by UUID column, type by ID (see {@link MObjectType} and relation
 * by Integer (foreign key) to {@link QUri}.
 */
public class RefItemFilterProcessor extends ItemValueFilterProcessor<RefFilter> {

    // only oidPath is strictly not-null, but then the filter better not ask for type or relation
    private final UuidPath oidPath;
    @Nullable private final EnumPath<MObjectType> typePath;
    @Nullable private final NumberPath<Integer> relationIdPath;
    @Nullable private final StringPath targetNamePath;

    public <Q extends FlexibleRelationalPathBase<R>, R> RefItemFilterProcessor(
            SqlQueryContext<?, Q, R> context,
            Function<Q, UuidPath> rootToOidPath,
            @Nullable Function<Q, EnumPath<MObjectType>> rootToTypePath,
            @Nullable Function<Q, NumberPath<Integer>> rootToRelationIdPath,
            @Nullable Function<Q, StringPath> rootToTargetNamePath) {
        this(context,
                rootToOidPath.apply(context.path()),
                rootToTypePath != null ? rootToTypePath.apply(context.path()) : null,
                rootToRelationIdPath != null ? rootToRelationIdPath.apply(context.path()) : null,
                rootToTargetNamePath != null ? rootToTargetNamePath.apply(context.path()) : null);
    }

    // exposed mainly for RefTableItemFilterProcessor
    <Q extends FlexibleRelationalPathBase<R>, R> RefItemFilterProcessor(
            SqlQueryContext<?, Q, R> context,
            UuidPath oidPath,
            @Nullable EnumPath<MObjectType> typePath,
            @Nullable NumberPath<Integer> relationIdPath,
            @Nullable StringPath targetNamePath) {
        super(context);
        this.oidPath = oidPath;
        this.typePath = typePath;
        this.relationIdPath = relationIdPath;
        this.targetNamePath = targetNamePath;
    }

    @Override
    public Predicate process(RefFilter filter) throws RepositoryException {
        if (filter instanceof RefFilterWithRepoPath) {
            return processRepoFilter((RefFilterWithRepoPath) filter);
        }


        List<PrismReferenceValue> values = filter.getValues();
        if (values == null || values.isEmpty()) {
            return filter.isOidNullAsAny() ? null : oidPath.isNull();
        }
        Predicate predicate = null;
        ObjectFilter targetFilter = filter.getFilter();
        if (values.size() == 1) {
            var value = values.get(0);
            predicate = processSingleValue(filter, value);
            if (targetFilter != null) {
                var targetType = Optional.ofNullable(value.getTargetType()).orElse(filter.getDefinition().getTargetTypeName());
                predicate = ExpressionUtils.and(predicate, targetFilterPredicate(targetType,targetFilter));
            }
            return predicate;
        } else {
            for (PrismReferenceValue ref : values) {
                predicate = ExpressionUtils.or(predicate, processSingleValue(filter, ref));
            }
        }


        if (targetFilter != null) {
            predicate = ExpressionUtils.and(predicate, targetFilterPredicate(filter.getDefinition().getTargetTypeName(),targetFilter));
        }

        return predicate;
    }

    private Predicate targetFilterPredicate(@Nullable QName targetType, ObjectFilter targetFilter) throws RepositoryException {
        targetType = targetType != null ? targetType : ObjectType.COMPLEX_TYPE;
        var targetClass = targetType != null ? context.prismContext().getSchemaRegistry().getCompileTimeClassForObjectType(targetType) : ObjectType.class;
        var subquery = context.subquery(context.repositoryContext().getMappingBySchemaType(targetClass));
        var targetPath = subquery.path(QObject.class);
        subquery.sqlQuery().where(oidPath.eq(targetPath.oid));
        subquery.processFilter(targetFilter);
        return subquery.sqlQuery().exists();
    }

    /**
     * Process reference filter used in {@link ReferencedByFilterProcessor}.
     *
     */
    private Predicate processRepoFilter(RefFilterWithRepoPath filter) {
        return relationPredicate(oidPath.eq(filter.getOidPath()), filter.getRelation());
    }

    private Predicate processSingleValue(RefFilter filter, PrismReferenceValue ref) {
        Predicate predicate = null;
        if (ref.getOid() != null) {
            predicate = predicateWithNotTreated(oidPath,
                    oidPath.eq(UUID.fromString(ref.getOid())));
        } else if (!filter.isOidNullAsAny()) {
            predicate = oidPath.isNull();
        }

        // Audit sometimes does not use target type path
        if (typePath != null) {
            if (ref.getTargetType() != null) {
                MObjectType objectType = MObjectType.fromTypeQName(ref.getTargetType());
                predicate = ExpressionUtils.and(predicate,
                        predicateWithNotTreated(typePath, typePath.eq(objectType)));
            } else if (!filter.isTargetTypeNullAsAny()) {
                predicate = ExpressionUtils.and(predicate, typePath.isNull());
            }
        }

        // Audit tables do not use relation paths
        predicate = relationPredicate(predicate, ref.getRelation());

        if (targetNamePath != null && ref.getTargetName() != null) {
            predicate = ExpressionUtils.and(predicate,
                    predicateWithNotTreated(targetNamePath,
                            targetNamePath.eq(ref.getTargetName().getOrig())));
        }
        return predicate;
    }

    private Predicate relationPredicate(Predicate predicate, QName relation) {
        if (relationIdPath != null) {
            if (relation == null || !relation.equals(PrismConstants.Q_ANY)) {
                Integer relationId = ((SqaleQueryContext<?, ?, ?>) context)
                        .searchCachedRelationId(relation);
                predicate = ExpressionUtils.and(predicate,
                        predicateWithNotTreated(relationIdPath, relationIdPath.eq(relationId)));
            } // else relation == Q_ANY, no additional predicate needed
        }
        return predicate;
    }
}
