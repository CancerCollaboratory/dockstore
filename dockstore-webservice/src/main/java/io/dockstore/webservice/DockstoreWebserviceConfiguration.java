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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.benmanes.caffeine.cache.CaffeineSpec;
import io.dockstore.webservice.helpers.URIHelper;
import io.dropwizard.client.HttpClientConfiguration;
import io.dropwizard.core.Configuration;
import io.dropwizard.db.DataSourceFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public class DockstoreWebserviceConfiguration extends Configuration {

    @Valid
    @NotNull
    private DataSourceFactory database = new DataSourceFactory();

    @Valid
    @NotNull
    private HttpClientConfiguration httpClient = new HttpClientConfiguration();

    @Valid
    private ElasticSearchConfig esConfiguration = new ElasticSearchConfig();

    @Valid
    @NotNull
    private ExternalConfig externalConfig = new ExternalConfig();

    @Valid
    private SamConfiguration samConfiguration = new SamConfiguration();

    @Valid
    private LimitConfig limitConfig = new LimitConfig();

    @Valid
    private MetricsConfig metricsConfig = new MetricsConfig();

    @Valid
    private DiagnosticsConfig diagnosticsConfig = new DiagnosticsConfig();

    @NotEmpty
    private String template;

    @NotEmpty
    private String quayClientID;

    @NotEmpty
    private String githubClientID;

    @NotEmpty
    private String googleClientID;

    @NotEmpty
    private String gitlabClientID;

    @NotEmpty
    private String bitbucketClientID;

    @NotEmpty
    private String bitbucketClientSecret;

    @NotEmpty
    private String quayRedirectURI;

    @NotEmpty
    @JsonProperty
    private String githubRedirectURI;

    @NotEmpty
    private String githubClientSecret;

    @NotEmpty
    private String googleRedirectURI;

    @NotEmpty
    private String googleClientSecret;

    @NotEmpty
    private String gitlabRedirectURI;

    @NotEmpty
    private String gitlabClientSecret;

    @NotEmpty
    private String zenodoClientID;

    @NotEmpty
    private String zenodoRedirectURI;

    @NotEmpty
    private String zenodoUrl;

    @NotEmpty
    private String zenodoClientSecret;

    private String dockstoreZenodoAccessToken;

    private String dockstoreZenodoCommunityId;

    @NotEmpty
    private String orcidClientID;

    @NotEmpty
    private String orcidClientSecret;

    @NotEmpty
    private String discourseUrl;

    @NotEmpty
    private String discourseKey;

    @NotNull
    private Integer discourseCategoryId;

    @NotNull
    private String gitHubAppId;

    @NotNull
    private String gitHubAppPrivateKeyFile;

    @NotNull
    private CaffeineSpec authenticationCachePolicy;

    private String languagePluginLocation;

    private String sqsURL;

    private String toolTesterBucket = null;

    private String authorizerType = null;

    private List<String> externalGoogleClientIdPrefixes = new ArrayList<>();

    private String dashboard = "dashboard.dockstore.org";

    private boolean localCloudWatchMetrics = false;

    @Valid
    @NotNull
    private UIConfig uiConfig;

    private String checkUrlLambdaUrl;

    private String ezidUser;

    private String ezidPassword;

    private String customDoiShoulder;

    private String sourceFilePathRegex = "^[a-zA-Z0-9 ./_-]*$";

    private String sourceFilePathViolationMessage = "Filenames and paths must not contain characters other than letters, digits, '.', '/', '-', '_', and ' '.";

    @JsonProperty("toolTesterBucket")
    public String getToolTesterBucket() {
        return toolTesterBucket;
    }

    @JsonProperty("database")
    public DataSourceFactory getDataSourceFactory() {
        return database;
    }

    @JsonProperty("httpClient")
    public HttpClientConfiguration getHttpClientConfiguration() {
        return httpClient;
    }

    @JsonProperty("externalConfig")
    public ExternalConfig getExternalConfig() {
        return externalConfig;
    }

    @JsonProperty
    public String getTemplate() {
        return template;
    }

    @JsonProperty
    public void setTemplate(String template) {
        this.template = template;
    }

    /**
     * @return the quayClientID
     */
    @JsonProperty
    public String getQuayClientID() {
        return quayClientID;
    }

    /**
     * @param quayClientID the quayClientID to set
     */
    @JsonProperty
    public void setQuayClientID(String quayClientID) {
        this.quayClientID = quayClientID;
    }

    /**
     * @return the quayRedirectURI
     */
    @JsonProperty
    public String getQuayRedirectURI() {
        return quayRedirectURI;
    }

    /**
     * @param quayRedirectURI the quayRedirectURI to set
     */
    @JsonProperty
    public void setQuayRedirectURI(String quayRedirectURI) {
        this.quayRedirectURI = quayRedirectURI;
    }

    /**
     * @param database the database to set
     */
    @JsonProperty("database")
    public void setDatabase(DataSourceFactory database) {
        this.database = database;
    }

    /**
     * @param newHttpClient the httpClient to set
     */
    @JsonProperty("httpClient")
    public void setHttpClientConfiguration(HttpClientConfiguration newHttpClient) {
        this.httpClient = newHttpClient;
    }

    /**
     * @return the githubClientID
     */
    @JsonProperty
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public String getGithubClientID() {
        return githubClientID;
    }

    /**
     * @param githubClientID the githubClientID to set
     */
    @JsonProperty
    public void setGithubClientID(String githubClientID) {
        this.githubClientID = githubClientID;
    }

    /**
     * @return the githubRedirectURI
     */
    @JsonProperty
    public String getGithubRedirectURI() {
        return githubRedirectURI;
    }

    /**
     * @param githubRedirectURI the githubRedirectURI to set
     */
    @JsonProperty
    public void setGithubRedirectURI(String githubRedirectURI) {
        this.githubRedirectURI = githubRedirectURI;
    }

    /**
     * @return the githubClientSecret
     */
    @JsonProperty
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public String getGithubClientSecret() {
        return githubClientSecret;
    }

    /**
     * @param githubClientSecret the githubClientSecret to set
     */
    @JsonProperty
    public void setGithubClientSecret(String githubClientSecret) {
        this.githubClientSecret = githubClientSecret;
    }

    /**
     * @return the bitbucketClientID
     */
    @JsonProperty
    public String getBitbucketClientID() {
        return bitbucketClientID;
    }

    /**
     * @param bitbucketClientID the bitbucketClientID to set
     */
    @JsonProperty
    public void setBitbucketClientID(String bitbucketClientID) {
        this.bitbucketClientID = bitbucketClientID;
    }

    /**
     * @return the bitbucketClientSecret
     */
    @JsonProperty
    public String getBitbucketClientSecret() {
        return bitbucketClientSecret;
    }

    /**
     * @param bitbucketClientSecret the bitbucketClientSecret to set
     */
    @JsonProperty
    public void setBitbucketClientSecret(String bitbucketClientSecret) {
        this.bitbucketClientSecret = bitbucketClientSecret;
    }

    public CaffeineSpec getAuthenticationCachePolicy() {
        return authenticationCachePolicy;
    }

    public void setAuthenticationCachePolicy(CaffeineSpec authenticationCachePolicy) {
        this.authenticationCachePolicy = authenticationCachePolicy;
    }

    public String getGitlabClientID() {
        return gitlabClientID;
    }

    public void setGitlabClientID(String gitlabClientID) {
        this.gitlabClientID = gitlabClientID;
    }

    public String getGitlabRedirectURI() {
        return gitlabRedirectURI;
    }

    public void setGitlabRedirectURI(String gitlabRedirectURI) {
        this.gitlabRedirectURI = gitlabRedirectURI;
    }

    public String getGitlabClientSecret() {
        return gitlabClientSecret;
    }

    public void setGitlabClientSecret(String gitlabClientSecret) {
        this.gitlabClientSecret = gitlabClientSecret;
    }

    public String getZenodoClientID() {
        return zenodoClientID;
    }

    public void setZenodoClientID(String zenodoClientID) {
        this.zenodoClientID = zenodoClientID;
    }

    public String getZenodoRedirectURI() {
        return zenodoRedirectURI;
    }

    public void setZenodoRedirectURI(String zenodoRedirectURI) {
        this.zenodoRedirectURI = zenodoRedirectURI;
    }

    public String getZenodoUrl() {
        return zenodoUrl;
    }

    public void setZenodoUrl(String zenodoUrl) {
        this.zenodoUrl = zenodoUrl;
    }

    public String getZenodoClientSecret() {
        return zenodoClientSecret;
    }

    public void setZenodoClientSecret(String zenodoClientSecret) {
        this.zenodoClientSecret = zenodoClientSecret;
    }

    public String getDockstoreZenodoAccessToken() {
        return dockstoreZenodoAccessToken;
    }

    public void setDockstoreZenodoAccessToken(String dockstoreZenodoAccessToken) {
        this.dockstoreZenodoAccessToken = dockstoreZenodoAccessToken;
    }

    public String getDockstoreZenodoCommunityId() {
        return dockstoreZenodoCommunityId;
    }

    public void setDockstoreZenodoCommunityId(String dockstoreZenodoCommunityId) {
        this.dockstoreZenodoCommunityId = dockstoreZenodoCommunityId;
    }

    public String getOrcidClientID() {
        return orcidClientID;
    }

    public void setOrcidClientID(String orcidClientID) {
        this.orcidClientID = orcidClientID;
    }

    public String getOrcidClientSecret() {
        return orcidClientSecret;
    }

    public void setOrcidClientSecret(String orcidClientSecret) {
        this.orcidClientSecret = orcidClientSecret;
    }

    public String getDiscourseUrl() {
        return discourseUrl;
    }

    public void setDiscourseUrl(String discourseUrl) {
        this.discourseUrl = discourseUrl;
    }

    public String getDiscourseKey() {
        return discourseKey;
    }

    public void setDiscourseKey(String discourseKey) {
        this.discourseKey = discourseKey;
    }

    public Integer getDiscourseCategoryId() {
        return discourseCategoryId;
    }

    public void setDiscourseCategoryId(Integer discourseCategoryId) {
        this.discourseCategoryId = discourseCategoryId;
    }

    public String getGitHubAppId() {
        return gitHubAppId;
    }

    public void setGitHubAppId(String gitHubAppId) {
        this.gitHubAppId = gitHubAppId;
    }

    public String getGitHubAppPrivateKeyFile() {
        return gitHubAppPrivateKeyFile;
    }

    public void setGitHubAppPrivateKeyFile(String gitHubAppPrivateKeyFile) {
        this.gitHubAppPrivateKeyFile = gitHubAppPrivateKeyFile;
    }

    @JsonProperty("esconfiguration")
    public ElasticSearchConfig getEsConfiguration() {
        return esConfiguration;
    }

    public void setEsConfiguration(ElasticSearchConfig esConfiguration) {
        this.esConfiguration = esConfiguration;
    }

    @JsonProperty
    public String getSqsURL() {
        return sqsURL;
    }

    public void setSqsURL(String sqsURL) {
        this.sqsURL = sqsURL;
    }

    @JsonProperty
    public String getGoogleClientID() {
        return googleClientID;
    }

    public void setGoogleClientID(String googleClientID) {
        this.googleClientID = googleClientID;
    }

    @JsonProperty
    public String getGoogleRedirectURI() {
        return googleRedirectURI;
    }

    public void setGoogleRedirectURI(String googleRedirectURI) {
        this.googleRedirectURI = googleRedirectURI;
    }

    @JsonProperty
    public String getGoogleClientSecret() {
        return googleClientSecret;
    }

    public void setGoogleClientSecret(String googleClientSecret) {
        this.googleClientSecret = googleClientSecret;
    }

    @JsonProperty("authorizerType")
    public String getAuthorizerType() {
        return authorizerType;
    }

    public void setAuthorizerType(String authorizerType) {
        this.authorizerType = authorizerType;
    }

    @JsonProperty("samconfiguration")
    public SamConfiguration getSamConfiguration() {
        return samConfiguration;
    }

    public void setSamConfiguration(SamConfiguration samConfiguration) {
        this.samConfiguration = samConfiguration;
    }

    /**
     * A list of a additional Google client ids that Dockstore will accept google tokens from. These ids are in addition
     * to getGoogleClientID, and is intended for any external Google clients that Dockstore will accept tokens from.
     * @return a list of google client ids
     */
    @JsonProperty("externalGoogleClientIdPrefixes")
    public List<String> getExternalGoogleClientIdPrefixes() {
        return externalGoogleClientIdPrefixes;
    }

    public void setExternalGoogleClientIdPrefixes(List<String> externalGoogleClientIdPrefixes) {
        this.externalGoogleClientIdPrefixes = externalGoogleClientIdPrefixes;
    }

    @JsonProperty("dashboard")
    public String getDashboard() {
        return dashboard;
    }

    public void setDashboard(String dashboard) {
        this.dashboard = dashboard;
    }

    @JsonProperty
    public LimitConfig getLimitConfig() {
        return limitConfig;
    }

    public void setLimitConfig(LimitConfig limitConfig) {
        this.limitConfig = limitConfig;
    }

    @JsonProperty
    public MetricsConfig getMetricsConfig() {
        return metricsConfig;
    }

    public void setMetricsConfig(MetricsConfig metricsConfig) {
        this.metricsConfig = metricsConfig;
    }

    @JsonProperty
    public DiagnosticsConfig getDiagnosticsConfig() {
        return diagnosticsConfig;
    }

    public void setDiagnosticsConfig(DiagnosticsConfig diagnosticsConfig) {
        this.diagnosticsConfig = diagnosticsConfig;
    }

    @JsonProperty
    public UIConfig getUiConfig() {
        return uiConfig;
    }

    @JsonProperty
    public String getLanguagePluginLocation() {
        return languagePluginLocation;
    }

    public void setLanguagePluginLocation(String languagePluginLocation) {
        this.languagePluginLocation = languagePluginLocation;
    }

    public String getCheckUrlLambdaUrl() {
        return checkUrlLambdaUrl;
    }

    public void setCheckUrlLambdaUrl(String checkUrlLambdaUrl) {
        this.checkUrlLambdaUrl = checkUrlLambdaUrl;
    }

    public String getEzidUser() {
        return ezidUser;
    }

    public void setEzidUser(String ezidUser) {
        this.ezidUser = ezidUser;
    }

    public String getEzidPassword() {
        return ezidPassword;
    }

    public void setEzidPassword(String ezidPassword) {
        this.ezidPassword = ezidPassword;
    }

    public String getCustomDoiShoulder() {
        return customDoiShoulder;
    }

    public void setCustomDoiShoulder(String customDoiShoulder) {
        this.customDoiShoulder = customDoiShoulder;
    }

    public String getSourceFilePathRegex() {
        return sourceFilePathRegex;
    }

    public void setSourceFilePathRegex(String sourceFilePathRegex) {
        this.sourceFilePathRegex = sourceFilePathRegex;
    }

    public String getSourceFilePathViolationMessage() {
        return sourceFilePathViolationMessage;
    }

    public void setSourceFilePathViolationMessage(String sourceFilePathViolationMessage) {
        this.sourceFilePathViolationMessage = sourceFilePathViolationMessage;
    }

    @JsonProperty
    public boolean isLocalCloudWatchMetrics() {
        return localCloudWatchMetrics;
    }

    public void setLocalCloudWatchMetrics(boolean localCloudWatchMetrics) {
        this.localCloudWatchMetrics = localCloudWatchMetrics;
    }

    /**
     * This config defines values that define the webservice from the outside world.
     * Most notably, for swagger. But also to configure generated RSS paths and TRS paths
     */
    public static class ExternalConfig {
        @NotEmpty
        private String hostname;

        private String basePath;

        @NotEmpty
        private String scheme;

        private String port;

        private String uiPort = null;

        public String getHostname() {
            return hostname;
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        public String getScheme() {
            return scheme;
        }

        public void setScheme(String scheme) {
            this.scheme = scheme;
        }

        public String getPort() {
            return port;
        }

        public void setPort(String port) {
            this.port = port;
        }

        public String getBasePath() {
            return basePath;
        }

        public void setBasePath(String basePath) {
            this.basePath = basePath;
        }

        public String getUiPort() {
            return uiPort;
        }

        public void setUiPort(String uiPort) {
            this.uiPort = uiPort;
        }

        public String computeBaseUrl() {
            return URIHelper.createBaseUrl(getScheme(), getHostname(), getUiPort());
        }

        public boolean computeIsProduction() {
            return "dockstore.org".equals(getHostname());
        }
    }

    public static class ElasticSearchConfig {
        private String hostname;
        private int port;
        private String protocol;
        private String user;
        private String password;
        private Integer maxConcurrentSessions;

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(final String protocol) {
            this.protocol = protocol;
        }

        public String getUser() {
            return user;
        }

        public void setUser(final String user) {
            this.user = user;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(final String password) {
            this.password = password;
        }

        public String getHostname() {
            return hostname;
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public void setMaxConcurrentSessions(Integer maxConcurrentSessions) {
            this.maxConcurrentSessions = maxConcurrentSessions;
        }
        public Integer getMaxConcurrentSessions() {
            return this.maxConcurrentSessions;
        }
    }

    public static class SamConfiguration {
        private String basepath;

        public String getBasepath() {
            return basepath;
        }

        public void setBasepath(String basepath) {
            this.basepath = basepath;
        }
    }

    public static class LimitConfig {
        private Integer workflowLimit;
        private Integer workflowVersionLimit;

        public Integer getWorkflowLimit() {
            return workflowLimit;
        }

        public void setWorkflowLimit(int workflowLimit) {
            this.workflowLimit = workflowLimit;
        }

        public Integer getWorkflowVersionLimit() {
            return workflowVersionLimit;
        }

        public void setWorkflowVersionLimit(int workflowVersionLimit) {
            this.workflowVersionLimit = workflowVersionLimit;
        }
    }

    public static class MetricsConfig {
        private String s3BucketName;

        private String s3EndpointOverride;

        public String getS3BucketName() {
            return s3BucketName;
        }

        public void setS3BucketName(String s3BucketName) {
            this.s3BucketName = s3BucketName;
        }

        public String getS3EndpointOverride() {
            return s3EndpointOverride;
        }

        public void setS3EndpointOverride(String s3EndpointOverride) {
            this.s3EndpointOverride = s3EndpointOverride;
        }
    }

    public static class DiagnosticsConfig {
        private static final long DEFAULT_PERIOD_SECONDS = 600L;
        private boolean logRequests = false;
        private boolean logPeriodic = false;
        private long periodSeconds = DEFAULT_PERIOD_SECONDS;

        public boolean getLogPeriodic() {
            return logPeriodic;
        }

        public void setLogPeriodic(boolean logPeriodic) {
            this.logPeriodic = logPeriodic;
        }

        public boolean getLogRequests() {
            return logRequests;
        }

        public void setLogRequests(boolean logRequests) {
            this.logRequests = logRequests;
        }

        public Long getPeriodSeconds() {
            return periodSeconds;
        }

        public void setPeriodSeconds(long periodSeconds) {
            this.periodSeconds = periodSeconds;
        }

        public boolean getEnabled() {
            return logRequests || logPeriodic;
        }
    }

    /**
     * A subset of properties returned to the UI. Only a subset because some properties that will
     * be used by the UI are also used by the web service and predate the existences of this class.
     */
    public static class UIConfig {

        private String dnaStackImportUrl;
        private String dnaNexusImportUrl;
        private String terraImportUrl;
        private String bdCatalystTerraImportUrl;
        private String bdCatalystSevenBridgesImportUrl;
        private String elwaziImportUrl;
        private String colabImportUrl;
        private String mybinderImportUrl;
        private String gitHubCodespacesImportUrl;

        private String gitHubAuthUrl;
        private String gitHubRedirectPath;
        private String gitHubScope;

        private String quayIoAuthUrl;
        private String quayIoRedirectPath;
        private String quayIoScope;

        private String bitBucketAuthUrl;

        private String gitlabAuthUrl;
        private String gitlabRedirectPath;
        private String gitlabScope;

        private String zenodoAuthUrl;
        private String zenodoRedirectPath;
        private String zenodoScope;

        private String orcidAuthUrl;
        private String orcidRedirectPath;
        private String orcidScope;

        private String googleScope;

        private String cwlVisualizerUri;

        private String tagManagerId;

        private String gitHubAppInstallationUrl;

        private String documentationUrl;

        private String featuredContentUrl;

        private String featuredNewsUrl;

        private String deployVersion;

        private String cwlParsingLambdaVersion;
        private String wdlParsingLambdaVersion;
        private String nextflowParsingLambdaVersion;
        private String galaxyParsingPluginVersion;
        private String checkUrlLambdaVersion;

        public String getDnaStackImportUrl() {
            return dnaStackImportUrl;
        }

        public void setDnaStackImportUrl(String dnaStackImportUrl) {
            this.dnaStackImportUrl = dnaStackImportUrl;
        }

        public String getDnaNexusImportUrl() {
            return dnaNexusImportUrl;
        }

        public void setDnaNexusImportUrl(String dnaNexusImportUrl) {
            this.dnaNexusImportUrl = dnaNexusImportUrl;
        }

        public String getTerraImportUrl() {
            return terraImportUrl;
        }

        public void setTerraImportUrl(String terraImportUrl) {
            this.terraImportUrl = terraImportUrl;
        }

        public String getBdCatalystTerraImportUrl() {
            return bdCatalystTerraImportUrl;
        }

        public void setBdCatalystTerraImportUrl(String bdCatalystTerraImportUrl) {
            this.bdCatalystTerraImportUrl = bdCatalystTerraImportUrl;
        }

        public String getBdCatalystSevenBridgesImportUrl() {
            return bdCatalystSevenBridgesImportUrl;
        }

        public void setBdCatalystSevenBridgesImportUrl(String bdCatalystSevenBridgesImportUrl) {
            this.bdCatalystSevenBridgesImportUrl = bdCatalystSevenBridgesImportUrl;
        }

        public String getElwaziImportUrl() {
            return elwaziImportUrl;
        }

        public void setElwaziImportUrl(String elwaziImportUrl) {
            this.elwaziImportUrl = elwaziImportUrl;
        }

        public String getColabImportUrl() {
            return colabImportUrl;
        }

        public void setColabImportUrl(String colabImportUrl) {
            this.colabImportUrl = colabImportUrl;
        }

        public String getMybinderImportUrl() {
            return mybinderImportUrl;
        }

        public void setMybinderImportUrl(String mybinderImportUrl) {
            this.mybinderImportUrl = mybinderImportUrl;
        }

        public String getGitHubCodespacesImportUrl() {
            return gitHubCodespacesImportUrl;
        }

        public void setGitHubCodespacesImportUrl(String gitHubCodespacesImportUrl) {
            this.gitHubCodespacesImportUrl = gitHubCodespacesImportUrl;
        }

        public String getGitHubAuthUrl() {
            return gitHubAuthUrl;
        }

        public void setGitHubAuthUrl(String gitHubAuthUrl) {
            this.gitHubAuthUrl = gitHubAuthUrl;
        }

        public String getGitHubRedirectPath() {
            return gitHubRedirectPath;
        }

        public void setGitHubRedirectPath(String gitHubRedirectPath) {
            this.gitHubRedirectPath = gitHubRedirectPath;
        }

        public String getGitHubScope() {
            return gitHubScope;
        }

        public void setGitHubScope(String gitHubScope) {
            this.gitHubScope = gitHubScope;
        }

        public String getQuayIoAuthUrl() {
            return quayIoAuthUrl;
        }

        public void setQuayIoAuthUrl(String quayIoAuthUrl) {
            this.quayIoAuthUrl = quayIoAuthUrl;
        }

        public String getQuayIoRedirectPath() {
            return quayIoRedirectPath;
        }

        public void setQuayIoRedirectPath(String quayIoRedirectPath) {
            this.quayIoRedirectPath = quayIoRedirectPath;
        }

        public String getQuayIoScope() {
            return quayIoScope;
        }

        public void setQuayIoScope(String quayIoScope) {
            this.quayIoScope = quayIoScope;
        }

        public String getBitBucketAuthUrl() {
            return bitBucketAuthUrl;
        }

        public void setBitBucketAuthUrl(String bitBucketAuthUrl) {
            this.bitBucketAuthUrl = bitBucketAuthUrl;
        }

        public String getGitlabAuthUrl() {
            return gitlabAuthUrl;
        }

        public void setGitlabAuthUrl(String gitlabAuthUrl) {
            this.gitlabAuthUrl = gitlabAuthUrl;
        }

        public String getGitlabRedirectPath() {
            return gitlabRedirectPath;
        }

        public void setGitlabRedirectPath(String gitlabRedirectPath) {
            this.gitlabRedirectPath = gitlabRedirectPath;
        }

        public String getGitlabScope() {
            return gitlabScope;
        }

        public void setGitlabScope(String gitlabScope) {
            this.gitlabScope = gitlabScope;
        }


        public String getZenodoAuthUrl() {
            return zenodoAuthUrl;
        }

        public void setZenodoAuthUrl(String zenodoAuthUrl) {
            this.zenodoAuthUrl = zenodoAuthUrl;
        }

        public String getZenodoRedirectPath() {
            return zenodoRedirectPath;
        }

        public void setZenodoRedirectPath(String zenodoRedirectPath) {
            this.zenodoRedirectPath = zenodoRedirectPath;
        }

        public String getZenodoScope() {
            return zenodoScope;
        }

        public void setZenodoScope(String zenodoScope) {
            this.zenodoScope = zenodoScope;
        }

        public String getOrcidAuthUrl() {
            return orcidAuthUrl;
        }

        public void setOrcidAuthUrl(String orcidAuthUrl) {
            this.orcidAuthUrl = orcidAuthUrl;
        }

        public String getOrcidRedirectPath() {
            return orcidRedirectPath;
        }

        public void setOrcidRedirectPath(String orcidRedirectPath) {
            this.orcidRedirectPath = orcidRedirectPath;
        }

        public String getOrcidScope() {
            return orcidScope;
        }

        public void setOrcidScope(String orcidScope) {
            this.orcidScope = orcidScope;
        }

        public String getGoogleScope() {
            return googleScope;
        }

        public void setGoogleScope(String googleScope) {
            this.googleScope = googleScope;
        }

        public String getCwlVisualizerUri() {
            return cwlVisualizerUri;
        }

        public void setCwlVisualizerUri(String cwlVisualizerUri) {
            this.cwlVisualizerUri = cwlVisualizerUri;
        }

        public String getTagManagerId() {
            return tagManagerId;
        }

        public void setTagManagerId(String tagManagerId) {
            this.tagManagerId = tagManagerId;
        }

        public String getGitHubAppInstallationUrl() {
            return gitHubAppInstallationUrl;
        }

        public void setGitHubAppInstallationUrl(String gitHubAppInstallationUrl) {
            this.gitHubAppInstallationUrl = gitHubAppInstallationUrl;
        }

        public String getDocumentationUrl() {
            return documentationUrl;
        }

        public void setDocumentationUrl(String documentationUrl) {
            this.documentationUrl = documentationUrl;
        }

        public String getFeaturedContentUrl() {
            return featuredContentUrl;
        }

        public void setFeaturedContentUrl(String featuredContentUrl) {
            this.featuredContentUrl = featuredContentUrl;
        }

        public String getDeployVersion() {
            return deployVersion;
        }

        public void setDeployVersion(final String deployVersion) {
            this.deployVersion = deployVersion;
        }

        public String getFeaturedNewsUrl() {
            return featuredNewsUrl;
        }

        public void setFeaturedNewsUrl(String featuredNewsUrl) {
            this.featuredNewsUrl = featuredNewsUrl;
        }

        public String getCwlParsingLambdaVersion() {
            return cwlParsingLambdaVersion;
        }

        public void setCwlParsingLambdaVersion(String cwlParsingLambdaVersion) {
            this.cwlParsingLambdaVersion = cwlParsingLambdaVersion;
        }

        public String getWdlParsingLambdaVersion() {
            return wdlParsingLambdaVersion;
        }

        public void setWdlParsingLambdaVersion(String wdlParsingLambdaVersion) {
            this.wdlParsingLambdaVersion = wdlParsingLambdaVersion;
        }

        public String getNextflowParsingLambdaVersion() {
            return nextflowParsingLambdaVersion;
        }

        public void setNextflowParsingLambdaVersion(String nextflowParsingLambdaVersion) {
            this.nextflowParsingLambdaVersion = nextflowParsingLambdaVersion;
        }

        public String getGalaxyParsingPluginVersion() {
            return galaxyParsingPluginVersion;
        }

        public void setGalaxyParsingPluginVersion(String galaxyParsingPluginVersion) {
            this.galaxyParsingPluginVersion = galaxyParsingPluginVersion;
        }

        public String getCheckUrlLambdaVersion() {
            return checkUrlLambdaVersion;
        }

        public void setCheckUrlLambdaVersion(String checkUrlLambdaVersion) {
            this.checkUrlLambdaVersion = checkUrlLambdaVersion;
        }
    }
}
