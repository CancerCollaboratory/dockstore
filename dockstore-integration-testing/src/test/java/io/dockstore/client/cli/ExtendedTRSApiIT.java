/*
 *    Copyright 2018 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.client.cli;

import static io.dockstore.client.cli.ExtendedMetricsTRSOpenApiIT.DOCKSTORE_WORKFLOW_CNV_REPO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.Registry;
import io.dockstore.common.SourceControl;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ContainersApi;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.DockstoreTool;
import io.dockstore.openapi.client.model.UpdateAITopicRequest;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.Workflow.TopicSelectionEnum;
import io.dockstore.openapi.client.model.WorkflowVersion;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * Extra confidential integration tests, focuses on proposed GA4GH extensions.
 * {@link BaseIT}
 *
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
class ExtendedTRSApiIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    @Test
    void testSimpleIndex() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final ContainersApi containersApi = new ContainersApi(webClient);

        Workflow workflow = workflowsApi.manualRegister(SourceControl.GITHUB.name(), DOCKSTORE_WORKFLOW_CNV_REPO, "/workflow/cnv.cwl", "my-workflow", "cwl",
            "/test.json");
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        workflowsApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        Workflow checkerWorkflow = workflowsApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser2/hello-dockstore-workflow", "/Dockstore.cwl", "cwlworkflow",
            DescriptorLanguage.CWL.getShortName(), "");
        checkerWorkflow = workflowsApi.refresh1(checkerWorkflow.getId(), true);
        workflowsApi.publish1(checkerWorkflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        // Manually register a tool
        DockstoreTool newTool = new DockstoreTool();
        newTool.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);
        newTool.setName("my-md5sum");
        newTool.setGitUrl("git@github.com:DockstoreTestUser2/md5sum-checker.git");
        newTool.setDefaultDockerfilePath("/md5sum/Dockerfile");
        newTool.setDefaultCwlPath("/md5sum/md5sum-tool.cwl");
        newTool.setRegistryString(Registry.QUAY_IO.getDockerPath());
        newTool.setNamespace("dockstoretestuser2");
        newTool.setToolname("altname");
        newTool.setPrivateAccess(false);
        newTool.setDefaultCWLTestParameterFile("/testcwl.json");
        DockstoreTool githubTool = containersApi.registerManual(newTool);

        // Refresh the workflow
        DockstoreTool refresh = containersApi.refresh(githubTool.getId());
        containersApi.publish(refresh.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        // Try to set a single checker workflow to two entries
        // TODO: For now, this subtest is disabled.  It will probably be
        // reenabled in some form as part of https://ucsc-cgl.atlassian.net/browse/SEAB-5848
        /*
        testingPostgres.runUpdateStatement("update workflow set checkerid = '" + checkerWorkflow.getId() + "' where id = '" + workflow.getId() + "'");
        try {
            testingPostgres.runUpdateStatement("update tool set checkerid = '" + checkerWorkflow.getId() + "' where id = '"
                    + refresh.getId() + "'");
            fail("Should have had a constraint violation");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("violates check constraint \"check_tool_checkerid_globally_unique\""));
        }
        */
        testingPostgres.runUpdateStatement("update workflow set checkerid = '" + checkerWorkflow.getId() + "' where id = '" + workflow.getId() + "'");
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow where checkerid = " + checkerWorkflow.getId(), long.class);
        workflowCount += testingPostgres.runSelectStatement("select count(*) from tool where checkerid = " + checkerWorkflow.getId(), long.class);
        assertEquals(1, workflowCount);

        ExtendedGa4GhApi api = new ExtendedGa4GhApi(webClient);
        // test json results
        final Integer integer = api.updateTheWorkflowsAndToolsIndices();
        assertTrue(integer > 0);
        // test text results
        final String textResults = webClient
            .invokeAPI("/api/ga4gh/v2/extended/tools/index", "POST", new ArrayList<>(), null, new HashMap<>(), new HashMap<>(), MediaType.TEXT_PLAIN, MediaType.TEXT_PLAIN,
                new String[]{"BEARER"}, new GenericType<>(String.class));
        assertTrue(Integer.parseInt(textResults) >= 3);
    }

    @Test
    void testUpdateAITopic() {
        // Admin user
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        // Non-admin user
        final ApiClient otherWebClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        final ExtendedGa4GhApi otherExtendedGa4GhApi = new ExtendedGa4GhApi(otherWebClient);
        final String trsId = "#workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv";
        final String aiTopic = "This is an AI topic";
        final UpdateAITopicRequest updateAITopicRequest = new UpdateAITopicRequest().aiTopic(aiTopic);

        Workflow workflow = workflowsApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "",
                DescriptorLanguage.CWL.toString(), "/test.json");
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        assertNull(workflow.getTopicAI());
        assertNull(workflow.getTopicManual());
        assertNotNull(workflow.getTopicAutomatic());
        assertEquals(TopicSelectionEnum.AUTOMATIC, workflow.getTopicSelection());

        // Should not be able to submit AI topic for unpublished workflow
        ApiException exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.updateAITopic(updateAITopicRequest, "develop", trsId));
        assertEquals(HttpStatus.SC_NOT_FOUND, exception.getCode());

        // Publish the workflow
        workflowsApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        String versionName = extendedGa4GhApi.getAITopicCandidate(trsId);

        long latestDate = workflow.getWorkflowVersions().stream().filter(t -> t.getName().equals(versionName)).findFirst().get().getDbUpdateDate();
        assertTrue(workflow.getWorkflowVersions().stream().allMatch(t -> t.getLastModified() <= latestDate), latestDate + " issue with " + workflow.getWorkflowVersions().stream().map(
            WorkflowVersion::getLastModified).collect(
            Collectors.toSet()));

        assertFalse(testingPostgres.runSelectStatement("select aitopicprocessed from workflowversion where name = '" + versionName + "' and parentid = " + workflow.getId(), Boolean.class));
        // Non-admin user should not be able to submit AI topic
        exception = assertThrows(ApiException.class, () -> otherExtendedGa4GhApi.updateAITopic(updateAITopicRequest, versionName, trsId));
        assertEquals(HttpStatus.SC_FORBIDDEN, exception.getCode());

        // Admin should be able to submit AI topic for published workflow
        assertThrows(ApiException.class, () -> extendedGa4GhApi.updateAITopic(updateAITopicRequest, "messed up version that does not exist", trsId));
        extendedGa4GhApi.updateAITopic(updateAITopicRequest, versionName, trsId);
        assertTrue(testingPostgres.runSelectStatement("select aitopicprocessed from workflowversion where name = '" + versionName + "' and parentid = " + workflow.getId(), Boolean.class));
        workflow = workflowsApi.getWorkflow(workflow.getId(), null);
        assertEquals(aiTopic, workflow.getTopicAI());
        assertEquals(TopicSelectionEnum.AUTOMATIC, workflow.getTopicSelection()); // Topic selection is unchanged because an automatic topic exists

        // Set topic automatic to null and update AI topic again. The topic selection should automatically be AI
        testingPostgres.runUpdateStatement("update workflow set topicautomatic = null where id = " + workflow.getId());
        extendedGa4GhApi.updateAITopic(updateAITopicRequest, versionName, trsId);
        workflow = workflowsApi.getWorkflow(workflow.getId(), null);
        assertEquals(TopicSelectionEnum.AI, workflow.getTopicSelection());
        assertFalse(workflow.isApprovedAITopic(), "Should be false because the user didn't approve the AI topic");

        // User approves AI topic
        workflow.setApprovedAITopic(true);
        workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflow = workflowsApi.getWorkflow(workflow.getId(), null);
        assertTrue(workflow.isApprovedAITopic());

        // Update AI topic. approvedAITopic should be reset to false
        extendedGa4GhApi.updateAITopic(updateAITopicRequest, versionName, trsId);
        workflow = workflowsApi.getWorkflow(workflow.getId(), null);
        assertFalse(workflow.isApprovedAITopic(), "Should be false because it's a new AI topic");
    }

    @Test
    void testUpdateAITopicTools() {
        // Admin user
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final ContainersApi containersApi = new ContainersApi(webClient);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        // Non-admin user
        final ApiClient otherWebClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        final ExtendedGa4GhApi otherExtendedGa4GhApi = new ExtendedGa4GhApi(otherWebClient);
        final String trsId = "quay.io/dockstoretestuser2/quayandgithub";
        final String aiTopic = "This is an AI topic";
        final UpdateAITopicRequest updateAITopicRequest = new UpdateAITopicRequest().aiTopic(aiTopic);

        DockstoreTool containerByToolPath = containersApi.getContainerByToolPath(trsId, null);
        // Publish the tool
        containersApi.publish(containerByToolPath.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        String versionName = extendedGa4GhApi.getAITopicCandidate(trsId);
        assertThrows(ApiException.class, () -> extendedGa4GhApi.getAITopicCandidate("messed up id that does not exist"));

        Long latestDate = containerByToolPath.getWorkflowVersions().stream().filter(t -> t.getName().equals(versionName)).findFirst().get().getDbUpdateDate();
        assertTrue(containerByToolPath.getWorkflowVersions().stream().allMatch(t -> t.getDbUpdateDate() <= latestDate));

        assertFalse(testingPostgres.runSelectStatement("select aitopicprocessed from tag where name = '" + versionName + "'", Boolean.class));
        // Non-admin user should not be able to submit AI topic
        ApiException apiException = assertThrows(ApiException.class, () -> otherExtendedGa4GhApi.updateAITopic(updateAITopicRequest, versionName, trsId));
        assertEquals(HttpStatus.SC_FORBIDDEN, apiException.getCode());

        // Admin should be able to submit AI topic for published workflow
        assertThrows(ApiException.class, () -> extendedGa4GhApi.updateAITopic(updateAITopicRequest, "messed up version that does not exist", trsId));
        extendedGa4GhApi.updateAITopic(updateAITopicRequest, versionName, trsId);
        containerByToolPath = containersApi.getContainerByToolPath(trsId, null);
        assertEquals(DockstoreTool.TopicSelectionEnum.AI, containerByToolPath.getTopicSelection());
        assertEquals(aiTopic, containerByToolPath.getTopicAI());
        assertTrue(testingPostgres.runSelectStatement("select aitopicprocessed from tag where name = '" + versionName + "' and parentid = " + containerByToolPath.getId(), Boolean.class));
    }
}
