package io.dockstore.webservice.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import org.junit.jupiter.api.Test;

class ZenodoHelperTest {

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
            assertTrue(ex.getErrorMessage().contains("Please create aliases without these prefixes"));
        }
    }

    @Test
    void checkCreationFromValidDoi() {
        String doi = "10.5072/zenodo.372767";
        ZenodoHelper.createAliasUsingDoi(doi);
    }

}
