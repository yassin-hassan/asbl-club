package club.asbl.asbl_club.auth;

import club.asbl.asbl_club.config.JwtConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Authentication", description = "Exchange credentials for tokens")
class MeController {

    private final UserService userService;

    MeController(UserService userService) {
        this.userService = userService;
    }

    // Identity from the token; name and email from the database, so a change shows at once (a token keeps what it
    // said for up to 15 minutes).
    @Operation(operationId = "getCurrentUser", summary = "Who am I? Requires a valid access token",
            security = @SecurityRequirement(name = "bearer"))
    @GetMapping("/api/v1/me")
    MeResponse me(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        User user = userService.getAuthenticated(authentication);
        return new MeResponse(user.getPublicId(), user.getEmail(), user.getName(),
                jwt.getClaimAsStringList(JwtConfig.ROLES_CLAIM));
    }
}
