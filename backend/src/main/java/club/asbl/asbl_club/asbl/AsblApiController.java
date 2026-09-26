package club.asbl.asbl_club.asbl;

import club.asbl.asbl_club.api.AsblResource;
import club.asbl.asbl_club.membership.MembershipService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Associations as seen by logged-in users: creating one, and its member area.
@RestController
@RequestMapping("/api/v1/asbls")
@Tag(name = "Associations", description = "Creating and managing associations (logged-in users)")
class AsblApiController {

    private final AsblService asblService;
    private final UserService userService;
    private final MembershipService membershipService;
    private final MessageSource messageSource;

    AsblApiController(AsblService asblService, UserService userService, MembershipService membershipService,
            MessageSource messageSource) {
        this.asblService = asblService;
        this.userService = userService;
        this.membershipService = membershipService;
        this.messageSource = messageSource;
    }

    // The creator becomes the association's first administrator.
    @Operation(operationId = "createAsbl", summary = "Create an association; I become its administrator",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "201", description = "Created",
            content = @Content(schema = @Schema(implementation = AsblResource.class)))
    @ApiResponse(responseCode = "409", description = "URL identifier or BCE number already taken",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping
    ResponseEntity<AsblResource> create(@Valid @RequestBody CreateAsblRequest request, Authentication authentication) {
        User creator = userService.getAuthenticated(authentication);
        Asbl asbl;
        try {
            asbl = asblService.createAsbl(creator, request.denomination(), request.bceNumber(), request.slug(),
                    request.defaultLanguage());
        } catch (SlugAlreadyUsedException e) {
            throw conflict("slug", "asbl.slug.duplicate", e);
        } catch (BceAlreadyUsedException e) {
            throw conflict("bceNumber", "asbl.bce.duplicate", e);
        }
        return ResponseEntity.created(URI.create("/api/v1/asbls/" + asbl.getSlug()))
                .body(new AsblResource(asbl.getSlug(), asbl.getDenomination()));
    }

    // Members only. The association itself is public, so a non-member gets an honest 403 rather than a 404.
    @Operation(operationId = "getAsblMembers", summary = "An association's members (members only)",
            security = @SecurityRequirement(name = "bearer"))
    @GetMapping("/{slug}/members")
    AsblMembers members(@PathVariable String slug, Authentication authentication) {
        User viewer = userService.getAuthenticated(authentication);
        Asbl asbl = asblService.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String myRole = membershipService.roleOf(viewer, asbl)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        // Pending join requests are the administrators' business: other members only see who is in.
        boolean admin = "ADMIN".equals(myRole);
        var members = membershipService.membersOf(asbl).stream()
                .filter(m -> admin || "ACTIVE".equals(m.status()))
                .map(m -> new AsblMembers.Member(m.id(), m.name(), m.email(), m.role(), m.status()))
                .toList();
        return new AsblMembers(asbl.getSlug(), asbl.getDenomination(), myRole, asbl.getStripeAccountId() != null,
                members);
    }

    private ErrorResponseException conflict(String field, String messageKey, RuntimeException cause) {
        String message = messageSource.getMessage(messageKey, null, LocaleContextHolder.getLocale());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, message);
        problem.setProperty("errors", Map.of(field, message));
        return new ErrorResponseException(HttpStatus.CONFLICT, problem, cause);
    }
}
