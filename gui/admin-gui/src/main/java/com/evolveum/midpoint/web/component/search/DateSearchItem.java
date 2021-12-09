/*
 * Copyright (c) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.component.search;

import javax.xml.datatype.XMLGregorianCalendar;

/**
 * @author honchar
 */
public class DateSearchItem extends AttributeSearchItem {

    private static final long serialVersionUID = 1L;

    private XMLGregorianCalendar fromDate;
    private XMLGregorianCalendar toDate;

    public DateSearchItem(Search search, AttributeSearchItemDefinition definition) {
        super(search, definition);
    }

    public XMLGregorianCalendar getFromDate() {
        return fromDate;
    }

    public void setFromDate(XMLGregorianCalendar fromDate) {
        this.fromDate = fromDate;
    }

    public XMLGregorianCalendar getToDate() {
        return toDate;
    }

    public void setToDate(XMLGregorianCalendar toDate) {
        this.toDate = toDate;
    }

    @Override
    public Type getSearchItemType() {
        return Type.DATE;
    }
}
