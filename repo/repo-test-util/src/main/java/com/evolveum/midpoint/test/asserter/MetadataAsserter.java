/*
 * Copyright (c) 2018-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.test.asserter;

import static com.evolveum.midpoint.prism.Referencable.getOid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

import com.evolveum.midpoint.xml.ns._public.common.common_3.MetadataType;

/**
 * @author semancik
 *
 */
public class MetadataAsserter<RA> extends AbstractAsserter<RA> {

    private MetadataType metadata;

    public MetadataAsserter(MetadataType metadata, RA returnAsserter, String details) {
        super(returnAsserter, details);
        this.metadata = metadata;
    }

    MetadataType getMetadata() {
        return metadata;
    }

    public MetadataAsserter<RA> assertNone() {
        assertNull("Unexpected "+desc(), metadata);
        return this;
    }

    public MetadataAsserter<RA> assertOriginMappingName(String expected) {
        assertEquals("Wrong origin mapping name in " + desc(), expected, getMetadata().getOriginMappingName());
        return this;
    }

    @Override
    protected String desc() {
        return descWithDetails("metadata of "+getDetails());
    }

    public MetadataAsserter<RA> assertModifyTaskOid(String expectedOid) {
        String realOid = getOid(metadata.getModifyTaskRef());
        assertThat(realOid).as("modify task ref OID").isEqualTo(expectedOid);
        return this;
    }
}
