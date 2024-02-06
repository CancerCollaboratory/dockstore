package io.dockstore.webservice.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.swagger.zenodo.client.ApiClient;
import io.swagger.zenodo.client.api.PreviewApi;
import io.swagger.zenodo.client.model.Author;
import io.swagger.zenodo.client.model.DepositMetadata;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ZenodoHelperTest {

    @Test
    void testBasicFunctionality() {
        ApiClient zenodoClient = new ApiClient();
        String zenodoUrlApi = "https://sandbox.zenodo.org/api";
        zenodoClient.setBasePath(zenodoUrlApi);
        PreviewApi previewApi = new PreviewApi(zenodoClient);
        final Map map = (Map) previewApi.listLicenses();
        // this is just a basic sanity check, the licenses api is one of the apis that does not require an access token, but it returns what
        // looks like an elasticsearch object, this does not match the documentation. Ironically, we have this too for search. 
        assertFalse(map.isEmpty());
    }

    @Test
    void testAuthorHashSetSanity() {
        // this tests that the zenodo classes used in zenodo helper have sane hashcodes and equals methods
        final String awesomeUniversity = "awesomeUniversity";
        final String someGuy = "Some guy";
        Author author1 = new Author();
        author1.setAffiliation("awesomeUniversity");
        author1.setName("Some guy");
        Author author2 = new Author();
        author2.setAffiliation("mediocreUniversity");
        author2.setName("Some other guy");
        Author author3 = new Author();
        author3.setAffiliation(awesomeUniversity);
        author3.setName(someGuy);
        Set<Author> authorSet = new HashSet<>();
        authorSet.add(author1);
        authorSet.add(author2);
        authorSet.add(author3);
        assertEquals(2, authorSet.size());
        Author oAuthor1 = new Author();
        oAuthor1.setOrcid("xxx-xxxx-1234-1234");
        String orcid = "XXX-xxx-4321-4321";
        Author oAuthor2 = new Author();
        oAuthor2.setOrcid(orcid);
        Author oAuthor3 = new Author();
        oAuthor3.setOrcid(orcid);
        authorSet.add(oAuthor1);
        authorSet.add(oAuthor2);
        authorSet.add(oAuthor3);
        assertEquals(4, authorSet.size());
    }

    @Test
    void testcreateWorkflowTrsUrl() {
        final Workflow workflow = new BioWorkflow();

        final WorkflowVersion workflowVersion = new WorkflowVersion();
        workflow.setSourceControl(SourceControl.GITHUB);
        workflow.setOrganization("DataBiosphere");
        workflow.setRepository("topmed-workflows");
        workflow.setWorkflowName("UM_variant_caller_wdl");
        workflow.setDescriptorType(DescriptorLanguage.WDL);

        workflowVersion.setWorkflowPath("topmed_freeze3_calling.wdl");
        workflowVersion.setName("1.32.0");

        String trsUrl = ZenodoHelper.createWorkflowTrsUrl(workflow, workflowVersion, "https://dockstore.org/api/api/ga4gh/v2/tools/");
        assertEquals("https://dockstore.org/api/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2FDataBiosphere"
                + "%2Ftopmed-workflows%2FUM_variant_caller_wdl/versions/1.32.0/PLAIN-WDL/descriptor/topmed_freeze3_calling.wdl", trsUrl);
    }

    @Test
    void extractDoiFromDoiUrl() {
        String doiUrl = "https://doi.org/10.5072/zenodo.372767";
        String doi = ZenodoHelper.extractDoiFromDoiUrl(doiUrl);
        assertEquals("10.5072/zenodo.372767", doi);
    }

    @Test
    void extractDoiFromBadDoiUrl() {
        String doiUrl = "https://doi.org/blah/10.5072/zenodo.372767";
        String doi = ZenodoHelper.extractDoiFromDoiUrl(doiUrl);
        assertNotEquals("10.5072/zenodo.372767", doi);
    }

    @Test
    void checkAliasCreationFromDoiWithInvalidPrefix() {
        String doi = "drs:10.5072/zenodo.372767";
        try {
            ZenodoHelper.createAliasUsingDoi(doi);
            fail("Was able to create an alias with an invalid prefix.");
        } catch (CustomWebApplicationException ex) {
            assertTrue(ex.getMessage().contains("Please create aliases without these prefixes"));
        }
    }

    @Test
    void checkCreationFromValidDoi() {
        String doi = "10.5072/zenodo.372767";
        ZenodoHelper.createAliasUsingDoi(doi);
    }

    @Test
    void testMetadataCreatorWithNoAuthors() {
        final DepositMetadata depositMetadata = new DepositMetadata();
        final BioWorkflow bioWorkflow = new BioWorkflow();
        final WorkflowVersion workflowVersion = new WorkflowVersion();
        bioWorkflow.getWorkflowVersions().add(workflowVersion);
        try {
            ZenodoHelper.setMetadataCreator(depositMetadata, bioWorkflow, workflowVersion);
            fail("Should have failed");
        } catch (CustomWebApplicationException ex) {
            assertEquals(ZenodoHelper.AT_LEAST_ONE_AUTHOR_IS_REQUIRED_TO_PUBLISH_TO_ZENODO, ex.getMessage());
        }
    }

    @Test
    void testMetadataCreateWithOneAuthor() {
        final DepositMetadata depositMetadata = new DepositMetadata();
        final BioWorkflow bioWorkflow = new BioWorkflow();
        final WorkflowVersion workflowVersion = new WorkflowVersion();
        final io.dockstore.webservice.core.Author author = new io.dockstore.webservice.core.Author();
        final String joeBlow = "Joe Blow";
        author.setName(joeBlow);
        workflowVersion.addAuthor(author);
        bioWorkflow.getWorkflowVersions().add(workflowVersion);
        bioWorkflow.setActualDefaultVersion(workflowVersion);
        ZenodoHelper.setMetadataCreator(depositMetadata, bioWorkflow, workflowVersion);
        assertEquals(joeBlow, depositMetadata.getCreators().get(0).getName());
    }

}
