package club.asbl.asbl_club.auth;

// Unknown, revoked or expired refresh token, or a closed account. Deliberately one exception:
// the client gets the same 401 whatever the reason.
class InvalidRefreshTokenException extends RuntimeException {
}
