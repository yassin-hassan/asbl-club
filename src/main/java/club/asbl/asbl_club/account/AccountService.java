package club.asbl.asbl_club.account;

import club.asbl.asbl_club.membership.MembershipService;
import club.asbl.asbl_club.payment.PaymentService;
import club.asbl.asbl_club.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final MembershipService membershipService;
    private final PaymentService paymentService;

    AccountService(MembershipService membershipService, PaymentService paymentService) {
        this.membershipService = membershipService;
        this.paymentService = paymentService;
    }

    @Transactional(readOnly = true)
    public AccountExport exportFor(User user) {
        ProfileExport profile = new ProfileExport(user.getName(), user.getEmail(), user.getLanguage());
        return new AccountExport(profile,
                membershipService.membershipsOf(user),
                paymentService.exportPaymentsOf(user));
    }
}
