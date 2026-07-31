package club.asbl.asbl_club.demo;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.event.EventService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Populates the database with demonstration data. It runs only under the
 * "demo" profile, so it never fires during tests or in a clean production, and
 * it is idempotent, it seeds only when no association exists yet.
 */
@Component
@Profile("demo")
class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final int COUNT = 100;

    // A test connected account that is already onboarded and can accept charges,
    // so the payment flow works end to end without any Stripe onboarding.
    private static final String DEMO_STRIPE_ACCOUNT = "acct_1TxApqRVezmODcDW";

    private final UserService userService;
    private final AsblService asblService;
    private final EventService eventService;

    DemoDataSeeder(UserService userService, AsblService asblService, EventService eventService) {
        this.userService = userService;
        this.asblService = asblService;
        this.eventService = eventService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (asblService.count() > 0) {
            log.info("Demo data already present, skipping the seed");
            return;
        }
        log.info("Seeding {} demo users, associations and memberships", COUNT);
        userService.registerSuperAdmin("Super Admin", "admin@demo.asbl.club", "password123");
        log.info("Seeded a super administrator, admin@demo.asbl.club");
        for (int i = 1; i <= COUNT; i++) {
            User founder = userService.register("Membre " + i, "membre" + i + "@demo.asbl.club", "password123");
            asblService.createAsbl(founder, "Association " + i, String.format("0%03d.000.000", i),
                    "association-" + i, "fr");
        }
        seedStripeReadyAssociation();
        log.info("Demo data seeded");
    }

    /**
     * A ready-to-use association already linked to a chargeable test Stripe
     * account, with an admin login and a published event with a ticket, so the
     * whole payment flow can be demonstrated without any manual setup.
     */
    private void seedStripeReadyAssociation() {
        User admin = userService.register("Demo Admin", "demo@asbl.club", "password123");
        Asbl club = asblService.createAsbl(admin, "Club Démo", "0999.999.999", "club-demo", "fr");
        asblService.linkStripeAccount(club, DEMO_STRIPE_ACCOUNT);

        Event event = eventService.createEvent(club, "Concert de gala", "Une soirée de démonstration",
                Instant.parse("2026-12-01T19:00:00Z"), "Bruxelles", "PUBLIC");
        eventService.addTicketCategory(event, "Place standard", new BigDecimal("12.00"), 50);
        eventService.publish(event);

        log.info("Seeded a Stripe-ready association, log in as demo@asbl.club / password123 (club-demo)");
    }
}
