package club.asbl.asbl_club.membership;

/** Lifecycle of a {@link Membership}. Mirrors the {@code memberships.status} check constraint. */
public enum MembershipStatus {
    PENDING, ACTIVE, EXCLUDED, LEFT
}
