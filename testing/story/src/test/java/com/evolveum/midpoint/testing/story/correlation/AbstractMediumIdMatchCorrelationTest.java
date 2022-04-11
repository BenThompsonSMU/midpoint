/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.testing.story.correlation;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.CsvResource;
import com.evolveum.midpoint.test.TestResource;
import com.evolveum.midpoint.test.TestTask;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.testng.annotations.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests ID Match correlation in the "medium" case:
 *
 * 1. Three source resources (SIS, HR, External)
 * 2. Personal data are taken from SIS (if present), then from HR (if present), and finally from External.
 */
public abstract class AbstractMediumIdMatchCorrelationTest extends AbstractIdMatchCorrelationTest {

    public static final File TEST_DIR = new File(AbstractCorrelationTest.TEST_DIR, "idmatch/medium");

    private static final File SYSTEM_CONFIGURATION_FILE = new File(TEST_DIR, "system-configuration.xml");

    private static final TestResource<FunctionLibraryType> FUNCTION_LIBRARY_MYLIB =
            new TestResource<>(TEST_DIR, "function-library-mylib.xml", "fea7be76-a57d-435e-b874-b0f8b4ca39c9");

    private static final TestResource<ObjectTemplateType> OBJECT_TEMPLATE_USER =
            new TestResource<>(TEST_DIR, "object-template-user.xml", "bf275746-f2ce-4ae3-9e91-0c40e26422b7");

    private static final CsvResource RESOURCE_SIS = new CsvResource(TEST_DIR, "resource-sis.xml",
            "773991ae-4853-4e88-9cfc-b10bec750f3b", "resource-sis.csv",
            "sisId,firstName,lastName,born,nationalId");
    private static final CsvResource RESOURCE_HR = new CsvResource(TEST_DIR, "resource-hr.xml",
            "084dfbfa-c465-421b-a2ac-2ab3afbf20ff", "resource-hr.csv",
            "HR_ID,FIRSTN,LASTN,DOB,NATIDENT");
    private static final CsvResource RESOURCE_EXTERNAL = new CsvResource(TEST_DIR, "resource-external.xml",
            "106c248c-ce69-4274-845f-7fb391e1545a", "resource-external.csv",
            "EXT_ID,FIRSTN,LASTN,DOB,NATIDENT"); // schema similar to HR

    private static final TestTask TASK_IMPORT_SIS = new TestTask(TEST_DIR, "task-import-sis.xml",
            "d5b49ed0-5916-4371-a7d8-67d55597c31b", 30000);
    private static final TestTask TASK_IMPORT_HR = new TestTask(TEST_DIR, "task-import-hr.xml",
            "d2e64047-6b10-49fd-98d0-af7b57228a52", 30000);
    private static final TestTask TASK_IMPORT_EXTERNAL = new TestTask(TEST_DIR, "task-import-external.xml",
            "aa7cb0bc-50ed-4f83-90ab-cb2e72f4ac2c", 30000);

    private String johnOid;

    @Override
    public void initSystem(Task initTask, OperationResult initResult) throws Exception {
        super.initSystem(initTask, initResult);

        addObject(OBJECT_TEMPLATE_USER, initTask, initResult);
        addObject(FUNCTION_LIBRARY_MYLIB, initTask, initResult);

        RESOURCE_SIS.initializeAndTest(this, initTask, initResult);
        RESOURCE_HR.initializeAndTest(this, initTask, initResult);
        RESOURCE_EXTERNAL.initializeAndTest(this, initTask, initResult);

        TASK_IMPORT_SIS.initialize(this, initTask, initResult);
        TASK_IMPORT_HR.initialize(this, initTask, initResult);
        TASK_IMPORT_EXTERNAL.initialize(this, initTask, initResult);
    }

    @Override
    protected File getSystemConfigurationFile() {
        return SYSTEM_CONFIGURATION_FILE;
    }

    /**
     * Imports John from SIS. It's the only user, so no correlation conflicts here.
     */
    @Test
    public void test100ImportJohnFromSis() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();

        given("John is in SIS as #1");
        RESOURCE_SIS.appendLine("1,John,Smith,2004-02-06,040206/1328");

        when("import task from SIS is run");
        TASK_IMPORT_SIS.rerun(result);

        then("John should be imported");

        // @formatter:off
        TASK_IMPORT_SIS.assertAfter()
                .assertClosed()
                .assertSuccess()
                .rootActivityState()
                    .progress()
                        .assertCommitted(1, 0, 0);
        // @formatter:on

        johnOid = assertUserAfterByUsername("smith1")
                .assertGivenName("John")
                .assertFamilyName("Smith")
                .assertLinks(1, 0)
                .assertExtensionValue("sisId", "1")
                .assertExtensionValue("sisGivenName", "John")
                .assertExtensionValue("sisFamilyName", "Smith")
                .assertExtensionValue("sisDateOfBirth", "2004-02-06")
                .assertExtensionValue("sisNationalId", "040206/1328")
                .assertExtensionValue("dateOfBirth", "2004-02-06")
                .assertExtensionValue("nationalId", "040206/1328")
                .getOid();
    }

    /**
     * Imports John from HR. The National ID does not match, so manual correlation is needed.
     */
    @Test
    public void test110ImportJohnFromHrDisputed() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();

        given("John is in HR as #A1001");
        RESOURCE_HR.appendLine("A1001,John,Smith,2004-02-06,040206/132x");

        when("import task from HR is run");
        TASK_IMPORT_HR.rerun(result);

        then("Correlation case should be created");

        // @formatter:off
        TASK_IMPORT_HR.assertAfter()
                .assertClosed()
                .assertSuccess()
                .rootActivityState()
                    .progress()
                        .assertCommitted(1, 0, 0);
        // @formatter:on

        PrismObject<ShadowType> a1001 = findShadowByPrismName("A1001", RESOURCE_HR.getObject(), result);
        assertShadowAfter(a1001)
                .assertCorrelationSituation(CorrelationSituationType.UNCERTAIN);
        CaseType aCase = correlationCaseManager.findCorrelationCase(a1001.asObjectable(), true, result);
        assertThat(aCase).as("correlation case").isNotNull();
        assertCase(aCase, "case")
                .displayXml();

        assertUserAfterByUsername("smith1")
                .assertLinks(1, 0); // The account should not be linked yet

        when("resolving the case");
        resolveCase(aCase, johnOid, task, result);

        then("John should be updated");
        assertUserAfterByUsername("smith1")
                .assertGivenName("John")
                .assertFamilyName("Smith")
                .assertLinks(2, 0)
                .assertExtensionValue("hrId", "A1001")
                .assertExtensionValue("hrGivenName", "John")
                .assertExtensionValue("hrFamilyName", "Smith")
                .assertExtensionValue("hrDateOfBirth", "2004-02-06")
                .assertExtensionValue("hrNationalId", "040206/132x")
                .assertExtensionValue("sisId", "1")
                .assertExtensionValue("sisGivenName", "John")
                .assertExtensionValue("sisFamilyName", "Smith")
                .assertExtensionValue("sisDateOfBirth", "2004-02-06")
                .assertExtensionValue("sisNationalId", "040206/1328")
                .assertExtensionValue("dateOfBirth", "2004-02-06")
                .assertExtensionValue("nationalId", "040206/1328");
    }

    /**
     * Imports John from External. Here we match on the HR version of the data (matching National ID but not the date of birth).
     */
    @Test
    public void test120ImportJohnFromExternalDisputed() throws Exception {
        Task task = getTestTask();
        OperationResult result = task.getResult();

        given("John is in External as #X1");
        RESOURCE_EXTERNAL.appendLine("X1,John,Smith,2004-02-26,040206/132x");

        when("import task from EXTERNAL is run");
        TASK_IMPORT_EXTERNAL.rerun(result);

        then("Correlation case should be created");

        // @formatter:off
        TASK_IMPORT_EXTERNAL.assertAfter()
                .assertClosed()
                .assertSuccess()
                .rootActivityState()
                    .progress()
                        .assertCommitted(1, 0, 0);
        // @formatter:on

        PrismObject<ShadowType> x1 = findShadowByPrismName("X1", RESOURCE_EXTERNAL.getObject(), result);
        assertShadowAfter(x1)
                .assertCorrelationSituation(CorrelationSituationType.UNCERTAIN);
        CaseType aCase = correlationCaseManager.findCorrelationCase(x1.asObjectable(), true, result);
        assertThat(aCase).as("correlation case").isNotNull();
        assertCase(aCase, "case")
                .displayXml();

        assertUserAfterByUsername("smith1")
                .assertLinks(2, 0); // The account should not be linked yet

        when("resolving the case");
        resolveCase(aCase, johnOid, task, result);

        then("John should be updated");
        assertUserAfterByUsername("smith1")
                .assertGivenName("John")
                .assertFamilyName("Smith")
                .assertLinks(3, 0)
                .assertExtensionValue("externalId", "X1")
                .assertExtensionValue("externalGivenName", "John")
                .assertExtensionValue("externalFamilyName", "Smith")
                .assertExtensionValue("externalDateOfBirth", "2004-02-26")
                .assertExtensionValue("externalNationalId", "040206/132x")
                .assertExtensionValue("hrId", "A1001")
                .assertExtensionValue("hrGivenName", "John")
                .assertExtensionValue("hrFamilyName", "Smith")
                .assertExtensionValue("hrDateOfBirth", "2004-02-06")
                .assertExtensionValue("hrNationalId", "040206/132x")
                .assertExtensionValue("sisId", "1")
                .assertExtensionValue("sisGivenName", "John")
                .assertExtensionValue("sisFamilyName", "Smith")
                .assertExtensionValue("sisDateOfBirth", "2004-02-06")
                .assertExtensionValue("sisNationalId", "040206/1328")
                .assertExtensionValue("dateOfBirth", "2004-02-06")
                .assertExtensionValue("nationalId", "040206/1328");
    }
}
