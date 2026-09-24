package club.asbl.asbl_club.auth;

record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
