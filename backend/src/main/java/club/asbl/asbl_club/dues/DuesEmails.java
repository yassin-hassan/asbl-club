package club.asbl.asbl_club.dues;

import club.asbl.asbl_club.email.EmailService;
import club.asbl.asbl_club.payment.PaymentSucceeded;
import club.asbl.asbl_club.user.User;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// The receipt for paid dues, in the member's language. It runs inside the transaction that records the payment
// (a plain event listener) and queues the email in the outbox: sent if and only if the payment was recorded.
@Component
class DuesEmails {

    private final DueRepository dueRepository;
    private final EmailService emailService;

    DuesEmails(DueRepository dueRepository, EmailService emailService) {
        this.dueRepository = dueRepository;
        this.emailService = emailService;
    }

    @EventListener
    void onPaymentSucceeded(PaymentSucceeded paid) {
        dueRepository.findById(paid.payableId()).ifPresent(this::receipt); // anything else isn't dues
    }

    private void receipt(Due due) {
        User member = due.getUser();
        if (member.getDeletedAt() != null) {
            return; // the address is no longer theirs
        }
        Locale locale = Locale.forLanguageTag(member.getLanguage());
        emailService.queue(member.getEmail(), locale, "duesPaid", member.getName(), String.valueOf(due.getYear()),
                due.getAsbl().getDenomination(), money(due.getAmount(), locale));
    }

    private static String money(BigDecimal amount, Locale locale) {
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        format.setCurrency(Currency.getInstance("EUR"));
        return format.format(amount);
    }
}
