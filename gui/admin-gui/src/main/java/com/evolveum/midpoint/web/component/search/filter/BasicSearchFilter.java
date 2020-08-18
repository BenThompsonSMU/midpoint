/*
 * Copyright (c) 2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.component.search.filter;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.query.*;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

import javax.xml.namespace.QName;

/**
 * @author honchar
 */
public class BasicSearchFilter<C extends Containerable> extends SearchFilter<C> {

    private static final long serialVersionUID = 1L;
    private static final Trace LOGGER = TraceManager.getTrace(BasicSearchFilter.class);

    private LogicalFilterValue logicalFilterValue = LogicalFilterValue.AND;

    public BasicSearchFilter(PrismContext prismContext, ObjectFilter baseFilter, Class<C> type) {
        super(prismContext, baseFilter, type);
    }

    @Override
    public void addSearchFilterItem(ValueSearchFilterItem valueSearchFilterItem) {
        getValueSearchFilterItems().add(valueSearchFilterItem);
    }

    public void deleteSearchFilterItem(ItemDefinition itemToDelete) {
        if (itemToDelete == null){
            return;
        }
        Iterator<ValueSearchFilterItem> it = getValueSearchFilterItems().iterator();
        while (it.hasNext()){
            if (QNameUtil.match(itemToDelete.getItemName(), it.next().getPropertyDef().getItemName())){
                it.remove();
                return;
            }
        }
    }

//    public ObjectFilter convertToObjectFilter() {
//        if (CollectionUtils.isEmpty(getSearchFilterItems())) {
//            return null;
//        }
//        List<ObjectFilter> subFilterList = new ArrayList<>();
//        getSearchFilterItems().forEach(searchFilterItem -> {
//            subFilterList.add(((ValueSearchFilterItem) searchFilterItem).buildObjectFilter());
//        });
//        if (logicalFilterItem == null && subFilterList.size() == 1) {
//            return subFilterList.get(0);
//        }
//        if (logicalFilterItem == null) {
//            logicalFilterItem = getPrismContext().queryFactory().createAnd();
//        }
//        if (logicalFilterItem instanceof OrSearchFilterItem) {
//            return getPrismContext().queryFactory().createOr(subFilterList);
//        } else {
//            return getPrismContext().queryFactory().createAnd(subFilterList);
//        }
//    }

    protected void initSearchFilterItems(ObjectFilter baseFilter) {
        if (baseFilter == null) {
            return;
        }
        if (baseFilter instanceof AndFilter) {
            logicalFilterValue = LogicalFilterValue.AND;

            AndFilter andFilter = (AndFilter) baseFilter;
            addValueFilters(andFilter.getConditions());
        } else if (baseFilter instanceof OrFilter) {
            logicalFilterValue = LogicalFilterValue.OR;

            OrFilter orFilter = (OrFilter) baseFilter;
            addValueFilters(orFilter.getConditions());
        } else if (baseFilter instanceof ValueFilter) {
            addValueFilters(Arrays.asList(baseFilter));
        }
    }

    @Override
    public ObjectFilter buildObjectFilter(){
        if (logicalFilterValue.equals(LogicalFilterValue.OR)){
            return getPrismContext().queryFactory().createAnd(getObjectFilterList());
        } else {
            return getPrismContext().queryFactory().createOr(getObjectFilterList());
        }
    }

    public void addValueFilters(List<ObjectFilter> objectFilters) {
        objectFilters.forEach(filter -> {
            boolean applyNegation = false;
            if (filter instanceof NotFilter){
                applyNegation = true;
            }
            ObjectFilter realFilter = applyNegation ? ((NotFilter) filter).getFilter() : filter;
            if (realFilter instanceof ValueFilter) {
                ValueSearchFilterItem filterItem = new ValueSearchFilterItem((ValueFilter)realFilter, applyNegation);
                addSearchFilterItem(filterItem);
            }
        });
    }

    public LogicalFilterValue getLogicalFilterValue() {
        return logicalFilterValue;
    }

    public void setLogicalFilterValue(LogicalFilterValue logicalFilterValue) {
        this.logicalFilterValue = logicalFilterValue;
    }
}
