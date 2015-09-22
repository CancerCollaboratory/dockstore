/*
 * Copyright (C) 2015 Consonance
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.dockstore.webservice.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.gson.Gson;
import io.dockstore.webservice.core.Container;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
//import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.ContainerDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dropwizard.hibernate.UnitOfWork;
import io.dropwizard.jackson.Jackson;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
//import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.DELETE;
//import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.apache.http.client.HttpClient;

/**
 *
 * @author dyuen
 */
@Path("/docker.repo")
@Api(value = "/docker.repo")
@Produces(MediaType.APPLICATION_JSON)
public class DockerRepoResource {

    private final UserDAO userDAO;
    private final TokenDAO tokenDAO;
    private final ContainerDAO containerDAO;
    private final HttpClient client;
    public static final String TARGET_URL = "https://quay.io/api/v1/";

    private static final ObjectMapper MAPPER = Jackson.newObjectMapper();

    public static final int GIT_URL_INDEX = 3;

    private static class RepoList {

        private List<Container> repositories;

        public void setRepositories(List<Container> repositories) {
            this.repositories = repositories;
        }

        public List<Container> getRepositories() {
            return this.repositories;
        }
    }

    public DockerRepoResource(HttpClient client, UserDAO userDAO, TokenDAO tokenDAO, ContainerDAO containerDAO) {
        this.userDAO = userDAO;
        this.tokenDAO = tokenDAO;
        this.client = client;

        this.containerDAO = containerDAO;
    }

    @GET
    @Path("/listOwned")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List repos owned by the logged-in user", notes = "This part needs to be fleshed out but the user "
            + "can list only the repos they own by default", response = Container.class)
    public List<Container> listOwned(@QueryParam("enduser_id") Long userId) {
        List<Token> tokens = tokenDAO.findByUserId(userId);
        List<Container> ownedContainers = new ArrayList<>(0);
        for (Token token : tokens) {
            String tokenType = token.getTokenSource();
            if (tokenType.equals(TokenType.QUAY_IO.toString())) {
                Optional<String> asString = ResourceUtilities.asString(TARGET_URL + "repository?last_modified=true&public=false",
                        token.getContent(), client);

                if (asString.isPresent()) {
                    RepoList repos;
                    try {
                        repos = MAPPER.readValue(asString.get(), RepoList.class);
                        List<Container> containers = repos.getRepositories();
                        // ownedContainers.addAll(containers);

                        for (Container c : containers) {
                            String name = c.getName();
                            String namespace = c.getNamespace();
                            List<Container> list = containerDAO.findByNameAndNamespaceAndRegistry(name, namespace, tokenType);

                            if (list.size() == 1) {
                                ownedContainers.add(list.get(0));
                            } else {
                                ownedContainers.add(c);
                            }

                        }

                    } catch (IOException ex) {
                        Logger.getLogger(DockerRepoResource.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }

        return ownedContainers;
    }

    @PUT
    @Path("/refreshRepos")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Refresh repos owned by the logged-in user", notes = "This part needs to be fleshed out but the user "
            + "can trigger a sync on the repos they're associated with", response = Container.class)
    public List<Container> refreshRepos(@QueryParam("user_id") Long userId) {
        List<Container> currentRepos = containerDAO.findByUserId(userId);
        List<Container> allRepos = new ArrayList<>(0);
        List<Token> tokens = tokenDAO.findByUserId(userId);

        for (Token token : tokens) {
            if (token.getTokenSource().equals(TokenType.QUAY_IO.toString())) {
                Optional<String> asString = ResourceUtilities.asString(TARGET_URL + "repository?last_modified=true&public=false",
                        token.getContent(), client);

                if (asString.isPresent()) {
                    RepoList repos;
                    try {
                        repos = MAPPER.readValue(asString.get(), RepoList.class);
                        allRepos.addAll(repos.getRepositories());
                    } catch (IOException ex) {
                        Logger.getLogger(DockerRepoResource.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }

        for (Container newContainer : allRepos) {
            for (Container oldContainer : currentRepos) {
                if (newContainer.getName().equals(oldContainer.getName())
                        && newContainer.getNamespace().equals(oldContainer.getNamespace())) {
                    System.out.println("container " + oldContainer.getId() + " is being updated ...");
                    oldContainer.update(newContainer);
                    containerDAO.create(oldContainer);
                    System.out.println("container " + oldContainer.getId() + " is updated");
                }
            }
        }

        return currentRepos;
    }

    @GET
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List all repos known via all registered tokens", notes = "List docker container repos currently known. "
            + "Right now, tokens are used to synchronously talk to the quay.io API to list repos. "
            + "Ultimately, we should cache this information and refresh either by user request or by time "
            + "TODO: This should be a properly defined list of objects, it also needs admin authentication", response = Container.class)
    public List<Container> getRepos() {
        // public String getRepos() {
        List<Token> findAll = tokenDAO.findAll();
        StringBuilder builder = new StringBuilder();
        List<Container> containerList = new ArrayList<>(0);
        for (Token token : findAll) {
            String tokenType = token.getTokenSource();
            if (tokenType.equals(TokenType.QUAY_IO.toString())) {
                Optional<String> asString = ResourceUtilities.asString(TARGET_URL + "repository?last_modified=true&public=false",
                        token.getContent(), client);
                builder.append("Token: ").append(token.getId()).append("\n");
                if (asString.isPresent()) {
                    builder.append(asString.get());

                    RepoList repos;
                    try {
                        repos = MAPPER.readValue(asString.get(), RepoList.class);
                        List<Container> containers = repos.getRepositories();
                        // containerList.addAll(containers);

                        for (Container c : containers) {
                            String name = c.getName();
                            String namespace = c.getNamespace();
                            List<Container> list = containerDAO.findByNameAndNamespaceAndRegistry(name, namespace, tokenType);

                            if (list.size() == 1) {
                                containerList.add(list.get(0));
                            } else {
                                containerList.add(c);
                            }

                        }
                    } catch (IOException ex) {
                        Logger.getLogger(DockerRepoResource.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }
                builder.append("\n");
            }
        }
        // return builder.toString();
        return containerList;
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/registerContainer")
    @ApiOperation(value = "Register a container", notes = "Register a container (public or private). Assumes that user is using quay.io and github", response = Container.class)
    public Container registerContainer(@QueryParam("container_name") String name, @QueryParam("enduser_id") Long userId) throws IOException {
        // User user = userDAO.findById(userId);
        List<Token> tokens = tokenDAO.findByUserId(userId);

        for (Token token : tokens) {
            String tokenSource = token.getTokenSource();
            if (tokenSource.equals(TokenType.QUAY_IO.toString())) {
                Optional<String> asString = ResourceUtilities.asString(TARGET_URL + "repository?last_modified=true&public=false",
                        token.getContent(), client);

                if (asString.isPresent()) {
                    RepoList repos = MAPPER.readValue(asString.get(), RepoList.class);
                    List<Container> containers = repos.getRepositories();
                    for (Container c : containers) {

                        if (name == null ? (String) c.getName() == null : name.equals((String) c.getName())) {
                            String namespace = (String) c.getNamespace();
                            List<Container> list = containerDAO.findByNameAndNamespaceAndRegistry(name, namespace, tokenSource);

                            if (list.isEmpty()) {
                                String repo = namespace + "/" + name;
                                Optional<String> asStringBuilds = ResourceUtilities.asString(TARGET_URL + "repository/" + repo + "/build/",
                                        token.getContent(), client);
                                String gitURL = "";

                                if (asStringBuilds.isPresent()) {
                                    String json = asStringBuilds.get();

                                    // System.out.println(json);

                                    Gson gson = new Gson();
                                    Map<String, ArrayList> map = new HashMap<>();
                                    map = (Map<String, ArrayList>) gson.fromJson(json, map.getClass());

                                    Map<String, Map<String, String>> map2 = new HashMap<>();
                                    map2 = (Map<String, Map<String, String>>) map.get("builds").get(0);

                                    gitURL = map2.get("trigger_metadata").get("git_url");
                                    System.out.println(gitURL);
                                }

                                c.setUserId(userId);
                                c.setRegistry("quay.io");
                                c.setGitUrl(gitURL);
                                c.setIsRegistered(true);
                                long create = containerDAO.create(c);
                                return containerDAO.findById(create);
                            } else {
                                System.out.println("Container already registered");
                            }
                        }

                    }
                } else {
                    System.out.println("Received no repos from client");
                }
            }
        }
        return null;
    }

    @DELETE
    @Path("/unregisterContainer/{containerId}")
    @ApiOperation(value = "Deletes a container")
    public Container unregisterContainer(
            @ApiParam(value = "Container id to delete", required = true) @PathParam("containerId") Long containerId) {
        throw new UnsupportedOperationException();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/getUserRegisteredContainers")
    @ApiOperation(value = "List all registered containers from a user", notes = "", response = Container.class)
    public List<Container> getUserRegisteredContainers(@QueryParam("user_id") Long userId) {
        List<Container> repositories = containerDAO.findByUserId(userId);
        return repositories;
    }

    @GET
    @Timed
    @Path("getAllRegisteredContainers")
    public List<Container> getAllRegisteredContainers() {
        List<Container> repositories = containerDAO.findAll();
        return repositories;
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/shareWithUser")
    @ApiOperation(value = "User shares a container with a chosen user", notes = "Needs to be fleshed out.")
    public void shareWithUser(@QueryParam("container_id") Long containerId, @QueryParam("user_id") Long userId) {
        throw new UnsupportedOperationException();
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/shareWithGroup")
    @ApiOperation(value = "User shares a container with a chosen group", notes = "Needs to be fleshed out.")
    public void shareWithGroup(@QueryParam("container_id") Long containerId, @QueryParam("group_id") Long groupId) {
        throw new UnsupportedOperationException();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/getRepo/{userId}/{repository}")
    @ApiOperation(value = "Fetch repo from quay.io", response = String.class)
    public String getRepo(@ApiParam(value = "The full path of the repository. e.g. namespace/name") @PathParam("repository") String repo,
            @ApiParam(value = "user id") @PathParam("userId") long userId) {
        List<Token> tokens = tokenDAO.findByUserId(userId);
        StringBuilder builder = new StringBuilder();

        for (Token token : tokens) {
            if (token.getTokenSource().equals(TokenType.QUAY_IO.toString())) {
                Optional<String> asString = ResourceUtilities.asString(TARGET_URL + "repository/" + repo, token.getContent(), client);

                if (asString.isPresent()) {
                    builder.append(asString.get());
                }
                builder.append("\n");
            }
        }

        return builder.toString();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/getBuilds")
    @ApiOperation(value = "Get the list of repository builds.", notes = "For TESTING purposes. Also useful for getting more information about the repository.", response = String.class)
    public String getBuilds(@QueryParam("repository") String repo, @QueryParam("userId") long userId) {

        List<Token> tokens = tokenDAO.findByUserId(userId);
        StringBuilder builder = new StringBuilder();

        for (Token token : tokens) {
            if (token.getTokenSource().equals(TokenType.QUAY_IO.toString())) {
                Optional<String> asString = ResourceUtilities.asString(TARGET_URL + "repository/" + repo + "/build/", token.getContent(),
                        client);

                if (asString.isPresent()) {
                    String json = asString.get();

                    Gson gson = new Gson();
                    Map<String, ArrayList> map = new HashMap<>();
                    map = (Map<String, ArrayList>) gson.fromJson(json, map.getClass());

                    Map<String, Map<String, String>> map2 = new HashMap<>();
                    map2 = (Map<String, Map<String, String>>) map.get("builds").get(0);

                    String gitURL = map2.get("trigger_metadata").get("git_url");
                    System.out.println(gitURL);

                    builder.append(asString.get());
                }
                builder.append("\n");
            }
        }

        return builder.toString();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/searchContainers")
    @ApiOperation(value = "Search for matching registered containers", notes = "Search on the name (full path name) and description.", response = Container.class)
    public List<Container> searchContainers(@QueryParam("pattern") String word) {
        return containerDAO.searchPattern(word);
    }
}
