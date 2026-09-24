// TypeScript mirrors of the backend's auth records (package club.asbl.asbl_club.auth).

// Java: record TokenResponse(String accessToken, String tokenType, long expiresIn)
export interface TokenResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number; // seconds
}

// Java: record MeResponse(UUID id, String email, List<String> roles)
export interface CurrentUser {
  id: string; // UUID
  email: string;
  roles: string[]; // e.g. ["USER", "SUPERADMIN"]
}
