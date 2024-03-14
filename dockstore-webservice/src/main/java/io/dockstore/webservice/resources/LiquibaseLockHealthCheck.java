package io.dockstore.webservice.resources;

import com.codahale.metrics.health.HealthCheck;
import java.util.Date;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LiquibaseLockHealthCheck extends HealthCheck  {
    private static final Logger LOG = LoggerFactory.getLogger(LiquibaseLockHealthCheck.class);
    private static final long MILLISECONDS_PER_SECOND = 1000L;
    private static final long HELD_TOO_LONG_SECONDS = 600L;
    private final SessionFactory sessionFactory;

    public LiquibaseLockHealthCheck(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    protected Result check() throws Exception {

        Session session = sessionFactory.openSession();
        ManagedSessionContext.bind(session);
        Query query = session.createNativeQuery("select lockgranted from databasechangeloglock");
        Object result = query.getSingleResult();
        session.close();

        if (result == null) {
            return Result.healthy();
        }

        if (result instanceof Date grantedDate) {
            long heldSeconds = (new Date().getTime() - grantedDate.getTime()) / MILLISECONDS_PER_SECOND;
            if (heldSeconds > HELD_TOO_LONG_SECONDS) {
                return Result.unhealthy(String.format("Liquibase lock held too long: granted at %s, held for %d seconds", grantedDate, heldSeconds));
            } else {
                return Result.healthy();
            }
        }

        return Result.unhealthy("Unexpected result from liquibase query");
    }
}
