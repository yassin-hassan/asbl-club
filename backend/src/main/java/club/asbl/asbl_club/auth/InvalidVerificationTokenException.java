package club.asbl.asbl_club.auth;

// The verification link is unknown, expired or already used (one case on purpose).
class InvalidVerificationTokenException extends RuntimeException {
}
