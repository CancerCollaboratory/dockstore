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

import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;

import javax.ws.rs.Path;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.VersionValidation;
import io.dockstore.webservice.helpers.ElasticMode;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import io.dockstore.webservice.permissions.PermissionsInterface;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

/**
 * @author dyuen
 */
@Api("hosted")
@Path("/containers")
public class HostedToolResource extends AbstractHostedEntryResource<Tool, Tag, ToolDAO, TagDAO> {
    private static final Logger LOG = LoggerFactory.getLogger(HostedToolResource.class);
    private final ToolDAO toolDAO;
    private final TagDAO tagDAO;

    public HostedToolResource(SessionFactory sessionFactory, PermissionsInterface permissionsInterface, DockstoreWebserviceConfiguration.LimitConfig limitConfig) {
        super(sessionFactory, permissionsInterface, limitConfig);
        this.tagDAO = new TagDAO(sessionFactory);
        this.toolDAO = new ToolDAO(sessionFactory);
    }

    @Override
    protected ToolDAO getEntryDAO() {
        return toolDAO;
    }

    @Override
    protected TagDAO getVersionDAO() {
        return tagDAO;
    }

    @Override
    @ApiOperation(nickname = "createHostedTool", value = "Create a hosted tool", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Create a hosted tool", response = Tool.class)
    public Tool createHosted(User user, String registry, String name, String descriptorType, String namespace) {
        return super.createHosted(user, registry, name, descriptorType, namespace);
    }

    @Override
    protected Tool getEntry(User user, String registry, String name, String descriptorType, String namespace) {
        Tool tool = new Tool();
        tool.setRegistry(registry);
        tool.setNamespace(namespace);
        tool.setName(name);
        tool.setMode(ToolMode.HOSTED);
        tool.setLastUpdated(new Date());
        tool.setLastModified(new Date());
        tool.getUsers().add(user);
        return tool;
    }

    @Override
    @ApiOperation(nickname = "editHostedTool", value = "Non-idempotent operation for creating new revisions of hosted tools", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Non-idempotent operation for creating new revisions of hosted tools", response = Tool.class)
    public Tool editHosted(User user, Long entryId, Set<SourceFile> sourceFiles) {
        return super.editHosted(user, entryId, sourceFiles);
    }

    @Override
    protected void populateMetadata(Set<SourceFile> sourceFiles, Tool entry, Tag tag) {
        for (SourceFile file : sourceFiles) {
            if (file.getPath().equals(tag.getCwlPath()) || file.getPath().equals(tag.getWdlPath())) {
                LOG.info("refreshing metadata based on " + file.getPath() + " from " + tag.getName());
                LanguageHandlerFactory.getInterface(file.getType()).parseWorkflowContent(entry, file.getContent(), sourceFiles);
            }
        }
    }

    @Override
    protected void checkForDuplicatePath(Tool tool) {
        MutablePair<String, Entry> duplicate = getEntryDAO().findEntryByPath(tool.getToolPath(), false);
        if (duplicate != null) {
            throw new CustomWebApplicationException("A tool already exists with that path. Please change the tool name to something unique.", HttpStatus.SC_BAD_REQUEST);
        }
    }

    @Override
    protected Tag getVersion(Tool tool) {
        Tag tag = new Tag();
        tag.setCwlPath("/Dockstore.cwl");
        tag.setDockerfilePath("/Dockerfile");
        tag.setAutomated(false);
        tag.setWdlPath("/Dockstore.wdl");
        tag.setReferenceType(Version.ReferenceType.TAG);
        tag.setLastModified(new Date());
        return tag;
    }

    @Override
    @ApiOperation(nickname = "deleteHostedToolVersion", value = "Delete a revision of a hosted tool", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Delete a revision of a hosted tool", response = Tool.class)
    public Tool deleteHostedVersion(User user, Long entryId, String version) {
        Tool tool = super.deleteHostedVersion(user, entryId, version);
        elasticManager.handleIndexUpdate(tool, ElasticMode.UPDATE);
        return tool;
    }

    @Override
    protected Tag versionValidation(Tag version, Tool entry) {
        Set<SourceFile> sourceFiles = version.getSourceFiles();

        ImmutablePair validDockerfile = validateDockerfile(sourceFiles);
        VersionValidation dockerfileValidation = new VersionValidation(SourceFile.FileType.DOCKERFILE, validDockerfile);
        version.addOrUpdateVersionValidation(dockerfileValidation);

        ImmutablePair validCWLDescriptorSet = LanguageHandlerFactory.getInterface(SourceFile.FileType.DOCKSTORE_CWL).validateToolSet(sourceFiles, "/Dockstore.cwl");
        VersionValidation cwlValidation = new VersionValidation(SourceFile.FileType.DOCKSTORE_CWL, validCWLDescriptorSet);
        version.addOrUpdateVersionValidation(cwlValidation);

        ImmutablePair validCWLTestParameterSet = LanguageHandlerFactory.getInterface(SourceFile.FileType.DOCKSTORE_CWL).validateTestParameterSet(sourceFiles);
        VersionValidation cwlTestParameterValidation = new VersionValidation(SourceFile.FileType.CWL_TEST_JSON, validCWLTestParameterSet);
        version.addOrUpdateVersionValidation(cwlTestParameterValidation);

        ImmutablePair validWDLDescriptorSet = LanguageHandlerFactory.getInterface(SourceFile.FileType.DOCKSTORE_WDL).validateToolSet(sourceFiles, "/Dockstore.wdl");
        VersionValidation wdlValidation = new VersionValidation(SourceFile.FileType.DOCKSTORE_WDL, validWDLDescriptorSet);
        version.addOrUpdateVersionValidation(wdlValidation);

        ImmutablePair validWDLTestParameterSet = LanguageHandlerFactory.getInterface(SourceFile.FileType.DOCKSTORE_WDL).validateTestParameterSet(sourceFiles);
        VersionValidation wdlTestParameterValidation = new VersionValidation(SourceFile.FileType.WDL_TEST_JSON, validWDLTestParameterSet);
        version.addOrUpdateVersionValidation(wdlTestParameterValidation);

        return version;
    }

    /**
     * Validates dockerfile (currently just ensurse that it exists)
     * @param sourceFiles List of sourcefiles for a version
     * @return Pair including if dockerfile is valid, along with error message if it is not
     */
    protected ImmutablePair validateDockerfile(Set<SourceFile> sourceFiles) {
        boolean hasDockerfile = sourceFiles.stream().anyMatch(sf -> Objects.equals(sf.getType(), SourceFile.FileType.DOCKERFILE));
        String validationMessage = null;
        if (!hasDockerfile) {
            validationMessage = "Missing Dockerfile.";
        }
        return new ImmutablePair<>(hasDockerfile, validationMessage);
    }

    /**
     * A tag is valid if it has a valid Dockerfile, at least one valid descriptor set, and a matching set of valid test parameter files.
     * @param tag Tag to validate
     * @return Updated tag
     */
    @Override
    protected boolean isValidVersion(Tag tag) {
        SortedSet<VersionValidation> versionValidations = tag.getValidations();
        boolean validDockerfile = isVersionTypeValidated(versionValidations, SourceFile.FileType.DOCKERFILE);
        boolean validCwl = isVersionTypeValidated(versionValidations, SourceFile.FileType.DOCKSTORE_CWL);
        boolean validWdl = isVersionTypeValidated(versionValidations, SourceFile.FileType.DOCKSTORE_WDL);
        boolean validCwlTestParameters = isVersionTypeValidated(versionValidations, SourceFile.FileType.CWL_TEST_JSON);
        boolean validWdlTestParameters = isVersionTypeValidated(versionValidations, SourceFile.FileType.WDL_TEST_JSON);

        boolean hasCwl = tag.getSourceFiles().stream().anyMatch(file -> file.getType() == SourceFile.FileType.DOCKSTORE_CWL);
        boolean hasWdl = tag.getSourceFiles().stream().anyMatch(file -> file.getType() == SourceFile.FileType.DOCKSTORE_WDL);

        return validDockerfile && ((hasCwl && validCwl && validCwlTestParameters) || (hasWdl && validWdl && validWdlTestParameters));
    }

    /**
     * A helper function which finds the first sourcefile of a given type and returns whether or not it is valid
     * @param versionValidations Set of version validations
     * @param fileType FileType to look for
     * @return True if sourcefile exists and is valid, false otherwise
     */
    protected boolean isVersionTypeValidated(SortedSet<VersionValidation> versionValidations, SourceFile.FileType fileType) {
        Optional<VersionValidation> foundFile = versionValidations
                .stream()
                .filter(versionValidation -> Objects.equals(versionValidation.getType(), fileType))
                .findFirst();

        return foundFile.isPresent() && foundFile.get().isValid();
    }
}
