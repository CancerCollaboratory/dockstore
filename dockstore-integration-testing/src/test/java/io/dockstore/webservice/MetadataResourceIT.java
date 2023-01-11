/*
 * Copyright 2022 OICR and UCSC
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.dockstore.webservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.MetadataApi;
import io.dockstore.openapi.client.model.HealthCheckResult;
import io.dropwizard.testing.DropwizardTestSupport;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MetadataResourceIT extends BaseIT {
    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
            DockstoreWebserviceApplication.class, CommonTestUtilities.PUBLIC_CONFIG_PATH);

    @BeforeAll
    public static void dumpDBAndCreateSchema() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT, CommonTestUtilities.PUBLIC_CONFIG_PATH);
    }

    @Test
    void testCheckHealth() {
        ApiClient anonymousApiClient = getAnonymousOpenAPIWebClient();
        MetadataApi metadataApi = new MetadataApi(anonymousApiClient);

        List<HealthCheckResult> results;
        List<String> healthCheckNames;
        try {
            results = metadataApi.checkHealth(null);
            // Not asserting size because there seems to be an JdbiHealthCheck present when running tests
            healthCheckNames = results.stream().map(HealthCheckResult::getHealthCheckName).collect(Collectors.toList());
            assertTrue(healthCheckNames.contains("hibernate") && healthCheckNames.contains("deadlocks") && healthCheckNames.contains("connectionPool"));
        } catch (ApiException e) {
            fail("Health checks should not fail");
        }

        try {
            results = metadataApi.checkHealth(List.of("hibernate"));
            assertEquals(1, results.size());
            healthCheckNames = results.stream().map(HealthCheckResult::getHealthCheckName).collect(Collectors.toList());
            assertTrue(healthCheckNames.contains("hibernate"));
        } catch (ApiException e) {
            fail("Hibernate health check should not fail");
        }

        try {
            results = metadataApi.checkHealth(List.of("deadlocks"));
            assertEquals(1, results.size());
            healthCheckNames = results.stream().map(HealthCheckResult::getHealthCheckName).collect(Collectors.toList());
            assertTrue(healthCheckNames.contains("deadlocks"));
        } catch (ApiException e) {
            fail("Deadlocks health check should not fail");
        }

        try {
            results = metadataApi.checkHealth(List.of("connectionPool"));
            assertEquals(1, results.size());
            healthCheckNames = results.stream().map(HealthCheckResult::getHealthCheckName).collect(Collectors.toList());
            assertTrue(healthCheckNames.contains("connectionPool"));
        } catch (ApiException e) {
            fail("Connection pool health check should not fail");
        }

        try {
            results = metadataApi.checkHealth(List.of("hibernate", "connectionPool"));
            assertEquals(2, results.size());
            healthCheckNames = results.stream().map(HealthCheckResult::getHealthCheckName).collect(Collectors.toList());
            assertTrue(healthCheckNames.contains("connectionPool"));
        } catch (ApiException e) {
            fail("Hibernate and connection pool health checks should not fail");
        }

        Assert.assertThrows(ApiException.class, () -> metadataApi.checkHealth(List.of("foobar")));
    }
}
