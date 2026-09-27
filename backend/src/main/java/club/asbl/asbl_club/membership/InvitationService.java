package club.asbl.asbl_club.membership;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.audit.AuditService;
import club.asbl.asbl_club.auth.OneTimeTokens;
import club.asbl.asbl_club.email.EmailService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Personal invitations by email. An administrator types an address; the person gets a link that joins them
// directly, and only them: the link works only for an account with that very address (a forwarded email is
// useless to anyone else).
@Service
class InvitationService {

    static final Duration VALIDITY = Duration.ofDays(14);
    // Each invitation spends one of the provider's free emails (300 a day for the whole platform), and an
    // association must not become a way to email strangers in bulk.
    static final int DAILY_LIMIT_PER_ASSOCIATION = 50;

    private final InvitationRepository invitations;
    private final MembershipRepository memberships;
    private final MembershipService membershipService;
    private final UserService userService;
    private final EmailService emailService;
    private final AuditService auditService;
    private final String publicUrl;

    InvitationService(InvitationRepository invitations, MembershipRepository memberships,
            MembershipService membershipService, UserService userService, EmailService emailService,
            AuditService auditService, @Value("${app.public-url}") String publicUrl) {
        this.invitations = invitations;
        this.memberships = memberships;
        this.membershipService = membershipService;
        this.userService = userService;
        this.emailService = emailService;
        this.auditService = auditService;
        this.publicUrl = publicUrl;
    }

    @Transactional
    public void invite(Asbl asbl, User admin, String email) {
        String address = email.strip().toLowerCase(Locale.ROOT);
        boolean alreadyMember = userService.findByEmail(address)
                .flatMap(user -> memberships.findByUserAndAsbl(user, asbl))
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .isPresent();
        if (alreadyMember) {
            throw new AlreadyMemberException();
        }
        Instant now = Instant.now();
        if (invitations.countByAsblAndCreatedAtAfter(asbl, now.minus(Duration.ofDays(1))) >= DAILY_LIMIT_PER_ASSOCIATION) {
            throw new InvitationLimitException();
        }
        invitations.deletePending(asbl.getId(), address);
        String rawToken = OneTimeTokens.newToken();
        invitations.save(new Invitation(asbl, address, OneTimeTokens.hash(rawToken), admin, now.plus(VALIDITY)));
        // In the association's language: the person may not have an account, so there's no language of theirs yet.
        emailService.queue(address, Locale.forLanguageTag(asbl.getDefaultLanguage()), "invitation",
                admin.getName(), asbl.getDenomination(), publicUrl + "/invitation#token=" + rawToken, VALIDITY.toDays());
        auditService.record("MEMBER_INVITED", asbl, "Asbl", asbl.getId(), Map.of("email", address));
    }

    @Transactional(readOnly = true)
    public List<Invitation> pending(Asbl asbl) {
        return invitations.findPending(asbl, Instant.now());
    }

    @Transactional
    public void cancel(Asbl asbl, Long invitationId) {
        Invitation invitation = invitations.findByIdAndAsbl(invitationId, asbl)
                .orElseThrow(InvalidInvitationException::new);
        invitations.delete(invitation);
        auditService.record("INVITATION_CANCELLED", asbl, "Asbl", asbl.getId(), Map.of("email", invitation.getEmail()));
    }

    // What the invitation page shows before anyone logs in: who invites, to what, for which address (masked).
    @Transactional(readOnly = true)
    public Invitation preview(String rawToken) {
        return invitations.findByTokenHash(OneTimeTokens.hash(rawToken))
                .filter(i -> i.getAcceptedAt() == null && i.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(InvalidInvitationException::new);
    }

    // Joins the logged-in person, if the invitation was sent to their address. Their address is confirmed (login
    // requires it), so owning the account proves owning the inbox the invitation went to.
    @Transactional
    public Asbl accept(User user, String rawToken) {
        Invitation invitation = invitations.findByTokenHash(OneTimeTokens.hash(rawToken))
                .orElseThrow(InvalidInvitationException::new);
        if (!invitation.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw new InvitationForAnotherAddressException();
        }
        if (invitations.accept(invitation.getId(), Instant.now()) == 0) {
            throw new InvalidInvitationException(); // expired or already used
        }
        Asbl asbl = invitation.getAsbl();
        membershipService.joinByInvitation(user, asbl);
        auditService.record("MEMBER_JOINED_BY_INVITATION", asbl, "User", user.getId(),
                Map.of("invitedBy", invitation.getInvitedBy().getId()));
        return asbl;
    }

    // "a•••e@gmail.com": enough for the person to recognise which of their addresses to log in with.
    static String masked(String email) {
        int at = email.indexOf('@');
        String name = email.substring(0, at);
        String shown = name.length() <= 2 ? name.substring(0, 1) + "•••"
                : name.charAt(0) + "•••" + name.charAt(name.length() - 1);
        return shown + email.substring(at);
    }
}
