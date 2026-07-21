package club.asbl.asbl_club.demo;

import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
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

    private final UserService userService;
    private final AsblService asblService;

    DemoDataSeeder(UserService userService, AsblService asblService) {
        this.userService = userService;
        this.asblService = asblService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (asblService.count() > 0) {
            log.info("Demo data already present, skipping the seed");
            return;
        }
        log.info("Seeding {} demo users, associations and memberships", COUNT);
        for (int i = 1; i <= COUNT; i++) {
            User founder = userService.register("Membre " + i, "membre" + i + "@demo.asbl.club", "password123");
            asblService.createAsbl(founder, "Association " + i, String.format("0%03d.000.000", i),
                    "association-" + i, "fr");
        }
        log.info("Demo data seeded");
    }
}
