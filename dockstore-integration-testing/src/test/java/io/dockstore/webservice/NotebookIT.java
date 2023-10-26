/*
 *    Copyright 2023 OICR and UCSC
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

package io.dockstore.webservice;

import static io.dockstore.common.DescriptorLanguage.FileType.DOCKSTORE_NOTEBOOK_DEVCONTAINER;
import static io.dockstore.webservice.Constants.DOCKSTORE_YML_PATH;
import static io.dockstore.webservice.Constants.LAMBDA_FAILURE;
import static io.dockstore.webservice.helpers.GitHubAppHelper.handleGitHubRelease;
import static io.dockstore.webservice.resources.ResourceConstants.PAGINATION_LIMIT;
import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.SourceControl;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.CategoriesApi;
import io.dockstore.openapi.client.api.EntriesApi;
import io.dockstore.openapi.client.api.EventsApi;
import io.dockstore.openapi.client.api.MetadataApi;
import io.dockstore.openapi.client.api.OrganizationsApi;
import io.dockstore.openapi.client.api.UsersApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Author;
import io.dockstore.openapi.client.model.Category;
import io.dockstore.openapi.client.model.Collection;
import io.dockstore.openapi.client.model.CollectionOrganization;
import io.dockstore.openapi.client.model.EntryType;
import io.dockstore.openapi.client.model.EntryTypeMetadata;
import io.dockstore.openapi.client.model.Event;
import io.dockstore.openapi.client.model.Organization;
import io.dockstore.openapi.client.model.PublishRequest;
import io.dockstore.openapi.client.model.SourceFile;
import io.dockstore.openapi.client.model.StarRequest;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.jdbi.NotebookDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.resources.EventSearchType;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.http.HttpStatus;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
class NotebookIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    private final String simpleRepo = "dockstore-testing/simple-notebook";
    private final String simpleRepoPath = SourceControl.GITHUB + "/" + simpleRepo;

    private NotebookDAO notebookDAO;
    private WorkflowDAO workflowDAO;
    private UserDAO userDAO;
    private Session session;

    @BeforeEach
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();

        this.notebookDAO = new NotebookDAO(sessionFactory);
        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);

        // non-confidential test database sequences seem messed up and need to be iterated past, but other tests may depend on ids
        testingPostgres.runUpdateStatement("alter sequence enduser_id_seq increment by 50 restart with 100");
        testingPostgres.runUpdateStatement("alter sequence token_id_seq increment by 50 restart with 100");

        // used to allow us to use notebookDAO outside of the web service
        this.session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    @Test
    void testDAOs() {
        CommonTestUtilities.dropAndCreateWithTestData(SUPPORT, false);
        CreateContent createContent = new CreateContent().invoke();
        long notebookID = createContent.getNotebookID();

        // might not be right if our test database is larger than PAGINATION_LIMIT
        final List<io.dockstore.webservice.core.Workflow> allPublished = workflowDAO.findAllPublished(0, Integer.valueOf(PAGINATION_LIMIT), null, null, null);
        assertTrue(allPublished.stream().anyMatch(workflow -> workflow.getId() == notebookID && workflow instanceof io.dockstore.webservice.core.Notebook));

        final io.dockstore.webservice.core.Notebook byID = notebookDAO.findById(notebookID);
        assertNotNull(byID);
        assertEquals(byID.getId(), notebookID);

        assertEquals(1, notebookDAO.findAllPublishedPaths().size());
        assertEquals(1, notebookDAO.findAllPublishedPathsOrderByDbupdatedate().size());
        assertEquals(1, workflowDAO.findAllPublished(1, Integer.valueOf(PAGINATION_LIMIT), null, null, null).size());
        session.close();
    }

    @Test
    void testRegisterSimpleNotebook() {
        ApiClient apiClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        handleGitHubRelease(workflowsApi, simpleRepo, "refs/tags/simple-v1", USER_2_USERNAME);

        String path = SourceControl.GITHUB + "/" + simpleRepo;
        Workflow notebook = workflowsApi.getWorkflowByPath(path, WorkflowSubClass.NOTEBOOK, "versions");
        assertEquals(path, notebook.getFullWorkflowPath());
        assertEquals(EntryType.NOTEBOOK, notebook.getEntryType());
        assertEquals(Workflow.DescriptorTypeEnum.JUPYTER, notebook.getDescriptorType());
        assertEquals(Workflow.DescriptorTypeSubclassEnum.PYTHON, notebook.getDescriptorTypeSubclass());
        assertEquals(1, notebook.getWorkflowVersions().size());
        WorkflowVersion version = notebook.getWorkflowVersions().get(0);
        assertEquals("/notebook.ipynb", version.getWorkflowPath());
        assertTrue(version.isValid());
        assertEquals(Set.of("Author One", "Author Two"), version.getAuthors().stream().map(Author::getName).collect(Collectors.toSet()));
        List<SourceFile> sourceFiles = workflowsApi.getWorkflowVersionsSourcefiles(notebook.getId(), version.getId(), null);
        assertEquals(Set.of("/notebook.ipynb", "/.dockstore.yml"), sourceFiles.stream().map(SourceFile::getAbsolutePath).collect(Collectors.toSet()));
        assertEquals(List.of("4.0"), version.getVersionMetadata().getDescriptorTypeVersions());
    }

    @Test
    void testRegisterLessSimpleNotebook() {
        ApiClient apiClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        handleGitHubRelease(workflowsApi, simpleRepo, "refs/tags/less-simple-v2", USER_2_USERNAME);
        // Check only the values that should differ from testRegisterSimpleNotebook()
        String path = simpleRepoPath + "/simple";
        Workflow notebook = workflowsApi.getWorkflowByPath(path, WorkflowSubClass.NOTEBOOK, "versions");
        assertEquals(path, notebook.getFullWorkflowPath());
        WorkflowVersion version = notebook.getWorkflowVersions().get(0);
        List<SourceFile> sourceFiles = workflowsApi.getWorkflowVersionsSourcefiles(notebook.getId(), version.getId(), null);
        assertEquals(Set.of("/notebook.ipynb", "/.dockstore.yml", "/info.txt", "/data/a.txt", "/data/b.txt", "/requirements.txt", "/.binder/runtime.txt"), sourceFiles.stream().map(SourceFile::getAbsolutePath).collect(Collectors.toSet()));
    }

    @Test
    void testRegisterOldNotebook() {
        ApiClient apiClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        handleGitHubRelease(workflowsApi, simpleRepo, "refs/tags/old-v1", USER_2_USERNAME);
        // Check a few fields to make sure we registered successfully
        String path = simpleRepoPath + "/old";
        Workflow notebook = workflowsApi.getWorkflowByPath(path, WorkflowSubClass.NOTEBOOK, "versions");
        assertEquals(EntryType.NOTEBOOK, notebook.getEntryType());
        assertEquals(Workflow.DescriptorTypeEnum.JUPYTER, notebook.getDescriptorType());
        assertEquals(Workflow.DescriptorTypeSubclassEnum.PYTHON, notebook.getDescriptorTypeSubclass());
        assertEquals(1, notebook.getWorkflowVersions().size());
        WorkflowVersion version = notebook.getWorkflowVersions().get(0);
        assertTrue(version.isValid());
        assertEquals(List.of("3.0"), version.getVersionMetadata().getDescriptorTypeVersions());
    }

    @Test
    void testRegisterCorruptNotebook() {
        ApiClient apiClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        handleGitHubRelease(workflowsApi, simpleRepo, "refs/tags/corrupt-ipynb-v1", USER_2_USERNAME);
        // The update should be "successful" but there should be a negative validation on the notebook file.
        Workflow notebook = workflowsApi.getWorkflowByPath(simpleRepoPath, WorkflowSubClass.NOTEBOOK, "versions");
        assertEquals(1, notebook.getWorkflowVersions().size());
        assertFalse(notebook.getWorkflowVersions().get(0).isValid());
    }

    @Test
    void testRegisterRootDevcontainerNotebook() {
        List<SourceFile> files = registerSimpleRepoAndGetSourceFiles("simple", "refs/tags/root-devcontainer-v1");
        assertEquals(Set.of("/.devcontainer.json", "/notebook.ipynb", ".dockstore.yml"), getAbsolutePaths(files));
        assertEquals(1, countFileType(files, DOCKSTORE_NOTEBOOK_DEVCONTAINER));
    }

    @Test
    void testRegisterDotdirDevcontainerNotebook() {
        List<SourceFile> files = registerSimpleRepoAndGetSourceFiles("simple", "refs/tags/dotdir-devcontainer-v1");
        assertEquals(Set.of("/.devcontainer/devcontainer.json", "/notebook.ipynb", ".dockstore.yml"), getAbsolutePaths(files));
        assertEquals(1, countFileType(files, DOCKSTORE_NOTEBOOK_DEVCONTAINER));
    }

    @Test
    void testRegisterDotdirFolderDevcontainersNotebook() {
        List<SourceFile> files = registerSimpleRepoAndGetSourceFiles("simple", "refs/tags/dotdir-folder-devcontainers-v1");
        assertEquals(Set.of("/.devcontainer/a/devcontainer.json", "/.devcontainer/b/devcontainer.json", "/notebook.ipynb", ".dockstore.yml"),
            getAbsolutePaths(files));
        assertEquals(2, countFileType(files, DOCKSTORE_NOTEBOOK_DEVCONTAINER));
    }

    private List<SourceFile> registerSimpleRepoAndGetSourceFiles(String name, String ref) {
        ApiClient apiClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        handleGitHubRelease(workflowsApi, simpleRepo, ref, USER_2_USERNAME);
        String path = simpleRepoPath + "/" + name;
        Workflow notebook = workflowsApi.getWorkflowByPath(path, WorkflowSubClass.NOTEBOOK, "versions");
        WorkflowVersion version = notebook.getWorkflowVersions().get(0);
        return workflowsApi.getWorkflowVersionsSourcefiles(notebook.getId(), version.getId(), null);
    }

    private Set<String> getAbsolutePaths(List<SourceFile> sourceFiles) {
        return sourceFiles.stream().map(SourceFile::getAbsolutePath).collect(Collectors.toSet());
    }

    private long countFileType(List<SourceFile> sourceFiles, DescriptorLanguage.FileType type) {
        return sourceFiles.stream().filter(file -> file.getType().equals(type)).count();
    }

    @Test
    void testUserNotebooks() {
        ApiClient apiClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        handleGitHubRelease(workflowsApi, simpleRepo, "refs/tags/simple-v1", USER_2_USERNAME);
        Workflow notebook = workflowsApi.getWorkflowByPath(SourceControl.GITHUB + "/" + simpleRepo, WorkflowSubClass.NOTEBOOK, "versions");
        assertNotNull(notebook);

        UsersApi usersApi = new UsersApi(apiClient);
        final long userId = testingPostgres.runSelectStatement("select userid from user_entry where entryid = '" + notebook.getId() + "'", long.class);

        List<Workflow> notebooks = usersApi.userNotebooks(userId);
        assertEquals(1, notebooks.size());
        assertEquals(notebook.getId(), notebooks.get(0).getId());
        List<Workflow> workflows = usersApi.userWorkflows(userId);
        assertEquals(0, workflows.size());
    }

    @Test
    void testPublishInDockstoreYml() {
        ApiClient apiClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        assertEquals(0, workflowsApi.allPublishedWorkflows(null, null, null, null, null, null, WorkflowSubClass.NOTEBOOK).size());
        handleGitHubRelease(workflowsApi, simpleRepo, "refs/tags/simple-published-v1", USER_2_USERNAME);
        assertEquals(1, workflowsApi.allPublishedWorkflows(null, null, null, null, null, null, WorkflowSubClass.NOTEBOOK).size());
    }

    @Test
    void testWithImage() {
        ApiClient apiClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        handleGitHubRelease(workflowsApi, simpleRepo, "refs/tags/with-kernel-v1", USER_2_USERNAME);
        Workflow notebook = workflowsApi.getWorkflowByPath(simpleRepoPath, WorkflowSubClass.NOTEBOOK, "versions");
        assertEquals(1, notebook.getWorkflowVersions().size());
        assertEquals("quay.io/seqware/seqware_full/1.1", notebook.getWorkflowVersions().get(0).getKernelImagePath());
    }

    @Test
    void testSnapshot() {
        ApiClient apiClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        handleGitHubRelease(workflowsApi, simpleRepo, "refs/tags/with-tagged-kernel-v1", USER_2_USERNAME);
        Workflow notebook = workflowsApi.getWorkflowByPath(simpleRepoPath, WorkflowSubClass.NOTEBOOK, "versions");
        WorkflowVersion version = notebook.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).findFirst().get();
        // Publish the notebook
        PublishRequest publishRequest = new PublishRequest();
        publishRequest.setPublish(true);
        workflowsApi.publish1(notebook.getId(), publishRequest);
        assertFalse(version.isFrozen());
        assertEquals(0, testingPostgres.runSelectStatement("select count(*) from entry_version_image where versionid = " + version.getId(), long.class));
        // Snapshot the notebook
        version.setFrozen(true);
        workflowsApi.updateWorkflowVersion(notebook.getId(), List.of(version));
        // Confirm that the version is frozen and the Image is stored
        notebook = workflowsApi.getWorkflow(notebook.getId(), null);
        version = notebook.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).findFirst().get();
        assertTrue(version.isFrozen());
        assertEquals(1, testingPostgres.runSelectStatement("select count(*) from entry_version_image where versionid = " + version.getId(), long.class));
    }

    @Test
    void testMetadata() {
        ApiClient apiClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        handleGitHubRelease(workflowsApi, simpleRepo, "refs/tags/simple-v1", USER_2_USERNAME);

        String path = SourceControl.GITHUB + "/" + simpleRepo;
        Workflow notebook = workflowsApi.getWorkflowByPath(path, WorkflowSubClass.NOTEBOOK, "versions");

        assertEquals(EntryType.NOTEBOOK, notebook.getEntryType());
        EntryTypeMetadata metadata = notebook.getEntryTypeMetadata();
        assertEquals(EntryType.NOTEBOOK, metadata.getType());
        assertEquals("notebook", metadata.getTerm());
        assertEquals("notebooks", metadata.getTermPlural());
        assertEquals("notebooks", metadata.getSitePath());
        assertEquals(true, metadata.isTrsSupported());
        assertEquals("#notebook/", metadata.getTrsPrefix());
    }

    @Test
    void testEvents() {
        ApiClient apiClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        handleGitHubRelease(workflowsApi, simpleRepo, "refs/tags/simple-published-v1", USER_2_USERNAME);

        Workflow notebook = workflowsApi.getWorkflowByPath(simpleRepoPath, WorkflowSubClass.NOTEBOOK, "versions");

        EventsApi eventsApi = new EventsApi(apiClient);
        List<Event> events = eventsApi.getEvents(EventSearchType.PROFILE.toString(), null, null);
        assertTrue(events.stream().anyMatch(e -> e.getNotebook() != null && Objects.equals(e.getNotebook().getId(), notebook.getId())));
    }

    @Test
    void testStarringNotebook() {
        ApiClient openApiClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(openApiClient);
        UsersApi usersApi = new UsersApi(openApiClient);

        handleGitHubRelease(workflowsApi, simpleRepo, "refs/tags/less-simple-v2", USER_2_USERNAME);
        Long notebookID = workflowsApi.getWorkflowByPath(simpleRepoPath + "/simple", WorkflowSubClass.NOTEBOOK, "versions").getId();

        //star notebook
        workflowsApi.starEntry1(notebookID, new StarRequest().star(true));
        Workflow notebook = workflowsApi.getWorkflow(notebookID, "");
        assertEquals(1, notebook.getStarredUsers().size());
        assertEquals(1, usersApi.getStarredNotebooks().size());

        //unstar notebook
        workflowsApi.starEntry1(notebookID, new StarRequest().star(false));
        notebook = workflowsApi.getWorkflow(notebookID, "");
        assertEquals(0, notebook.getStarredUsers().size());
        assertEquals(0, usersApi.getStarredNotebooks().size());
    }

    @Test
    void testNotebookRSSFeedAndSitemap() {
        ApiClient openApiClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);

        // There should be no notebooks
        assertEquals(0, notebookDAO.findAllPublishedPaths().size());
        assertEquals(0, notebookDAO.findAllPublishedPathsOrderByDbupdatedate().size());

        new CreateContent().invoke();

        // There should be 1 notebook
        assertEquals(1, notebookDAO.findAllPublishedPaths().size());
        assertEquals(1, notebookDAO.findAllPublishedPathsOrderByDbupdatedate().size());

        final MetadataApi metadataApi = new MetadataApi(openApiClient);
        String rssFeed = metadataApi.rssFeed();
        assertTrue(rssFeed.contains("http://localhost/notebooks/github.com/hydra/hydra_repo"), "RSS feed should contain 1 notebook");

        String sitemap = metadataApi.sitemap();
        assertTrue(sitemap.contains("http://localhost/notebooks/github.com/hydra/hydra_repo"), "Sitemap with testing data should have 1 notebook");
    }

    @Test
    void testNotebookToCollectionCategory() {
        final ApiClient webClientAdminUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        final EntriesApi entriesApi = new EntriesApi(webClientAdminUser);
        final OrganizationsApi organizationsApi = new OrganizationsApi(webClientAdminUser);
        final CategoriesApi categoriesApi = new CategoriesApi(webClientAdminUser);

        //create organizations
        createTestOrganization("nonCategorizer", false);
        createTestOrganization("categorizer", true);

        //approve organizations
        Organization nonCategorizerOrg = organizationsApi.getOrganizationByName("nonCategorizer");
        Organization categorizerOrg = organizationsApi.getOrganizationByName("categorizer");
        nonCategorizerOrg = organizationsApi.approveOrganization(nonCategorizerOrg.getId());
        categorizerOrg = organizationsApi.approveOrganization(categorizerOrg.getId());

        //create collection and category
        Collection collection = new Collection();
        collection.setName("Collection");
        collection.setDisplayName("Collection");
        collection.setDescription("A collection of notebooks");
        collection = organizationsApi.createCollection(collection, nonCategorizerOrg.getId());

        Collection category = new Collection();
        category.setName("Category");
        category.setDisplayName("Category");
        category.setDescription("A category of notebooks");
        category = organizationsApi.createCollection(category, categorizerOrg.getId());

        //create notebook
        CreateContent createContent = new CreateContent().invoke();
        long notebookID = createContent.getNotebookID();

        //add notebook to collection
        Set<String> expectedCollectionNames = new HashSet<>();
        expectedCollectionNames.add("Collection");
        organizationsApi.addEntryToCollection(nonCategorizerOrg.getId(), collection.getId(), notebookID, null);
        List<CollectionOrganization> entryCollection = entriesApi.entryCollections(notebookID);
        assertEquals(expectedCollectionNames,  entryCollection.stream().map(CollectionOrganization::getCollectionName).collect(Collectors.toSet()));
        assertEquals(1, entryCollection.stream().map(CollectionOrganization::getCollectionName).collect(Collectors.toSet()).size());
        assertEquals(0, organizationsApi.getCollectionByName(nonCategorizerOrg.getName(), collection.getName()).getWorkflowsLength());
        assertEquals(1, organizationsApi.getCollectionByName(nonCategorizerOrg.getName(), collection.getName()).getNotebooksLength());

        //remove notebook from collection
        organizationsApi.deleteEntryFromCollection(nonCategorizerOrg.getId(), collection.getId(), notebookID, null);
        expectedCollectionNames.remove("Collection");
        entryCollection = entriesApi.entryCollections(notebookID);
        assertEquals(expectedCollectionNames,  entryCollection.stream().map(CollectionOrganization::getCollectionName).collect(Collectors.toSet()));
        assertEquals(0, entryCollection.stream().map(CollectionOrganization::getCollectionName).collect(Collectors.toSet()).size());
        assertEquals(0, organizationsApi.getCollectionByName(nonCategorizerOrg.getName(), collection.getName()).getWorkflowsLength());
        assertEquals(0, organizationsApi.getCollectionByName(nonCategorizerOrg.getName(), collection.getName()).getNotebooksLength());

        //add notebook to category
        Set<String> expectedCategoryNames = new HashSet<>();
        expectedCategoryNames.add("Category");
        organizationsApi.addEntryToCollection(categorizerOrg.getId(), category.getId(), notebookID, null);
        List<Category> entryCategory = entriesApi.entryCategories(notebookID);
        assertEquals(expectedCategoryNames,  entryCategory.stream().map(Category::getName).collect(Collectors.toSet()));
        assertEquals(1,  entryCategory.stream().map(Category::getName).collect(Collectors.toSet()).size());
        assertEquals(0, categoriesApi.getCategoryById(category.getId()).getWorkflowsLength());
        assertEquals(1, categoriesApi.getCategoryById(category.getId()).getNotebooksLength());


        //remove notebook from category
        organizationsApi.deleteEntryFromCollection(categorizerOrg.getId(), category.getId(), notebookID, null);
        expectedCategoryNames.remove("Category");
        entryCategory = entriesApi.entryCategories(notebookID);
        assertEquals(expectedCategoryNames,  entryCategory.stream().map(Category::getName).collect(Collectors.toSet()));
        assertEquals(0,  entryCategory.stream().map(Category::getName).collect(Collectors.toSet()).size());
        assertEquals(0, categoriesApi.getCategoryById(category.getId()).getWorkflowsLength());
        assertEquals(0, categoriesApi.getCategoryById(category.getId()).getNotebooksLength());
    }

    private void shouldThrow(Executable executable, String whyMessage, int expectedCode) {
        ApiException e = assertThrows(ApiException.class, executable, "Should have thrown an ApiException because: " + whyMessage);
        assertEquals(expectedCode, e.getCode());
    }

    @Test
    void testDeletabilityAndDeletion() {
        ApiClient apiClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        EntriesApi entriesApi = new EntriesApi(apiClient);

        // Create a new notebook
        handleGitHubRelease(workflowsApi, simpleRepo, "refs/tags/simple-v1", USER_2_USERNAME);
        Workflow notebookA = workflowsApi.getWorkflowByPath(simpleRepoPath, WorkflowSubClass.NOTEBOOK, "versions");
        long idA = notebookA.getId();

        // Make sure the initial state is as expected
        Workflow notebook = workflowsApi.getWorkflow(idA, "");
        assertFalse(notebook.isIsPublished());
        assertTrue(notebook.isDeletable());

        // Try to delete the notebook as a user without write access
        ApiClient otherClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        EntriesApi otherEntriesApi = new EntriesApi(otherClient);
        shouldThrow(() -> otherEntriesApi.deleteEntry(idA), "the user didn't have write access", HttpStatus.SC_FORBIDDEN);

        // Delete the notebook and confirm that it no longer exists
        entriesApi.deleteEntry(idA);
        shouldThrow(() -> workflowsApi.getWorkflow(idA, ""), "the notebook has been deleted", HttpStatus.SC_NOT_FOUND);

        // Attempt to again delete the now-nonexistent notebook
        shouldThrow(() -> entriesApi.deleteEntry(idA), "the notebook has been deleted", HttpStatus.SC_NOT_FOUND);

        // Create the notebook again
        handleGitHubRelease(workflowsApi, simpleRepo, "refs/tags/simple-v1", USER_2_USERNAME);
        Workflow notebookB = workflowsApi.getWorkflowByPath(simpleRepoPath, WorkflowSubClass.NOTEBOOK, "versions");
        long idB = notebookB.getId();

        // Unpublish the notebook
        // Nothing should change, since the notebook is currently unpublished
        workflowsApi.publish1(idB, CommonTestUtilities.createOpenAPIPublishRequest(false));
        notebook = workflowsApi.getWorkflow(idB, "");
        assertFalse(notebook.isIsPublished());
        assertTrue(notebook.isDeletable());

        // Publish the notebook
        workflowsApi.publish1(idB, CommonTestUtilities.createOpenAPIPublishRequest(true));
        notebook = workflowsApi.getWorkflow(idB, "");
        assertTrue(notebook.isIsPublished());
        assertFalse(notebook.isDeletable());

        // Attempt to delete, which should fail because the notebook was previously published
        shouldThrow(() -> entriesApi.deleteEntry(idB), "the notebook was previously published", HttpStatus.SC_FORBIDDEN);
        notebook = workflowsApi.getWorkflow(idB, "");
        assertTrue(notebook.isIsPublished());
        assertFalse(notebook.isDeletable());

        // Unpublish
        workflowsApi.publish1(idB, CommonTestUtilities.createOpenAPIPublishRequest(false));
        notebook = workflowsApi.getWorkflow(idB, "");
        assertFalse(notebook.isIsPublished());
        assertFalse(notebook.isDeletable());

        // Attempt to delete, which should fail because the notebook was previously published
        shouldThrow(() -> entriesApi.deleteEntry(idB), "the notebook was previously published", HttpStatus.SC_FORBIDDEN);
        notebook = workflowsApi.getWorkflow(idB, "");
        assertFalse(notebook.isIsPublished());
        assertFalse(notebook.isDeletable());
    }

    private long countEvents(long notebookId) {
        return testingPostgres.runSelectStatement(String.format("select count(*) from event where notebookid = %s", notebookId), long.class);
    }

    private long countEvents(long notebookId, String type) {
        return testingPostgres.runSelectStatement(String.format("select count(*) from event where notebookid = %s and type = '%s'", notebookId, type), long.class);
    }


    @Test
    void testEventDeletion() {
        ApiClient apiClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        UsersApi usersApi = new UsersApi(apiClient);
        EventsApi eventsApi = new EventsApi(apiClient);

        handleGitHubRelease(workflowsApi, simpleRepo, "refs/tags/simple-v1", USER_2_USERNAME);
        Workflow notebook = workflowsApi.getWorkflowByPath(simpleRepoPath, WorkflowSubClass.NOTEBOOK, "versions");
        long id = notebook.getId();

        // Count the events referencing the notebook
        long unpublishedCount = countEvents(id);

        // Publish notebook, which will add a PUBLISH_EVENT referencing the notebook
        workflowsApi.publish1(id, CommonTestUtilities.createOpenAPIPublishRequest(true));

        // Count the events referencing the notebook, should be greater than before
        assertTrue(countEvents(id) > unpublishedCount);

        // Star the notebook, then check that the getEvents endpoint returns the correct number of events
        // getEvents(STARRED_ENTRIES, ...) uses eventDAO.findEventsByEntryIDs internally, which is what we're trying to test
        workflowsApi.starEntry1(id, new StarRequest().star(true));
        assertEquals(countEvents(id), eventsApi.getEvents(EventSearchType.STARRED_ENTRIES.toString(), null, null).size());

        // Delete the user, which in the process will delete the Events referencing the notebook
        workflowsApi.publish1(id, CommonTestUtilities.createOpenAPIPublishRequest(false));
        usersApi.selfDestruct(1L);

        // Count the events referencing the notebook, there should be none, they should have all been deleted
        assertEquals(0, countEvents(id));
    }

    @Test
    void testArchive() {
        ApiClient apiClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        EntriesApi entriesApi = new EntriesApi(apiClient);
        String ref = "refs/tags/simple-v1";

        handleGitHubRelease(workflowsApi, simpleRepo, ref, USER_2_USERNAME);
        Workflow notebook = workflowsApi.getWorkflowByPath(simpleRepoPath, WorkflowSubClass.NOTEBOOK, "versions");
        long id = notebook.getId();

        // Archive and unarchive, verifying that the property changes correctly and the appropriate events are created
        assertFalse(workflowsApi.getWorkflow(id, null).isArchived());
        assertEquals(0, countEvents(id, "ARCHIVE_ENTRY"));
        assertEquals(0, countEvents(id, "UNARCHIVE_ENTRY"));
        entriesApi.archiveEntry(id);
        assertTrue(workflowsApi.getWorkflow(id, null).isArchived());
        assertEquals(1, countEvents(id, "ARCHIVE_ENTRY"));
        assertEquals(0, countEvents(id, "UNARCHIVE_ENTRY"));
        entriesApi.unarchiveEntry(id);
        assertFalse(workflowsApi.getWorkflow(id, null).isArchived());
        assertEquals(1, countEvents(id, "ARCHIVE_ENTRY"));
        assertEquals(1, countEvents(id, "UNARCHIVE_ENTRY"));

        // Attempting to archive an archived entry should not produce an event
        // Similarly, unarchiving an unarchive entry...
        entriesApi.archiveEntry(id);
        entriesApi.archiveEntry(id);
        assertEquals(2, countEvents(id, "ARCHIVE_ENTRY"));
        entriesApi.unarchiveEntry(id);
        entriesApi.unarchiveEntry(id);
        assertEquals(2, countEvents(id, "UNARCHIVE_ENTRY"));

        // Make sure an archived entry cannot be pushed to
        entriesApi.archiveEntry(id);
        shouldThrow(() -> handleGitHubRelease(workflowsApi, simpleRepo, ref, USER_2_USERNAME), "an archived entry should not be pushable", LAMBDA_FAILURE);

        // Make sure an archived entry cannot be published
        PublishRequest publishRequest = new PublishRequest();
        publishRequest.setPublish(true);
        assertFalse(workflowsApi.getWorkflow(id, null).isIsPublished());
        shouldThrow(() -> workflowsApi.publish1(id, publishRequest), "an archived entry should not be publishable", HttpStatus.SC_FORBIDDEN);
        assertFalse(workflowsApi.getWorkflow(id, null).isIsPublished());

        // Make sure an archived entry cannot be modified
        assertEquals(null, workflowsApi.getWorkflow(id, null).getDefaultVersion());
        shouldThrow(() -> workflowsApi.updateDefaultVersion1(id, "simple-v1"), "an archived entry should not be modifiable", HttpStatus.SC_FORBIDDEN);
        assertEquals(null, workflowsApi.getWorkflow(id, null).getDefaultVersion());

        // Make sure an unarchived entry can be pushed to
        entriesApi.unarchiveEntry(id);
        handleGitHubRelease(workflowsApi, simpleRepo, ref, USER_2_USERNAME);

        // Make sure an unarchived entry can be published
        assertFalse(workflowsApi.getWorkflow(id, null).isIsPublished());
        workflowsApi.publish1(id, publishRequest);
        assertTrue(workflowsApi.getWorkflow(id, null).isIsPublished());

        // Make sure an unarchived entry can be modified
        assertEquals(null, workflowsApi.getWorkflow(id, null).getDefaultVersion());
        workflowsApi.updateDefaultVersion1(id, "simple-v1");
        assertEquals("simple-v1", workflowsApi.getWorkflow(id, null).getDefaultVersion());

        // Non-owner admins should be able to archive/unarchive an entry
        ApiClient differentAdminApiClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        EntriesApi differentAdminEntriesApi = new EntriesApi(differentAdminApiClient);
        differentAdminEntriesApi.archiveEntry(id);
        differentAdminEntriesApi.unarchiveEntry(id);

        // Non-owner non-admins should not be able to archive/unarchive an entry
        ApiClient unprivilegedApiClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        EntriesApi unprivilegedEntriesApi = new EntriesApi(unprivilegedApiClient);
        shouldThrow(() -> unprivilegedEntriesApi.archiveEntry(id), "unprivileged users should not be able to archive", HttpStatus.SC_FORBIDDEN);
        shouldThrow(() -> unprivilegedEntriesApi.unarchiveEntry(id), "unprivileged users should not be able to unarchive", HttpStatus.SC_FORBIDDEN);
    }

    private Organization createTestOrganization(String name, boolean categorizer) {
        final ApiClient webClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        final OrganizationsApi organizationsApi = new OrganizationsApi(webClient);

        Organization organization = new Organization();
        organization.setName(name);
        organization.setDisplayName(name);
        organization.setLocation("testlocation");
        organization.setLink("https://www.google.com");
        organization.setEmail("test@email.com");
        organization.setDescription("test test test");
        organization.setTopic("This is a short topic");
        organization.setCategorizer(categorizer);
        return organizationsApi.createOrganization(organization);
    }

    private class CreateContent {
        private long notebookID;

        long getNotebookID() {
            return notebookID;
        }

        CreateContent invoke() {
            return invoke(false);
        }

        CreateContent invoke(boolean cleanup) {
            final Transaction transaction = session.beginTransaction();

            io.dockstore.webservice.core.Notebook testNotebook = new io.dockstore.webservice.core.Notebook();
            testNotebook.setDescription("test notebook");
            testNotebook.setIsPublished(true);
            testNotebook.setSourceControl(SourceControl.GITHUB);
            testNotebook.setDescriptorType(DescriptorLanguage.SERVICE);
            testNotebook.setMode(io.dockstore.webservice.core.WorkflowMode.DOCKSTORE_YML);
            testNotebook.setOrganization("hydra");
            testNotebook.setRepository("hydra_repo");
            testNotebook.setWorkflowName(null);
            testNotebook.setDefaultWorkflowPath(DOCKSTORE_YML_PATH);

            // add all users to all things for now
            for (io.dockstore.webservice.core.User user : userDAO.findAll()) {
                testNotebook.addUser(user);
            }

            notebookID = notebookDAO.create(testNotebook);

            assertTrue(notebookID != 0);

            session.flush();
            transaction.commit();
            if (cleanup) {
                session.close();
            }
            return this;
        }
    }
}
