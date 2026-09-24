package club.asbl.asbl_club.account;

import club.asbl.asbl_club.membership.MembershipService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The current user's own account. "me" in the path: the identity comes from the access token, so there's no
// user ID a caller could change to reach someone else's data.
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Account", description = "The logged-in user's own account")
class AccountApiController {

    private final UserService userService;
    private final AccountService accountService;
    private final MembershipService membershipService;

    AccountApiController(UserService userService, AccountService accountService, MembershipService membershipService) {
        this.userService = userService;
        this.accountService = accountService;
        this.membershipService = membershipService;
    }

    @Operation(operationId = "listMyAssociations", summary = "The associations I belong to, with my role",
            security = @SecurityRequirement(name = "bearer"))
    @GetMapping("/associations")
    List<MyAssociation> associations(Authentication authentication) {
        User user = userService.getAuthenticated(authentication);
        return membershipService.membershipsOf(user).stream()
                .map(a -> new MyAssociation(a.slug(), a.denomination(), a.role()))
                .toList();
    }

    @Operation(operationId = "exportMyData", summary = "Everything we hold about me (GDPR right of access)",
            security = @SecurityRequirement(name = "bearer"))
    @GetMapping("/export")
    ResponseEntity<AccountExport> export(Authentication authentication) {
        User user = userService.getAuthenticated(authentication);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"my-data.json\"")
                .body(accountService.exportFor(user));
    }

    // Anonymises the account and ends every login session. The access token in the caller's hands stays valid
    // until it expires (at most 15 minutes), like after any logout; the app discards it straight away.
    @Operation(operationId = "deleteMyAccount", summary = "Close my account (GDPR right to erasure)",
            security = @SecurityRequirement(name = "bearer"))
    @DeleteMapping
    ResponseEntity<Void> delete(Authentication authentication) {
        accountService.deleteAccount(userService.getAuthenticated(authentication));
        return ResponseEntity.noContent().build();
    }
}
