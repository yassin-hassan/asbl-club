package club.asbl.asbl_club.payment;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.stripe.exception.StripeException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
class PaymentController {

    private final PaymentService paymentService;
    private final RegistrationRepository registrationRepository;
    private final UserService userService;
    private final StripeProperties stripeProperties;

    PaymentController(PaymentService paymentService, RegistrationRepository registrationRepository,
            UserService userService, StripeProperties stripeProperties) {
        this.paymentService = paymentService;
        this.registrationRepository = registrationRepository;
        this.userService = userService;
        this.stripeProperties = stripeProperties;
    }

    @GetMapping("/pay/{registrationId}")
    String checkout(@PathVariable Long registrationId, Model model, Authentication authentication)
            throws StripeException {
        User user = userService.getByEmail(authentication.getName());
        Registration registration = ownRegistration(registrationId, user);
        Asbl asbl = registration.getEvent().getAsbl();

        PaymentInitiation initiation =
                paymentService.initiate(registration, asbl, user.getName(), user.getEmail(), user);

        model.addAttribute("registrationId", registrationId);
        model.addAttribute("amount", registration.getAmount());
        model.addAttribute("clientSecret", initiation.clientSecret());
        model.addAttribute("publishableKey", stripeProperties.publishableKey());
        model.addAttribute("stripeAccount", asbl.getStripeAccountId());
        return "payment/checkout";
    }

    @GetMapping("/pay/{registrationId}/complete")
    String complete(@PathVariable Long registrationId, Model model, Authentication authentication) {
        Registration registration =
                ownRegistration(registrationId, userService.getByEmail(authentication.getName()));
        Asbl asbl = registration.getEvent().getAsbl();
        model.addAttribute("publishableKey", stripeProperties.publishableKey());
        model.addAttribute("stripeAccount", asbl.getStripeAccountId());
        return "payment/complete";
    }

    // Someone else's registration is "not found": registration IDs are sequential, so without this check any
    // logged-in user could open (and start paying) another person's booking by changing the number.
    private Registration ownRegistration(Long registrationId, User user) {
        return registrationRepository.findByIdWithEventAndAsbl(registrationId)
                .filter(r -> r.getUser() != null && r.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
