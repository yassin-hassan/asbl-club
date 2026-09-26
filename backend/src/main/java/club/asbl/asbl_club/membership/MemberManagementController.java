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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// An association's administrators manage who joins: the join link they share, and the requests it produces.
// Administrators only (403 for everyone else).
@RestController
@RequestMapping("/api/v1/asbls/{slug}/manage")
@Tag(name = "Member management", description = "The join link and join requests (administrators)")
class MemberManagementController {

    private final AsblService asblService;
    private final UserService userService;
    private final MembershipService membershipService;

    MemberManagementController(AsblService asblService, UserService userService, MembershipService membershipService) {
        this.asblService = asblService;
        this.userService = userService;
        this.membershipService = membershipService;
    }

    @Operation(operationId = "getJoinLink", summary = "The association's current join link, if any (administrators)",
            security = @SecurityRequirement(name = "bearer"))
    @GetMapping("/join-link")
    JoinLink joinLink(@PathVariable String slug, Authentication authentication) {
        return new JoinLink(asAdmin(slug, authentication).getJoinToken());
    }

    @Operation(operationId = "newJoinLink",
            summary = "Create a join link, replacing the current one (which stops working) (administrators)",
            security = @SecurityRequirement(name = "bearer"))
    @PostMapping("/join-link")
    JoinLink newJoinLink(@PathVariable String slug, Authentication authentication) {
        return new JoinLink(asblService.newJoinLink(asAdmin(slug, authentication)));
    }

    @Operation(operationId = "disableJoinLink", summary = "Switch the join link off (administrators)",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "204", description = "Switched off")
    @DeleteMapping("/join-link")
    ResponseEntity<Void> disableJoinLink(@PathVariable String slug, Authentication authentication) {
        asblService.disableJoinLink(asAdmin(slug, authentication));
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "approveJoinRequest", summary = "Accept a join request: the person becomes a member",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "204", description = "Approved")
    @ApiResponse(responseCode = "404", description = "No pending request from that person here",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/members/{userId}/approve")
    ResponseEntity<Void> approve(@PathVariable String slug, @PathVariable UUID userId, Authentication authentication) {
        Asbl asbl = asAdmin(slug, authentication);
        try {
            membershipService.approve(asbl, userId);
        } catch (NoSuchJoinRequestException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "declineJoinRequest", summary = "Turn a join request down",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "204", description = "Declined")
    @ApiResponse(responseCode = "404", description = "No pending request from that person here",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/members/{userId}/decline")
    ResponseEntity<Void> decline(@PathVariable String slug, @PathVariable UUID userId, Authentication authentication) {
        Asbl asbl = asAdmin(slug, authentication);
        try {
            membershipService.decline(asbl, userId);
        } catch (NoSuchJoinRequestException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "changeMemberRole", summary = "Give an active member another role (administrators)",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "204", description = "Changed")
    @ApiResponse(responseCode = "404", description = "No active member with that ID here",
            content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "409", description = "The last active administrator (code LAST_ADMIN)",
            content = @Content(mediaType = "application/problem+json"))
    @PutMapping("/members/{userId}/role")
    ResponseEntity<Void> changeRole(@PathVariable String slug, @PathVariable UUID userId,
            @Valid @RequestBody RoleChange change, Authentication authentication) {
        Asbl asbl = asAdmin(slug, authentication);
        return MembershipDecisions.decided(() -> membershipService.changeRole(asbl, userId, MembershipRole.valueOf(change.role())));
    }

    @Operation(operationId = "excludeMember",
            summary = "Exclude a member: access ends at once and the join link won't let them back (administrators)",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "204", description = "Excluded")
    @ApiResponse(responseCode = "404", description = "No active member with that ID here",
            content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "409", description = "The last active administrator (LAST_ADMIN), or oneself (NOT_ON_YOURSELF)",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/members/{userId}/exclude")
    ResponseEntity<Void> exclude(@PathVariable String slug, @PathVariable UUID userId, Authentication authentication) {
        Asbl asbl = asAdmin(slug, authentication);
        User actor = userService.getAuthenticated(authentication);
        return MembershipDecisions.decided(() -> membershipService.exclude(asbl, userId, actor));
    }

    private Asbl asAdmin(String slug, Authentication authentication) {
        User user = userService.getAuthenticated(authentication);
        Asbl asbl = asblService.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!membershipService.isAdmin(user, asbl)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return asbl;
    }

    @Schema(name = "RoleChange")
    record RoleChange(@NotNull @Pattern(regexp = "ADMIN|TREASURER|VIEWER|MEMBER")
                      @Schema(requiredMode = REQUIRED, allowableValues = {"ADMIN", "TREASURER", "VIEWER", "MEMBER"})
                      String role) {
    }

    @Schema(name = "JoinLink")
    record JoinLink(@Schema(description = "The link's token (the link is <site>/join/<token>); absent when switched off")
                    String token) {
    }
}
