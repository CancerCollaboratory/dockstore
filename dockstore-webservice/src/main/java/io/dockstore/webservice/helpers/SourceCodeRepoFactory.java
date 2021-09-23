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

package io.dockstore.webservice.helpers;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.dockstore.common.SourceControl;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dyuen
 */
public final class SourceCodeRepoFactory {

    private static final Logger LOG = LoggerFactory.getLogger(SourceCodeRepoFactory.class);

    private SourceCodeRepoFactory() {
        // hide the constructor for utility classes
    }

    public static SourceCodeRepoInterface createGitHubAppRepo(String token) {
        // The gitUsername doesn't seem to matter
        return new GitHubSourceCodeRepo("JWT", token);
    }

    /**
     * Assumes the token has already been refreshed.
     * @param token
     * @return
     */
    public static SourceCodeRepoInterface createSourceCodeRepo(Token token) {
        SourceCodeRepoInterface repo;
        if (Objects.equals(token.getTokenSource(), TokenType.GITHUB_COM)) {
            repo = new GitHubSourceCodeRepo(token.getUsername(), token.getContent());
        } else if (Objects.equals(token.getTokenSource(), TokenType.BITBUCKET_ORG)) {
            repo = new BitBucketSourceCodeRepo(token.getUsername(), token.getContent());
        } else if (Objects.equals(token.getTokenSource(), TokenType.GITLAB_COM)) {
            repo = new GitLabSourceCodeRepo(token.getUsername(), token.getContent());
        } else {
            LOG.error("We do not currently support: " + token.getTokenSource());
            throw new CustomWebApplicationException("Sorry, we do not support " + token.getTokenSource() + ".",
                HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
        }
        repo.checkSourceCodeValidity();
        return repo;
    }

    /**
     * Assumes the token has already been refreshed.
     * @param gitUrl
     * @param bitbucketTokenContent
     * @param gitlabTokenContent
     * @param githubToken
     * @return
     */
    public static SourceCodeRepoInterface createSourceCodeRepo(String gitUrl, String bitbucketTokenContent, String gitlabTokenContent,
            Token githubToken) {

        Map<String, String> repoUrlMap = parseGitUrl(gitUrl);

        if (repoUrlMap == null) {
            return null;
        }

        String source = repoUrlMap.get("Source");
        String gitUsername = repoUrlMap.get("Username");

        SourceCodeRepoInterface repo;
        if (SourceControl.GITHUB.toString().equals(source)) {
            repo = new GitHubSourceCodeRepo(githubToken.getUsername(), githubToken.getContent());
        } else if (SourceControl.BITBUCKET.toString().equals(source)) {
            if (bitbucketTokenContent != null) {
                repo = new BitBucketSourceCodeRepo(gitUsername, bitbucketTokenContent);
            } else {
                LOG.info("WARNING: Source is from Bitbucket, but user does not have Bitbucket token!");
                return null;
            }
        } else if (SourceControl.GITLAB.toString().equals(source)) {
            if (gitlabTokenContent != null) {
                repo = new GitLabSourceCodeRepo(gitUsername, gitlabTokenContent);
            } else {
                LOG.info("WARNING: Source is from Gitlab, but user does not have Gitlab token!");
                return null;
            }
        } else {
            LOG.info("Do not support: " + source);
            throw new CustomWebApplicationException("Sorry, we do not support " + source + ".", HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
        }
        repo.checkSourceCodeValidity();
        return repo;
    }

    /**
     * Parse Git URL to retrieve source, username and repository name.
     *
     * @param url
     * @return a map with keys: Source, Username, Repository
     */
    public static Map<String, String> parseGitUrl(String url) {
        // format 1 git@github.com:dockstore/dockstore-ui.git
        // Avoid SonarCloud warning: Using slow regular expressions is security-sensitive
        // https://sonarcloud.io/organizations/dockstore/rules?open=java%3AS5852&rule_key=java%3AS5852
        // See Prevent Catastrophic Backtracking and Possessive Quantifiers and Atomic Grouping to The Rescue
        // in https://www.regular-expressions.info/catastrophic.html
        // So use more restrictive regex and possesive quantifiers '++' with atomic group '?>'
        Pattern p1 = Pattern.compile("git@([^\\s:]++):([^\\s/]++)/(?>(\\S+)\\.git$)");
        Matcher m1 = p1.matcher(url);
        // format 2 git://github.com/denis-yuen/dockstore-whalesay.git (should be avoided)
        Pattern p2 = Pattern.compile("git://([^\\s/]++)/([^\\s/]++)/(?>(\\S+)\\.git$)");
        Matcher m2 = p2.matcher(url);

        Matcher matcherActual;
        if (m1.find()) {
            matcherActual = m1;
        } else if (m2.find()) {
            matcherActual = m2;
        } else {
            LOG.info("Cannot parse url using any format: " + url);
            return null;
        }

        final int sourceIndex = 1;
        final int usernameIndex = 2;
        final int reponameIndex = 3;
        String source = matcherActual.group(sourceIndex);
        String gitUsername = matcherActual.group(usernameIndex);
        String gitRepository = matcherActual.group(reponameIndex);

        LOG.debug("Source: " + source);
        LOG.debug("Username: " + gitUsername);
        LOG.debug("Repository: " + gitRepository);

        Map<String, String> map = new HashMap<>();
        map.put("Source", source);
        map.put("Username", gitUsername);
        map.put("Repository", gitRepository);
        return map;
    }

    /**
     * Determines which SourceControl is associated with the Git url.
     *
     * @param url The Git url of the repository
     * @return The associated SourceControl
     */
    public static SourceControl mapGitUrlToSourceCodeRepo(String url) {
        Map<String, String> repoUrlMap = parseGitUrl(url);
        if (repoUrlMap == null) {
            throw new CustomWebApplicationException("Dockstore could not parse: " + url, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        String source = repoUrlMap.get("Source");
        if (SourceControl.GITHUB.toString().equals(source)) {
            return SourceControl.GITHUB;
        }
        if (SourceControl.GITLAB.toString().equals(source)) {
            return SourceControl.GITLAB;
        }
        if (SourceControl.BITBUCKET.toString().equals(source)) {
            return SourceControl.BITBUCKET;
        }
        LOG.info("Do not support: {}", source);
        throw new CustomWebApplicationException(String.format("Sorry, we do not support %s.", source), HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
    }
}
