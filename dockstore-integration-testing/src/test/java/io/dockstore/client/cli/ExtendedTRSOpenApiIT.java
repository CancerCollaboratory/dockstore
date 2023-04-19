/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.client.cli;

import static io.dockstore.common.LocalStackTestUtilities.IMAGE_TAG;
import static io.dockstore.common.LocalStackTestUtilities.createBucket;
import static io.dockstore.common.LocalStackTestUtilities.deleteBucketContents;
import static io.dockstore.webservice.core.metrics.ExecutionStatusCountMetric.ExecutionStatus.FAILED_RUNTIME_INVALID;
import static io.dockstore.webservice.core.metrics.ExecutionStatusCountMetric.ExecutionStatus.FAILED_SEMANTIC_INVALID;
import static io.dockstore.webservice.core.metrics.ExecutionStatusCountMetric.ExecutionStatus.SUCCESSFUL;
import static io.dockstore.webservice.core.metrics.ValidationExecution.ValidatorTool.MINIWDL;
import static io.dockstore.webservice.core.metrics.constraints.HasMetrics.MUST_CONTAIN_METRICS;
import static io.dockstore.webservice.resources.proposedGA4GH.ToolsApiExtendedServiceImpl.TOOL_NOT_FOUND_ERROR;
import static io.dockstore.webservice.resources.proposedGA4GH.ToolsApiExtendedServiceImpl.VERSION_NOT_FOUND_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cloud.localstack.ServiceName;
import cloud.localstack.awssdkv2.TestUtils;
import cloud.localstack.docker.LocalstackDockerExtension;
import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import com.google.gson.Gson;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.LocalStackTest;
import io.dockstore.common.LocalStackTestUtilities;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.SourceControl;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.CpuMetric;
import io.dockstore.openapi.client.model.ExecutionStatusMetric;
import io.dockstore.openapi.client.model.ExecutionTimeMetric;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.MemoryMetric;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.ValidationInfo;
import io.dockstore.openapi.client.model.ValidationStatusMetric;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.core.Partner;
import io.dockstore.webservice.core.metrics.ExecutionTimeStatisticMetric;
import io.dockstore.webservice.core.metrics.MemoryStatisticMetric;
import io.dockstore.webservice.core.metrics.MetricsData;
import io.dockstore.webservice.core.metrics.MetricsDataMetadata;
import io.dockstore.webservice.core.metrics.MetricsDataS3Client;
import io.dockstore.webservice.metrics.MetricsDataS3ClientIT;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.services.s3.S3Client;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * Extra confidential integration tests, focuses on proposed GA4GH extensions
 * {@link BaseIT}
 */
@LocalstackDockerProperties(imageTag = IMAGE_TAG, services = { ServiceName.S3 }, environmentVariableProvider = LocalStackTestUtilities.LocalStackEnvironmentVariables.class)
@ExtendWith({ SystemStubsExtension.class, MuteForSuccessfulTests.class, BaseIT.TestStatus.class, LocalstackDockerExtension.class })
@Tag(ConfidentialTest.NAME)
@Tag(LocalStackTest.NAME)
class ExtendedTRSOpenApiIT extends BaseIT {

    private static final String DOCKSTORE_WORKFLOW_CNV_REPO = "DockstoreTestUser2/dockstore_workflow_cnv";
    private static final String DOCKSTORE_WORKFLOW_CNV_PATH = SourceControl.GITHUB + "/" + DOCKSTORE_WORKFLOW_CNV_REPO;
    private static final Gson GSON = new Gson();

    private static String bucketName;
    private static String s3EndpointOverride;
    private static MetricsDataS3Client metricsDataClient;
    private static S3Client s3Client;

    @SystemStub
    public final SystemOut systemOut = new SystemOut();

    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    @BeforeAll
    public static void setup() throws Exception {
        bucketName = SUPPORT.getConfiguration().getMetricsConfig().getS3BucketName();
        s3EndpointOverride = SUPPORT.getConfiguration().getMetricsConfig().getS3EndpointOverride();
        metricsDataClient = new MetricsDataS3Client(bucketName, s3EndpointOverride);
        // Create a bucket to be used for tests
        s3Client = TestUtils.getClientS3V2(); // Use localstack S3Client
        createBucket(s3Client, bucketName);
        deleteBucketContents(s3Client, bucketName); // This is here just in case a test was stopped before tearDown could clean up the bucket
    }

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    @AfterEach
    public void tearDown() {
        // Delete all objects from the S3 bucket after each test
        deleteBucketContents(s3Client, bucketName);
    }

    @Test
    void testAggregatedMetrics() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        final WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final String platform1 = Partner.TERRA.name();
        final String platform2 = Partner.DNA_STACK.name();

        // Register and publish a workflow
        final String workflowId = String.format("#workflow/%s/my-workflow", DOCKSTORE_WORKFLOW_CNV_PATH);
        final String workflowVersionId = "master";
        Workflow workflow = workflowsApi.manualRegister(SourceControl.GITHUB.name(), DOCKSTORE_WORKFLOW_CNV_REPO, "/workflow/cnv.cwl", "my-workflow", "cwl",
                "/test.json");
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        workflowsApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        ExecutionStatusMetric executionStatusMetric = new ExecutionStatusMetric()
                .count(Map.of(SUCCESSFUL.name(), 1,
                        FAILED_SEMANTIC_INVALID.name(), 1));
        final double min = 1.0;
        final double max = 3.0;
        final double average = 2.0;
        final int numberOfDataPointsForAverage = 3;
        ExecutionTimeMetric executionTimeMetric = new ExecutionTimeMetric()
                .minimum(min)
                .maximum(max)
                .average(average)
                .numberOfDataPointsForAverage(numberOfDataPointsForAverage);
        CpuMetric cpuMetric = new CpuMetric()
                .minimum(min)
                .maximum(max)
                .average(average)
                .numberOfDataPointsForAverage(numberOfDataPointsForAverage);
        MemoryMetric memoryMetric = new MemoryMetric()
                .minimum(min)
                .maximum(max)
                .average(average)
                .numberOfDataPointsForAverage(numberOfDataPointsForAverage);
        Metrics metrics = new Metrics()
                .executionStatusCount(executionStatusMetric)
                .executionTime(executionTimeMetric)
                .cpu(cpuMetric)
                .memory(memoryMetric);

        // Put run metrics for platform 1
        extendedGa4GhApi.aggregatedMetricsPut(metrics, platform1, workflowId, workflowVersionId);
        workflow = workflowsApi.getPublishedWorkflow(workflow.getId(), "metrics");
        WorkflowVersion workflowVersion = workflow.getWorkflowVersions().stream().filter(v -> workflowVersionId.equals(v.getName())).findFirst().orElse(null);
        assertNotNull(workflowVersion);
        assertEquals(1, workflowVersion.getMetricsByPlatform().size());

        Metrics platform1Metrics = workflowVersion.getMetricsByPlatform().get(platform1);
        assertNotNull(platform1Metrics);
        // Verify execution status
        assertFalse(platform1Metrics.getExecutionStatusCount().isValid());
        assertEquals(1, platform1Metrics.getExecutionStatusCount().getNumberOfSuccessfulExecutions());
        assertEquals(1, platform1Metrics.getExecutionStatusCount().getNumberOfFailedExecutions());
        assertFalse(platform1Metrics.getExecutionStatusCount().getCount().containsKey(FAILED_RUNTIME_INVALID.name()), "Should not contain this because no executions had this status");
        // Verify execution time
        assertEquals(min, platform1Metrics.getExecutionTime().getMinimum());
        assertEquals(max, platform1Metrics.getExecutionTime().getMaximum());
        assertEquals(average, platform1Metrics.getExecutionTime().getAverage());
        assertEquals(numberOfDataPointsForAverage, platform1Metrics.getExecutionTime().getNumberOfDataPointsForAverage());
        assertEquals(ExecutionTimeStatisticMetric.UNIT, platform1Metrics.getExecutionTime().getUnit());
        // Verify CPU
        assertEquals(min, platform1Metrics.getCpu().getMinimum());
        assertEquals(max, platform1Metrics.getCpu().getMaximum());
        assertEquals(average, platform1Metrics.getCpu().getAverage());
        assertEquals(numberOfDataPointsForAverage, platform1Metrics.getCpu().getNumberOfDataPointsForAverage());
        assertNull(null, "CPU has no units");
        // Verify memory
        assertEquals(min, platform1Metrics.getMemory().getMinimum());
        assertEquals(max, platform1Metrics.getMemory().getMaximum());
        assertEquals(average, platform1Metrics.getMemory().getAverage());
        assertEquals(numberOfDataPointsForAverage, platform1Metrics.getMemory().getNumberOfDataPointsForAverage());
        assertEquals(MemoryStatisticMetric.UNIT, platform1Metrics.getMemory().getUnit());

        // Put metrics for platform1 again to verify that the old metrics are deleted from the DB and there are no orphans
        extendedGa4GhApi.aggregatedMetricsPut(metrics, platform1, workflowId, workflowVersionId);
        long metricsDbCount = testingPostgres.runSelectStatement("select count(*) from metrics", long.class);
        assertEquals(1, metricsDbCount, "There should only be 1 row in the metrics table because we only have one entry version with aggregated metrics");

        // Put validation metrics for platform2
        ValidationStatusMetric validationStatusMetric = new ValidationStatusMetric().validatorToolToValidationInfo(Map.of(
                MINIWDL.toString(),
                new ValidationInfo()
                        .mostRecentIsValid(true)
                        .mostRecentVersion("1.0")
                        .passingRate(100d)
                        .numberOfRuns(1)
                        .successfulValidationVersions(List.of("1.0"))));
        metrics = new Metrics().validationStatus(validationStatusMetric);
        extendedGa4GhApi.aggregatedMetricsPut(metrics, platform2, workflowId, workflowVersionId);
        workflow = workflowsApi.getPublishedWorkflow(workflow.getId(), "metrics");
        workflowVersion = workflow.getWorkflowVersions().stream().filter(v -> workflowVersionId.equals(v.getName())).findFirst().orElse(null);

        assertNotNull(workflowVersion);
        assertEquals(2, workflowVersion.getMetricsByPlatform().size(), "Version should have metrics for 2 platforms");

        assertNotNull(workflowVersion.getMetricsByPlatform().get(platform1));
        assertNotNull(workflowVersion.getMetricsByPlatform().get(platform2));

        // Verify validation status
        Metrics platform2Metrics = workflowVersion.getMetricsByPlatform().get(platform2);
        ValidationInfo validationInfo = platform2Metrics.getValidationStatus().getValidatorToolToValidationInfo().get(MINIWDL.toString());
        assertNotNull(validationInfo);
        assertTrue(validationInfo.isMostRecentIsValid());
        assertEquals("1.0", validationInfo.getMostRecentVersion());
        assertEquals(List.of("1.0"), validationInfo.getSuccessfulValidationVersions());
        assertTrue(validationInfo.getFailedValidationVersions().isEmpty());
        assertEquals(100d, validationInfo.getPassingRate());
        assertEquals(1, validationInfo.getNumberOfRuns());
        platform1Metrics = workflowVersion.getMetricsByPlatform().get(platform1);

        Map<String, Metrics> metricsGet = extendedGa4GhApi.aggregatedMetricsGet(workflowId, workflowVersionId);
        assertNotNull(metricsGet.get(platform1));
        assertNotNull(metricsGet.get(platform2));
        assertEquals(platform1Metrics, metricsGet.get(platform1));
        assertEquals(platform2Metrics, metricsGet.get(platform2));
    }

    @Test
    void testPartnerPermissions() {
        // Admin user
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        // Non-admin user
        final ApiClient otherWebClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        final ExtendedGa4GhApi otherExtendedGa4GhApi = new ExtendedGa4GhApi(otherWebClient);

        String id = String.format("#workflow/%s", DOCKSTORE_WORKFLOW_CNV_PATH);
        String versionId = "master";
        String platform = Partner.TERRA.name();

        // setup and publish the workflow
        Workflow workflow = workflowsApi.manualRegister(SourceControl.GITHUB.name(), DOCKSTORE_WORKFLOW_CNV_REPO, "/workflow/cnv.cwl", null, "cwl",
            "/test.json");
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        workflowsApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        ExecutionStatusMetric executionStatusMetric = new ExecutionStatusMetric().count(Map.of(SUCCESSFUL.name(), 1));
        Metrics metrics = new Metrics().executionStatusCount(executionStatusMetric);

        // Test that a non-admin/non-curator user can't put aggregated metrics
        ApiException exception = assertThrows(ApiException.class, () -> otherExtendedGa4GhApi.aggregatedMetricsPut(metrics, platform, id, versionId));
        assertEquals(HttpStatus.SC_FORBIDDEN, exception.getCode(), "Non-admin and non-curator user should not be able to put aggregated metrics");

        // convert the user role and test that a platform partner can't put aggregated metrics (which adds metrics to the database)
        testingPostgres.runUpdateStatement("update enduser set platformpartner = 't' where username = '" + OTHER_USERNAME + "'");
        exception = assertThrows(ApiException.class, () -> otherExtendedGa4GhApi.aggregatedMetricsPut(metrics, platform, id, versionId));
        assertEquals(HttpStatus.SC_FORBIDDEN, exception.getCode(), "Platform partner should not be able to put aggregated metrics");

        // Test that a platform partner can post run executions
        List<RunExecution> executions = MetricsDataS3ClientIT.createRunExecutions(1);
        otherExtendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(executions), platform, id, versionId, "foo");
        verifyMetricsDataList(id, versionId, 1);

        // Test that a platform partner can post aggregated metrics
        Metrics aggregatedMetrics = new Metrics().executionStatusCount(new ExecutionStatusMetric().count(Map.of(SUCCESSFUL.name(), 5)));
        otherExtendedGa4GhApi.aggregatedMetricsPost(aggregatedMetrics, platform, id, versionId, "foo");
        verifyMetricsDataList(id, versionId, 2);
    }

    @Test
    void testAggregatedMetricsErrors() {
        // Admin user
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        // Non-admin user
        final ApiClient otherWebClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        final ExtendedGa4GhApi otherExtendedGa4GhApi = new ExtendedGa4GhApi(otherWebClient);

        String id = String.format("#workflow/%s", DOCKSTORE_WORKFLOW_CNV_PATH);
        String versionId = "master";
        String platform = Partner.TERRA.name();

        ExecutionStatusMetric executionStatusMetric = new ExecutionStatusMetric().count(Map.of(SUCCESSFUL.name(), 1));
        Metrics metrics = new Metrics().executionStatusCount(executionStatusMetric);
        // Test malformed ID
        ApiException exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.aggregatedMetricsPut(metrics, platform, "malformedId", "malformedVersionId"));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getCode());

        // Test ID that doesn't exist
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.aggregatedMetricsPut(metrics, platform, "github.com/nonexistent/id", "master"));
        assertEquals(HttpStatus.SC_NOT_FOUND, exception.getCode(), "Should not be able to submit metrics for non-existent id");
        assertTrue(exception.getMessage().contains(TOOL_NOT_FOUND_ERROR));

        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.aggregatedMetricsGet("github.com/nonexistent/id", "master"));
        assertEquals(HttpStatus.SC_NOT_FOUND, exception.getCode(), "Should not be able to get metrics for non-existent id");
        assertTrue(exception.getMessage().contains(TOOL_NOT_FOUND_ERROR));

        // Test version ID that doesn't exist
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.name(), DOCKSTORE_WORKFLOW_CNV_REPO, "/workflow/cnv.cwl", "",
                DescriptorLanguage.CWL.toString(), "/test.json");
        workflow = workflowApi.refresh1(workflow.getId(), false);
        workflowApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.aggregatedMetricsPut(metrics, platform, id, "nonexistentVersionId"));
        assertEquals(HttpStatus.SC_NOT_FOUND, exception.getCode(), "Should not be able to put aggregated metrics for non-existent version");
        assertTrue(exception.getMessage().contains(VERSION_NOT_FOUND_ERROR));

        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.aggregatedMetricsGet(id, "nonexistentVersionId"));
        assertEquals(HttpStatus.SC_NOT_FOUND, exception.getCode(), "Should not be able to get aggregated metrics for non-existent version");
        assertTrue(exception.getMessage().contains(VERSION_NOT_FOUND_ERROR));

        // Test that a non-admin/non-curator user can't put aggregated metrics
        exception = assertThrows(ApiException.class, () -> otherExtendedGa4GhApi.aggregatedMetricsPut(metrics, platform, id, versionId));
        assertEquals(HttpStatus.SC_FORBIDDEN, exception.getCode(), "Non-admin and non-curator user should not be able to put aggregated metrics");

        Metrics emptyMetrics = new Metrics();
        // Test that the response body must contain ExecutionStatusCount or ValidationStatus
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.aggregatedMetricsPut(emptyMetrics, platform, id, versionId));
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, exception.getCode(), "Should not be able to put aggregated metrics if ExecutionStatusCount and ValidationStatus is missing");
        assertTrue(exception.getMessage().contains(MUST_CONTAIN_METRICS));

        // Verify that not providing metrics throws an exception
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.aggregatedMetricsPut(null, platform, id, versionId));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getCode(), "Should throw if execution metrics not provided");
    }

    @Test
    void testSubmitAggregatedMetricsData() throws IOException {
        // Admin user
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        final io.dockstore.openapi.client.api.UsersApi usersApi = new io.dockstore.openapi.client.api.UsersApi(webClient);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        final String platform1 = Partner.TERRA.name();
        final String description = "Aggregated metrics";
        final Long ownerUserId = usersApi.getUser().getId();

        // Register and publish a workflow
        final String workflowId = "#workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv/my-workflow";
        final String workflowVersionId = "master";
        final String workflowExpectedS3KeyPrefixFormat = "workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv%%2Fmy-workflow/master/%s"; // This is the prefix without the file name
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "my-workflow", "cwl",
                "/test.json");
        workflow = workflowApi.refresh1(workflow.getId(), false);
        workflowApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        // Add execution metrics for a workflow version for one platform
        Metrics expectedAggregatedMetrics = new Metrics()
                .executionStatusCount(new ExecutionStatusMetric().count(Map.of(SUCCESSFUL.name(), 5)))
                .additionalAggregatedMetrics(Map.of("cpu_utilization", 50.0));
        extendedGa4GhApi.aggregatedMetricsPost(expectedAggregatedMetrics, platform1, workflowId, workflowVersionId, description);
        List<MetricsData> metricsDataList = verifyMetricsDataList(workflowId, workflowVersionId, 1);
        MetricsData metricsData = verifyMetricsDataInfo(metricsDataList, workflowId, workflowVersionId, platform1, String.format(workflowExpectedS3KeyPrefixFormat, platform1));
        verifyMetricsDataMetadata(metricsData, ownerUserId, description);
        String metricsDataContent = metricsDataClient.getMetricsDataFileContent(metricsData.toolId(), metricsData.toolVersionName(), metricsData.platform(), metricsData.fileName());
        Metrics aggregatedMetrics = GSON.fromJson(metricsDataContent, Metrics.class);
        assertEquals(5, aggregatedMetrics.getExecutionStatusCount().getNumberOfSuccessfulExecutions());
        assertEquals(0, aggregatedMetrics.getExecutionStatusCount().getNumberOfFailedExecutions());
        assertEquals(50.0, aggregatedMetrics.getAdditionalAggregatedMetrics().get("cpu_utilization"), "Should be able to submit additional aggregated metrics");
    }

    /**
     * Checks the number of MetricsData that a version has then return the list
     * @param id
     * @param versionId
     * @param expectedSize
     * @return List of MetricsData
     */
    private List<MetricsData> verifyMetricsDataList(String id, String versionId, int expectedSize) {
        List<MetricsData> metricsDataList = metricsDataClient.getMetricsData(id, versionId);
        assertEquals(expectedSize, metricsDataList.size());
        return metricsDataList;
    }

    /**
     * Verifies the MetricsData info then returns it
     * @param metricsDataList
     * @param id
     * @param versionId
     * @param platform
     * @param s3KeyPrefix
     * @return
     */
    private MetricsData verifyMetricsDataInfo(List<MetricsData> metricsDataList, String id, String versionId, String platform, String s3KeyPrefix) {
        MetricsData metricsData = metricsDataList.stream().filter(data -> data.s3Key().startsWith(s3KeyPrefix)).findFirst().orElse(null);
        assertNotNull(metricsData);
        assertEquals(id, metricsData.toolId());
        assertEquals(versionId, metricsData.toolVersionName());
        assertEquals(platform, metricsData.platform());
        // The full file name and S3 key are not checked because it depends on the time the data was submitted which is unknown
        assertTrue(metricsData.fileName().endsWith(".json"));
        assertTrue(metricsData.s3Key().startsWith(s3KeyPrefix));
        return metricsData;
    }

    private void verifyMetricsDataMetadata(MetricsData metricsData, long ownerUserId, String description) {
        MetricsDataMetadata metricsDataMetadata = metricsDataClient.getMetricsDataMetadata(metricsData);
        assertEquals(ownerUserId, metricsDataMetadata.owner());
        assertEquals(description, metricsDataMetadata.description());
    }
}
