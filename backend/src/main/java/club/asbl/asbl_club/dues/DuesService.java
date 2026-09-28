package club.asbl.asbl_club.dues;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.audit.AuditService;
import club.asbl.asbl_club.membership.MembershipService;
import club.asbl.asbl_club.payment.PaymentInitiation;
import club.asbl.asbl_club.payment.PaymentService;
import club.asbl.asbl_club.user.User;
import com.stripe.exception.StripeException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Membership dues: an association sets one yearly fee, and each active member pays it online for the current
// (calendar) year. Paying reuses the ticket payment flow unchanged: a Due is a Payable, PaymentService takes it
// to Stripe, and Stripe's webhook marks its payment as succeeded.
@Service
public class DuesService {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

    private final DueRepository dueRepository;
    private final PaymentService paymentService;
    private final MembershipService membershipService;
    private final AsblService asblService;
    private final AuditService auditService;

    DuesService(DueRepository dueRepository, PaymentService paymentService, MembershipService membershipService,
            AsblService asblService, AuditService auditService) {
        this.dueRepository = dueRepository;
        this.paymentService = paymentService;
        this.membershipService = membershipService;
        this.asblService = asblService;
        this.auditService = auditService;
    }

    // What a member owes one association this year, and whether it's paid.
    public record MemberDues(Asbl asbl, int year, BigDecimal amount, Optional<Instant> paidAt) {
    }

    public int currentYear() {
        return LocalDate.now(BRUSSELS).getYear(); // dues are per calendar year, in Belgian time
    }

    // Set or remove (null) the yearly fee. Dues already started keep the amount they started with.
    @Transactional
    public void setFee(Asbl asbl, BigDecimal fee) throws PaymentsDisabledException {
        if (fee != null && asbl.getStripeAccountId() == null) {
            throw new PaymentsDisabledException(); // members couldn't pay it
        }
        asblService.setAnnualFee(asbl, fee);
        Map<String, Object> change = new HashMap<>();
        change.put("annualFee", fee);
        auditService.record("DUES_FEE_CHANGED", asbl, "Asbl", asbl.getId(), change);
    }

    // This year's dues in every association the user is an active member of that collects them.
    @Transactional(readOnly = true)
    public List<MemberDues> duesOf(User user) {
        int year = currentYear();
        return membershipService.activeAssociationsOf(user).stream()
                .filter(asbl -> asbl.getAnnualFee() != null)
                .map(asbl -> dueRepository.findByAsblAndUserAndYear(asbl, user, year)
                        .map(due -> new MemberDues(asbl, year, due.getAmount(), paymentService.paidAt(due)))
                        .orElseGet(() -> new MemberDues(asbl, year, asbl.getAnnualFee(), Optional.empty())))
                .toList();
    }

    // Each current (active) member and whether they paid that year's dues: the treasurer's follow-up list.
    public record MemberStatus(String name, String email, boolean paid, BigDecimal amount, Instant paidAt) {
    }

    @Transactional(readOnly = true)
    public List<MemberStatus> statusOfMembers(Asbl asbl, int year) {
        Map<Long, PaidDue> paid = dueRepository.findPaid(asbl, year).stream()
                .collect(Collectors.toMap(PaidDue::userId, Function.identity()));
        return membershipService.activeMembersOf(asbl).stream()
                .map(member -> {
                    PaidDue due = paid.get(member.getId());
                    return new MemberStatus(member.getName(), member.getEmail(), due != null,
                            due == null ? null : due.amount(), due == null ? null : due.paidAt());
                })
                .toList();
    }

    // Start (or resume) paying this year's dues. The Due is created on the first attempt at the current fee; later
    // attempts reuse it and its Stripe payment, so two tabs or a retry can't create a second payment.
    @Transactional
    public Started startPayment(Asbl asbl, User user)
            throws NotAMemberException, NoDuesException, PaymentsDisabledException, AlreadyPaidException,
            StripeException {
        if (membershipService.roleOf(user, asbl).isEmpty()) {
            throw new NotAMemberException();
        }
        if (asbl.getAnnualFee() == null) {
            throw new NoDuesException();
        }
        if (asbl.getStripeAccountId() == null) {
            throw new PaymentsDisabledException();
        }
        int year = currentYear();
        Due due = dueRepository.findByAsblAndUserAndYear(asbl, user, year)
                .orElseGet(() -> dueRepository.save(new Due(asbl, user, year, asbl.getAnnualFee())));
        if (paymentService.paidAt(due).isPresent()) {
            throw new AlreadyPaidException();
        }
        PaymentInitiation initiation = paymentService.initiate(due, asbl, user.getName(), user.getEmail(), user);
        return new Started(initiation.clientSecret(), due.getAmount());
    }

    // What the Stripe form needs, and the amount being paid (the fee when this year's payment was first started).
    public record Started(String clientSecret, BigDecimal amount) {
    }

    static class NotAMemberException extends Exception {
    }

    static class NoDuesException extends Exception {
    }

    static class PaymentsDisabledException extends Exception {
    }

    static class AlreadyPaidException extends Exception {
    }
}
