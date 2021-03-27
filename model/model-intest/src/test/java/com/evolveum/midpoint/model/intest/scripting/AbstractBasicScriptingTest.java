/*
 * Copyright (c) 2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.intest.scripting;

import static com.evolveum.midpoint.util.MiscUtil.emptyIfNull;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.AssertJUnit.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.model.api.ModelPublicConstants;
import com.evolveum.midpoint.test.TestResource;
import com.evolveum.midpoint.util.exception.ScriptExecutionException;

import com.evolveum.midpoint.notifications.api.transports.Message;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.crypto.EncryptionException;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.schema.constants.SchemaConstants;

import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.SkipException;
import org.testng.annotations.Test;

import com.evolveum.midpoint.common.LoggingConfigurationManager;
import com.evolveum.midpoint.model.api.PipelineItem;
import com.evolveum.midpoint.model.impl.scripting.ExecutionContext;
import com.evolveum.midpoint.model.impl.scripting.PipelineData;
import com.evolveum.midpoint.model.impl.scripting.ScriptingExpressionEvaluator;
import com.evolveum.midpoint.model.intest.AbstractInitializedModelIntegrationTest;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.schema.internals.InternalMonitor;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.util.LogfileTestTailer;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.xml.ns._public.model.scripting_3.*;

import org.testng.collections.Sets;

@ContextConfiguration(locations = { "classpath:ctx-model-intest-test-main.xml" })
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public abstract class AbstractBasicScriptingTest extends AbstractInitializedModelIntegrationTest {

    public static final File TEST_DIR = new File("src/test/resources/scripting");

    private static final File SYSTEM_CONFIGURATION_FILE = new File(TEST_DIR, "system-configuration.xml");

    private static final ItemName USER_NAME_TASK_EXTENSION_PROPERTY = new ItemName("http://midpoint.evolveum.com/xml/ns/samples/piracy", "userName");
    private static final ItemName USER_DESCRIPTION_TASK_EXTENSION_PROPERTY = new ItemName("http://midpoint.evolveum.com/xml/ns/samples/piracy", "userDescription");
    private static final ItemName STUDY_GROUP_TASK_EXTENSION_PROPERTY = new ItemName("http://midpoint.evolveum.com/xml/ns/samples/piracy", "studyGroup");

    // Tests 1xx
    private static final String ECHO = "echo";
    private static final String LOG = "log";

    // Tests 2xx: No "legacy" and "new" versions for these
    private static final File SEARCH_FOR_USERS_FILE = new File(TEST_DIR, "search-for-users.xml");
    private static final File SEARCH_FOR_USERS_WITH_EXPRESSIONS_FILE = new File(TEST_DIR, "search-for-users-with-expressions.xml");
    private static final File SEARCH_FOR_SHADOWS_FILE = new File(TEST_DIR, "search-for-shadows.xml");
    private static final String SEARCH_FOR_SHADOWS_NOFETCH = "search-for-shadows-nofetch";
    private static final File SEARCH_FOR_RESOURCES_FILE = new File(TEST_DIR, "search-for-resources.xml");
    private static final File SEARCH_FOR_ROLES_FILE = new File(TEST_DIR, "search-for-roles.xml");
    private static final File SEARCH_FOR_USERS_ACCOUNTS_FILE = new File(TEST_DIR, "search-for-users-accounts.xml");
    private static final File SEARCH_FOR_USERS_ACCOUNTS_NOFETCH_FILE = new File(TEST_DIR, "search-for-users-accounts-nofetch.xml");
    private static final File SEARCH_FOR_USERS_RESOLVE_NAMES_FOR_ROLE_MEMBERSHIP_REF_FILE = new File(TEST_DIR, "search-for-users-resolve-names-for-roleMembershipRef.xml");
    private static final File SEARCH_FOR_USERS_RESOLVE_ROLE_MEMBERSHIP_REF_FILE = new File(TEST_DIR, "search-for-users-resolve-roleMembershipRef.xml");

    // Tests 300-359
    private static final String DISABLE_JACK = "disable-jack";
    private static final String ENABLE_JACK = "enable-jack";
    private static final String DELETE_AND_ADD_JACK = "delete-and-add-jack";
    private static final String MODIFY_JACK = "modify-jack";
    private static final String MODIFY_JACK_BACK = "modify-jack-back";
    private static final String RECOMPUTE_JACK = "recompute-jack";

    // Tests 360-399
    private static final String ASSIGN_CAPTAIN_AND_DUMMY_RED_TO_JACK = "assign-captain-and-dummy-red-to-jack";
    private static final String ASSIGN_TO_JACK_DRY_AND_RAW = "assign-to-jack-dry-and-raw";
    private static final String ASSIGN_NICE_PIRATE_BY_NAME_TO_JACK = "assign-nice-pirate-by-name-to-jack";

    private static final String ASSIGN_PIRATE_MANAGER_TO_WILL = "assign-pirate-manager-to-will";
    private static final String UNASSIGN_PIRATE_DEFAULT_FROM_WILL = "unassign-pirate-default-from-will";
    private static final String UNASSIGN_PIRATE_MANAGER_AND_OWNER_FROM_WILL = "unassign-pirate-manager-and-owner-from-will";
    private static final String UNASSIGN_DUMMY_RESOURCE_FROM_WILL = "unassign-dummy-resource-from-will";
    private static final String ASSIGN_PIRATE_RELATION_CAPTAIN_TO_WILL = "assign-pirate-relation-captain-to-will";

    // Tests 4xx
    private static final String PURGE_DUMMY_BLACK_SCHEMA = "purge-dummy-black-schema";

    private static final String TEST_DUMMY_RESOURCE = "test-dummy-resource";
    private static final String NOTIFICATION_ABOUT_JACK = "notification-about-jack";
    private static final String NOTIFICATION_ABOUT_JACK_TYPE2 = "notification-about-jack-type2";

    // Tests 5xx
    private static final String SCRIPTING_USERS = "scripting-users";
    private static final String SCRIPTING_USERS_IN_BACKGROUND = "scripting-users-in-background";
    private static final String SCRIPTING_USERS_IN_BACKGROUND_ASSIGN = "scripting-users-in-background-assign";

    private static final String GENERATE_PASSWORDS = "generate-passwords";
    private static final String GENERATE_PASSWORDS_2 = "generate-passwords-2";
    private static final String GENERATE_PASSWORDS_3 = "generate-passwords-3";

    private static final String USE_VARIABLES = "use-variables";
    private static final String START_TASKS_FROM_TEMPLATE = "start-tasks-from-template";
    private static final File SCRIPTING_USERS_IN_BACKGROUND_TASK_FILE = new File(TEST_DIR, "scripting-users-in-background-task.xml");

    private static final String SCRIPTING_USERS_IN_BACKGROUND_ITERATIVE_TASK = "scripting-users-in-background-iterative-task";

    private static final File TASK_TO_RESUME_FILE = new File(TEST_DIR, "task-to-resume.xml");
    private static final File TASK_TO_KEEP_SUSPENDED_FILE = new File(TEST_DIR, "task-to-keep-suspended.xml");
    private static final String RESUME_SUSPENDED_TASKS = "resume-suspended-tasks";

    private static final TestResource<UserType> ROLE_OPERATOR = new TestResource<>(TEST_DIR, "role-operator.xml", "8ecc780c-93ad-4f2f-a720-ae3c2c584cbf");
    private static final TestResource<UserType> USER_OPERATOR = new TestResource<>(TEST_DIR, "user-operator.xml", "0f748045-450d-43a5-a720-ea6adc83e43f");

    // Tests 6xx
    private static final String MODIFY_JACK_PASSWORD = "modify-jack-password";
    private static final String MODIFY_JACK_PASSWORD_TASK = "modify-jack-password-task";
    private static final String MODIFY_JACK_PASSWORD_TASK_OID = "9de76345-0f02-48de-86bf-e7a887cb374a";

    private static final String PASSWORD_PLAINTEXT_FRAGMENT = "pass1234wor";
    private static final String PASSWORD_PLAINTEXT_1 = "pass1234wor1";
    private static final String PASSWORD_PLAINTEXT_2 = "pass1234wor2";
    private static final String PASSWORD_PLAINTEXT_3 = "pass1234wor3";

    @Autowired ScriptingExpressionEvaluator evaluator;

    @Override
    public void initSystem(Task initTask, OperationResult initResult)
            throws Exception {
        super.initSystem(initTask, initResult);
        InternalMonitor.reset();

        DebugUtil.setPrettyPrintBeansAs(PrismContext.LANG_YAML);
    }

    @Override
    protected File getSystemConfigurationFile() {
        return SYSTEM_CONFIGURATION_FILE;
    }

    private File getFile(String name) {
        return new File(TEST_DIR, name + getSuffix() + ".xml");
    }

    abstract String getSuffix();

    @Test
    public void test100EmptySequence() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ExpressionSequenceType sequence = new ExpressionSequenceType();

        when();
        ExecutionContext output = evaluator.evaluateExpression(sequence, task, result);

        then();
        dumpOutput(output, result);
        assertNoOutputData(output);

        assertSuccess(result);
    }

    @Test
    public void test110EmptyPipeline() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ExpressionPipelineType pipeline = new ExpressionPipelineType();

        when();
        ExecutionContext output = evaluator.evaluateExpression(pipeline, task, result);

        then();
        dumpOutput(output, result);
        assertNoOutputData(output);

        assertSuccess(result);
    }

    @Test
    public void test112Echo() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ExecuteScriptType executeScript = parseExecuteScript(ECHO);

        when();
        ExecutionContext output = evaluator.evaluateExpression(executeScript, VariablesMap.emptyMap(), false, task, result);

        then();
        dumpOutput(output, result);

        PipelineData data = output.getFinalOutput();
        assertEquals("Unexpected # of items in output", 4, data.getData().size());

        // TODO check correct serialization
    }

    @Test
    public void test120Log() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ScriptingExpressionType logAction = parseScriptingExpression(LOG);

        LogfileTestTailer tailer = new LogfileTestTailer(LoggingConfigurationManager.AUDIT_LOGGER_NAME);
        tailer.tail();
        tailer.setExpecteMessage("Custom message:");

        when();
        ExecutionContext output = evaluator.evaluateExpression(logAction, task, result);

        then();
        dumpOutput(output, result);
        assertNoOutputData(output);

        assertSuccess(result);
        tailer.tail();
        tailer.assertExpectedMessage();
    }

    @Test
    public void test200SearchUser() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        // no legacy/new versions here
        ScriptingExpressionType expression = parseScriptingExpression(SEARCH_FOR_USERS_FILE);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);

        assertSuccess(result);
        assertEquals(2, output.getFinalOutput().getData().size());
    }

    @Test
    public void test202SearchUserWithExpressions() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        // no legacy/new versions here
        ExecuteScriptType executeScript = prismContext.parserFor(SEARCH_FOR_USERS_WITH_EXPRESSIONS_FILE).parseRealValue();
        VariablesMap variables = new VariablesMap();
        variables.put("value1", "administrator", String.class);
        variables.put("value2", "jack", String.class);

        when();
        ExecutionContext output = evaluator.evaluateExpression(executeScript, variables, false, task, result);

        then();
        dumpOutput(output, result);

        assertSuccess(result);
        assertEquals(2, output.getFinalOutput().getData().size());
        assertEquals(new HashSet<>(Arrays.asList("administrator", "jack")),
                output.getFinalOutput().getData().stream()
                        .map(i -> ((PrismObjectValue<?>) i.getValue()).getName().getOrig())
                        .collect(Collectors.toSet()));
    }

    @Test
    public void test205SearchForResources() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        // no legacy/new versions here
        ScriptingExpressionType expression = parseScriptingExpression(SEARCH_FOR_RESOURCES_FILE);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);

        assertSuccess(result);
        assertEquals(13, output.getFinalOutput().getData().size());
    }

    @Test
    public void test206SearchForRoles() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        // no legacy/new versions here
        ScriptingExpressionType expression = parseScriptingExpression(SEARCH_FOR_ROLES_FILE);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);

        assertSuccess(result);
    }

    @Test
    public void test210SearchForShadows() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        // no legacy/new versions here
        ScriptingExpressionType expression = parseScriptingExpression(SEARCH_FOR_SHADOWS_FILE);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);

        assertSuccess(result);
        assertEquals(5, output.getFinalOutput().getData().size());
        assertAttributesFetched(output.getFinalOutput().getData());
    }

    @Test
    public void test215SearchForShadowsNoFetch() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ScriptingExpressionType expression = parseScriptingExpression(SEARCH_FOR_SHADOWS_NOFETCH);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);

        assertSuccess(result);
        assertEquals(5, output.getFinalOutput().getData().size());
        assertAttributesNotFetched(output.getFinalOutput().getData());
    }

    @Test
    public void test220SearchForUsersAccounts() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();
        ScriptingExpressionType expression = parseScriptingExpression(SEARCH_FOR_USERS_ACCOUNTS_FILE);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);

        assertSuccess(result);
        assertEquals(4, output.getFinalOutput().getData().size());
        assertAttributesFetched(output.getFinalOutput().getData());
    }

    @Test
    public void test225SearchForUsersAccountsNoFetch() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();
        ScriptingExpressionType expression = parseScriptingExpression(SEARCH_FOR_USERS_ACCOUNTS_NOFETCH_FILE);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);

        assertSuccess(result);
        assertEquals(4, output.getFinalOutput().getData().size());
        assertAttributesNotFetched(output.getFinalOutput().getData());
    }

    @Test
    public void test230SearchUserResolveNamesForRoleMembershipRef() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();
        ScriptingExpressionType expression = parseScriptingExpression(SEARCH_FOR_USERS_RESOLVE_NAMES_FOR_ROLE_MEMBERSHIP_REF_FILE);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);

        assertSuccess(result);
        assertEquals(2, output.getFinalOutput().getData().size());
        //assertEquals("administrator", ((PrismObject<UserType>) output.getData().get(0)).asObjectable().getName().getOrig());

        for (PipelineItem item : output.getFinalOutput().getData()) {
            PrismAsserts.assertHasTargetName((PrismContainerValue<?>) item.getValue(), UserType.F_ROLE_MEMBERSHIP_REF);
            PrismAsserts.assertHasNoTargetName((PrismContainerValue<?>) item.getValue(), UserType.F_LINK_REF);
        }
    }

    @Test
    public void test240SearchUserResolveRoleMembershipRef() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();
        ScriptingExpressionType expression = parseScriptingExpression(SEARCH_FOR_USERS_RESOLVE_ROLE_MEMBERSHIP_REF_FILE);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);

        assertSuccess(result);
        assertEquals(2, output.getFinalOutput().getData().size());

        for (PipelineItem item : output.getFinalOutput().getData()) {
            PrismAsserts.assertHasObject((PrismContainerValue<?>) item.getValue(), UserType.F_ROLE_MEMBERSHIP_REF);
            PrismAsserts.assertHasNoObject((PrismContainerValue<?>) item.getValue(), UserType.F_LINK_REF);
        }
    }

    @Test
    public void test300DisableJack() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();
        ScriptingExpressionType expression = parseScriptingExpression(DISABLE_JACK);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);
        assertOutputData(output, 1, OperationResultStatus.SUCCESS);
        assertEquals("Disabled user:c0c010c0-d34d-b33f-f00d-111111111111(jack)\n", output.getConsoleOutput());

        assertSuccess(result);
        assertAdministrativeStatusDisabled(searchObjectByName(UserType.class, "jack"));
    }

    @Test
    public void test310EnableJack() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();
        ScriptingExpressionType expression = parseScriptingExpression(ENABLE_JACK);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);
        assertOutputData(output, 1, OperationResultStatus.SUCCESS);

        assertSuccess(result);
        assertEquals("Enabled user:c0c010c0-d34d-b33f-f00d-111111111111(jack)\n", output.getConsoleOutput());
        assertAdministrativeStatusEnabled(searchObjectByName(UserType.class, "jack"));
    }

    @Test
    public void test320DeleteAndAddJack() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();
        ScriptingExpressionType expression = parseScriptingExpression(DELETE_AND_ADD_JACK);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);
        assertOutputData(output, 1, OperationResultStatus.SUCCESS);

        assertSuccess(result);
        assertEquals("Deleted user:c0c010c0-d34d-b33f-f00d-111111111111(jack)\nAdded user:c0c010c0-d34d-b33f-f00d-111111111111(jack)\n", output.getConsoleOutput());
        assertAdministrativeStatusEnabled(searchObjectByName(UserType.class, "jack"));
    }

    @Test
    public void test330ModifyJack() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ScriptingExpressionType expression = parseScriptingExpression(MODIFY_JACK);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);
        assertOutputData(output, 1, OperationResultStatus.SUCCESS);

        assertSuccess(result);
        assertEquals("Modified user:c0c010c0-d34d-b33f-f00d-111111111111(jack)\n", output.getConsoleOutput());
        assertUserAfterByUsername(USER_JACK_USERNAME)
                .assertLocality("Nowhere");
    }

    @Test
    public void test340ModifyJackBack() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ScriptingExpressionType expression = parseScriptingExpression(MODIFY_JACK_BACK);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);
        assertOutputData(output, 1, OperationResultStatus.SUCCESS);

        assertSuccess(result);
        assertEquals("Modified user:c0c010c0-d34d-b33f-f00d-111111111111(jack)\n", output.getConsoleOutput());
        assertUserAfterByUsername(USER_JACK_USERNAME)
                .assertLocality("Caribbean");
    }

    @Test
    public void test350RecomputeJack() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();
        ScriptingExpressionType expression = parseScriptingExpression(RECOMPUTE_JACK);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);
        assertOutputData(output, 1, OperationResultStatus.SUCCESS);
        assertSuccess(result);
        assertEquals("Recomputed user:c0c010c0-d34d-b33f-f00d-111111111111(jack)\n", output.getConsoleOutput());
    }

    @Test
    public void test352RecomputeJackTriggerDirect() throws Exception {
        throw new SkipException("Only in new scripting tests");
    }

    @Test
    public void test353RecomputeJackTriggerOptimized() throws Exception {
        throw new SkipException("Only in new scripting tests");
    }

    @Test
    public void test360AssignCaptainAndDummyRedToJack() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ScriptingExpressionType expression = parseScriptingExpression(ASSIGN_CAPTAIN_AND_DUMMY_RED_TO_JACK);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);
        assertOutputData(output, 1, OperationResultStatus.SUCCESS);

        assertSuccess(result);
        //assertEquals("Recomputed user:c0c010c0-d34d-b33f-f00d-111111111111(jack)\n", output.getConsoleOutput());
        assertUserAfterByUsername(USER_JACK_USERNAME)
                .assignments()
                    .assertAssignments(2)
                    .by()
                        .targetOid(ROLE_CAPTAIN_OID)
                        .find()
                        .end()
                    .by()
                        .resourceOid(RESOURCE_DUMMY_RED_OID)
                        .find()
                        .end();
    }

    @Test
    public void test361UnassignCaptainFromJack() throws Exception {
        throw new SkipException("Only in new scripting tests");
    }

    @Test
    public void test363AssignCaptainByNameToJack() throws Exception {
        throw new SkipException("Only in new scripting tests");
    }

    @Test
    public void test364UnassignAllFromJack() throws Exception {
        throw new SkipException("Only in new scripting tests");
    }

    /**
     * MID-6141
     */
    @Test
    public void test365AssignToJackDryAndRaw() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ScriptingExpressionType expression = parseScriptingExpression(ASSIGN_TO_JACK_DRY_AND_RAW);

        when();
        try {
            evaluator.evaluateExpression(expression, task, result);
            fail("unexpected success");
        } catch (ScriptExecutionException e) {
            displayExpectedException(e);
            assertThat(e).hasMessageContaining("previewChanges is not supported in raw mode");
        }
    }

    @Test
    public void test370AssignNicePirateByNameToJackInBackground() throws Exception {
        given();
        OperationResult result = getTestOperationResult();

        ScriptingExpressionType expression = parseScriptingExpression(ASSIGN_NICE_PIRATE_BY_NAME_TO_JACK);

        when();
        Task task = taskManager.createTaskInstance();
        task.setOwner(getUser(USER_ADMINISTRATOR_OID));
        evaluator.evaluateExpressionInBackground(expression, task, result);
        waitForTaskFinish(task.getOid(), false);
        task.refresh(result);

        then();
        assertSuccess(task.getResult());
        assertUserAfterByUsername(USER_JACK_USERNAME)
                .assignments()
                    .assertRole(ROLE_NICE_PIRATE_OID);
    }

    @Test
    public void test390AssignToWillRolePirateManager() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        assertUserBefore(USER_WILL_OID)
                .assignments()
                    .assertAssignments(3);

        ScriptingExpressionType expression = parseScriptingExpression(ASSIGN_PIRATE_MANAGER_TO_WILL);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);
        assertOutputData(output, 1, OperationResultStatus.SUCCESS);

        assertSuccess(result);
        assertUserAfter(USER_WILL_OID)
                .assignments()
                .assertAssignments(4)
                .by()
                    .targetOid(ROLE_PIRATE_OID)
                    .targetRelation(SchemaConstants.ORG_MANAGER)
                    .find();
    }

    @Test
    public void test391UnassignPirateDefaultFromWill() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();
        ScriptingExpressionType expression = parseScriptingExpression(UNASSIGN_PIRATE_DEFAULT_FROM_WILL);

        assertUserBefore(USER_WILL_OID)
                .assignments()
                .assertAssignments(4)
                .by()
                    .targetOid(ROLE_PIRATE_OID)
                    .targetRelation(SchemaConstants.ORG_MANAGER)
                    .find()
                    .end()
                .by()
                    .targetOid(ROLE_PIRATE_OID)
                    .targetRelation(SchemaConstants.ORG_DEFAULT)
                    .find()
                    .end()
                .by()
                    .targetOid(ROLE_PIRATE_OID)
                    .targetRelation(SchemaConstants.ORG_OWNER)
                    .find()
                    .end()
                .by()
                    .resourceOid(RESOURCE_DUMMY_OID)
                    .find();

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);
        assertOutputData(output, 1, OperationResultStatus.SUCCESS);

        assertSuccess(result);
        assertUserAfter(USER_WILL_OID)
                .assignments()
                .assertAssignments(3)
                .by()
                    .targetOid(ROLE_PIRATE_OID)
                    .targetRelation(SchemaConstants.ORG_MANAGER)
                    .find()
                    .end()
                .by()
                    .targetOid(ROLE_PIRATE_OID)
                    .targetRelation(SchemaConstants.ORG_OWNER)
                    .find()
                    .end()
                .by()
                    .resourceOid(RESOURCE_DUMMY_OID)
                    .find();
    }

    @Test
    public void test392UnassignPirateManagerAndOwnerFromWill() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ScriptingExpressionType expression = parseScriptingExpression(UNASSIGN_PIRATE_MANAGER_AND_OWNER_FROM_WILL);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);
        assertOutputData(output, 1, OperationResultStatus.SUCCESS);

        assertSuccess(result);
        assertUserAfter(USER_WILL_OID)
                .assignments()
                .assertAssignments(1)
                    .by()
                        .resourceOid(RESOURCE_DUMMY_OID)
                        .find();
    }

    @Test
    public void test393UnassignDummyResourceFromWill() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ScriptingExpressionType expression = parseScriptingExpression(UNASSIGN_DUMMY_RESOURCE_FROM_WILL);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);
        assertOutputData(output, 1, OperationResultStatus.SUCCESS);

        assertSuccess(result);
        assertUserAfter(USER_WILL_OID)
                .assignments()
                .assertAssignments(0);
    }

    @Test
    public void test394AssignPirateRelationCaptainToWill() throws Exception {
        given();
        QName customRelation = new QName("http://midpoint.evolveum.com/xml/ns/samples/piracy", "captain");

        Task task = getTestTask();
        OperationResult result = task.getResult();

        ScriptingExpressionType expression = parseScriptingExpression(ASSIGN_PIRATE_RELATION_CAPTAIN_TO_WILL);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);
        assertOutputData(output, 1, OperationResultStatus.SUCCESS);

        assertSuccess(result);
        assertUserAfter(USER_WILL_OID)
                .assignments()
                .assertAssignments(1)
                .by()
                    .targetOid(ROLE_PIRATE_OID)
                    .targetRelation(customRelation)
                    .find();
    }

    @Test
    public void test400PurgeSchema() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ScriptingExpressionType expression = parseScriptingExpression(PURGE_DUMMY_BLACK_SCHEMA);

        assertResourceBefore(RESOURCE_DUMMY_BLACK_OID)
                .assertHasSchema();

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);

        assertSuccess(result);
        assertEquals(1, output.getFinalOutput().getData().size());

        assertEquals("Purged schema information from resource:10000000-0000-0000-0000-000000000305(Dummy Resource Black)\n", output.getConsoleOutput());

        PrismObject<ResourceType> resourceAfter = modelService.getObject(ResourceType.class,
                RESOURCE_DUMMY_BLACK_OID, getOperationOptionsBuilder().noFetch().build(), task, result);
        assertResource(resourceAfter, "after (no fetch)")
                .display()
                .assertHasNoSchema();
    }

    @Test
    public void test410TestResource() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ScriptingExpressionType expression = parseScriptingExpression(TEST_DUMMY_RESOURCE);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);
        ResourceType dummy = modelService.getObject(ResourceType.class, RESOURCE_DUMMY_OID, null, taskManager.createTaskInstance(), result).asObjectable();
        display("dummy resource after test connection", dummy.asPrismObject());

        assertSuccess(result);
        assertEquals(1, output.getFinalOutput().getData().size());
        assertEquals("Tested resource:10000000-0000-0000-0000-000000000004(Dummy Resource): SUCCESS\n", output.getConsoleOutput());
    }

    @Test
    public void test420NotificationAboutJack() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ScriptingExpressionType expression = parseScriptingExpression(NOTIFICATION_ABOUT_JACK);
        prepareNotifications();

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);

        assertSuccess(result);
        assertOutputData(output, 1, OperationResultStatus.SUCCESS);
        assertEquals("Produced 1 event(s)\n", output.getConsoleOutput());

        displayDumpable("Dummy transport", dummyTransport);
        checkDummyTransportMessages("Custom", 1);
        Message m = dummyTransport.getMessages("dummy:Custom").get(0);
        assertEquals("Wrong message body", "jack/" + USER_JACK_OID, m.getBody());
        assertEquals("Wrong message subject", "Ad hoc notification", m.getSubject());
    }

    @Test
    public void test430NotificationAboutJackType2() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ScriptingExpressionType expression = parseScriptingExpression(NOTIFICATION_ABOUT_JACK_TYPE2);
        prepareNotifications();

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);

        assertSuccess(result);
        assertOutputData(output, 1, OperationResultStatus.SUCCESS);
        assertEquals("Produced 1 event(s)\n", output.getConsoleOutput());

        displayDumpable("Dummy transport", dummyTransport);
        checkDummyTransportMessages("Custom", 1);
        Message m = dummyTransport.getMessages("dummy:Custom").get(0);
        assertEquals("Wrong message body", "1", m.getBody());
        assertEquals("Wrong message subject", "Ad hoc notification 2", m.getSubject());

        checkDummyTransportMessages("CustomType2", 1);
        m = dummyTransport.getMessages("dummy:CustomType2").get(0);
        assertEquals("Wrong message body", "POV:user:c0c010c0-d34d-b33f-f00d-111111111111(jack)", m.getBody());
        assertEquals("Wrong message subject", "Failure notification of type 2", m.getSubject());
    }

    @Test
    public void test500ScriptingUsers() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ScriptingExpressionType expression = parseScriptingExpression(SCRIPTING_USERS);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);

        assertSuccess(result);
        PipelineData data = output.getFinalOutput();
        assertEquals("Unexpected # of items in output", 6, data.getData().size());
        Set<String> realOids = new HashSet<>();
        for (PipelineItem item : data.getData()) {
            PrismValue value = item.getValue();
            //noinspection unchecked
            UserType user = ((PrismObjectValue<UserType>) value).asObjectable();
            assertEquals("Description not set", "Test", user.getDescription());
            realOids.add(user.getOid());
            assertSuccess(item.getResult());
        }
        assertEquals("Unexpected OIDs in output",
                Sets.newHashSet(Arrays.asList(USER_ADMINISTRATOR_OID, USER_JACK_OID, USER_BARBOSSA_OID, USER_GUYBRUSH_OID, USER_ELAINE_OID, USER_WILL_OID)),
                realOids);
    }

    @Test
    public void test505ScriptingUsersInBackground() throws Exception {
        given();
        Task task = getTestTask();
        task.setOwner(getUser(USER_ADMINISTRATOR_OID));
        OperationResult result = task.getResult();

        ExecuteScriptType exec = parseExecuteScript(SCRIPTING_USERS_IN_BACKGROUND);

        when();

        task.setExtensionPropertyValue(SchemaConstants.SE_EXECUTE_SCRIPT, exec);
        task.getExtensionOrClone()
                .findOrCreateProperty(USER_NAME_TASK_EXTENSION_PROPERTY)
                .addRealValue(USER_ADMINISTRATOR_USERNAME);
        task.getExtensionOrClone()
                .findOrCreateProperty(USER_DESCRIPTION_TASK_EXTENSION_PROPERTY)
                .addRealValue("admin description");
        task.setHandlerUri(ModelPublicConstants.SCRIPT_EXECUTION_TASK_HANDLER_URI);

        dummyTransport.clearMessages();
        boolean notificationsDisabled = notificationManager.isDisabled();
        notificationManager.setDisabled(false);

        taskManager.switchToBackground(task, result);

        waitForTaskFinish(task.getOid(), false);
        task.refresh(result);

        then();
        display(task.getResult());
        TestUtil.assertSuccess(task.getResult());
        PrismObject<UserType> admin = getUser(USER_ADMINISTRATOR_OID);
        display("admin after operation", admin);
        assertEquals("Wrong description", "admin description", admin.asObjectable().getDescription());

        displayDumpable("dummy transport", dummyTransport);
        notificationManager.setDisabled(notificationsDisabled);

        assertEquals("Wrong # of messages in dummy transport", 1,
                emptyIfNull(dummyTransport.getMessages("dummy:simpleUserNotifier")).size());
    }

    @Test
    public void test507ScriptingUsersInBackgroundAssign() throws Exception {
        given();
        Task task = getTestTask();
        task.setOwner(getUser(USER_ADMINISTRATOR_OID));
        OperationResult result = task.getResult();

        ExecuteScriptType exec = parseExecuteScript(SCRIPTING_USERS_IN_BACKGROUND_ASSIGN);

        when();

        task.setExtensionPropertyValue(SchemaConstants.SE_EXECUTE_SCRIPT, exec);
        task.setHandlerUri(ModelPublicConstants.SCRIPT_EXECUTION_TASK_HANDLER_URI);

        dummyTransport.clearMessages();
        boolean notificationsDisabled = notificationManager.isDisabled();
        notificationManager.setDisabled(false);

        taskManager.switchToBackground(task, result);

        waitForTaskFinish(task.getOid(), false);
        task.refresh(result);

        then();
        display(task.getResult());
        TestUtil.assertSuccess(task.getResult());
        PrismObject<UserType> admin = getUser(USER_ADMINISTRATOR_OID);
        display("admin after operation", admin);
        assertAssignedRole(admin, ROLE_EMPTY_OID);

        displayDumpable("dummy transport", dummyTransport);
        notificationManager.setDisabled(notificationsDisabled);

        assertEquals("Wrong # of messages in dummy transport", 1,
                emptyIfNull(dummyTransport.getMessages("dummy:simpleUserNotifier")).size());
    }

    @Test
    public void test510GeneratePasswords() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ScriptingExpressionType expression = parseScriptingExpression(GENERATE_PASSWORDS);

        addObject(PASSWORD_POLICY_GLOBAL_FILE);

        List<ItemDelta<?, ?>> itemDeltas = prismContext.deltaFor(SecurityPolicyType.class)
                .item(SecurityPolicyType.F_CREDENTIALS, CredentialsPolicyType.F_PASSWORD,
                        PasswordCredentialsPolicyType.F_VALUE_POLICY_REF)
                .add(itemFactory().createReferenceValue(PASSWORD_POLICY_GLOBAL_OID))
                .asItemDeltas();
        modifySystemObjectInRepo(SecurityPolicyType.class, SECURITY_POLICY_OID, itemDeltas, result);

        when();
        ExecutionContext output = evaluator.evaluateExpression(expression, task, result);

        then();
        dumpOutput(output, result);

        assertSuccess(result);
        PipelineData data = output.getFinalOutput();
        assertEquals("Unexpected # of items in output", 6, data.getData().size());
        Set<String> realOids = new HashSet<>();
        for (PipelineItem item : data.getData()) {
            PrismValue value = item.getValue();
            //noinspection unchecked
            UserType user = ((PrismObjectValue<UserType>) value).asObjectable();
            ProtectedStringType passwordValue = user.getCredentials().getPassword().getValue();
            assertNotNull("clearValue for password not set", passwordValue.getClearValue());
            realOids.add(user.getOid());
        }
        assertEquals("Unexpected OIDs in output",
                Sets.newHashSet(Arrays.asList(USER_ADMINISTRATOR_OID, USER_JACK_OID, USER_BARBOSSA_OID, USER_GUYBRUSH_OID, USER_ELAINE_OID, USER_WILL_OID)),
                realOids);
    }

    @Test
    public void test520GeneratePasswordsFullInput() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ExecuteScriptType executeScript = parseExecuteScript(GENERATE_PASSWORDS_2);

        when();
        ExecutionContext output = evaluator.evaluateExpression(executeScript, VariablesMap.emptyMap(), false, task, result);

        then();
        dumpOutput(output, result);

        //assertSuccess(result);
        PipelineData data = output.getFinalOutput();
        List<PipelineItem> items = data.getData();
        assertEquals("Unexpected # of items in output", 4, items.size());
        assertSuccess(items.get(0).getResult());
        assertFailure(items.get(1).getResult());
        assertSuccess(items.get(2).getResult());
        assertSuccess(items.get(3).getResult());
    }

    @Test
    public void test530GeneratePasswordsReally() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ExecuteScriptType executeScript = parseExecuteScript(GENERATE_PASSWORDS_3);

        when();
        ExecutionContext output = evaluator.evaluateExpression(executeScript, VariablesMap.emptyMap(), false, task, result);

        then();
        dumpOutput(output, result);

        PipelineData data = output.getFinalOutput();
        List<PipelineItem> items = data.getData();
        assertEquals("Unexpected # of items in output", 3, items.size());
        assertFailure(items.get(0).getResult());
        assertSuccess(items.get(1).getResult());
        assertSuccess(items.get(2).getResult());

        checkPassword(items.get(1), USER_GUYBRUSH_OID);
        checkPassword(items.get(2), USER_ELAINE_OID);
    }

    @SuppressWarnings("unchecked")
    private void checkPassword(PipelineItem item, String userOid)
            throws CommunicationException, ObjectNotFoundException, SchemaException, SecurityViolationException,
            ConfigurationException, ExpressionEvaluationException, EncryptionException {
        PrismProperty<ProtectedStringType> returnedPassword = (PrismProperty<ProtectedStringType>)
                item.getValue().find(SchemaConstants.PATH_PASSWORD_VALUE);
        ProtectedStringType returnedRealValue = returnedPassword.getRealValue();
        PrismObject<UserType> user = getUser(userOid);
        ProtectedStringType repoRealValue = user.asObjectable().getCredentials().getPassword().getValue();
        String returnedClearValue = protector.decryptString(returnedRealValue);
        String repoClearValue = protector.decryptString(repoRealValue);
        System.out.println("Returned password = " + returnedClearValue + ", repo password = " + repoClearValue);
        assertEquals("Wrong password stored in repository", returnedClearValue, repoClearValue);
    }

    @Test
    public void test550UseVariables() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();
        ExecuteScriptType executeScript = parseExecuteScript(USE_VARIABLES);

        PrismContainer<? extends ExtensionType> taskExtension = task.getOrCreateExtension();
        taskExtension
                .findOrCreateProperty(USER_NAME_TASK_EXTENSION_PROPERTY)
                .addRealValue("user1");
        taskExtension
                .findOrCreateProperty(STUDY_GROUP_TASK_EXTENSION_PROPERTY)
                .addRealValues("group1", "group2", "group3");

        when();
        ExecutionContext output = evaluator.evaluateExpression(executeScript, VariablesMap.emptyMap(), false, task, result);

        then();
        dumpOutput(output, result);

        PipelineData data = output.getFinalOutput();
        assertEquals("Unexpected # of items in output", 1, data.getData().size());

        String returned = data.getData().get(0).getValue().getRealValue();
        assertEquals("Wrong returned status", "ok", returned);
    }

    @Test
    public void test560StartTaskFromTemplate() throws Exception {
        given();
        Task task = getTestTask();
        task.setOwner(getUser(USER_ADMINISTRATOR_OID));
        OperationResult result = task.getResult();

        repoAddObject(ROLE_OPERATOR, result);
        repoAddObject(USER_OPERATOR, result);

        repoAddObjectFromFile(SCRIPTING_USERS_IN_BACKGROUND_TASK_FILE, result);
        ExecuteScriptType exec = parseExecuteScript(START_TASKS_FROM_TEMPLATE);

        when();
        ExecutionContext output;
        try {
            setEnableRunAsTaskTemplateOwnerAuthorization(true, result);
            login(getUser(USER_OPERATOR.oid));
            output = evaluator.evaluateExpression(exec, VariablesMap.emptyMap(), false, task, result);
        } finally {
            login(userAdministrator);
            setEnableRunAsTaskTemplateOwnerAuthorization(false, result);
        }

        then();
        dumpOutput(output, result);

        PipelineData data = output.getFinalOutput();
        assertEquals("Unexpected # of items in output", 2, data.getData().size());

        String oid1 = ((PrismObjectValue<?>) data.getData().get(0).getValue()).getOid();
        String oid2 = ((PrismObjectValue<?>) data.getData().get(1).getValue()).getOid();

        waitForTaskCloseOrSuspend(oid1, 20000);
        waitForTaskCloseOrSuspend(oid2, 20000);

        PrismObject<UserType> jack = getUser(USER_JACK_OID);
        PrismObject<UserType> administrator = getUser(USER_ADMINISTRATOR_OID);
        display("jack", jack);
        display("administrator", administrator);
        assertEquals("Wrong jack description", "new desc jack", jack.asObjectable().getDescription());
        assertEquals("Wrong administrator description", "new desc admin", administrator.asObjectable().getDescription());

        // cleaning up the tasks

        Thread.sleep(5000L); // cleanup is set to 1 second after completion

        importObjectFromFile(TASK_TRIGGER_SCANNER_FILE);

        waitForTaskStart(TASK_TRIGGER_SCANNER_OID, false);
        waitForTaskFinish(TASK_TRIGGER_SCANNER_OID, true);

        assertNoObject(TaskType.class, oid1, task, result);
        assertNoObject(TaskType.class, oid2, task, result);

        taskManager.suspendTasks(singleton(TASK_TRIGGER_SCANNER_OID), 10000L, result);
    }

    @Test
    public void test570IterativeScriptingTask() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();

        String taskOid = repoAddObjectFromFile(getFile(SCRIPTING_USERS_IN_BACKGROUND_ITERATIVE_TASK), result).getOid();
        int numberOfUsers = modelService.countObjects(UserType.class, null, null, task, result);

        when();
        Task taskAfterFirstRun = waitForTaskFinish(taskOid, false);

        then();
        PrismObject<UserType> jack = getUser(USER_JACK_OID);
        PrismObject<UserType> administrator = getUser(USER_ADMINISTRATOR_OID);
        display("jack", jack);
        display("administrator", administrator);
        assertEquals("Wrong jack description", "hello jack", jack.asObjectable().getDescription());
        assertEquals("Wrong administrator description", "hello administrator", administrator.asObjectable().getDescription());

        assertTask(taskAfterFirstRun, "task after first run")
                .assertProgress(numberOfUsers)
                .iterativeTaskInformation()
                    .assertSuccessCount(numberOfUsers);

        // Testing for MID-6488
        when("second run");
        Task taskAfterSecondRun = rerunTask(taskOid, result);

        then("second run");
        assertTask(taskAfterSecondRun, "task after second run")
                .assertProgress(numberOfUsers)
                .iterativeTaskInformation()
                    .assertSuccessCount(numberOfUsers);
    }

    @Test
    public void test575ResumeTask() throws Exception {
        given();
        Task task = getTestTask();
        task.setOwner(getUser(USER_ADMINISTRATOR_OID));
        OperationResult result = task.getResult();

        String taskToResumeOid = addObject(TASK_TO_RESUME_FILE);
        addObject(TASK_TO_KEEP_SUSPENDED_FILE);

        ExecuteScriptType exec = parseExecuteScript(RESUME_SUSPENDED_TASKS);

        when();
        ExecutionContext output = evaluator.evaluateExpression(exec, VariablesMap.emptyMap(), false, task, result);

        then();
        dumpOutput(output, result);

        // the task should be there
        assertEquals("Unexpected # of items in output", 1, output.getFinalOutput().getData().size());

        PrismObject<TaskType> taskAfter = getObject(TaskType.class, taskToResumeOid);
        assertNotSame("Task is still suspended", taskAfter.asObjectable().getExecutionStatus(), TaskExecutionStateType.SUSPENDED);
    }

    /**
     * MID-5359
     */
    @Test
    public void test600ModifyJackPasswordInBackground() throws Exception {
        given();
        OperationResult result = getTestOperationResult();

        ScriptingExpressionType expression = parseScriptingExpression(MODIFY_JACK_PASSWORD);

        prepareNotifications();
        dummyAuditService.clear();

        when();
        Task task = taskManager.createTaskInstance();
        task.setOwner(getUser(USER_ADMINISTRATOR_OID));
        evaluator.evaluateExpressionInBackground(expression, task, result);
        waitForTaskFinish(task.getOid(), false);
        task.refresh(result);

        then();
        display(task.getResult());
        TestUtil.assertSuccess(task.getResult());
        PrismObject<UserType> jack = getUser(USER_JACK_OID);
        display("jack after password change", jack);
        assertEncryptedUserPassword(jack, PASSWORD_PLAINTEXT_1);

        String xml = prismContext.xmlSerializer().serialize(task.getUpdatedTaskObject());
        displayValue("task", xml);
        assertFalse("Plaintext password is present in the task", xml.contains(PASSWORD_PLAINTEXT_FRAGMENT));

        displayDumpable("Dummy transport", dummyTransport);
        displayDumpable("Audit", dummyAuditService);
    }

    /**
     * MID-5359
     */
    @Test
    public void test610ModifyJackPasswordImportingTask() throws Exception {
        given();
        Task opTask = getTestTask();
        opTask.setOwner(getUser(USER_ADMINISTRATOR_OID));
        OperationResult result = opTask.getResult();

        prepareNotifications();
        dummyAuditService.clear();

        when();
        FileInputStream stream = new FileInputStream(getFile(MODIFY_JACK_PASSWORD_TASK));
        modelService.importObjectsFromStream(stream, PrismContext.LANG_XML, null, opTask, result);
        stream.close();

        assertSuccess(result);

        Task task = waitForTaskFinish(MODIFY_JACK_PASSWORD_TASK_OID, false);

        then();
        display(task.getResult());
        TestUtil.assertSuccess(task.getResult());
        PrismObject<UserType> jack = getUser(USER_JACK_OID);
        display("jack after password change", jack);
        assertEncryptedUserPassword(jack, PASSWORD_PLAINTEXT_2);

        String xml = prismContext.xmlSerializer().serialize(task.getUpdatedTaskObject());
        displayValue("task", xml);
        assertFalse("Plaintext password is present in the task", xml.contains(PASSWORD_PLAINTEXT_FRAGMENT));

        displayDumpable("Dummy transport", dummyTransport);
        displayDumpable("Audit", dummyAuditService);
    }

    /**
     * MID-5359 (not using scripting as such, but related)
     */
    @Test
    public void test620ModifyJackPasswordViaExecuteChangesAsynchronously() throws Exception {
        given();
        Task opTask = getTestTask();
        opTask.setOwner(getUser(USER_ADMINISTRATOR_OID));
        OperationResult result = opTask.getResult();

        prepareNotifications();
        dummyAuditService.clear();

        when();
        ProtectedStringType password = new ProtectedStringType();
        password.setClearValue(PASSWORD_PLAINTEXT_3);

        ObjectDelta<UserType> delta = prismContext.deltaFor(UserType.class)
                .item(UserType.F_CREDENTIALS, CredentialsType.F_PASSWORD, PasswordType.F_VALUE)
                .replace(password)
                .asObjectDelta(USER_JACK_OID);
        TaskType newTask = libraryMidpointFunctions.executeChangesAsynchronously(singleton(delta), null, null, opTask, result);

        assertSuccess(result);

        Task task = waitForTaskFinish(newTask.getOid(), false);

        then();
        display(task.getResult());
        TestUtil.assertSuccess(task.getResult());
        PrismObject<UserType> jack = getUser(USER_JACK_OID);
        display("jack after password change", jack);
        assertEncryptedUserPassword(jack, PASSWORD_PLAINTEXT_3);

        String xml = prismContext.xmlSerializer().serialize(task.getUpdatedTaskObject());
        displayValue("task", xml);
        assertFalse("Plaintext password is present in the task", xml.contains(PASSWORD_PLAINTEXT_FRAGMENT));

        displayDumpable("Dummy transport", dummyTransport);
        displayDumpable("Audit", dummyAuditService);
    }

    private void assertNoOutputData(ExecutionContext output) {
        assertTrue("Script returned unexpected data", output.getFinalOutput() == null || output.getFinalOutput().getData().isEmpty());
    }

    @SuppressWarnings("SameParameterValue")
    void assertOutputData(ExecutionContext output, int size, OperationResultStatus status) {
        assertEquals("Wrong # of output items", size, output.getFinalOutput().getData().size());
        for (PipelineItem item : output.getFinalOutput().getData()) {
            assertEquals("Wrong op result status", status, item.getResult().getStatus());
        }
    }

    // the following tests are a bit crude but for now it should be OK

    private void assertAttributesNotFetched(List<PipelineItem> data) {
        for (PipelineItem item : data) {
            PrismValue value = item.getValue();
            //noinspection unchecked
            if (((PrismObjectValue<ShadowType>) value).asObjectable().getAttributes().getAny().size() > 2) {
                throw new AssertionError("There are some unexpected attributes present in " + value.debugDump());
            }
        }
    }

    private void assertAttributesFetched(List<PipelineItem> data) {
        for (PipelineItem item : data) {
            PrismValue value = item.getValue();
            //noinspection unchecked
            if (((PrismObjectValue<ShadowType>) value).asObjectable().getAttributes().getAny().size() <= 2) {
                throw new AssertionError("There are no attributes present in " + value.debugDump());
            }
        }
    }

    void dumpOutput(ExecutionContext output, OperationResult result) throws SchemaException {
        displayDumpable("output", output.getFinalOutput());
        displayValue("stdout", output.getConsoleOutput());
        display(result);
        if (output.getFinalOutput() != null) {
            PipelineDataType bean = PipelineData.prepareXmlData(output.getFinalOutput().getData(), null);
            displayValue("output in XML", prismContext.xmlSerializer().root(new QName("output")).serializeRealValue(bean));
        }
    }

    ScriptingExpressionType parseScriptingExpression(File file) throws IOException, SchemaException {
        // we cannot specify explicit type parameter here, as the parsed files contain subtypes of ScriptingExpressionType
        return prismContext.parserFor(file).parseRealValue();
    }

    private ScriptingExpressionType parseScriptingExpression(String name) throws IOException, SchemaException {
        return parseScriptingExpression(getFile(name));
    }

    ExecuteScriptType parseExecuteScript(File file) throws IOException, SchemaException {
        return prismContext.parserFor(file).parseRealValue(ExecuteScriptType.class);
    }

    private ExecuteScriptType parseExecuteScript(String name) throws IOException, SchemaException {
        return parseExecuteScript(getFile(name));
    }

    private void setEnableRunAsTaskTemplateOwnerAuthorization(boolean value, OperationResult result)
            throws ObjectAlreadyExistsException, ObjectNotFoundException, SchemaException {
        List<ItemDelta<?, ?>> itemDeltas = deltaFor(SystemConfigurationType.class)
                .item(SystemConfigurationType.F_INTERNALS, InternalsConfigurationType.F_ENABLE_RUN_AS_TASK_TEMPLATE_OWNER_AUTHORIZATION)
                .replace(value)
                .asItemDeltas();
        repositoryService.modifyObject(SystemConfigurationType.class, SystemObjectsType.SYSTEM_CONFIGURATION.value(), itemDeltas,
                result);
    }
}
