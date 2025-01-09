/*
 *    Copyright 2017 OICR
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

import static io.dockstore.webservice.resources.proposedGA4GH.ToolsApiExtendedServiceFactory.getToolsExtendedApi;
import static jakarta.servlet.DispatcherType.REQUEST;
import static org.eclipse.jetty.servlets.CrossOriginFilter.ACCESS_CONTROL_ALLOW_METHODS_HEADER;
import static org.eclipse.jetty.servlets.CrossOriginFilter.ALLOWED_HEADERS_PARAM;
import static org.eclipse.jetty.servlets.CrossOriginFilter.ALLOWED_METHODS_PARAM;
import static org.eclipse.jetty.servlets.CrossOriginFilter.ALLOWED_ORIGINS_PARAM;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.datatype.hibernate5.jakarta.Hibernate5JakartaModule;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import io.dockstore.common.LanguagePluginManager;
import io.dockstore.language.CompleteLanguageInterface;
import io.dockstore.language.MinimalLanguageInterface;
import io.dockstore.language.RecommendedLanguageInterface;
import io.dockstore.webservice.core.AppTool;
import io.dockstore.webservice.core.Author;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Category;
import io.dockstore.webservice.core.Checksum;
import io.dockstore.webservice.core.CloudInstance;
import io.dockstore.webservice.core.Collection;
import io.dockstore.webservice.core.CollectionOrganization;
import io.dockstore.webservice.core.DeletedUsername;
import io.dockstore.webservice.core.Doi;
import io.dockstore.webservice.core.EntryVersion;
import io.dockstore.webservice.core.Event;
import io.dockstore.webservice.core.FileFormat;
import io.dockstore.webservice.core.Image;
import io.dockstore.webservice.core.Label;
import io.dockstore.webservice.core.LambdaEvent;
import io.dockstore.webservice.core.Notebook;
import io.dockstore.webservice.core.Notification;
import io.dockstore.webservice.core.OrcidAuthor;
import io.dockstore.webservice.core.Organization;
import io.dockstore.webservice.core.OrganizationUser;
import io.dockstore.webservice.core.ParsedInformation;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.SourceFileMetadata;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Validation;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.VersionMetadata;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.core.metrics.CostStatisticMetric;
import io.dockstore.webservice.core.metrics.CpuStatisticMetric;
import io.dockstore.webservice.core.metrics.ExecutionStatusCountMetric;
import io.dockstore.webservice.core.metrics.ExecutionTimeStatisticMetric;
import io.dockstore.webservice.core.metrics.MemoryStatisticMetric;
import io.dockstore.webservice.core.metrics.Metrics;
import io.dockstore.webservice.core.metrics.MetricsByStatus;
import io.dockstore.webservice.core.metrics.ValidationStatusCountMetric;
import io.dockstore.webservice.core.metrics.ValidatorInfo;
import io.dockstore.webservice.core.metrics.ValidatorVersionInfo;
import io.dockstore.webservice.doi.DOIGeneratorFactory;
import io.dockstore.webservice.filters.AdminPrivilegesFilter;
import io.dockstore.webservice.filters.AuthenticatedUserFilter;
import io.dockstore.webservice.filters.UsernameRenameRequiredFilter;
import io.dockstore.webservice.helpers.CacheConfigManager;
import io.dockstore.webservice.helpers.DiagnosticsHelper;
import io.dockstore.webservice.helpers.ElasticSearchHelper;
import io.dockstore.webservice.helpers.EmailPropertyFilter;
import io.dockstore.webservice.helpers.GoogleHelper;
import io.dockstore.webservice.helpers.MetadataResourceHelper;
import io.dockstore.webservice.helpers.ORCIDHelper;
import io.dockstore.webservice.helpers.PersistenceExceptionMapper;
import io.dockstore.webservice.helpers.PublicStateManager;
import io.dockstore.webservice.helpers.PublicUserFilter;
import io.dockstore.webservice.helpers.ZenodoHelper;
import io.dockstore.webservice.helpers.doi.DoiHelper;
import io.dockstore.webservice.helpers.statelisteners.PopulateEntryListener;
import io.dockstore.webservice.jdbi.AppToolDAO;
import io.dockstore.webservice.jdbi.BioWorkflowDAO;
import io.dockstore.webservice.jdbi.DeletedUsernameDAO;
import io.dockstore.webservice.jdbi.EventDAO;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.NotebookDAO;
import io.dockstore.webservice.jdbi.ServiceDAO;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.VersionDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.jdbi.WorkflowVersionDAO;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import io.dockstore.webservice.permissions.PermissionsFactory;
import io.dockstore.webservice.permissions.PermissionsInterface;
import io.dockstore.webservice.resources.AliasResource;
import io.dockstore.webservice.resources.CategoryResource;
import io.dockstore.webservice.resources.CloudInstanceResource;
import io.dockstore.webservice.resources.CollectionResource;
import io.dockstore.webservice.resources.ConnectionPoolHealthCheck;
import io.dockstore.webservice.resources.DockerRepoResource;
import io.dockstore.webservice.resources.DockerRepoTagResource;
import io.dockstore.webservice.resources.ElasticsearchConsistencyHealthCheck;
import io.dockstore.webservice.resources.EntryResource;
import io.dockstore.webservice.resources.EventResource;
import io.dockstore.webservice.resources.HostedToolResource;
import io.dockstore.webservice.resources.HostedWorkflowResource;
import io.dockstore.webservice.resources.LambdaEventResource;
import io.dockstore.webservice.resources.LiquibaseLockHealthCheck;
import io.dockstore.webservice.resources.MetadataResource;
import io.dockstore.webservice.resources.NotificationResource;
import io.dockstore.webservice.resources.OrganizationResource;
import io.dockstore.webservice.resources.ServiceResource;
import io.dockstore.webservice.resources.TokenResource;
import io.dockstore.webservice.resources.ToolTesterResource;
import io.dockstore.webservice.resources.UserResource;
import io.dockstore.webservice.resources.UserResourceDockerRegistries;
import io.dockstore.webservice.resources.WorkflowResource;
import io.dockstore.webservice.resources.proposedGA4GH.ToolsApiExtendedServiceImpl;
import io.dockstore.webservice.resources.proposedGA4GH.ToolsExtendedApi;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.auth.CachingAuthenticator;
import io.dropwizard.auth.oauth.OAuthCredentialAuthFilter;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.hibernate.HibernateBundle;
import io.dropwizard.hibernate.UnitOfWorkAwareProxyFactory;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;
import io.dropwizard.migrations.MigrationsBundle;
import io.openapi.api.impl.ToolsApiServiceImpl;
import io.swagger.api.MetadataApi;
import io.swagger.api.MetadataApiV1;
import io.swagger.api.ToolClassesApi;
import io.swagger.api.ToolClassesApiV1;
import io.swagger.api.ToolsApi;
import io.swagger.api.ToolsApiV1;
import io.swagger.v3.jaxrs2.SwaggerSerializers;
import io.swagger.v3.jaxrs2.integration.resources.BaseOpenApiResource;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.http.HttpStatus;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.CommonProperties;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.kohsuke.github.extras.okhttp3.ObsoleteUrlFactory;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dyuen
 */
public class DockstoreWebserviceApplication extends Application<DockstoreWebserviceConfiguration> {
    public static final String GA4GH_API_PATH_V2_BETA = "/api/ga4gh/v2";
    public static final String GA4GH_API_PATH_V2_FINAL = "/ga4gh/trs/v2";
    public static final String GA4GH_API_PATH_V1 = "/api/ga4gh/v1";
    private static final List<String> CORS_ENDPOINTS = Arrays.asList(
            GA4GH_API_PATH_V2_BETA + "/metadata/*",
            GA4GH_API_PATH_V2_BETA + "/tools/*",
            GA4GH_API_PATH_V2_BETA + "/toolClasses/*",
            GA4GH_API_PATH_V2_FINAL + "/*",
            GA4GH_API_PATH_V1 + "/*");
    public static final String DOCKSTORE_WEB_CACHE = "/tmp/dockstore-web-cache";
    public static final String DOCKSTORE_WEB_CACHE_MISS_LOG_FILE = "/tmp/dockstore-web-cache.misses.log";
    public static final File CACHE_MISS_LOG_FILE = new File(DOCKSTORE_WEB_CACHE_MISS_LOG_FILE);

    /**
     * use this to detect whether we're running on CircleCI. Definitely not kosher, use sparingly and only as required.
     */
    public static final String CIRCLE_SHA_1 = "CIRCLE_SHA1";
    public static final String EMAIL_FILTER = "emailFilter";
    public static final String SLIM_COLLECTION_FILTER = "slimCollectionFilter";
    public static final String SLIM_ORGANIZATION_FILTER = "slimOrganizationFilter";
    public static final String SLIM_WORKFLOW_FILTER = "slimWorkflowFilter";
    public static final String SLIM_VERSION_FILTER = "slimVersionFilter";

    public static final String PUBLIC_USER_FILTER = "publicUserFilter";
    public static final String IO_DROPWIZARD_DB_HIBERNATE_ACTIVE = "io.dropwizard.db.ManagedPooledDataSource.hibernate.active";
    public static final String IO_DROPWIZARD_DB_HIBERNATE_SIZE = "io.dropwizard.db.ManagedPooledDataSource.hibernate.size";
    public static final String IO_DROPWIZARD_DB_HIBERNATE_IDLE = "io.dropwizard.db.ManagedPooledDataSource.hibernate.idle";
    public static final String IO_DROPWIZARD_DB_HIBERNATE_CALCULATED_LOAD = "io.dropwizard.db.ManagedPooledDataSource.hibernate.calculatedLoad";
    public static final int PERCENT = 100;


    private static OkHttpClient okHttpClient = null;
    private static final Logger LOG = LoggerFactory.getLogger(DockstoreWebserviceApplication.class);
    private static final int BYTES_IN_KILOBYTE = 1024;
    private static final int KILOBYTES_IN_MEGABYTE = 1024;
    private static final int CACHE_IN_MB = 100;
    private static Cache cache = null;

    static {
        // https://ucsc-cgl.atlassian.net/browse/SEAB-3122, see org.jboss.logging.LoggerProviders.java:29
        System.setProperty("org.jboss.logging.provider", "slf4j");
    }

    private final HibernateBundle<DockstoreWebserviceConfiguration> hibernate = new HibernateBundle<>(Token.class, Tool.class, User.class,
            Tag.class, Label.class, SourceFile.class, Workflow.class, CollectionOrganization.class, WorkflowVersion.class, FileFormat.class,
            Organization.class, Notification.class, OrganizationUser.class, Event.class, Collection.class, Validation.class, BioWorkflow.class, Service.class, VersionMetadata.class, Image.class, Checksum.class, LambdaEvent.class,
            ParsedInformation.class, EntryVersion.class, DeletedUsername.class, CloudInstance.class, Author.class, OrcidAuthor.class,
            AppTool.class, Category.class, FullWorkflowPath.class, Notebook.class, SourceFileMetadata.class, Metrics.class, CpuStatisticMetric.class, MemoryStatisticMetric.class, ExecutionTimeStatisticMetric.class, CostStatisticMetric.class,
            ExecutionStatusCountMetric.class, ValidationStatusCountMetric.class, ValidatorInfo.class, ValidatorVersionInfo.class, MetricsByStatus.class, Doi.class) {
        @Override
        public DataSourceFactory getDataSourceFactory(DockstoreWebserviceConfiguration configuration) {
            return configuration.getDataSourceFactory();
        }
    };
    private MetricRegistry metricRegistry;

    public static void main(String[] args) throws Exception {
        new DockstoreWebserviceApplication().run(args);
    }

    public static Cache getCache(String cacheNamespace) {
        if (cacheNamespace == null) {
            return cache;
        } else {
            return generateCache(cacheNamespace);
        }
    }

    public static OkHttpClient getOkHttpClient() {
        return okHttpClient;
    }

    @Override
    public String getName() {
        return "webservice";
    }

    @Override
    public void initialize(Bootstrap<DockstoreWebserviceConfiguration> bootstrap) {

        configureMapper(bootstrap.getObjectMapper());

        // setup hibernate+postgres
        bootstrap.addBundle(hibernate);

        // serve static html as well
        bootstrap.addBundle(new AssetsBundle("/assets/", "/static/"));

        // for database migrations.xml
        bootstrap.addBundle(new MigrationsBundle<>() {
            @Override
            public DataSourceFactory getDataSourceFactory(DockstoreWebserviceConfiguration configuration) {
                return configuration.getDataSourceFactory();
            }
        });

        bootstrap.addBundle(new MultiPartBundle());

        if (cache == null) {
            cache = generateCache(null);
        }
        try {
            cache.initialize();
        } catch (IOException e) {
            LOG.error("Could not create web cache, initialization exception", e);
            throw new RuntimeException(e);
        }
        // match HttpURLConnection which does not have a timeout by default
        OkHttpClient.Builder builder = new OkHttpClient().newBuilder();
        if (runningOnCircleCI()) {
            builder.eventListener(new CacheHitListener(DockstoreWebserviceApplication.class.getSimpleName(), "central"));
        }
        okHttpClient = builder.cache(cache).connectTimeout(0, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(0, TimeUnit.SECONDS).build();
        try {
            // this can only be called once per JVM, a factory exception is thrown in our tests
            URL.setURLStreamHandlerFactory(new ObsoleteUrlFactory(okHttpClient));
        } catch (Error factoryException) {
            if (factoryException.getMessage().contains("factory already defined")) {
                LOG.debug("OkHttpClient already registered, skipping");
            } else {
                LOG.error("Could not create web cache, factory exception", factoryException);
                throw new RuntimeException(factoryException);
            }
        }
    }

    /**
     * Use this sparingly. Generally, we want the application under test to be as similar to production as possible.
     * However, sometimes it is necessary to trigger different behaviour due to test limitations/edge cases.
     * @return true if we're running on CircleCI
     */
    public static boolean runningOnCircleCI() {
        return System.getenv(CIRCLE_SHA_1) != null;
    }    

    private static Cache generateCache(String suffix) {
        int cacheSize = CACHE_IN_MB * BYTES_IN_KILOBYTE * KILOBYTES_IN_MEGABYTE; // 100 MiB
        final File cacheDir;
        try {
            // let's try using the same cache each time
            // not sure how corruptible/non-corruptible the cache is
            // namespace cache when testing on circle ci
            cacheDir = Files.createDirectories(Paths.get(DOCKSTORE_WEB_CACHE + (suffix == null ? "" : "/" + suffix))).toFile();
        } catch (IOException e) {
            LOG.error("Could not create or re-use web cache", e);
            throw new RuntimeException(e);
        }
        return new Cache(cacheDir, cacheSize);
    }

    /**
     * Configures an ObjectMapper.
     * @param objectMapper
     */
    public static void configureMapper(ObjectMapper objectMapper) {
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.registerModule(new Hibernate5JakartaModule());
        objectMapper.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
        objectMapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        // doesn't seem to work, when it does, we could avoid overriding pojo.mustache in swagger
        objectMapper.enable(MapperFeature.ALLOW_EXPLICIT_PROPERTY_RENAMING);
        objectMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        // To convert every Date we have to RFC 3339, we can use this
        // objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz"));

        // try to set a filter
        objectMapper.setFilterProvider(new SimpleFilterProvider().addFilter(EMAIL_FILTER, new EmailPropertyFilter())
            .addFilter(SLIM_ORGANIZATION_FILTER, Organization.SLIM_FILTER)
            .addFilter(SLIM_WORKFLOW_FILTER, Workflow.SLIM_FILTER)
            .addFilter(SLIM_COLLECTION_FILTER, Collection.SLIM_FILTER)
            .addFilter(SLIM_VERSION_FILTER, Version.SLIM_FILTER)
            .addFilter(PUBLIC_USER_FILTER, new PublicUserFilter())
        );
    }

    public static File getFilePluginLocation(DockstoreWebserviceConfiguration configuration) {
        String userHome = System.getProperty("user.home");
        String filename = userHome + File.separator + ".dockstore" + File.separator + "language-plugins";
        filename = StringUtils.isEmpty(configuration.getLanguagePluginLocation()) ? filename : configuration.getLanguagePluginLocation();
        LOG.info("File plugin path set to:" + filename);
        return new File(filename);
    }

    @Override
    @SuppressWarnings("checkstyle:MethodLength")
    public void run(DockstoreWebserviceConfiguration configuration, Environment environment) {

        restrictSourceFiles(configuration);

        final DefaultPluginManager languagePluginManager = LanguagePluginManager.getInstance(getFilePluginLocation(configuration));
        describeAvailableLanguagePlugins(languagePluginManager);
        LanguageHandlerFactory.setLanguagePluginManager(languagePluginManager);

        final PublicStateManager publicStateManager = PublicStateManager.getInstance();
        publicStateManager.reset();
        publicStateManager.setConfig(configuration);

        environment.jersey().property(CommonProperties.FEATURE_AUTO_DISCOVERY_DISABLE, true);
        environment.jersey().register(new JsonProcessingExceptionMapper(true));

        environment.lifecycle().manage(new ElasticSearchHelper(configuration.getEsConfiguration()));
        final UserDAO userDAO = new UserDAO(hibernate.getSessionFactory());
        final TokenDAO tokenDAO = new TokenDAO(hibernate.getSessionFactory());
        final DeletedUsernameDAO deletedUsernameDAO = new DeletedUsernameDAO(hibernate.getSessionFactory());
        final ToolDAO toolDAO = new ToolDAO(hibernate.getSessionFactory());
        final ServiceDAO serviceDAO = new ServiceDAO(hibernate.getSessionFactory());
        final FileDAO fileDAO = new FileDAO(hibernate.getSessionFactory());
        final WorkflowDAO workflowDAO = new WorkflowDAO(hibernate.getSessionFactory());
        final AppToolDAO appToolDAO = new AppToolDAO(hibernate.getSessionFactory());
        final NotebookDAO notebookDAO = new NotebookDAO(hibernate.getSessionFactory());
        final TagDAO tagDAO = new TagDAO(hibernate.getSessionFactory());
        final EventDAO eventDAO = new EventDAO(hibernate.getSessionFactory());
        final VersionDAO versionDAO = new VersionDAO(hibernate.getSessionFactory());
        final BioWorkflowDAO bioWorkflowDAO = new BioWorkflowDAO(hibernate.getSessionFactory());
        final WorkflowVersionDAO workflowVersionDAO = new WorkflowVersionDAO((hibernate.getSessionFactory()));

        publicStateManager.insertListener(new PopulateEntryListener(toolDAO), publicStateManager.getElasticListener());

        LOG.info("Cache directory for OkHttp is: " + cache.directory().getAbsolutePath());
        LOG.info("This is our custom logger saying that we're about to load authenticators");
        // setup authentication to allow session access in authenticators, see https://github.com/dropwizard/dropwizard/pull/1361
        UnitOfWorkAwareProxyFactory unitOfWorkAwareProxyFactory = new UnitOfWorkAwareProxyFactory(getHibernate());
        SimpleAuthenticator authenticator = unitOfWorkAwareProxyFactory
                .create(SimpleAuthenticator.class, new Class[] { TokenDAO.class, UserDAO.class }, new Object[] { tokenDAO, userDAO });
        CachingAuthenticator<String, User> cachingAuthenticator = new CachingAuthenticator<>(environment.metrics(), authenticator,
                configuration.getAuthenticationCachePolicy());
        environment.jersey().register(new AuthDynamicFeature(
                new OAuthCredentialAuthFilter.Builder<User>().setAuthenticator(cachingAuthenticator).setAuthorizer(new SimpleAuthorizer())
                        .setPrefix("Bearer").setRealm("Dockstore User Authentication").buildAuthFilter()));
        environment.jersey().register(new AuthValueFactoryProvider.Binder<>(User.class));
        environment.jersey().register(RolesAllowedDynamicFeature.class);

        final HttpClient httpClient = new HttpClientBuilder(environment).using(configuration.getHttpClientConfiguration()).build(getName());

        final PermissionsInterface authorizer = PermissionsFactory.createAuthorizer(tokenDAO, configuration);

        final EntryResource entryResource = new EntryResource(hibernate.getSessionFactory(), authorizer, eventDAO, tokenDAO, toolDAO, versionDAO, userDAO, workflowDAO, configuration);
        environment.jersey().register(entryResource);

        final WorkflowResource workflowResource = new WorkflowResource(httpClient, hibernate.getSessionFactory(), authorizer, entryResource, configuration);
        environment.jersey().register(workflowResource);
        final ServiceResource serviceResource = new ServiceResource(httpClient, hibernate.getSessionFactory(), entryResource, configuration);
        environment.jersey().register(serviceResource);

        // Note workflow resource must be passed to the docker repo resource, as the workflow resource refresh must be called for checker workflows
        final DockerRepoResource dockerRepoResource = new DockerRepoResource(httpClient, hibernate.getSessionFactory(), configuration, workflowResource, entryResource);

        environment.jersey().register(dockerRepoResource);
        environment.jersey().register(new DockerRepoTagResource(toolDAO, tagDAO, eventDAO, fileDAO, versionDAO));
        environment.jersey().register(new TokenResource(tokenDAO, userDAO, deletedUsernameDAO, httpClient, cachingAuthenticator, configuration));

        environment.jersey().register(new UserResource(httpClient, getHibernate().getSessionFactory(), workflowResource, dockerRepoResource, cachingAuthenticator, authorizer, configuration));

        MetadataResourceHelper.init(configuration);
        ORCIDHelper.init(configuration);
        ZenodoHelper.init(configuration, httpClient, getHibernate().getSessionFactory());
        environment.jersey().register(new UserResourceDockerRegistries(getHibernate().getSessionFactory()));
        final MetadataResource metadataResource = new MetadataResource(getHibernate().getSessionFactory(), configuration);
        environment.jersey().register(metadataResource);
        environment.jersey().register(new HostedToolResource(getHibernate().getSessionFactory(), authorizer, configuration));
        environment.jersey().register(new HostedWorkflowResource(getHibernate().getSessionFactory(), authorizer, configuration));
        environment.jersey().register(new OrganizationResource(getHibernate().getSessionFactory()));
        environment.jersey().register(new LambdaEventResource(getHibernate().getSessionFactory()));
        environment.jersey().register(new NotificationResource(getHibernate().getSessionFactory()));
        environment.jersey().register(new CollectionResource(getHibernate().getSessionFactory()));
        environment.jersey().register(new EventResource(eventDAO, userDAO));
        environment.jersey().register(new ToolTesterResource(configuration));
        environment.jersey().register(new CloudInstanceResource(getHibernate().getSessionFactory()));
        environment.jersey().register(new CategoryResource(getHibernate().getSessionFactory()));

        // disable odd extra endpoints showing up
        final SwaggerConfiguration swaggerConfiguration = new SwaggerConfiguration().prettyPrint(true);
        swaggerConfiguration.setIgnoredRoutes(Lists.newArrayList("/application.wadl", "/pprof"));
        BaseOpenApiResource openApiResource = new OpenApiResource().openApiConfiguration(swaggerConfiguration);
        environment.jersey().register(openApiResource);

        final AliasResource aliasResource = new AliasResource(hibernate.getSessionFactory(), workflowResource);
        environment.jersey().register(aliasResource);

        // attach the container dao statically to avoid too much modification of generated code
        ToolsApiServiceImpl.setToolDAO(toolDAO);
        ToolsApiServiceImpl.setWorkflowDAO(workflowDAO);
        ToolsApiServiceImpl.setBioWorkflowDAO(bioWorkflowDAO);
        ToolsApiServiceImpl.setServiceDAO(serviceDAO);
        ToolsApiServiceImpl.setAppToolDAO(appToolDAO);
        ToolsApiServiceImpl.setNotebookDAO(notebookDAO);
        ToolsApiServiceImpl.setFileDAO(fileDAO);
        ToolsApiServiceImpl.setVersionDAO(versionDAO);
        ToolsApiServiceImpl.setSessionFactory(hibernate.getSessionFactory());
        ToolsApiServiceImpl.setConfig(configuration);
        ToolsApiServiceImpl.setAuthorizer(authorizer);

        ToolsApiExtendedServiceImpl.setStateManager(publicStateManager);
        ToolsApiExtendedServiceImpl.setToolDAO(toolDAO);
        ToolsApiExtendedServiceImpl.setWorkflowDAO(workflowDAO);
        ToolsApiExtendedServiceImpl.setAppToolDAO(appToolDAO);
        ToolsApiExtendedServiceImpl.setNotebookDAO(notebookDAO);
        ToolsApiExtendedServiceImpl.setBioWorkflowDAO(bioWorkflowDAO);
        ToolsApiExtendedServiceImpl.setServiceDAO(serviceDAO);
        ToolsApiExtendedServiceImpl.setWorkflowVersionDAO(workflowVersionDAO);
        ToolsApiExtendedServiceImpl.setConfig(configuration);

        DOIGeneratorFactory.setConfig(configuration);

        GoogleHelper.setConfig(configuration);
        DoiHelper.setConfig(configuration);

        if (configuration.getDiagnosticsConfig().getEnabled()) {
            LOG.info("enabling diagnostic logging output");
            new DiagnosticsHelper().start(environment, hibernate.getSessionFactory(), configuration.getDiagnosticsConfig());
        }

        registerAPIsAndMisc(environment);

        // optional CORS support
        // Enable CORS headers
        // final FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
        final String methods = "GET,HEAD,POST,DELETE,PUT,OPTIONS,PATCH";
        CORS_ENDPOINTS.stream().forEach(urlContext -> {
            FilterHolder filterHolder = environment.getApplicationContext().addFilter(CrossOriginFilter.class, urlContext, EnumSet.of(REQUEST));

            filterHolder.setInitParameter(ACCESS_CONTROL_ALLOW_METHODS_HEADER, methods);
            filterHolder.setInitParameter(ALLOWED_ORIGINS_PARAM, "*");
            filterHolder.setInitParameter(ALLOWED_METHODS_PARAM, methods);
            filterHolder.setInitParameter(ALLOWED_HEADERS_PARAM,
                    "Accept-Encoding,Authorization,X-Requested-With,Content-Type,Accept,Origin,Access-Control-Request-Headers,cache-control");
        });


        // Log information about privileged endpoints.
        environment.jersey().register(new LogPrivilegedEndpointsListener());

        // Initialize GitHub App Installation Access Token cache
        CacheConfigManager.initCache(configuration.getGitHubAppId(), configuration.getGitHubAppPrivateKeyFile());

        // Register connection pool health check after server starts so the environment has dropwizard metrics
        environment.lifecycle().addServerLifecycleListener(server -> {
            final ConnectionPoolHealthCheck connectionPoolHealthCheck = new ConnectionPoolHealthCheck(configuration.getDataSourceFactory().getMaxSize(), environment.metrics().getGauges());
            environment.healthChecks().register("connectionPool", connectionPoolHealthCheck);
            final LiquibaseLockHealthCheck liquibaseLockHealthCheck = unitOfWorkAwareProxyFactory.create(
                LiquibaseLockHealthCheck.class,
                new Class[] { SessionFactory.class },
                new Object[] { hibernate.getSessionFactory() });
            environment.healthChecks().register("liquibaseLock", liquibaseLockHealthCheck);
            final ElasticsearchConsistencyHealthCheck elasticsearchConsistencyHealthCheck = unitOfWorkAwareProxyFactory.create(
                ElasticsearchConsistencyHealthCheck.class,
                new Class[] { ToolDAO.class, BioWorkflowDAO.class, AppToolDAO.class, NotebookDAO.class },
                new Object[] { toolDAO, bioWorkflowDAO, appToolDAO, notebookDAO });
            environment.healthChecks().register("elasticsearchConsistency", elasticsearchConsistencyHealthCheck);
            metadataResource.setHealthCheckRegistry(environment.healthChecks());
        });

        configureDropwizardMetrics(configuration, environment);

        // Indexes Elasticsearch if mappings don't exist when the application is started
        environment.lifecycle().addServerLifecycleListener(event -> {
            if (!ElasticSearchHelper.doMappingsExist()) {
                // A lock is used to prevent concurrent indexing requests in a deployment where multiple webservices start at the same time
                if (ElasticSearchHelper.acquireLock()) {
                    try {
                        LOG.info("Elasticsearch indices don't exist. Indexing Elasticsearch...");
                        Session session = hibernate.getSessionFactory().openSession();
                        ManagedSessionContext.bind(session);
                        Response response = getToolsExtendedApi().toolsIndexGet(null);
                        session.close();
                        if (response.getStatus() == HttpStatus.SC_OK) {
                            LOG.info("Indexed Elasticsearch");
                        } else {
                            LOG.error("Error indexing Elasticsearch with status code {}", response.getStatus());
                        }
                    } catch (Exception e) {
                        LOG.error("Could not index Elasticsearch", e);
                    } finally {
                        ElasticSearchHelper.releaseLock();
                    }
                }
            } else {
                LOG.info("Elasticsearch indices already exist");
            }
        });
    }

    /**
     * Instrument the webservice to output metrics
     * @param configuration
     * @param environment
     */
    private void configureDropwizardMetrics(DockstoreWebserviceConfiguration configuration, Environment environment) {
        this.metricRegistry = new MetricRegistry();

        metricRegistry.registerGauge(IO_DROPWIZARD_DB_HIBERNATE_CALCULATED_LOAD, new Gauge() {
            @Override
            public Object getValue() {
                final int activeConnections = (int) environment.metrics().getGauges().get(IO_DROPWIZARD_DB_HIBERNATE_ACTIVE).getValue();
                return ((double) activeConnections / configuration.getDataSourceFactory().getMaxSize()) * PERCENT;
            }
        });

        metricRegistry.registerGauge(
            IO_DROPWIZARD_DB_HIBERNATE_ACTIVE, () -> (int) environment.metrics().getGauges().get(IO_DROPWIZARD_DB_HIBERNATE_ACTIVE).getValue());
        metricRegistry.registerGauge(
            IO_DROPWIZARD_DB_HIBERNATE_SIZE, () -> (int) environment.metrics().getGauges().get(IO_DROPWIZARD_DB_HIBERNATE_SIZE).getValue());
        metricRegistry.registerGauge(
            IO_DROPWIZARD_DB_HIBERNATE_IDLE, () -> (int) environment.metrics().getGauges().get(IO_DROPWIZARD_DB_HIBERNATE_IDLE).getValue());

        ScheduledReporter reporter;
        if (configuration.isLocalCloudWatchMetrics()) {
            reporter = ConsoleReporter.forRegistry(metricRegistry)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        } else {
            reporter = new CloudWatchMetricsReporter(metricRegistry, "CloudWatchMetricsReporter", MetricFilter.ALL, TimeUnit.SECONDS, TimeUnit.MILLISECONDS, configuration.getExternalConfig());
        }
        reporter.start(1, TimeUnit.MINUTES);
    }

    private void registerAPIsAndMisc(Environment environment) {
        ToolsApi toolsApi = new ToolsApi(null);
        environment.jersey().register(toolsApi);

        // TODO: attach V2 final properly
        environment.jersey().register(new io.openapi.api.ToolsApi(null));

        environment.jersey().register(new ToolsExtendedApi());
        environment.jersey().register(new io.openapi.api.ToolClassesApi(null));
        environment.jersey().register(new io.openapi.api.ServiceInfoApi(null));
        environment.jersey().register(new MetadataApi(null));
        environment.jersey().register(new ToolClassesApi(null));
        environment.jersey().register(new PersistenceExceptionMapper());
        environment.jersey().register(new ToolsApiV1());
        environment.jersey().register(new MetadataApiV1());
        environment.jersey().register(new ToolClassesApiV1());

        // extra renderers
        environment.jersey().register(new CharsetResponseFilter());

        // Filter used to log every request an admin user makes.
        environment.jersey().register(new AdminPrivilegesFilter());

        environment.jersey().register(new UsernameRenameRequiredFilter());

        environment.jersey().register(new AuthenticatedUserFilter());

        // Swagger providers
        environment.jersey().register(SwaggerSerializers.class);
    }

    private void describeAvailableLanguagePlugins(DefaultPluginManager languagePluginManager) {
        List<PluginWrapper> plugins = languagePluginManager.getStartedPlugins();
        if (plugins.isEmpty()) {
            LOG.info("No language plugins installed");
            return;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("PluginId\tPlugin Version\tPlugin Path\tInitial Path Pattern\tPlugin Type(s)\n");
        for (PluginWrapper plugin : plugins) {
            builder.append(plugin.getPluginId());
            builder.append("\t");
            builder.append(plugin.getPlugin().getWrapper().getDescriptor().getVersion());
            builder.append("\t");
            builder.append(plugin.getPlugin().getWrapper().getPluginPath());
            builder.append("\t");
            List<CompleteLanguageInterface> completeLanguageInterfaces = languagePluginManager.getExtensions(CompleteLanguageInterface.class, plugin.getPluginId());
            List<RecommendedLanguageInterface> recommendedLanguageInterfaces = languagePluginManager.getExtensions(RecommendedLanguageInterface.class, plugin.getPluginId());
            List<MinimalLanguageInterface> minimalLanguageInterfaces = languagePluginManager.getExtensions(MinimalLanguageInterface.class, plugin.getPluginId());
            minimalLanguageInterfaces.forEach(extension -> Joiner.on(',').appendTo(builder, Collections.singleton(extension.initialPathPattern())));
            builder.append("\t");
            minimalLanguageInterfaces.forEach(extension -> Joiner.on(',').appendTo(builder, Collections.singleton("Minimal")));
            builder.append(" ");
            recommendedLanguageInterfaces.forEach(extension -> Joiner.on(',').appendTo(builder, Collections.singleton("Recommended")));
            builder.append(" ");
            completeLanguageInterfaces.forEach(extension -> Joiner.on(',').appendTo(builder, Collections.singleton("Complete")));
            builder.append("\n");
        }
        LOG.info("Started language plugins:\n" + builder);
    }

    public HibernateBundle<DockstoreWebserviceConfiguration> getHibernate() {
        return hibernate;
    }

    private void restrictSourceFiles(DockstoreWebserviceConfiguration configuration) {

        String regexString = configuration.getSourceFilePathRegex();
        String violationMessage = configuration.getSourceFilePathViolationMessage();

        if (regexString != null) {
            Pattern regex;
            try {
                regex = Pattern.compile(regexString);
            } catch (Exception e) {
                LOG.error("Could not parse SourceFile path regex " + regexString);
                throw e;
            }
            if (violationMessage == null) {
                violationMessage = "SourceFile path contains unexpected characters.";
            }
            LOG.info("Restricting SourceFile paths to the regular expression " + regex);
            SourceFile.restrictPaths(regex, violationMessage);
        } else {
            SourceFile.unrestrictPaths();
        }
    }

    private static class LogPrivilegedEndpointsListener implements ApplicationEventListener {

        @Override
        public void onEvent(ApplicationEvent event) {
            if (event.getType() == ApplicationEvent.Type.INITIALIZATION_APP_FINISHED) {
                StringBuilder builder = new StringBuilder();
                List<String> roles = SimpleAuthorizer.ROLES;
                for (Resource resource: event.getResourceModel().getResources()) {
                    formatResource(builder, "/", resource, roles);
                }
                LOG.info(String.format("Endpoints that allow a role in %s:\n%s", roles, builder.toString()));
            }
        }

        private void formatResource(StringBuilder builder, String parentPath, Resource resource, List<String> selectRoles) {
            String path = joinPaths(parentPath, resource.getPath());
            for (ResourceMethod resourceMethod: resource.getAllMethods()) {
                RolesAllowed rolesAllowed = resourceMethod.getInvocable().getHandlingMethod().getAnnotation(RolesAllowed.class);
                if (rolesAllowed != null && !Collections.disjoint(Set.of(rolesAllowed.value()), selectRoles)) {
                    builder.append(String.format("    %s %s %s\n", resourceMethod.getHttpMethod(), path, rolesAllowed));
                }
            }
            for (Resource child: resource.getChildResources()) {
                formatResource(builder, path, child, selectRoles);
            }
        }

        private String joinPaths(String parentPath, String childPath) {
            return "/" + Stream.concat(Arrays.stream(parentPath.split("/")), Arrays.stream(childPath.split("/"))).filter(s -> s.length() > 0).collect(Collectors.joining("/"));
        }

        @Override
        public RequestEventListener onRequest(RequestEvent requestEvent) {
            return null;
        }
    }
}
