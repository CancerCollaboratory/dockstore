/*
 *    Copyright 2019 OICR
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
package io.dockstore.webservice.helpers.statelisteners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Author;
import io.dockstore.webservice.core.Category;
import io.dockstore.webservice.core.Doi;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.EntryTypeMetadata;
import io.dockstore.webservice.core.Label;
import io.dockstore.webservice.core.OrcidAuthor;
import io.dockstore.webservice.core.OrcidAuthorInformation;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.helpers.ElasticSearchHelper;
import io.dockstore.webservice.helpers.EntryVersionHelper;
import io.dockstore.webservice.helpers.ORCIDHelper;
import io.dockstore.webservice.helpers.StateManagerMode;
import io.dropwizard.jackson.Jackson;
import io.openapi.model.DescriptorType;
import io.swagger.api.impl.ToolsImplCommon;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.bulk.BackoffPolicy;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkItemResponse.Failure;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkProcessor.Builder;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Formerly the ElasticManager, this listens for changes that might affect elastic search
 */
public class ElasticListener implements StateListenerInterface {
    public static final String TOOLS_INDEX = "tools";
    public static final String WORKFLOWS_INDEX = "workflows";
    public static final String NOTEBOOKS_INDEX = "notebooks";
    public static final List<String> INDEXES = EntryTypeMetadata.values().stream().filter(EntryTypeMetadata::isEsSupported).map(EntryTypeMetadata::getEsIndex).toList();
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticListener.class);
    private static final ObjectMapper MAPPER = Jackson.newObjectMapper().addMixIn(Version.class, Version.ElasticSearchMixin.class);
    private static final String MAPPER_ERROR = "Could not convert Dockstore entry to Elasticsearch object";
    protected static final String EXECUTION_PARTNERS = "execution_partners";
    protected static final String VALIDATION_PARTNERS = "validation_partners";
    private DockstoreWebserviceConfiguration.ElasticSearchConfig elasticSearchConfig;

    @Override
    public void setConfig(DockstoreWebserviceConfiguration config) {
        this.elasticSearchConfig = config.getEsConfiguration();
    }

    /**
     * Manually eager load certain fields
     * @param entry
     */
    private void eagerLoadEntry(Entry entry) {
        Hibernate.initialize(entry.getAliases());
    }

    private String determineIndex(Entry entry) {
        if (entry.getEntryTypeMetadata().isEsSupported()) {
            return entry.getEntryTypeMetadata().getEsIndex();
        } else {
            return null;
        }
    }

    private List<Entry> filterEntriesByIndex(List<Entry> entries, String index) {
        return entries.stream().filter(entry -> Objects.equals(determineIndex(entry), index)).toList();
    }

    @Override
    public void handleIndexUpdate(Entry entry, StateManagerMode command) {
        eagerLoadEntry(entry);
        entry = filterCheckerWorkflows(entry);
        if (entry == null) {
            return;
        }
        String index = determineIndex(entry);
        if (index == null) {
            return;
        }
        LOGGER.info("Performing index update with " + command + ".");
        if (StringUtils.isEmpty(elasticSearchConfig.getHostname())) {
            LOGGER.error("No elastic search host found.");
            return;
        }
        if (!checkValid(entry, command)) {
            LOGGER.info("Could not perform the elastic search index update.");
            return;
        }
        try {
            RestHighLevelClient client = ElasticSearchHelper.restHighLevelClient();
            DocWriteResponse post;
            switch (command) {
            case PUBLISH:
            case UPDATE:
                UpdateRequest updateRequest = new UpdateRequest(index, String.valueOf(entry.getId()));
                String json = MAPPER.writeValueAsString(dockstoreEntryToElasticSearchObject(entry));
                // The below should've worked but it doesn't, the 2 lines after are used instead
                // updateRequest.upsert(json, XContentType.JSON);
                updateRequest.doc(json, XContentType.JSON);
                updateRequest.docAsUpsert(true);
                post = client.update(updateRequest, RequestOptions.DEFAULT);
                break;
            case DELETE:
                DeleteRequest deleteRequest = new DeleteRequest(index, String.valueOf(entry.getId()));
                post  = client.delete(deleteRequest, RequestOptions.DEFAULT);
                break;
            default:
                throw new RuntimeException("Unknown index command: " + command);
            }
            int statusCode = post.status().getStatus();
            if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_CREATED) {
                LOGGER.info("Successful " + command + ".");
            } else {
                LOGGER.error("Could not submit index to elastic search " + post.status());
            }
        } catch (Exception e) {
            LOGGER.error("Could not submit index to elastic search. " + e.getMessage());
        }
    }

    /**
     * Check if the entry is valid to perform the elastic operation
     *
     * @param entry   The entry to check
     * @param command The command that will be used
     * @return Whether or not the entry is valid
     */
    private boolean checkValid(Entry<?, ?> entry, StateManagerMode command) {
        switch (command) {
        case PUBLISH:
        case UPDATE:
            return entry.getIsPublished();
        case DELETE:
            // Try deleting no matter what
            return true;
        default:
            LOGGER.error("Unrecognized Elasticsearch command.");
            return false;
        }
    }

    @Override
    public void bulkUpsert(List<Entry> entries) {
        entries.forEach(this::eagerLoadEntry);
        entries = filterCheckerWorkflows(entries);
        // For each index, bulk index the corresponding entries
        for (String index: INDEXES) {
            postBulkUpdate(index, filterEntriesByIndex(entries, index));
        }
    }

    private void postBulkUpdate(String index, List<Entry> entries) {

        if (entries.isEmpty()) {
            return;
        }

        BulkProcessor.Listener listener = new BulkProcessor.Listener() {
            @Override
            public void beforeBulk(long executionId, BulkRequest request) {
                int numberOfActions = request.numberOfActions();
                LOGGER.info("Executing bulk [{}] with {} requests",
                        executionId, numberOfActions);
            }

            @Override
            public void afterBulk(long executionId, BulkRequest request,
                    BulkResponse response) {
                if (response.hasFailures()) {
                    LOGGER.error("Bulk [{}] executed with failures", executionId);
                    for (BulkItemResponse bulkItemResponse : response.getItems()) {
                        if (bulkItemResponse.isFailed()) {
                            final Failure failure = bulkItemResponse.getFailure();
                            final Throwable throwable = failure.getCause().getCause();
                            final String message = String.format(
                                "Item %s in bulk [%s] executed with failure, for entry with id %s",
                                bulkItemResponse.getItemId(), executionId, failure.getId());
                            LOGGER.error(message, throwable);
                        }
                    }
                } else {
                    LOGGER.info("Bulk [{}] completed in {} milliseconds",
                            executionId, response.getTook().getMillis());
                }
            }

            @Override
            public void afterBulk(long executionId, BulkRequest request,
                    Throwable failure) {
                LOGGER.error("Failed to execute bulk", failure);
            }
        };

        try {
            RestHighLevelClient client = ElasticSearchHelper.restHighLevelClient();
            BulkProcessor.Builder builder = BulkProcessor.builder(
                (request, bulkListener) ->
                        client.bulkAsync(request, RequestOptions.DEFAULT, bulkListener),
                listener);

            configureBulkProcessorBuilder(builder);

            BulkProcessor bulkProcessor = builder.build();
            entries.forEach(entry -> {
                try {
                    String s = MAPPER.writeValueAsString(dockstoreEntryToElasticSearchObject(entry));
                    bulkProcessor.add(new IndexRequest(index).id(String.valueOf(entry.getId())).source(s, XContentType.JSON));

                } catch (IOException e) {
                    LOGGER.error(MAPPER_ERROR, e);
                    throw new CustomWebApplicationException(MAPPER_ERROR, HttpStatus.SC_INTERNAL_SERVER_ERROR);
                }
            });
            try {
                // When doing a bulk index, this is the max amount of time the bulk listener should wait before considering the
                // bulk request as failed. 1 minute appears to be more than enough time to index all the current Dockstore entries.
                // However, 5 minutes is used instead (just in case)
                final long bulkProcessorWaitTimeInMinutes = 5L;
                boolean terminated = bulkProcessor.awaitClose(bulkProcessorWaitTimeInMinutes, TimeUnit.MINUTES);
                if (!terminated) {
                    LOGGER.error("Could not submit " + index + " index to elastic search in time");
                    throw new CustomWebApplicationException("Could not submit " + index + " index to elastic search in time", HttpStatus.SC_INTERNAL_SERVER_ERROR);
                }
            } catch (InterruptedException e) {
                LOGGER.error("Could not submit " + index + " index to elastic search. " + e.getMessage(), e);
                throw new CustomWebApplicationException("Could not submit " + index + " index to elastic search", HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (Exception e) {
            LOGGER.error("Could not submit " + index + " index to elastic search. " + e.getMessage(), e);
            throw new CustomWebApplicationException("Could not submit " + index + " index to elastic search", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Configures the builder for the ES bulk processor. The default settings were causing AWS
     * ES 429 (too many requests) errors with our current prod data. Drop the default size
     * and increase the time between retries. Environment variables are there for emergency
     * overrides, but current settings work when tested.
     *
     * See https://ucsc-cgl.atlassian.net/browse/SEAB-3829
     * @param builder
     */
    private void configureBulkProcessorBuilder(Builder builder) {
        // Default is 5MB
        final int bulkSizeKb = getEnv("ESCLIENT_BULK_SIZE_KB", 2500);
        builder.setBulkSize(new ByteSizeValue(bulkSizeKb, ByteSizeUnit.KB));

        // Defaults are 50ms, 8 retries (leaving number of retries the same).
        final int initialDelayMs = getEnv("ESCLIENT_BACKOFF_INITIAL_DELAY", 500);
        final int maxNumberOfRetries = getEnv("ESCLIENT_BACKOFF_RETRIES", 8);
        builder.setBackoffPolicy(BackoffPolicy.exponentialBackoff(TimeValue.timeValueMillis(
            initialDelayMs), maxNumberOfRetries));
    }

    private int getEnv(final String name, final int defaultValue) {
        final String envValue = System.getenv(name);
        if (envValue != null) {
            try {
                return Integer.parseInt(envValue);
            } catch (NumberFormatException ex) {
                LOGGER.error(MessageFormat.format("Value {0} specified for {1} is not numeric, defaulting back to {2}", envValue, name, defaultValue), ex);
            }
        }
        return defaultValue;
    }

    /**
     * This should be using an actual Elasticsearch object class instead of jsonNode
     *
     * @param entry The Dockstore entry
     * @return The Elasticsearch object string to be placed into the index
     * @throws IOException  Mapper problems
     */
    public static JsonNode dockstoreEntryToElasticSearchObject(final Entry entry) throws IOException {
        // TODO: avoid loading all versions to calculate verified, openData and descriptor type versions
        Set<Version> workflowVersions = entry.getWorkflowVersions();
        boolean verified = workflowVersions.stream().anyMatch(Version::isVerified);
        final boolean openData = workflowVersions.stream()
            .map(wv -> wv.getVersionMetadata().getPublicAccessibleTestParameterFile())
            .filter(Objects::nonNull)
            .anyMatch(Boolean::booleanValue);
        Set<String> verifiedPlatforms = getVerifiedPlatforms(workflowVersions);
        List<String> descriptorTypeVersions = getDistinctDescriptorTypeVersions(entry, workflowVersions);
        List<String> engineVersions = getDistinctEngineVersions(workflowVersions);
        Set<Author> allAuthors = getAllAuthors(entry);
        Doi selectedConceptDoi = getSelectedConceptDoi(entry);
        Entry detachedEntry = detach(entry);
        JsonNode jsonNode = MAPPER.readTree(MAPPER.writeValueAsString(detachedEntry));
        // add number of starred users to allow sorting in the UI
        final ObjectNode objectNode = (ObjectNode) jsonNode;
        objectNode.put("stars_count", (long) entry.getStarredUsers().size());
        objectNode.put("verified", verified);
        objectNode.put("openData", openData);
        objectNode.set(EXECUTION_PARTNERS, MAPPER.valueToTree(entry.getExecutionPartners()));
        objectNode.set(VALIDATION_PARTNERS, MAPPER.valueToTree(entry.getValidationPartners()));
        objectNode.set("verified_platforms", MAPPER.valueToTree(verifiedPlatforms));
        objectNode.set("descriptor_type_versions", MAPPER.valueToTree(descriptorTypeVersions));
        objectNode.set("engine_versions", MAPPER.valueToTree(engineVersions));
        objectNode.set("all_authors", MAPPER.valueToTree(allAuthors));
        objectNode.set("categories", MAPPER.valueToTree(convertCategories(entry.getCategories())));
        objectNode.put("archived", entry.isArchived());
        objectNode.put("selected_concept_doi", MAPPER.valueToTree(selectedConceptDoi));
        return jsonNode;
    }


    private static List<Map<String, Object>> convertCategories(List<Category> categories) {
        return categories.stream().map(
            category -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", category.getId());
                map.put("name", category.getName());
                map.put("description", category.getDescription());
                map.put("displayName", category.getDisplayName());
                map.put("topic", category.getTopic());
                return map;
            }
        ).toList();
    }

    /**
     * Create a partial copy of an Entry that is detached from Hibernate and contains only information that should be indexed.
     * @param entry
     */
    static Entry detach(final Entry entry) {

        if (entry instanceof Tool tool) {
            Tool detachedTool = tool.createEmptyEntry();
            copyToolProperties(detachedTool, tool);
            return detachedTool;

        } else if (entry instanceof Workflow workflow) {
            Workflow detachedWorkflow = workflow.createEmptyEntry();
            copyWorkflowProperties(detachedWorkflow, workflow);
            return detachedWorkflow;

        } else {
            Entry detachedEntry = entry.createEmptyEntry();
            copyEntryProperties(detachedEntry, entry);
            return detachedEntry;
        }
    }

    private static Set<Version> detachVersions(final Set<Version> originalWorkflowVersions, final Version defaultVersion) {
        Set<Version> detachedVersions = new HashSet<>();
        originalWorkflowVersions.forEach(workflowVersion -> {
            Version detachedVersion = workflowVersion.createEmptyVersion();
            detachedVersion.setInputFileFormats(new TreeSet<>(workflowVersion.getInputFileFormats()));
            detachedVersion.setOutputFileFormats(new TreeSet<>(workflowVersion.getOutputFileFormats()));
            detachedVersion.setName(workflowVersion.getName());
            detachedVersion.setReference(workflowVersion.getReference());
            // Only include the description and sourcefiles in the default version
            if (workflowVersion == defaultVersion) {
                detachedVersion.setDescriptionAndDescriptionSource(workflowVersion.getDescription(), workflowVersion.getDescriptionSource());
                SortedSet<SourceFile> sourceFiles = workflowVersion.getSourceFiles();
                // Copy the sourcefiles to the detached version, except for .dockstore.yml, which contains the names
                // of all entries in the repo, thus clouding the search results for multi-entry repos
                sourceFiles.forEach(sourceFile -> {
                    if (sourceFile.getType() != DescriptorLanguage.FileType.DOCKSTORE_YML) {
                        detachedVersion.addSourceFile(SourceFile.copy(sourceFile));
                    }
                });
            }
            detachedVersions.add(detachedVersion);
        });
        return detachedVersions;
    }

    private static void copyEntryProperties(Entry detachedEntry, Entry entry) {
        detachedEntry.setLastModified(entry.getLastModifiedDate());
        detachedEntry.setDescription(entry.getDescription());
        detachedEntry.setAliases(entry.getAliases());
        detachedEntry.setLabels((SortedSet<Label>)entry.getLabels());
        detachedEntry.setCheckerWorkflow(entry.getCheckerWorkflow());
        // This is some weird hack to always set the topic (which is either automatic, AI, or manual) into the ES topicAutomatic property for search table
        // This is to avoid indexing both topicAutomatic and topicManual and having the frontend choose which one to display
        detachedEntry.setTopicAutomatic(entry.getTopic());
        detachedEntry.setTopicSelection(entry.getTopicSelection());
        detachedEntry.setApprovedAITopic(entry.isApprovedAITopic());
        detachedEntry.setInputFileFormats(new TreeSet<>(entry.getInputFileFormats()));
        entry.getStarredUsers().forEach(user -> detachedEntry.addStarredUser((User)user));

        // Add the detached versions
        Version defaultVersion = EntryVersionHelper.determineRepresentativeVersion(entry).orElse(null);
        Set<Version> detachedVersions = detachVersions(entry.getWorkflowVersions(), defaultVersion);
        detachedEntry.setWorkflowVersions(detachedVersions);
    }

    private static void copyToolProperties(Tool detachedTool, Tool tool) {
        copyEntryProperties(detachedTool, tool);

        // Copy tool-specified properties
        // These are for facets
        detachedTool.setDescriptorType(tool.getDescriptorType());
        detachedTool.setDefaultWdlPath(tool.getDefaultWdlPath());
        detachedTool.setDefaultCwlPath(tool.getDefaultCwlPath());
        detachedTool.setNamespace(tool.getNamespace());
        detachedTool.setRegistry(tool.getRegistry());
        detachedTool.setPrivateAccess(tool.isPrivateAccess());

        // These are for table
        detachedTool.setGitUrl(tool.getGitUrl());
        detachedTool.setName(tool.getName());
        detachedTool.setToolname(tool.getToolname());
    }

    private static void copyWorkflowProperties(Workflow detachedWorkflow, Workflow workflow) {
        copyEntryProperties(detachedWorkflow, workflow);

        // Copy workflow-specific properties
        // These are for facets
        detachedWorkflow.setDescriptorType(workflow.getDescriptorType());
        detachedWorkflow.setSourceControl(workflow.getSourceControl());
        detachedWorkflow.setOrganization(workflow.getOrganization());
        // Set the descriptor type subclass if it has a meaningful value
        if (workflow.getDescriptorTypeSubclass().isApplicable()) {
            detachedWorkflow.setDescriptorTypeSubclass(workflow.getDescriptorTypeSubclass());
        }

        // These are for table
        detachedWorkflow.setWorkflowName(workflow.getWorkflowName());
        detachedWorkflow.setRepository(workflow.getRepository());
        detachedWorkflow.setGitUrl(workflow.getGitUrl());
    }

    private static Set<String> getVerifiedPlatforms(Set<? extends Version> workflowVersions) {
        return workflowVersions.stream().map(Version::getVerifiedPlatforms).flatMap(Stream::of).collect(Collectors.toSet());
    }

    private static List<String> getDistinctDescriptorTypeVersions(Entry entry, Set<? extends Version> workflowVersions) {
        String language;
        if (entry instanceof Tool tool && tool.getDescriptorType().size() == 1) {
            // Only set descriptor type versions if there's one descriptor type otherwise we can't tell which version belongs to which type without looking at the source files
            language = tool.getDescriptorType().get(0);
        } else if (entry instanceof Workflow workflow) {
            language = ToolsImplCommon.getDescriptorTypeFromDescriptorLanguage(workflow.getDescriptorType()).map(DescriptorType::toString).orElse("unsupported language");
        } else {
            return List.of();
        }

        // Get a list of unique descriptor type versions with the descriptor type prepended. Ex: 'WDL 1.0'
        return workflowVersions.stream()
                .map(workflowVersion -> workflowVersion.getVersionMetadata().getDescriptorTypeVersions())
                .flatMap(List::stream)
                .distinct()
                .map(descriptorTypeVersion -> String.join(" ", language, descriptorTypeVersion))
                .toList();
    }

    private static List<String> getDistinctEngineVersions(final Set<Version> workflowVersions) {
        return workflowVersions.stream()
            .map(workflowVersion -> workflowVersion.getVersionMetadata().getEngineVersions())
            .flatMap(List::stream)
            .distinct()
            .toList();
    }

    /**
     * Returns a set of Author containing non-ORCID authors and ORCID authors with additional information.
     * @param entry
     * @return
     */
    private static Set<Author> getAllAuthors(Entry entry) {
        Set<Author> allAuthors = new HashSet<>();
        if (!entry.getOrcidAuthors().isEmpty()) {
            Optional<String> token = ORCIDHelper.getOrcidAccessToken();
            if (token.isPresent()) {
                Set<OrcidAuthorInformation> orcidAuthorInformation = ((Set<OrcidAuthor>)entry.getOrcidAuthors()).stream()
                        .map(orcidAuthor -> ORCIDHelper.getOrcidAuthorInformation(orcidAuthor.getOrcid(), token.get()))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toSet());
                allAuthors.addAll(orcidAuthorInformation);
            }
        }
        allAuthors.addAll(entry.getAuthors());

        if (allAuthors.isEmpty()) {
            // Add an empty author with a null name so that ES has something to replace with the null_value otherwise an empty array is ignored by ES
            allAuthors.add(new Author());
        }
        return allAuthors;
    }

    private static Doi getSelectedConceptDoi(Entry<?, ?> entry) {
        if (!entry.getConceptDois().isEmpty()) {
            Doi selectedDoi = entry.getConceptDois().get(entry.getDoiSelection());
            if (selectedDoi == null) {
                selectedDoi = entry.getDefaultConceptDoi();
            }
            return selectedDoi;
        }
        return null;
    }


    /**
     * If entry is a checker workflow, return null.  Otherwise, return entry
     * @param entry     The entry to check
     * @return          null if checker, entry otherwise
     */
    private static Entry filterCheckerWorkflows(Entry entry) {
        return entry instanceof Workflow workflow && workflow.isIsChecker() ? null : entry;
    }

    /**
     * Remove checker workflow from list of entries
     * @param entries   List of all entries
     * @return          List of entries without checker workflows
     */
    public static List<Entry> filterCheckerWorkflows(List<Entry> entries) {
        return entries.stream().filter(entry -> entry instanceof Tool || (entry instanceof Workflow workflow && !workflow.isIsChecker())).toList();
    }
}
