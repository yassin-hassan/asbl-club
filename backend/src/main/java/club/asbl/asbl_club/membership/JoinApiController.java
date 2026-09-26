package club.asbl.asbl_club.membership;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// The other side of a join link: a logged-in person sees which association it's for, and asks to join.
// An unknown, replaced or switched-off link is "not found".
@RestController
@RequestMapping("/api/v1/join/{token}")
@Tag(name = "Joining", description = "Asking to join an association through its join link")
class JoinApiController {

    private final AsblService asblService;
    private final UserService userService;
    private final MembershipService membershipService;

    JoinApiController(AsblService asblService, UserService userService, MembershipService membershipService) {
        this.asblService = asblService;
        this.userService = userService;
        this.membershipService = membershipService;
    }

    @Operation(operationId = "getJoinInvitation", summary = "Which association a join link is for, and my status there",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JoinInvitation.class)))
    @ApiResponse(responseCode = "404", description = "Unknown or expired link",
            content = @Content(mediaType = "application/problem+json"))
    @GetMapping
    JoinInvitation invitation(@PathVariable String token, Authentication authentication) {
        User user = userService.getAuthenticated(authentication);
        Asbl asbl = byToken(token);
        String status = membershipService.membershipsOf(user).stream()
                .filter(m -> m.slug().equals(asbl.getSlug()))
                .map(m -> m.status())
                .findFirst().orElse(null);
        return new JoinInvitation(asbl.getSlug(), asbl.getDenomination(), status);
    }

    @Operation(operationId = "requestToJoin", summary = "Ask to join: an administrator approves the request",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JoinInvitation.class)))
    @ApiResponse(responseCode = "403", description = "Excluded from this association (code JOIN_REFUSED)",
            content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "404", description = "Unknown or expired link",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping
    JoinInvitation requestToJoin(@PathVariable String token, Authentication authentication) {
        User user = userService.getAuthenticated(authentication);
        Asbl asbl = byToken(token);
        try {
            MembershipStatus status = membershipService.requestToJoin(user, asbl);
            return new JoinInvitation(asbl.getSlug(), asbl.getDenomination(), status.name());
        } catch (JoinRefusedException e) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                    "You can't join this association.");
            problem.setProperty("code", "JOIN_REFUSED");
            throw new ErrorResponseException(HttpStatus.FORBIDDEN, problem, e);
        }
    }

    private Asbl byToken(String token) {
        return asblService.findByJoinToken(token).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Schema(name = "JoinInvitation")
    record JoinInvitation(
            @Schema(requiredMode = REQUIRED) String slug,
            @Schema(requiredMode = REQUIRED) String denomination,
            @Schema(description = "My membership status there, if any",
                    allowableValues = {"PENDING", "ACTIVE", "EXCLUDED", "LEFT"}) String status) {
    }
}
