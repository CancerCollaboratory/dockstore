package io.dockstore.client.cli.nested;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.io.Files;
import io.dockstore.client.cli.SwaggerUtility;
import io.dockstore.common.FileProvisioning;
import io.dockstore.common.LanguageType;
import io.swagger.wes.client.api.WorkflowExecutionServiceApi;
import io.swagger.wes.client.model.RunId;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WESLauncher extends BaseLauncher {
    private static final Logger LOG = LoggerFactory.getLogger(WESLauncher.class);
    private static final String TAGS = "WorkflowExecutionService";
    private static final String WORKFLOW_TYPE_VERSION = "v1.0";

    protected List<String> command;
    protected Map<String, List<FileProvisioning.FileInfo>> outputMap;
    protected WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi;


    public WESLauncher(AbstractEntryClient abstractEntryClient, LanguageType language, boolean script) {
        super(abstractEntryClient, language, script);
        setLauncherName("wes");
    }

    /**
     * Creates a copy of the Workflow Execution Service (WES) API.
     */
    @Override
    public void initialize() {
        String wesUrl = abstractEntryClient.getWesUri();
        String wesAuth = abstractEntryClient.getWesAuth();
        clientWorkflowExecutionServiceApi = abstractEntryClient.getWorkflowExecutionServiceApi(wesUrl, wesAuth);
    }

    /**
     * Create a command to execute entry on the command line
     *
     * @return Command to run in list format
     */
    @Override
    public List<String> buildRunCommand() {
        return null;
    }

    @Override
    public void provisionOutputFiles(String stdout, String stderr, String wdlOutputTarget) {

    }

    @Override
    public ImmutablePair<String, String> executeEntry(String runCommand, File workingDir) throws RuntimeException {
        runWESCommand(this.originalParameterFile, this.primaryDescriptor, this.zippedEntry);

        //TODO return something better than this? or change the return....
        return new ImmutablePair<String, String>("", "");
    }

    /*
     * File type must match workflow language possible file types
     * E.g. for CWL workflows the file extension must be cwl, yaml, or yml
     * Also include json files
     */
    protected boolean fileIsCorrectType(File potentialAttachmentFile) {
        LanguageType potentialAttachmentFileLanguage = abstractEntryClient.checkFileExtension(potentialAttachmentFile.getName()); //file extension could be cwl,wdl or ""
        if (potentialAttachmentFile.exists() && !potentialAttachmentFile.isDirectory()) {
            if (potentialAttachmentFileLanguage.equals(this.languageType) || FilenameUtils.getExtension(potentialAttachmentFile.getAbsolutePath()).toLowerCase().equals("json")) {
                return true;
            }
        }
        return false;
    }

    protected void addFilesToWorkflowAttachment(List<File> workflowAttachment, File zippedEntry, File tempDir) {
        try {
            SwaggerUtility.unzipFile(zippedEntry, tempDir);
        } catch (IOException e) {
            System.out.println("Could not get files from workflow attachment. Request not sent.");
            throw new RuntimeException("Unable to get workflow attachment files from zip file", e);
        }

        // Put file names in workflow attachment list
        File[] listOfFiles = tempDir.listFiles();
        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile()) {
                // TODO check file extension (type) only for a local entry; pass all files found in remote entry folder?
                // There may be confidential or large files that are not needed in a local directory that should
                // not be sent to a remote endpoint?
                if (fileIsCorrectType(listOfFiles[i])) {
                    System.out.println("Adding file " + listOfFiles[i].getName() + " to workflow attachment");
                    File fileToAdd = new File(tempDir, listOfFiles[i].getName());
                    workflowAttachment.add(fileToAdd);
                } else {
                    System.out.println("File " + listOfFiles[i].getName() + " is not the correct type for the workflow so it will not be "
                            + "added to the workflow attachment");
                }
            } else if (listOfFiles[i].isDirectory()) {
                System.out.println("Found directory " + listOfFiles[i].getName());
            }
        }

    }

    public void runWESCommand(String jsonString, File localPrimaryDescriptorFile, File zippedEntry) {
        String workflowURL = localPrimaryDescriptorFile.getName();
        final File tempDir = Files.createTempDir();

        List<File> workflowAttachment = new ArrayList<>();
        addFilesToWorkflowAttachment(workflowAttachment, this.zippedEntry, tempDir);
        workflowAttachment.add(localPrimaryDescriptorFile);
        File jsonInputFile = new File(jsonString);
        workflowAttachment.add(jsonInputFile);

        try {
            RunId response = clientWorkflowExecutionServiceApi.runWorkflow(jsonInputFile, this.languageType.toString().toUpperCase(), WORKFLOW_TYPE_VERSION, TAGS,
                    "", workflowURL, workflowAttachment);
            System.out.println("Launched WES run with id: " + response.toString());
        } catch (io.swagger.wes.client.ApiException e) {
            LOG.error("Error launching WES run", e);
        } finally {
            try {
                FileUtils.deleteDirectory(tempDir);
            } catch (IOException ioe) {
                LOG.error("Could not delete temporary directory" + tempDir + " for workflow attachment files", ioe);
            }
        }
    }
}
