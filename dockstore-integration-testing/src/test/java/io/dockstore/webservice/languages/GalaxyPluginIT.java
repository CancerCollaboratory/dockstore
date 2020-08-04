/*
 *    Copyright 2020 OICR
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
package io.dockstore.webservice.languages;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.SwaggerUtility;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.Constants;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.common.TestingPostgres;
import io.dockstore.common.Utilities;
import io.dockstore.common.WorkflowTest;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.model.Tool;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import io.swagger.client.ApiClient;
import io.swagger.client.api.MetadataApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.DescriptorLanguageBean;
import io.swagger.client.model.Workflow;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import static io.dockstore.common.CommonTestUtilities.getWebClient;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * This test does not require confidential data.
 * These tests are a bit weird because we're testing the webservice running with the Galaxy language plugin installed.
 *
 * @author dyuen
 * @since 1.9.0
 */
@Category({ ConfidentialTest.class, WorkflowTest.class })
public class GalaxyPluginIT {
    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT;
    public static final String GALAXY_PLUGIN_VERSION = "0.0.4";
    public static final String GALAXY_PLUGIN_FILENAME = "dockstore-galaxy-interface-" + GALAXY_PLUGIN_VERSION + ".jar";
    public static final String GALAXY_PLUGIN_LOCATION =
        "https://artifacts.oicr.on.ca/artifactory/collab-release/com/github/galaxyproject/dockstore-galaxy-interface/dockstore-galaxy-interface/"
            + GALAXY_PLUGIN_VERSION + "/" + GALAXY_PLUGIN_FILENAME;

    private static final String DROPWIZARD_CONFIGURATION_FILE_PATH = CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH;

    static {
        try {
            // stash a Galaxy plugin in the plugin directory
            final Path temporaryTestingPlugins = Files.createTempDirectory("temporaryTestingPlugins");
            final Path path = Paths.get(temporaryTestingPlugins.toString(), GALAXY_PLUGIN_FILENAME);
            FileUtils.copyURLToFile(new URL(GALAXY_PLUGIN_LOCATION), path.toFile());
            System.out.println("copied Galaxy plugin to: " + path.toString());
            final String absolutePath = temporaryTestingPlugins.toFile().getAbsolutePath();
            System.out.println("path for support: " + absolutePath);
            SUPPORT = new DropwizardTestSupport<>(DockstoreWebserviceApplication.class, DROPWIZARD_CONFIGURATION_FILE_PATH,
                ConfigOverride.config("languagePluginLocation", absolutePath));
        } catch (IOException e) {
            throw new RuntimeException("could not create temporary directory");
        }
    }

    private static TestingPostgres testingPostgres;
    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            System.out.println("Starting test: " + description.getMethodName());
        }
    };

    @BeforeClass
    public static void dropAndRecreateDB() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT);
        SUPPORT.before();
        testingPostgres = new TestingPostgres(SUPPORT);
    }

    @AfterClass
    public static void afterClass() {
        SUPPORT.after();
    }

    @Before
    public void setup() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }

    @Test
    public void testGalaxyLanguagePlugin() {
        MetadataApi metadataApi = new MetadataApi(getWebClient(false, "n/a", testingPostgres));
        final List<DescriptorLanguageBean> descriptorLanguages = metadataApi.getDescriptorLanguages();
        // should have default languages plus galaxy via plugin
        assertTrue(
            descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.CWL.getFriendlyName())));
        assertTrue(
            descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.WDL.getFriendlyName())));
        assertTrue(
            descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.NEXTFLOW.getFriendlyName())));
        assertTrue(
            descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.GXFORMAT2.getFriendlyName())));
        // should not be present
        Assert.assertFalse(
            descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.SWL.getFriendlyName())));
    }

    @Test
    public void testFilterByDescriptorType() {
        final ApiClient webClient = getWebClient(true, BaseIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        Workflow wdlWorkflow = workflowApi
                .manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser/dockstore-whalesay-wdl-valid", "/Dockstore.wdl", "",
                        DescriptorLanguage.WDL.getShortName(), "");
        Workflow galaxyWorkflow = workflowApi
                .manualRegister(SourceControl.GITHUB.name(), "dockstore-testing/galaxy-workflow-dockstore-example-1", "/Dockstore.gxwf.yml",
                        "", DescriptorLanguage.GXFORMAT2.getShortName(), "");
        workflowApi.refresh(wdlWorkflow.getId(), false);
        workflowApi.refresh(galaxyWorkflow.getId(), false);
        workflowApi.publish(wdlWorkflow.getId(), SwaggerUtility.createPublishRequest(true));
        workflowApi.publish(galaxyWorkflow.getId(), SwaggerUtility.createPublishRequest(true));

        io.dockstore.openapi.client.ApiClient newWebClient = new io.dockstore.openapi.client.ApiClient();
        File configFile = FileUtils.getFile("src", "test", "resources", "config");
        INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        newWebClient.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(newWebClient);
        final List<Tool> allStuffGalaxy = ga4Ghv20Api
                .toolsGet(null, null, null, "galaxy", null, null, null, null, null, null, null, null, Integer.MAX_VALUE);
        final List<Tool> allStuffWdl = ga4Ghv20Api
                .toolsGet(null, null, null, DescriptorLanguage.WDL.getShortName(), null, null, null, null, null, null, null, null, Integer.MAX_VALUE);
        final List<Tool> allStuffCWL = ga4Ghv20Api
                .toolsGet(null, null, null, DescriptorLanguage.CWL.getShortName(), null, null, null, null, null, null, null, null, Integer.MAX_VALUE);
        assertEquals(1, allStuffGalaxy.size());
        assertEquals(1, allStuffWdl.size());
        assertTrue(allStuffCWL.isEmpty());
    }
}
