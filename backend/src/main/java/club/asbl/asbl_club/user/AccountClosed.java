package club.asbl.asbl_club.user;

// Published when an account is closed (GDPR deletion). Other parts of the application react on their own
// terms — e.g. the auth part ends every login session — without the user code knowing about them.
public record AccountClosed(Long userId) {
}
