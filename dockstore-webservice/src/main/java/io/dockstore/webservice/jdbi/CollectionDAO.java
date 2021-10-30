package io.dockstore.webservice.jdbi;

import io.dockstore.webservice.core.Collection;
import io.dropwizard.hibernate.AbstractDAO;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

public class CollectionDAO extends AbstractDAO<Collection> {
    public CollectionDAO(SessionFactory factory) {
        super(factory);
    }

    public Collection findById(Long id) {
        Collection collection = get(id);
        if (collection != null && collection.isDeleted()) {
            return null;
        }
        return (collection);
    }

    public long create(Collection collection) {
        return persist(collection).getId();
    }

    public long update(Collection collection) {
        return persist(collection).getId();
    }

    public void delete(Collection collection) {
        Session session = currentSession();
        session.delete(collection);
        session.flush();
    }

    public List<Collection> findAllByOrg(long organizationId) {
        Query query = namedTypedQuery("io.dockstore.webservice.core.Collection.findAllByOrg")
                .setParameter("organizationId", organizationId);
        return list(query);
    }

    public Collection findByNameAndOrg(String name, long organizationId) {
        Query query = namedTypedQuery("io.dockstore.webservice.core.Collection.findByNameAndOrg")
                .setParameter("name", name)
                .setParameter("organizationId", organizationId);
        return uniqueResult(query);
    }

    public Collection findByDisplayNameAndOrg(String displayName, long organizationId) {
        Query query = namedTypedQuery("io.dockstore.webservice.core.Collection.findByDisplayNameAndOrg")
            .setParameter("displayName", displayName)
            .setParameter("organizationId", organizationId);
        return uniqueResult(query);
    }

    public void deleteCollectionsByOrgId(long organizationId) {
        Query query = namedQuery("io.dockstore.webservice.core.Collection.deleteByOrgId")
                .setParameter("organizationId", organizationId);
        query.executeUpdate();
    }

    public void deleteEntryVersionByCollectionId(long collectionId) {
        Query query = namedQuery("io.dockstore.webservice.core.Collection.deleteEntryVersionsByCollectionId").setParameter("collectionId", collectionId);
        query.executeUpdate();
    }

    public Collection getByAlias(String alias) {
        return uniqueResult(namedTypedQuery("io.dockstore.webservice.core.Collection.getByAlias").setParameter("alias", alias));
    }
}
