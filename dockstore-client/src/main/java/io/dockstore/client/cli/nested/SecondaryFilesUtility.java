package io.dockstore.client.cli.nested;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.internal.LinkedTreeMap;
import io.cwl.avro.CWL;
import io.cwl.avro.CommandInputParameter;
import io.cwl.avro.CommandLineTool;
import io.cwl.avro.InputParameter;
import io.cwl.avro.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author gluu
 * @since 14/08/17
 */
public class SecondaryFilesUtility {
    private static final Logger LOG = LoggerFactory.getLogger(SecondaryFilesUtility.class);
    private CWL cwlUtil;
    private Gson gson;

    public SecondaryFilesUtility(CWL cwlUtil, Gson gson) {
        this.cwlUtil = cwlUtil;
        this.gson = gson;
    }

    /**
     * This retrieves a workflow input file ids
     *
     * @param workflow The workflow to retrieve
     * @return The list of input file ids
     */
    private List<String> getInputFileIds(Workflow workflow) {
        List<String> inputFileIds = new ArrayList<>();
        List<InputParameter> inputs = workflow.getInputs();
        inputs.parallelStream().map(input -> input.getId().toString()).forEachOrdered(inputFileIds::add);
        return inputFileIds;
    }

    private void loopThroughSource(String id, String fileId, LinkedTreeMap mapStep, List<Map<String, List<String>>> idAndSecondaryFiles) {
        String[] temp = id.split("#");
        String temp3 = temp[temp.length - 1];
        String[] split = temp3.split("/");
        String toolIDFromWorkflow = split[split.length - 1];

        String descriptor = mapStep.get("run").toString();
        final String toolDescriptor = this.cwlUtil.parseCWL(descriptor).getLeft();
        Object toolDescriptorObject;
        try {
            toolDescriptorObject = this.gson.fromJson(toolDescriptor, CommandLineTool.class);
            if (toolDescriptorObject != null) {
                CommandLineTool commandLineTool = (CommandLineTool)toolDescriptorObject;
                List<CommandInputParameter> inputs = commandLineTool.getInputs();
                inputs.forEach(input -> {

                    try {
                        @SuppressWarnings("unchecked")
                        List<String> secondaryFiles = (List<String>)input.get("secondaryFiles");
                        if (secondaryFiles != null) {
                            String toolId = input.getId().toString();
                            String[] temp2 = toolId.split("#");
                            String toolFileId = temp2[1];
                            // Check if the tool descriptor has secondary files and if the id matches the workflow id
                            if (toolFileId.equals(toolIDFromWorkflow)) {
                                //                            if (toolFileId.equals(workflowFileId)) {
                                Map<String, List<String>> hashMap = new HashMap<>();
                                hashMap.put(fileId, secondaryFiles);
                                idAndSecondaryFiles.add(hashMap);
                            }
                        }
                    } catch (ClassCastException e) {
                        LOG.error(e.getMessage());
                    }
                });

            }
        } catch (JsonParseException ex) {
            LOG.error("The JSON file provided is invalid.");
        }
    }

    /**
     * The modifies idAndSecondaryFiles to include secondaryFiles present in the tool descriptor (even though it may exist in the workflow descriptor)
     *
     * @param workflow            The workflow descriptor
     * @param fileId              The current file ID we're looking at in the workflow descriptor
     * @param idAndSecondaryFiles A file ids and secondary files that needed to be added to those file ids
     */
    private void getDescriptorsWithFileInput(Workflow workflow, String fileId, List<Map<String, List<String>>> idAndSecondaryFiles) {
        Object steps = workflow.getSteps();
        if (steps instanceof ArrayList) {
            @SuppressWarnings("unchecked")
            ArrayList<Object> stepsList = (ArrayList<Object>)steps;

            // Loop through every step inside the workflow descriptor
            stepsList.forEach((Object step) -> {
                if (step instanceof Map) {
                    LinkedTreeMap mapStep = (LinkedTreeMap)step;
                    if (mapStep.get("in") instanceof ArrayList) {
                        @SuppressWarnings("unchecked")
                        ArrayList<Map> in = (ArrayList)mapStep.get("in");

                        // Loop through every file input inside the step
                        for (Map inn : in) {
                            Object idObject = inn.get("id");
                            if (idObject instanceof String) {
                                String idString = (String)idObject;
                                Object sourceObject = inn.get("source");
                                if (sourceObject != null) {
                                    if (sourceObject instanceof String) {
                                        String id = (String)inn.get("source");
                                        if (id.equals(fileId)) {
                                            loopThroughSource(idString, fileId, mapStep, idAndSecondaryFiles);
                                        }
                                    } else if (sourceObject instanceof ArrayList) {
                                        @SuppressWarnings("unchecked")
                                        ArrayList<Object> sourceArrayList = (ArrayList)sourceObject;
                                        sourceArrayList.forEach(source -> {
                                            if (source instanceof String) {
                                                String sourceString = (String)source;
                                                if (sourceString.equals(fileId)) {
                                                    loopThroughSource(idString, fileId, mapStep, idAndSecondaryFiles);
                                                }
                                            } else {
                                                throwUnhandledTypeException(source);
                                            }
                                        });
                                    } else {
                                        throwUnhandledTypeException(sourceObject);
                                    }
                                }
                            } else {
                                LOG.error("Unknown id type");
                            }

                        }
                    }
                } else {
                    throwUnhandledTypeException(step);
                }
            });
        } else {
            throwUnhandledTypeException(steps);
        }
    }

    private void throwUnhandledTypeException(Object object) {
        throw new RuntimeException("Unhandled type" + object.getClass());
    }

    /**
     * Sets the workflow's secondary files for a given input file ID
     *
     * @param input              The workflow's current input parameters for the given workflow's input file ID
     * @param toolSecondaryFiles The tool's secondary files
     * @param workflowId         The given workflow's input file ID
     */
    private void setInputFile(InputParameter input, ArrayList<String> toolSecondaryFiles, String workflowId) {
        Object workflowSecondaryFiles = input.getSecondaryFiles();
        if (workflowSecondaryFiles == null) {
            LOG.info("Copying the secondary files to " + workflowId);
            input.setSecondaryFiles(toolSecondaryFiles);
        } else if (workflowSecondaryFiles instanceof ArrayList) {
            LOG.info("Copying the secondary files to " + workflowId);
            @SuppressWarnings("unchecked")
            ArrayList<String> arrayListWorkflowSecondaryFiles = (ArrayList<String>)workflowSecondaryFiles;
            arrayListWorkflowSecondaryFiles.removeAll(toolSecondaryFiles);
            toolSecondaryFiles.addAll(arrayListWorkflowSecondaryFiles);
            input.setSecondaryFiles(toolSecondaryFiles);
        } else if (workflowSecondaryFiles instanceof String) {
            // Not sure if this case ever occurs
            if (!toolSecondaryFiles.contains(workflowSecondaryFiles)) {
                toolSecondaryFiles.add((String)workflowSecondaryFiles);
                input.setSecondaryFiles(toolSecondaryFiles);
            }
        } else {
            throwUnhandledTypeException(workflowSecondaryFiles);
        }
    }

    /**
     * This modifies the workflow object to include secondary files specified in the tool descriptors
     *
     * @param workflow The workflow object
     */
    public void modifyWorkflowToIncludeToolSecondaryFiles(Workflow workflow) {
        // Contains a list of descriptor files that uses the files in the root workflow
        List<Map<String, List<String>>> descriptorsWithFiles = new ArrayList<>();
        List<String> inputFileIds = this.getInputFileIds(workflow);
        inputFileIds.forEach(inputFileId -> getDescriptorsWithFileInput(workflow, inputFileId, descriptorsWithFiles));
        List<InputParameter> inputs = workflow.getInputs();
        inputs.forEach(input -> {
            String workflowId = input.getId().toString();
            descriptorsWithFiles.forEach(file -> {
                if (file.containsKey(workflowId)) {
                    ArrayList<String> arrayListToolSecondaryFiles = (ArrayList<String>)file.get(workflowId);
                    setInputFile(input, arrayListToolSecondaryFiles, workflowId);
                }
            });
        });
    }
}
