package club.asbl.asbl_club.account;

import club.asbl.asbl_club.audit.AuditService;
import club.asbl.asbl_club.membership.MembershipService;
import club.asbl.asbl_club.payment.PaymentService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final UserService userService;
    private final MembershipService membershipService;
    private final PaymentService paymentService;
    private final AuditService auditService;

    AccountService(UserService userService, MembershipService membershipService,
            PaymentService paymentService, AuditService auditService) {
        this.userService = userService;
        this.membershipService = membershipService;
        this.paymentService = paymentService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public AccountExport exportFor(User user) {
        ProfileExport profile = new ProfileExport(user.getName(), user.getEmail(), user.getLanguage());
        return new AccountExport(profile,
                membershipService.membershipsOf(user),
                paymentService.exportPaymentsOf(user));
    }

    @Transactional
    public void deleteAccount(User user) {
        // Audit first, while the user's email still resolves the current actor.
        auditService.record("ACCOUNT_DELETED", null, "User", user.getId(), null);
        membershipService.removeAllFor(user);
        // Payments are left as they are: each keeps a frozen copy of who paid (payer name and email), required to
        // keep the association's accounting records valid for 10 years. GDPR allows it (art. 17.3.b, legal
        // obligation); only the account itself is anonymised.
        userService.anonymizeAndClose(user);
    }
}
