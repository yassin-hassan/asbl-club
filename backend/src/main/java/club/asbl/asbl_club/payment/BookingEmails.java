package club.asbl.asbl_club.payment;

import club.asbl.asbl_club.email.EmailService;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.user.User;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Currency;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// The emails a booking sends, in the member's language, with dates in Belgian time and amounts as Belgians write
// them. Queued in the payment's own transaction (the outbox), so an email goes out if and only if the payment was
// recorded. The ticket travels as an attached QR image: it works at the door without network or login.
@Component
class BookingEmails {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

    private final EmailService emailService;
    private final String publicUrl;

    BookingEmails(EmailService emailService, @Value("${app.public-url}") String publicUrl) {
        this.emailService = emailService;
        this.publicUrl = publicUrl;
    }

    // A guest's booking: the link back to it (the only way to pay later, or to see the ticket), sent at once.
    void guestBooked(Registration booking, String accessToken) {
        Recipient to = recipientOf(booking);
        Event event = booking.getEvent();
        emailService.queue(to.email(), to.locale(), "guestBooked", to.name(), event.getTitle(),
                when(event.getStartsAt(), to.locale()), booking.getTicketCategory().getLabel(),
                money(booking.getAmount(), to.locale()), publicUrl + "/tickets/" + accessToken,
                event.getAsbl().getDenomination());
    }

    void ticketReady(Registration booking) {
        Recipient to = recipientOf(booking);
        if (to == null) {
            return;
        }
        Event event = booking.getEvent();
        // A guest has no "My bookings" page: their email says the attached QR code is the ticket.
        String template = booking.getUser() == null ? "ticketReadyGuest" : "ticketReady";
        emailService.queueWithQrCode(to.email(), to.locale(), template, booking.getQrToken(),
                "ticket-" + booking.getId() + ".png",
                to.name(), event.getTitle(), when(event.getStartsAt(), to.locale()),
                event.getLocation() == null ? "–" : event.getLocation(), booking.getTicketCategory().getLabel(),
                money(booking.getAmount(), to.locale()), grouped(booking.getQrToken()), publicUrl + "/bookings",
                event.getAsbl().getDenomination());
    }

    // A payment that arrived for a booking that no longer wanted it, and went back. Why depends on what happened
    // to the booking meanwhile: its event was cancelled, or it expired and its seat was sold.
    void paymentRefunded(Registration booking, RegistrationStatus bookingWas) {
        Recipient to = recipientOf(booking);
        if (to == null) {
            return;
        }
        String template = bookingWas == RegistrationStatus.CANCELLED ? "refundEventCancelled" : "refundSoldOut";
        emailService.queue(to.email(), to.locale(), template, to.name(), booking.getEvent().getTitle(),
                money(booking.getAmount(), to.locale()), booking.getEvent().getAsbl().getDenomination());
    }

    private record Recipient(String email, String name, Locale locale) {
    }

    // A member (not a closed account: its address is no longer theirs), or a guest.
    private static Recipient recipientOf(Registration booking) {
        User user = booking.getUser();
        if (user != null) {
            return user.getDeletedAt() != null ? null
                    : new Recipient(user.getEmail(), user.getName(), Locale.forLanguageTag(user.getLanguage()));
        }
        return booking.getGuestEmail() == null ? null : new Recipient(booking.getGuestEmail(), booking.getGuestName(),
                Locale.forLanguageTag(booking.getGuestLanguage()));
    }

    static String when(Instant instant, Locale locale) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT)
                .withLocale(locale).withZone(BRUSSELS).format(instant);
    }

    static String money(BigDecimal amount, Locale locale) {
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        format.setCurrency(Currency.getInstance("EUR"));
        return format.format(amount);
    }

    // "0123 4567 89ab …": easier to read out or type at the door, like on the "My bookings" page.
    static String grouped(String code) {
        return code.replaceAll("(.{4})(?!$)", "$1 ");
    }
}
