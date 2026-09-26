package club.asbl.asbl_club.membership;

import java.util.UUID;

public record MemberView(UUID id, String name, String email, String role, String status) {
}
