package club.asbl.asbl_club.tenant;

/**
 * Holds the association a request is currently scoped to, if any. It is set for
 * operations that concern a single association, and left empty for cross
 * association operations such as listing a user's own memberships.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_ASBL = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long asblId) {
        CURRENT_ASBL.set(asblId);
    }

    public static Long get() {
        return CURRENT_ASBL.get();
    }

    public static void clear() {
        CURRENT_ASBL.remove();
    }
}
