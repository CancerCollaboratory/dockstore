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

package io.dockstore.webservice.jdbi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.database.RSSToolPath;
import io.dockstore.webservice.core.database.ToolPath;
import io.dockstore.webservice.helpers.JsonLdRetriever;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

import static io.dockstore.webservice.resources.MetadataResource.RSS_ENTRY_LIMIT;

/**
 * @author xliu
 */
public class ToolDAO extends EntryDAO<Tool> {

    public ToolDAO(SessionFactory factory) {
        super(factory);
    }

    public List<Tool> findByUserRegistryNamespace(final long userId, final String registry, final String namespace) {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Tool.findByUserRegistryNamespace").setParameter("userId", userId).setParameter("registry", registry)
            .setParameter("namespace", namespace));
    }

    public List<Tool> findByUserRegistryNamespaceRepository(final long userId, final String registry, final String namespace, final String repository) {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Tool.findByUserRegistryNamespaceRepository").setParameter("userId", userId).setParameter("registry", registry)
            .setParameter("namespace", namespace).setParameter("repository", repository));
    }

    public List<ToolPath> findAllPublishedPaths() {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Tool.findAllPublishedPaths"));
    }

    public List<RSSToolPath> findAllPublishedPathsOrderByDbupdatedate() {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Tool.findAllPublishedPathsOrderByDbupdatedate").setMaxResults(RSS_ENTRY_LIMIT));
    }

    public List<String> getAllPublishedNamespaces() {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Tool.getPublishedNamespaces"));
    }

    /**
     * Finds all tools with the given path (ignores tool name) When findPublished is true, will only look at published tools
     *
     * @param path
     * @param findPublished
     * @return A list of tools with the given path
     */
    public List<Tool> findAllByPath(String path, boolean findPublished) {
        String[] splitPath = Tool.splitPath(path);

        // Not a valid path
        if (splitPath == null) {
            return null;
        }

        // Valid path
        String registry = splitPath[registryIndex];
        String namespace = splitPath[orgIndex];
        String name = splitPath[repoIndex];

        // Create full query name
        String fullQueryName = "io.dockstore.webservice.core.Tool.";

        if (findPublished) {
            fullQueryName += "findPublishedByPath";
        } else {
            fullQueryName += "findByPath";
        }

        // Create query
        Query query = namedQuery(fullQueryName)
            .setParameter("registry", registry)
            .setParameter("namespace", namespace)
            .setParameter("name", name);

        return list(query);
    }

    /**
     * Finds the tool matching the given tool path When findPublished is true, will only look at published tools
     *
     * @param path
     * @param findPublished
     * @return Tool matching the path
     */
    public Tool findByPath(String path, boolean findPublished) {
        String[] splitPath = Tool.splitPath(path);

        // Not a valid path
        if (splitPath == null) {
            return null;
        }

        // Valid path
        String registry = splitPath[registryIndex];
        String namespace = splitPath[orgIndex];
        String name = splitPath[repoIndex];
        String toolname = splitPath[entryNameIndex];

        // Create full query name
        String fullQueryName = "io.dockstore.webservice.core.Tool.";

        if (splitPath[entryNameIndex] == null) {
            if (findPublished) {
                fullQueryName += "findPublishedByToolPathNullToolName";
            } else {
                fullQueryName += "findByToolPathNullToolName";
            }

        } else {
            if (findPublished) {
                fullQueryName += "findPublishedByToolPath";
            } else {
                fullQueryName += "findByToolPath";
            }
        }

        // Create query
        Query query = namedQuery(fullQueryName)
            .setParameter("registry", registry)
            .setParameter("namespace", namespace)
            .setParameter("name", name);

        if (splitPath[entryNameIndex] != null) {
            query.setParameter("toolname", toolname);
        }

        return uniqueResult(query);
    }

    public List<Tool> findPublishedByNamespace(String namespace) {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Tool.findPublishedByNamespace").setParameter("namespace", namespace));
    }

    /**
     * Return map containing schema.org info retrieved from the specified tool's descriptor cwl
     *
     * @param id of specified tool
     * @return map containing schema.org info to be used as json-ld data
     */
    public List findPublishedSchemaById(long id) {
        Tool tool = findPublishedById(id);
        return JsonLdRetriever.getSchema(tool);
    }

    public Tool findByAlias(String alias) {
        return uniqueResult(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Tool.getByAlias").setParameter("alias", alias));
    }

    @SuppressWarnings({"checkstyle:ParameterNumber"})
    protected Root<Tool> generatePredicate(DescriptorLanguage descriptorLanguage, String registry, String organization, String name, String toolname, String description, String author, Boolean checker,
        CriteriaBuilder cb, CriteriaQuery<?> q) {

        final Root<Tool> entryRoot = q.from(Tool.class);

        Predicate predicate = cb.isTrue(entryRoot.get("isPublished"));
        predicate = andLike(cb, predicate, entryRoot.get("namespace"), Optional.ofNullable(organization));
        predicate = andLike(cb, predicate, entryRoot.get("name"), Optional.ofNullable(name));
        predicate = andLike(cb, predicate, entryRoot.get("toolname"), Optional.ofNullable(toolname));
        predicate = andLike(cb, predicate, entryRoot.get("description"), Optional.ofNullable(description));
        predicate = andLike(cb, predicate, entryRoot.get("author"), Optional.ofNullable(author));

        if (descriptorLanguage != null) {
            // not quite right, this probably doesn't deal with tools that have both but https://hibernate.atlassian.net/browse/HHH-9991 is kicking my butt
            predicate = cb.and(predicate, cb.equal(entryRoot.get("descriptorType").as(String.class), descriptorLanguage.getShortName()));
        }
        predicate = andLike(cb, predicate, entryRoot.get("registry"), Optional.ofNullable(registry));

        q.where(predicate);
        return entryRoot;
    }

    @Override
    @SuppressWarnings({"checkstyle:ParameterNumber"})
    public List<Tool> filterTrsToolsGet(DescriptorLanguage descriptorLanguage, String registry, String organization, String name, String toolname,
        String description, String author, Boolean checker, int startIndex, int pageRemaining) {
        //TODO: probably a better way of doing this with the predicate builder, we can short circuit since tools are never checkers
        if (checker != null && checker) {
            return new ArrayList<>();
        }
        return super.filterTrsToolsGet(descriptorLanguage, registry, organization, name, toolname,
            description, author, checker, startIndex, pageRemaining);
    }

    @Override
    @SuppressWarnings({"checkstyle:ParameterNumber"})
    public long countAllPublished(DescriptorLanguage descriptorLanguage, String registry, String organization, String name, String toolname, String description, String author, Boolean checker) {
        //TODO: probably a better way of doing this with the predicate builder, we can short circuit since tools are never checkers
        if (checker != null && checker) {
            return 0;
        }
        return super.countAllPublished(descriptorLanguage, registry, organization, name, toolname, description, author, checker);
    }
}
