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
package io.dockstore.webservice.resources;

import static io.dockstore.webservice.helpers.ORCIDHelper.getPutCodeFromLocation;
import static io.dockstore.webservice.resources.AuthenticatedResourceInterface.throwIf;
import static io.dockstore.webservice.resources.ResourceConstants.JWT_SECURITY_DEFINITION_NAME;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.api.SyncStatus;
import io.dockstore.webservice.core.Category;
import io.dockstore.webservice.core.CollectionOrganization;
import io.dockstore.webservice.core.DescriptionMetrics;
import io.dockstore.webservice.core.Doi;
import io.dockstore.webservice.core.Doi.DoiInitiator;
import io.dockstore.webservice.core.Doi.DoiType;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.LambdaEvent;
import io.dockstore.webservice.core.OrcidPutCode;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenScope;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.database.VersionVerifiedPlatform;
import io.dockstore.webservice.helpers.LambdaUrlChecker;
import io.dockstore.webservice.helpers.ORCIDHelper;
import io.dockstore.webservice.helpers.PublicStateManager;
import io.dockstore.webservice.helpers.StateManagerMode;
import io.dockstore.webservice.helpers.TransactionHelper;
import io.dockstore.webservice.helpers.doi.DoiHelper;
import io.dockstore.webservice.jdbi.DoiDAO;
import io.dockstore.webservice.jdbi.EntryDAO;
import io.dockstore.webservice.jdbi.EventDAO;
import io.dockstore.webservice.jdbi.LambdaEventDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.VersionDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import io.dockstore.webservice.languages.LanguageHandlerInterface;
import io.dockstore.webservice.permissions.PermissionsInterface;
import io.dockstore.webservice.permissions.Role;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import io.swagger.discourse.client.ApiClient;
import io.swagger.discourse.client.ApiException;
import io.swagger.discourse.client.Configuration;
import io.swagger.discourse.client.api.TopicsApi;
import io.swagger.discourse.client.model.InlineResponse2005;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import javax.xml.datatype.DatatypeConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prototype for methods that apply identically across tools and workflows.
 *
 * @author dyuen
 */
@Path("/entries")
@Api("entries")
@Produces(MediaType.APPLICATION_JSON)
@SecuritySchemes({ @SecurityScheme(type = SecuritySchemeType.HTTP, name = JWT_SECURITY_DEFINITION_NAME, scheme = "bearer") })
@Tag(name = "entries", description = ResourceConstants.ENTRIES)
public class EntryResource implements AuthenticatedResourceInterface, AliasableResourceInterface<Entry> {

    public static final String VERSION_NOT_BELONG_TO_ENTRY_ERROR_MESSAGE = "Version does not belong to entry";
    public static final String ENTRY_NO_DOI_ERROR_MESSAGE = "Entry does not have a concept DOI associated with it";
    public static final String VERSION_NO_DOI_ERROR_MESSAGE = "Version does not have a DOI url associated with it";
    public static final String ENTRY_NOT_DELETABLE_MESSAGE = "The specified entry is not deletable.";
    private static final Logger LOG = LoggerFactory.getLogger(EntryResource.class);
    private static final int PROCESSOR_PAGE_SIZE = 25;

    private final TokenDAO tokenDAO;
    private LambdaUrlChecker lambdaUrlChecker;
    private ToolDAO toolDAO;
    private WorkflowDAO workflowDAO;
    private final VersionDAO<?> versionDAO;
    private final UserDAO userDAO;
    private final EventDAO eventDAO;
    private final LambdaEventDAO lambdaEventDAO;
    private final DoiDAO doiDAO;
    private final CollectionHelper collectionHelper;
    private final TopicsApi topicsApi;
    private final String discourseKey;
    private final String discourseUrl;
    private final int discourseCategoryId;
    private final String discourseApiUsername = "system";
    private final int maxDescriptionLength = 500;
    private final String baseUrl;
    private final String hostName;
    private final boolean isProduction;
    private final PermissionsInterface permissionsInterface;
    private final SessionFactory sessionFactory;

    private IntFunction<List<Workflow>> getWorkflows = offset -> workflowDAO.findAllWorkflows(offset, PROCESSOR_PAGE_SIZE);

    private IntFunction<List<Tool>> getTools = offset -> toolDAO.findAllTools(offset, PROCESSOR_PAGE_SIZE);

    private BiFunction<List<Workflow>, Boolean, Void> processWorkflowsForLanguageVersions =
            (workflows, allVersions) -> {
                workflows.forEach(workflow -> {
                    LanguageHandlerInterface languageHandlerInterface =
                            LanguageHandlerFactory.getInterface(workflow.getDescriptorType());
                    workflow.getWorkflowVersions().stream()
                            .filter(version -> allVersions || version.getVersionMetadata()
                                    .getDescriptorTypeVersions().isEmpty())
                            .forEach(version -> {
                                final String primaryPath = version.getWorkflowPath();
                                readSourceFilesAndUpdate(languageHandlerInterface, version, primaryPath);
                            });
                });
                return null;
            };

    private BiFunction<List<Workflow>, Boolean, Void> processWorkflowsForOpenData =
            (workflows, allVersions) -> {
                workflows.forEach(workflow -> {
                    LanguageHandlerInterface languageHandlerInterface =
                            LanguageHandlerFactory.getInterface(workflow.getDescriptorType());
                    workflow.getWorkflowVersions().stream()
                            .filter(version -> allVersions || version.getVersionMetadata().getPublicAccessibleTestParameterFile() == null)
                            .forEach(version -> {
                                final Boolean openData =
                                        languageHandlerInterface.isOpenData(version, lambdaUrlChecker)
                                                .orElse(null);
                                version.getVersionMetadata().setPublicAccessibleTestParameterFile(openData);
                            });
                });
                return null;
            };

    private BiFunction<List<Tool>, Boolean, Void> processLegacyToolsForLanguageVersions =
            (tools, allVersions) -> {
                tools.stream()
                        .filter(tool -> tool.getDescriptorType().size() == 1) // Only support tools with 1 language
                        .forEach(tool -> {
                            final String descriptorLanguageText = tool.getDescriptorType().get(0);
                            LanguageHandlerInterface languageHandlerInterface =
                                    LanguageHandlerFactory.getInterface(
                                            DescriptorLanguage.convertShortStringToEnum(descriptorLanguageText));
                            tool.getWorkflowVersions().stream()
                                    .filter(version -> allVersions || version.getVersionMetadata()
                                            .getDescriptorTypeVersions().isEmpty())
                                    .forEach(version -> {
                                        final String primaryPath;
                                        if (DescriptorLanguage.convertShortStringToEnum(descriptorLanguageText)
                                                == DescriptorLanguage.WDL) {
                                            primaryPath = version.getWdlPath();
                                        } else { // We will not add new languages to Tool (perhaps to AppTool), so this is safe
                                            primaryPath = version.getCwlPath();
                                        }
                                        readSourceFilesAndUpdate(languageHandlerInterface, version,
                                                primaryPath);
                                    });
                        });
                return null;
            };



    @SuppressWarnings("checkstyle:ParameterNumber")
    public EntryResource(SessionFactory sessionFactory, PermissionsInterface permissionsInterface, EventDAO eventDAO, TokenDAO tokenDAO, ToolDAO toolDAO, VersionDAO<?> versionDAO, UserDAO userDAO,
        WorkflowDAO workflowDAO, DockstoreWebserviceConfiguration configuration) {
        this.sessionFactory = sessionFactory;
        this.permissionsInterface = permissionsInterface;
        this.eventDAO = eventDAO;
        this.workflowDAO = workflowDAO;
        this.toolDAO = toolDAO;
        this.versionDAO = versionDAO;
        this.tokenDAO = tokenDAO;
        this.userDAO = userDAO;
        this.lambdaEventDAO = new LambdaEventDAO(sessionFactory);
        this.doiDAO = new DoiDAO(sessionFactory);
        this.collectionHelper = new CollectionHelper(sessionFactory, toolDAO, versionDAO);
        discourseUrl = configuration.getDiscourseUrl();
        discourseKey = configuration.getDiscourseKey();
        discourseCategoryId = configuration.getDiscourseCategoryId();
        final String checkUrlLambdaUrl = configuration.getCheckUrlLambdaUrl();
        lambdaUrlChecker = checkUrlLambdaUrl == null ? null : new LambdaUrlChecker(checkUrlLambdaUrl);

        ApiClient apiClient = Configuration.getDefaultApiClient();
        apiClient.addDefaultHeader("Content-Type", "application/x-www-form-urlencoded");
        apiClient.addDefaultHeader("cache-control", "no-cache");
        apiClient.setBasePath(discourseUrl);

        baseUrl = configuration.getExternalConfig().computeBaseUrl();
        hostName = configuration.getExternalConfig().getHostname();
        isProduction = configuration.getExternalConfig().computeIsProduction();
        topicsApi = new TopicsApi(apiClient);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{alias}/aliases")
    @Operation(operationId = "getEntryByAlias", description = "Retrieves an entry by alias.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully retrieved entry", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Entry.class)))
    public Entry getEntryByAlias(@Parameter(hidden = true, name = "user") @Auth Optional<User> user,
            @Parameter(description = "Alias", name = "alias", in = ParameterIn.PATH, required = true) @PathParam("alias") String alias) {
        Entry<? extends Entry, ? extends Version> entry = toolDAO.getGenericEntryByAlias(alias);
        checkNotNullEntry(entry);
        checkCanRead(user, entry);
        return entry;
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/{id}")
    @Operation(operationId = "deleteEntry", description = "Completely remove an entry from Dockstore.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully deleted the entry", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Entry.class)))
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = ENTRY_NOT_DELETABLE_MESSAGE)
    public Entry deleteEntry(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user,
        @ApiParam(value = "Entry to delete.", required = true) @PathParam("id") Long id) {
        Entry<?, ?> entry = toolDAO.getGenericEntryById(id);
        checkNotNullEntry(entry);
        checkCanWrite(user, entry);
        throwIf(!entry.isDeletable(), ENTRY_NOT_DELETABLE_MESSAGE, HttpStatus.SC_FORBIDDEN);
        // Remove the events associated with the entry
        eventDAO.deleteEventByEntryID(entry.getId());
        // Delete the entry using an arbitrary EntryDAO, which works, but isn't the "purest" approach.
        // Later, we may create a helper class to select the appropriate DAO for a given entry, and we should use it here...
        ((EntryDAO)workflowDAO).delete(entry);
        LOG.info("Deleted entry {}", entry.getEntryPath());
        return entry;
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/{id}/archive")
    @Operation(operationId = "archiveEntry", description = "Archive an entry.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully archived the entry", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Entry.class)))
    public Entry archiveEntry(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user,
        @ApiParam(value = "Entry to archive.", required = true) @PathParam("id") Long id) {
        Entry<?, ?> entry = toolDAO.getGenericEntryById(id);
        updateArchived(true, user, entry);
        Hibernate.initialize(entry.getWorkflowVersions());
        return entry;
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/{id}/unarchive")
    @Operation(operationId = "unarchiveEntry", description = "Unarchive an entry.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully unarchived the entry", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Entry.class)))
    public Entry unarchiveEntry(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user,
        @ApiParam(value = "Entry to unarchive.", required = true) @PathParam("id") Long id) {
        Entry<?, ?> entry = toolDAO.getGenericEntryById(id);
        updateArchived(false, user, entry);
        Hibernate.initialize(entry.getWorkflowVersions());
        return entry;
    }

    private void updateArchived(boolean archive, User user, Entry<?, ?> entry) {
        checkNotNullEntry(entry);
        checkIsOwnerOrAdmin(user, entry);

        if (entry.isArchived() != archive) {
            entry.setArchived(archive);
            eventDAO.archiveEvent(archive, Optional.of(user), entry);
            PublicStateManager.getInstance().handleIndexUpdate(entry, StateManagerMode.UPDATE);
            LOG.info("Set archived = {} on entry {}", archive, entry.getEntryPath());
        }
    }

    @POST
    @Timed
    @UnitOfWork
    @Override
    @Path("/{id}/aliases")
    @Operation(operationId = "addAliases", description = "Add aliases linked to a entry in Dockstore.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(nickname = "addAliases", value = "Add aliases linked to a entry in Dockstore.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Aliases are alphanumerical (case-insensitive and may contain internal hyphens), given in a comma-delimited list.", response = Entry.class)
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully added alias to entry", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Entry.class)))
    public Entry addAliases(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user,
                               @ApiParam(value = "Entry to modify.", required = true) @PathParam("id") Long id,
                               @ApiParam(value = "Comma-delimited list of aliases.", required = true) @QueryParam("aliases") String aliases) {
        return AliasableResourceInterface.super.addAliases(user, id, aliases);
    }

    @GET
    @Path("/{id}/collections")
    @Timed
    @UnitOfWork(readOnly = true)
    @Operation(operationId = "entryCollections", description = "Get the collections and approved organizations that contain the published entry")
    @ApiOperation(value = "Get the collections and organizations that contain the published entry", notes = "Entry must be published", response = CollectionOrganization.class, responseContainer = "List")
    public List<CollectionOrganization> entryCollections(@ApiParam(value = "id", required = true) @PathParam("id") Long id) {
        Entry<? extends Entry, ? extends Version> entry = toolDAO.getGenericEntryById(id);
        if (entry == null || !entry.getIsPublished()) {
            throw new CustomWebApplicationException("Published entry does not exist.", HttpStatus.SC_BAD_REQUEST);
        }
        return this.toolDAO.findCollectionsByEntryId(entry.getId());
    }

    @GET
    @Path("/{id}/categories")
    @Timed
    @UnitOfWork(readOnly = true)
    @Operation(operationId = "entryCategories", description = "Get the categories that contain the published entry")
    @ApiOperation(value = "Get the categories that contain the published entry", notes = "Entry must be published", response = Category.class, responseContainer = "List", hidden = true)
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully retrieved categories", content = @Content(mediaType = MediaType.APPLICATION_JSON, array = @ArraySchema(schema = @Schema(implementation = Category.class))))
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = "Entry must be published")
    public List<Category> entryCategories(@Parameter(hidden = true, name = "user")@Auth Optional<User> user,
            @Parameter(description = "Entry ID", name = "id", in = ParameterIn.PATH, required = true) @PathParam("id") Long id) {
        Entry<? extends Entry, ? extends Version> entry = toolDAO.getGenericEntryById(id);
        checkNotNullEntry(entry);
        checkCanRead(user, entry);
        List<Category> categories = this.toolDAO.findCategoriesByEntryId(entry.getId());
        collectionHelper.evictAndSummarize(categories);
        return categories;
    }

    @GET
    @Path("/{entryId}/verifiedPlatforms")
    @UnitOfWork
    @ApiOperation(value = "Get the verified platforms for each version of an entry.",  hidden = true)
    @Operation(operationId = "getVerifiedPlatforms", description = "Get the verified platforms for each version of an entry.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    public List<VersionVerifiedPlatform> getVerifiedPlatforms(@Parameter(hidden = true, name = "user")@Auth Optional<User> user,
            @Parameter(name = "entryId", description = "id of the entry", required = true, in = ParameterIn.PATH) @PathParam("entryId") Long entryId) {
        Entry<? extends Entry, ? extends Version> entry = toolDAO.getGenericEntryById(entryId);
        checkNotNullEntry(entry);
        checkCanRead(user, entry);

        List<VersionVerifiedPlatform> verifiedVersions = versionDAO.findEntryVersionsWithVerifiedPlatforms(entryId);
        return verifiedVersions;
    }

    @GET
    @Path("/{entryId}/syncStatus")
    @UnitOfWork
    @Operation(operationId = "syncStatus", description = "Get information about automatic updates to the entry", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Information about automatic updates to the entry",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SyncStatus.class)))
    public SyncStatus syncStatus(
        @Parameter(hidden = true, name = "user") @Auth User user,
        @Parameter(description = "id of the entry") @PathParam("entryId") Long entryId) {
        Entry<?, ?> entry = toolDAO.getGenericEntryById(entryId);
        checkNotNullEntry(entry);
        checkIsOwnerOrAdmin(user, entry);

        // If the entry is not a .dockstore.yml-based Workflow, it's not being automatically updated.
        if (!(entry instanceof Workflow workflow) || workflow.getMode() != WorkflowMode.DOCKSTORE_YML) {
            return new SyncStatus(false);
        }

        List<LambdaEvent> latest = lambdaEventDAO.findByOrganization(workflow.getOrganization(), 0, 1, null, "dbCreateDate", "desc", Optional.of(List.of(workflow.getRepository())));
        // If the Workflow exists in our database, the GitHub App must have been installed at some point.
        // If the last LambdaEvent is an UNINSTALL, the GitHub App has been uninstalled and not since reinstalled.
        // Otherwise, either: a) the GitHub App is installed, or b) the last uninstall happened before we started logging uninstalls.
        return new SyncStatus(latest.isEmpty() || latest.get(0).getType() != LambdaEvent.LambdaEventType.UNINSTALL);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{entryId}/versions/{versionId}/fileTypes")
    @ApiOperation(value = "Retrieve the file types of a version's sourcefiles",  hidden = true)
    @Operation(operationId = "getVersionsFileTypes", description = "Retrieve the unique file types of a version's sourcefile", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    public SortedSet<DescriptorLanguage.FileType> getVersionsFileTypes(@Parameter(hidden = true, name = "user")@Auth Optional<User> user,
            @Parameter(name = "entryId", description = "Entry to retrieve the version from", required = true, in = ParameterIn.PATH) @PathParam("entryId") Long entryId,
            @Parameter(name = "versionId", description = "Version to retrieve the sourcefile types from", required = true, in = ParameterIn.PATH) @PathParam("versionId") Long versionId) {
        Entry<? extends Entry, ? extends Version> entry = toolDAO.getGenericEntryById(entryId);
        checkNotNullEntry(entry);
        checkCanRead(user, entry);

        Version version = versionDAO.findVersionInEntry(entryId, versionId);
        if (version == null) {
            throw new CustomWebApplicationException("Version " + versionId + " does not exist for this entry", HttpStatus.SC_BAD_REQUEST);
        }

        SortedSet<SourceFile> sourceFiles = version.getSourceFiles();
        return sourceFiles.stream().map(sourceFile -> sourceFile.getType()).collect(Collectors.toCollection(TreeSet::new));
    }

    @GET
    @UnitOfWork
    @Path("/{entryId}/versions/{versionId}/descriptionMetrics")
    @ApiOperation(value = "Retrieve metrics on the description of an entry")
    @Operation(operationId = "getDescriptionMetrics", description = "Retrieve metrics on the description of an entry", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully calculated description metrics", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DescriptionMetrics.class)))
    public DescriptionMetrics calculateDescriptionMetrics(@Parameter(hidden = true, name = "user")@Auth Optional<User> user,
        @Parameter(name = "entryId", description = "Entry to retrieve the version from", required = true, in = ParameterIn.PATH) @PathParam("entryId") Long entryId,
        @Parameter(name = "versionId", description = "Version to retrieve the sourcefile types from", required = true, in = ParameterIn.PATH) @PathParam("versionId") Long versionId) {
        Entry<? extends Entry, ? extends Version> entry = toolDAO.getGenericEntryById(entryId);
        checkNotNullEntry(entry);
        checkCanRead(user, entry);

        Version version = versionDAO.findVersionInEntry(entryId, versionId);
        if (version == null) {
            throw new CustomWebApplicationException("Version " + versionId + " does not exist for this entry", HttpStatus.SC_NOT_FOUND);
        }

        final String description = version.getVersionMetadata().getDescription();

        return new DescriptionMetrics(description);
    }

    @POST
    @UnitOfWork
    @Path("/{entryId}/versions/{versionId}/generateCustomDoi")
    @Operation(operationId = "generateCustomDoi", description = "Generate a custom DOI for the specific version", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully created custom DOI", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Doi.class)))
    public Doi generateCustomDoi(@Parameter(hidden = true, name = "user")@Auth User user,
        @Parameter(name = "entryId", description = "Entry to generate the DOI for", required = true, in = ParameterIn.PATH) @PathParam("entryId") Long entryId,
        @Parameter(name = "versionId", description = "Version to generate the DOI for", required = true, in = ParameterIn.PATH) @PathParam("versionId") Long versionId) {
        Entry<? extends Entry, ? extends Version> entry = toolDAO.getGenericEntryById(entryId);
        checkNotNullEntry(entry);
        checkCanWrite(user, entry);

        Version version = versionDAO.findVersionInEntry(entryId, versionId);
        if (version == null) {
            throw new CustomWebApplicationException("Version " + versionId + " does not exist for this entry", HttpStatus.SC_NOT_FOUND);
        }

        // check that entry is published
        if (!entry.getIsPublished()) {
            throw new CustomWebApplicationException("Entry must be published", HttpStatus.SC_BAD_REQUEST);
        }
        // check that version is not hidden
        if (version.isHidden()) {
            throw new CustomWebApplicationException("Version must not be hidden", HttpStatus.SC_BAD_REQUEST);
        }

        String doiName;
        try {
            doiName = DoiHelper.createDoi(entry, version);
        } catch (RuntimeException e) {
            LOG.error("Could not create custom DOI", e);
            throw new CustomWebApplicationException("Could not create custom DOI", HttpStatus.SC_BAD_REQUEST);
        }

        long doiId = doiDAO.create(new Doi(DoiType.VERSION, DoiInitiator.CUSTOM, doiName));
        Doi doi = doiDAO.findById(doiId);

        version.getDois().put(doi.getInitiator(), doi);

        return doi;
    }

    @POST
    @Path("/{entryId}/exportToOrcid")
    @Timed
    @UnitOfWork
    @Operation(description = "Export entry to ORCID. DOI is required", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully exported entry to ORCID", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Entry.class)))
    @ApiResponse(responseCode = HttpStatus.SC_INTERNAL_SERVER_ERROR + "", description = "Internal Server Error")
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = "Not Found")
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = "Bad Request")
    @ApiOperation(value = "hidden", hidden = true)
    public Entry exportToORCID(@Parameter(hidden = true, name = "user") @Auth User user, @Parameter(description = "The id of the entry to export.", name = "entryId", in = ParameterIn.PATH, required = true)
        @PathParam("entryId") Long entryId,
        @Parameter(description = "Optional version ID of the entry version to export.", name = "versionId", in = ParameterIn.QUERY) @QueryParam("versionId") Long versionId) {
        Entry<? extends Entry, ? extends Version> entry = toolDAO.getGenericEntryById(entryId);
        checkNotNullEntry(entry);
        checkCanRead(Optional.of(user), entry);
        List<Token> orcidByUserId = tokenDAO.findOrcidByUserId(user.getId());
        String putCode;
        User nonCachedUser = this.userDAO.findById(user.getId());
        Optional<Version> optionalVersion = Optional.empty();

        if (versionId != null) {
            Version version = versionDAO.findVersionInEntry(entry.getId(), versionId);
            if (version == null) {
                throw new CustomWebApplicationException(VERSION_NOT_BELONG_TO_ENTRY_ERROR_MESSAGE, HttpStatus.SC_BAD_REQUEST);
            }
            if (version.getDois().isEmpty()) {
                throw new CustomWebApplicationException(VERSION_NO_DOI_ERROR_MESSAGE, HttpStatus.SC_BAD_REQUEST);
            }
            optionalVersion = Optional.ofNullable(version);
        } else {
            if (entry.getConceptDois().isEmpty()) {
                throw new CustomWebApplicationException(ENTRY_NO_DOI_ERROR_MESSAGE, HttpStatus.SC_BAD_REQUEST);
            }
        }
        if (orcidByUserId.isEmpty()) {
            throw new CustomWebApplicationException("ORCID account is not linked to user account", HttpStatus.SC_BAD_REQUEST);
        }
        if (!orcidByUserId.get(0).getScope().equals(TokenScope.ACTIVITIES_UPDATE)) {
            throw new CustomWebApplicationException("Please relink your ORCID ID in the accounts page.", HttpStatus.SC_UNAUTHORIZED);
        }
        if (ORCIDHelper.getOrcidBaseApiUrl() == null) {
            LOG.error("ORCID auth URL is likely incorrect");
            throw new CustomWebApplicationException("Could not export to ORCID: Dockstore ORCID integration is not set up correctly.", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }


        OrcidPutCode userPutCode;
        if (optionalVersion.isPresent()) {
            userPutCode = optionalVersion.get().getVersionMetadata().getUserIdToOrcidPutCode().get(user.getId());
        } else {
            userPutCode = entry.getUserIdToOrcidPutCode().get(user.getId());
        }
        putCode = (userPutCode == null) ? null : userPutCode.orcidPutCode;

        String orcidWorkString;
        boolean updateSuccess;
        String orcidId = nonCachedUser.getOrcid();
        if (orcidId == null) {
            throw new CustomWebApplicationException("Dockstore could not get your ORCID ID", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        try {
            orcidWorkString = ORCIDHelper.getOrcidWorkString(entry, optionalVersion, putCode);
            if (putCode == null) {
                int responseCode = createOrcidWork(optionalVersion, entry, orcidId, orcidWorkString, orcidByUserId, user.getId());
                // If there's a conflict, the user already has an ORCID work with the same DOI URL. Try to link the ORCID work to the Dockstore entry by getting its put code
                if (responseCode == HttpStatus.SC_CONFLICT) {
                    String doiUrl = optionalVersion.isPresent() ? optionalVersion.get().getDefaultDoi().getName() : entry.getDefaultConceptDoi().getName();
                    Optional<Long> existingPutCode = ORCIDHelper.searchForPutCodeByDoiUrl(orcidId, orcidByUserId, doiUrl);
                    if (existingPutCode.isPresent()) {
                        String existingPutCodeString = existingPutCode.get().toString();
                        // Sync the ORCID put code to Dockstore
                        setPutCode(optionalVersion, entry, existingPutCodeString, user.getId());
                        orcidWorkString = ORCIDHelper.getOrcidWorkString(entry, optionalVersion, existingPutCodeString);
                        // Since the ORCID work was already created, update the work
                        updateSuccess = updateOrcidWork(orcidId, orcidWorkString, orcidByUserId, existingPutCodeString);
                        if (!updateSuccess) {
                            // Shouldn't really get here because we know the work with the put code exists
                            LOG.error("Could not find ORCID work based on put code: {}", existingPutCodeString);
                        }
                    } else {
                        throw new CustomWebApplicationException("Could not export to ORCID: unable to find the put code for the existing ORCID work with DOI URL " + doiUrl, HttpStatus.SC_INTERNAL_SERVER_ERROR);
                    }
                }
            } else {
                updateSuccess = updateOrcidWork(orcidId, orcidWorkString, orcidByUserId, putCode);
                if (!updateSuccess) {
                    LOG.error("Could not find ORCID work based on put code: {} ", putCode);
                    // This is almost going to be redundant because it's going to attempt to create a new work
                    setPutCode(optionalVersion, entry, null, user.getId());
                    orcidWorkString = ORCIDHelper.getOrcidWorkString(entry, optionalVersion, null);
                    createOrcidWork(optionalVersion, entry, orcidId, orcidWorkString, orcidByUserId, user.getId());
                }
            }
        } catch (IOException | URISyntaxException | JAXBException | DatatypeConfigurationException e) {
            throw new CustomWebApplicationException("Could not export to ORCID: " + e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomWebApplicationException("Could not export to ORCID: " + e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }

        Hibernate.initialize(entry.getWorkflowVersions());
        return entry;
    }

    private void setPutCode(Optional<Version> optionalVersion, Entry entry, String putCode, long userId) {
        OrcidPutCode orcidPutCode = new OrcidPutCode(putCode);
        if (optionalVersion.isPresent()) {
            optionalVersion.get().getVersionMetadata().getUserIdToOrcidPutCode().put(userId, orcidPutCode);
        } else {
            entry.getUserIdToOrcidPutCode().put(userId, orcidPutCode);
        }
    }

    private int createOrcidWork(Optional<Version> optionalVersion, Entry entry, String orcidId, String orcidWorkString,
        List<Token> orcidTokens, long userId) throws IOException, URISyntaxException, InterruptedException {
        HttpResponse<String> response = ORCIDHelper
                .postWorkString(orcidId, orcidWorkString, orcidTokens.get(0).getToken());
        switch (response.statusCode()) {
        case HttpStatus.SC_CREATED:
            setPutCode(optionalVersion, entry, getPutCodeFromLocation(response), userId);
            return response.statusCode();
        case HttpStatus.SC_CONFLICT: // User has an ORCID work with the same DOI URL.
            return response.statusCode();
        default:
            throw new CustomWebApplicationException("Could not export to ORCID.\n" + response.body(), response.statusCode());
        }
    }

    /**
     * return true means everything is fine
     * return false means there's a syncing problem (Dockstore has put code, ORCID does not)
     */
    private boolean updateOrcidWork(String orcidId, String orcidWorkString, List<Token> orcidTokens, String putCode)
            throws IOException, URISyntaxException, InterruptedException {
        HttpResponse<String> response = ORCIDHelper
                .putWorkString(orcidId, orcidWorkString, orcidTokens.get(0).getToken(), putCode);
        switch (response.statusCode()) {
        case HttpStatus.SC_OK:
            return true;
        case HttpStatus.SC_NOT_FOUND:
            return false;
        default:
            throw new CustomWebApplicationException("Could not export to ORCID: " + response.body(), response.statusCode());
        }
    }

    @POST
    @Path("/{id}/topic")
    @Timed
    @RolesAllowed({"curator", "admin"})
    @UnitOfWork
    @ApiOperation(value = "Create a discourse topic for an entry.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = Entry.class)
    @Operation(description = "Create a discourse topic for an entry.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    public Entry setDiscourseTopic(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user,
            @ApiParam(value = "The id of the entry to add a topic to.", required = true)
            @Parameter(description = "The id of the entry to add a topic to.", name = "id", in = ParameterIn.PATH, required = true)
            @PathParam("id") Long id) {
        return createAndSetDiscourseTopic(id);
    }

    /**
     * Updates the language versions for all tool and workflow versions. If <code>allVersions</code> is true, processes all
     * versions; if it's false, only processes those that have not been processed.
     * @param user
     * @param allVersions
     * @return
     */
    @Path("/updateLanguageVersions")
    @RolesAllowed("admin")
    @UnitOfWork
    @POST
    @Operation(operationId = "updateLanguageVersions", description = "Update language versions", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Number of entries processed",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Integer.class)))
    public int updateLanguageVersions(
        @ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @Parameter(description = "Whether to process all versions or only versions without language descriptor already set", in = ParameterIn.QUERY, required = false)
        @QueryParam("allVersions") @DefaultValue("false") boolean allVersions) {
        LOG.info("Processing tools for language versions");
        final int processedTools = loadAndProcessEntries(getTools, processLegacyToolsForLanguageVersions, allVersions);
        LOG.info("Completed processing {} tools", processedTools);
        LOG.info("Processing workflows for language versions");
        final int processedWorkflows = loadAndProcessEntries(getWorkflows, processWorkflowsForLanguageVersions, allVersions);
        LOG.info("Completed processing {} workflows", processedWorkflows);
        return processedTools + processedWorkflows;
    }

    /**
     * Goes through workflow (BioWorkflow and AppTool) versions and updates their open data status. If <code>allVersions</code> is true,
     * processes all workflow versions, if it's false, processes only versions whose open data status is null.
     * @param user
     * @param allVersions
     * @return
     */
    @Path("/updateOpenData")
    @RolesAllowed("admin")
    @UnitOfWork
    @POST
    @Operation(operationId = "updateOpenData", description = "Update open data", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Number of entries processed",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Integer.class)))
    public int updateOpenData(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @Parameter(description = "Whether to process all versions or only versions without open data already set", in = ParameterIn.QUERY, required = false)
        @QueryParam("allVersions") @DefaultValue("false") boolean allVersions) {
        if (lambdaUrlChecker == null) {
            throw new CustomWebApplicationException("The url checker is not configured; this request cannot be processed.", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        LOG.info("Processing workflows for open data");
        final int processedEntries = loadAndProcessEntries(getWorkflows, processWorkflowsForOpenData, allVersions);
        LOG.info("Completed processing {} entries for open data", processedEntries);
        return processedEntries;
    }

    /**
     * Loads and processes entries in batches, committing each batch as it goes along. The batch
     * size is {@link #PROCESSOR_PAGE_SIZE}
     * @param loader
     * @param processor
     * @param allVersions
     * @return the number of entries processed
     */
    private <T extends Entry> int loadAndProcessEntries(IntFunction<List<T>> loader,
        BiFunction<List<T>, Boolean, Void> processor, Boolean allVersions) {
        final ProcessorProgress progress = new ProcessorProgress();
        while (!progress.done) {
            final TransactionHelper transactionHelper = new TransactionHelper(sessionFactory);
            transactionHelper.transaction(() -> {
                final List<T> list = loader.apply(progress.offset);
                if (list.size() < PROCESSOR_PAGE_SIZE) {
                    progress.done = true;
                }
                progress.processedEntries += list.size();
                LOG.info("Executing {} updates starting at offset {}", list.size(), progress.offset);
                progress.offset += PROCESSOR_PAGE_SIZE;
                try {
                    processor.apply(list, allVersions);
                } catch (Exception e) {
                    LOG.error("Error processing entries", e); // Log and continue
                }
            });
        }
        return progress.processedEntries;
    }


    private void readSourceFilesAndUpdate(final LanguageHandlerInterface languageHandlerInterface,
        final Version<? extends Version> workflowVersion, String primaryPath) {
        workflowVersion.getSourceFiles().stream()
            .filter(sf -> isPrimaryDescriptor(primaryPath, sf))
            .findFirst()
            .ifPresent(primary -> {
                try {
                    languageHandlerInterface.parseWorkflowContent(primaryPath,
                        primary.getContent(), workflowVersion.getSourceFiles(), workflowVersion);
                } catch (RuntimeException e) {
                    // Log the error and carry on.
                    final String message = String.format("Error parsing workflow content for version %s", workflowVersion.getId());
                    LOG.error(message, e);
                }
            });
    }

    private boolean isPrimaryDescriptor(String path, SourceFile sourceFile) {
        return sourceFile.getPath().equals(path);
    }


    /**
     * For a given entry, create a Discourse thread if applicable and set in database
     * @param id entry id
     * @return Entry with discourse ID set
     */
    public Entry createAndSetDiscourseTopic(Long id) throws CustomWebApplicationException {
        Entry entry = this.toolDAO.getGenericEntryById(id);

        if (entry == null || !entry.getIsPublished()) {
            throw new CustomWebApplicationException("Entry " + id + " does not exist or is not published.", HttpStatus.SC_NOT_FOUND);
        }

        if (entry.getTopicId() != null) {
            throw new CustomWebApplicationException("Entry " + id + " already has an associated Discourse topic.", HttpStatus.SC_BAD_REQUEST);
        }

        // Create title and link to entry
        final String entryPath = entry.getEntryPath();
        final String title = isProduction ? entryPath : (baseUrl + " " + entryPath);
        final String entryLink = baseUrl + "/" + entry.getEntryTypeMetadata().getSitePath() + "/" + entryPath;

        // Create description
        String description = StringUtils.defaultString(entry.getDescription());
        description = StringUtils.truncate(description, maxDescriptionLength);
        description += "\n<hr>\n<small>This is a companion discussion topic for the original entry at <a href='" + entryLink + "'>" + title + "</a></small>\n";

        // Check that discourse is reachable
        boolean isReachable;
        HttpURLConnection connection = null;
        try {
            URL url = new URL(discourseUrl);
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            int respCode = connection.getResponseCode();
            isReachable = respCode == HttpStatus.SC_OK;
        } catch (IOException ex) {
            LOG.error("Error reaching " + discourseUrl, ex);
            isReachable = false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        if (isReachable) {
            // Create a discourse topic
            InlineResponse2005 response;
            try {
                response = topicsApi.postsJsonPost(description, discourseKey, discourseApiUsername, title, null, discourseCategoryId, null, null, null);
                entry.setTopicId(response.getTopicId().longValue());
            } catch (ApiException ex) {
                String message = "Could not add a topic " + title + " to category " + discourseCategoryId;
                LOG.error(message, ex);
                throw new CustomWebApplicationException(message, HttpStatus.SC_BAD_REQUEST);
            }
        }

        return entry;
    }

    @Override
    public Optional<PublicStateManager> getPublicStateManager() {
        return Optional.of(PublicStateManager.getInstance());
    }

    @Override
    public Entry getAndCheckResource(User user, Long id) {
        Entry<? extends Entry, ? extends Version> c = toolDAO.getGenericEntryById(id);
        checkNotNullEntry(c);
        checkCanWrite(user, c);
        return c;
    }

    @Override
    public Entry getAndCheckResourceByAlias(String alias) {
        throw new UnsupportedOperationException("Use the TRS API for tools and workflows");
    }

    @Override
    public boolean canExamine(User user, Entry entry) {
        return AuthenticatedResourceInterface.super.canExamine(user, entry) || AuthenticatedResourceInterface.canDoAction(permissionsInterface, user, entry, Role.Action.READ);
    }

    @Override
    public boolean canWrite(User user, Entry entry) {
        return isWritable(entry) && (AuthenticatedResourceInterface.super.canWrite(user, entry) || AuthenticatedResourceInterface.canDoAction(permissionsInterface, user, entry, Role.Action.WRITE));
    }

    @Override
    public boolean canShare(User user, Entry entry) {
        return AuthenticatedResourceInterface.super.canShare(user, entry) || AuthenticatedResourceInterface.canDoAction(permissionsInterface, user, entry, Role.Action.SHARE);
    }

    /**
     * Need this class because the values need to be accessed from a lambda, and a lambda can
     * only access "effectively final" variables from outside the lambda.
     *
     */
    private static class ProcessorProgress {
        private int offset = 0;
        private int processedEntries = 0;
        private boolean done = false;
    }

}
