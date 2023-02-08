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
package io.dockstore.webservice;

import static io.dockstore.common.CommonTestUtilities.restartElasticsearch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.client.cli.WorkflowIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.MuteForSuccessfulTests;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.ApiResponse;
import io.swagger.client.api.EntriesApi;
import io.swagger.client.api.ExtendedGa4GhApi;
import io.swagger.client.api.MetadataApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Workflow;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

/**
 * @author dyuen
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
class SearchResourceIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    private final String exampleESQuery = "{\"size\":201,\"_source\":{\"excludes\":[\"*.content\",\"*.sourceFiles\",\"description\",\"users\",\"workflowVersions.dirtyBit\",\"workflowVersions.hidden\",\"workflowVersions.last_modified\",\"workflowVersions.name\",\"workflowVersions.valid\",\"workflowVersions.workflow_path\",\"workflowVersions.workingDirectory\",\"workflowVersions.reference\"]},\"query\":{\"match_all\":{}}}";

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        restartElasticsearch();
    }

    /**
     * Continuously checks the elasticsearch index to see if it has the correct amount of entries
     * Increasing amount of sleep time, up to 15 seconds or so
     * @param hit   The amount of hits expected
     * @param extendedGa4GhApi  The api to get the elasticsearch results
     * @param counter   The amount of tries attempted
     */
    private void waitForIndexRefresh(int hit, ExtendedGa4GhApi extendedGa4GhApi, int counter) {
        try {
            String s = extendedGa4GhApi.toolsIndexSearch(exampleESQuery);
            // There's actually two "total", one for shards and one for hits.
            // Need to only look at the hits one
            if (!s.contains("hits\":{\"total\":{\"value\":" + hit + ",")) {
                if (counter > 5) {
                    fail(s + " does not have the correct amount of hits");
                } else {
                    long sleepTime = 1000 * counter;
                    Thread.sleep(sleepTime);
                    waitForIndexRefresh(hit, extendedGa4GhApi, counter + 1);
                }
            }
        } catch (Exception e) {
            fail("There were troubles sleeping: " + e.getMessage());
        }
    }

    @Test
    void testSearchOperations() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        EntriesApi entriesApi = new EntriesApi(webClient);
        // update the search index
        ApiResponse<Void> voidApiResponse = extendedGa4GhApi.toolsIndexGetWithHttpInfo();
        int statusCode = voidApiResponse.getStatusCode();
        assertEquals(200, statusCode);
        waitForIndexRefresh(0, extendedGa4GhApi, 0);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi
            .getWorkflowByPath(WorkflowIT.DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, BIOWORKFLOW, null);
        // do targeted refresh, should promote workflow to fully-fleshed out workflow
        final Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId(), false);
        entriesApi.addAliases(workflow.getId(), "potatoAlias");
        workflowApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(false));
        workflowApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(true));
        waitForIndexRefresh(1, extendedGa4GhApi,  0);
        // after publication index should include workflow
        String s = extendedGa4GhApi.toolsIndexSearch(exampleESQuery);
        assertTrue(s.contains("\"aliases\":{\"potatoAlias\":{}}"), s + " should've contained potatoAlias");
        assertFalse(s.contains("\"aliases\":null"));
        assertTrue(s.contains(WorkflowIT.DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW));
        // ensure source file returns
        String newQuery = StringUtils.replace(exampleESQuery, "*.sourceFiles", "");
        String t = extendedGa4GhApi.toolsIndexSearch(newQuery);
        assertTrue(t.contains("sourceFiles") && t.contains("\"checksum\":\"cb5d0323091b22e0a1d6f52a4930ee256b15835c968462c03cf7be2cc842a4ad\""), t + " should've contained sourcefiles");
    }

    /**
     * This tests that the elastic search health check will fail if the Docker container is down, the
     * index is not made, or the index is made but there are no results.
     */
    @Test
    void testElasticSearchHealthCheck() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        MetadataApi metadataApi = new MetadataApi(webClient);

        // Should fail with no index
        try {
            metadataApi.checkElasticSearch();
        } catch (ApiException ex) {
            assertTrue(true, "Should fail");
        }

        // Update the search index
        extendedGa4GhApi.toolsIndexGet();
        waitForIndexRefresh(0, extendedGa4GhApi,  0);
        try {
            metadataApi.checkElasticSearch();
            fail("Should fail even with index because there's no hits");
        } catch (ApiException ex) {
            assertTrue(ex.getMessage().contains("Internal Server Error"));
        }

        // Register and publish workflow
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi
            .getWorkflowByPath(WorkflowIT.DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, BIOWORKFLOW, null);
        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        final Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId(), false);

        workflowApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(true));

        waitForIndexRefresh(1, extendedGa4GhApi,  0);

        // Should not fail since a workflow exists in index
        try {
            metadataApi.checkElasticSearch();
        } catch (ApiException ex) {
            fail("Should not fail");
        }
    }
}
