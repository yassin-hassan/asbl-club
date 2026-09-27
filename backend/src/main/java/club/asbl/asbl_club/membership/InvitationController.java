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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Invitations by email: administrators send, list and cancel them; the invited person previews one (before logging
// in) and accepts it (logged in with the invited address).
@RestController
@Tag(name = "Invitations", description = "Personal invitations by email")
class InvitationController {

    private final InvitationService invitationService;
    private final AsblService asblService;
    private final UserService userService;
    private final MembershipService membershipService;

    InvitationController(InvitationService invitationService, AsblService asblService, UserService userService,
            MembershipService membershipService) {
        this.invitationService = invitationService;
        this.asblService = asblService;
        this.userService = userService;
        this.membershipService = membershipService;
    }

    record InviteRequest(@NotBlank @Email @Size(max = 255) String email) {
    }

    record TokenRequest(@NotBlank @Size(max = 100) String token) {
    }

    @Schema(name = "PendingInvitation")
    record Pending(@Schema(requiredMode = REQUIRED) Long id, @Schema(requiredMode = REQUIRED) String email,
            @Schema(requiredMode = REQUIRED) Instant invitedAt, @Schema(requiredMode = REQUIRED) Instant expiresAt) {
    }

    @Schema(name = "InvitationPreview")
    record Preview(@Schema(requiredMode = REQUIRED) String association, @Schema(requiredMode = REQUIRED) String slug,
            @Schema(requiredMode = REQUIRED) String invitedBy,
            @Schema(requiredMode = REQUIRED, description = "The invited address, partly hidden") String email) {
    }

    @Schema(name = "AcceptedInvitation")
    record Accepted(@Schema(requiredMode = REQUIRED) String slug) {
    }

    @Operation(operationId = "inviteByEmail", summary = "Invite someone by email: the link joins them directly "
            + "(administrators)", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "202", description = "Invitation sent")
    @ApiResponse(responseCode = "409", description = "Already an active member (ALREADY_MEMBER), or the daily number "
            + "of invitations is reached (INVITATION_LIMIT)", content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/api/v1/asbls/{slug}/manage/invitations")
    ResponseEntity<Void> invite(@PathVariable String slug, @Valid @RequestBody InviteRequest request,
            Authentication authentication) {
        User admin = userService.getAuthenticated(authentication);
        Asbl asbl = asAdmin(slug, admin);
        try {
            invitationService.invite(asbl, admin, request.email());
        } catch (AlreadyMemberException e) {
            throw problem(HttpStatus.CONFLICT, "ALREADY_MEMBER", "This person is already a member.");
        } catch (InvitationLimitException e) {
            throw problem(HttpStatus.CONFLICT, "INVITATION_LIMIT", "Today's number of invitations is reached.");
        }
        return ResponseEntity.accepted().build();
    }

    @Operation(operationId = "listInvitations", summary = "Invitations not accepted yet (administrators)",
            security = @SecurityRequirement(name = "bearer"))
    @GetMapping("/api/v1/asbls/{slug}/manage/invitations")
    List<Pending> pending(@PathVariable String slug, Authentication authentication) {
        Asbl asbl = asAdmin(slug, userService.getAuthenticated(authentication));
        return invitationService.pending(asbl).stream()
                .map(i -> new Pending(i.getId(), i.getEmail(), i.getCreatedAt(), i.getExpiresAt())).toList();
    }

    @Operation(operationId = "cancelInvitation", summary = "Cancel an invitation: its link stops working "
            + "(administrators)", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "204", description = "Cancelled")
    @DeleteMapping("/api/v1/asbls/{slug}/manage/invitations/{invitationId}")
    ResponseEntity<Void> cancel(@PathVariable String slug, @PathVariable Long invitationId,
            Authentication authentication) {
        Asbl asbl = asAdmin(slug, userService.getAuthenticated(authentication));
        try {
            invitationService.cancel(asbl, invitationId);
        } catch (InvalidInvitationException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.noContent().build();
    }

    // Public: the page shows who invites to what before the person logs in or signs up. The token travels in the
    // body, never in a URL (it would end up in logs).
    @Operation(operationId = "previewInvitation", summary = "Who invites me, to which association")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Preview.class)))
    @ApiResponse(responseCode = "404", description = "Unknown, expired or already used invitation",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/api/v1/invitations/preview")
    Preview preview(@Valid @RequestBody TokenRequest request) {
        try {
            Invitation invitation = invitationService.preview(request.token());
            return new Preview(invitation.getAsbl().getDenomination(), invitation.getAsbl().getSlug(),
                    invitation.getInvitedBy().getName(), InvitationService.masked(invitation.getEmail()));
        } catch (InvalidInvitationException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @Operation(operationId = "acceptInvitation", summary = "Join the association: the invitation must have been "
            + "sent to my address", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "Joined", content = @Content(schema = @Schema(implementation = Accepted.class)))
    @ApiResponse(responseCode = "400", description = "Unknown, expired or already used (INVALID_INVITATION)",
            content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "403", description = "Sent to another address (WRONG_ACCOUNT)",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/api/v1/invitations/accept")
    Accepted accept(@Valid @RequestBody TokenRequest request, Authentication authentication) {
        try {
            return new Accepted(invitationService.accept(userService.getAuthenticated(authentication), request.token())
                    .getSlug());
        } catch (InvalidInvitationException e) {
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_INVITATION", "This invitation is invalid, expired or already used.");
        } catch (InvitationForAnotherAddressException e) {
            throw problem(HttpStatus.FORBIDDEN, "WRONG_ACCOUNT", "This invitation was sent to another email address.");
        }
    }

    private Asbl asAdmin(String slug, User user) {
        Asbl asbl = asblService.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!membershipService.isAdmin(user, asbl)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return asbl;
    }

    private static ErrorResponseException problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return new ErrorResponseException(status, problem, null);
    }
}
