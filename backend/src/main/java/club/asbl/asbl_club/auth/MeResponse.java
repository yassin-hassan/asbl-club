package club.asbl.asbl_club.auth;

import java.util.List;
import java.util.UUID;

record MeResponse(UUID id, String email, List<String> roles) {
}
