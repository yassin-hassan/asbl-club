package club.asbl.asbl_club.tenant;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Switches on the Hibernate asbl filter for the current transaction when a
 * tenant is set, so that every query on a scoped entity is silently limited to
 * that association. A scoped service calls enable at the start of its work.
 */
@Component
public class TenantFilter {

    private final EntityManager entityManager;

    TenantFilter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void enable() {
        Long asblId = TenantContext.get();
        if (asblId != null) {
            entityManager.unwrap(Session.class)
                    .enableFilter("asblFilter")
                    .setParameter("asblId", asblId);
        }
    }
}
