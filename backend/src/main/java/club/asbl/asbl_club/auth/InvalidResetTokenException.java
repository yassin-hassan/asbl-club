package club.asbl.asbl_club.auth;

// The reset link is unknown, expired or already used. Deliberately one case: which one isn't the caller's business.
class InvalidResetTokenException extends RuntimeException {
}
