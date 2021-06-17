/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.filtering;

import static com.querydsl.core.types.dsl.Expressions.booleanTemplate;
import static com.querydsl.core.types.dsl.Expressions.stringTemplate;

import static com.evolveum.midpoint.repo.sqale.qmodel.ext.MExtItemCardinality.ARRAY;
import static com.evolveum.midpoint.repo.sqale.qmodel.ext.MExtItemCardinality.SCALAR;

import java.util.function.Function;

import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Ops;
import com.querydsl.core.types.Predicate;

import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.query.PropertyValueFilter;
import com.evolveum.midpoint.repo.sqale.SqaleQueryContext;
import com.evolveum.midpoint.repo.sqale.qmodel.ext.MExtItem;
import com.evolveum.midpoint.repo.sqale.qmodel.ext.MExtItemHolderType;
import com.evolveum.midpoint.repo.sqlbase.QueryException;
import com.evolveum.midpoint.repo.sqlbase.RepositoryException;
import com.evolveum.midpoint.repo.sqlbase.SqlQueryContext;
import com.evolveum.midpoint.repo.sqlbase.filtering.ValueFilterValues;
import com.evolveum.midpoint.repo.sqlbase.filtering.item.SinglePathItemFilterProcessor;
import com.evolveum.midpoint.repo.sqlbase.querydsl.FlexibleRelationalPathBase;
import com.evolveum.midpoint.repo.sqlbase.querydsl.JsonbPath;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.QNameUtil;

/**
 * Filter processor for extension items stored in JSONB.
 * This takes care of any supported type, scalar or array, and handles any operation.
 */
public class ExtensionItemFilterProcessor
        extends SinglePathItemFilterProcessor<Object, JsonbPath> {

    // QName.toString produces different results, QNameUtil must be used here:
    public static final String STRING_TYPE = QNameUtil.qNameToUri(DOMUtil.XSD_STRING);

    private final MExtItemHolderType holderType;

    public <Q extends FlexibleRelationalPathBase<R>, R> ExtensionItemFilterProcessor(
            SqlQueryContext<?, Q, R> context,
            Function<Q, JsonbPath> rootToExtensionPath,
            MExtItemHolderType holderType) {
        super(context, rootToExtensionPath);

        this.holderType = holderType;
    }

    @Override
    public Predicate process(PropertyValueFilter<Object> filter) throws RepositoryException {
        PrismPropertyDefinition<?> definition = filter.getDefinition();
        MExtItem extItem = ((SqaleQueryContext<?, ?, ?>) context).repositoryContext()
                .resolveExtensionItem(definition, holderType);

        ValueFilterValues<?, ?> values = ValueFilterValues.from(filter);
        Ops operator = operation(filter);

        if (values.isEmpty()) {
            if (operator == Ops.EQ || operator == Ops.EQ_IGNORE_CASE) {
                // ?? is "escaped" ? operator, PG JDBC driver understands it. Alternative is to use
                // function jsonb_exists but that does NOT use GIN index, only operators do!
                // We have to use parenthesis with AND shovelled into the template like this.
                return booleanTemplate("({0} ?? {1} AND {0} is not null)",
                        path, extItem.id.toString()).not();
            } else {
                throw new QueryException("Null value for other than EQUAL filter: " + filter);
            }
        }

        // If long but monotonous, it can be one method, otherwise throws must be moved inside extracted methods too.
        if (extItem.valueType.equals(STRING_TYPE) && extItem.cardinality == SCALAR) {
            if (operator == Ops.EQ) {
                return predicateWithNotTreated(path, booleanTemplate("{0} @> {1}::jsonb", path,
                        String.format("{\"%d\":\"%s\"}", extItem.id, values.singleValue())));
            } else {
                // {1s} means "as string", this is replaced before JDBC driver, just as path is,
                // but for path types it's automagic, integer would turn to param and ?.
                return singleValuePredicate(stringTemplate("{0}->>'{1s}'", path, extItem.id),
                        operator, values.singleValue());
            }
        } else if (extItem.valueType.equals(STRING_TYPE) && extItem.cardinality == ARRAY) {
            // TODO only EQ: contains/not contains
        }

        // TODO other types

        throw new UnsupportedOperationException("Unsupported filter for extension item: " + filter);
    }

}
