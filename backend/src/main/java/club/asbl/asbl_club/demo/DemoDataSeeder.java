package club.asbl.asbl_club.demo;

import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.auth.OneTimeTokens;
import club.asbl.asbl_club.demo.DemoData.Association;
import club.asbl.asbl_club.demo.DemoData.Person;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fills an empty database with realistic demonstration data, under the "demo" profile only (never in tests or a
 * clean production), and only once (it does nothing when an association already exists).
 *
 * <p>It writes a history, not just a state: accounts created over the past year, people joining, events held and
 * coming up, tickets bought, scanned at the door or expired, dues paid, a closed account whose payments are kept,
 * and the audit log, emails and tokens that history leaves behind. Every table gets rows. Writing the rows directly
 * (rather than through the services) is what allows dates in the past; the values follow the application's rules:
 * same statuses, same audit actions and payloads, same commission, valid enterprise numbers.
 *
 * <p>Logins (one shared password): the demo association's administrator, a member for the second browser, a member
 * with payments to unsubscribe, and the platform administrator. See {@link #PASSWORD}.
 */
@Component
@Profile("demo")
class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    static final String PASSWORD = "demo-asbl-2026";

    // A test connected account that is already onboarded and can accept charges, so the payment flow works end to
    // end without any Stripe onboarding.
    static final String DEMO_STRIPE_ACCOUNT = "acct_1TxApqRVezmODcDW";

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.03");
    private static final BigDecimal COMMISSION_FIXED = new BigDecimal("0.30");

    private final AsblService asblService;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final MessageSource messages;
    private final JsonMapper json;

    // Per run: the same data every time (fixed seed), dated relative to now.
    private final Random random = new Random(2026);
    private Instant now;
    private String passwordHash;
    private final Map<Long, String> ipOf = new HashMap<>();

    DemoDataSeeder(AsblService asblService, JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
            MessageSource messages, JsonMapper json) {
        this.asblService = asblService;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.messages = messages;
        this.json = json;
    }

    /** A person with an account. */
    record Account(long id, Person person, Instant createdAt) {
    }

    /** An association and when it was created. */
    record Club(long id, Association association, Instant createdAt) {
    }

    record Category(long id, String label, BigDecimal price) {
    }

    record Event(long id, Club club, String title, Instant startsAt, Instant createdAt) {
    }

    /** What became of a booking. */
    enum Outcome { PAID, ATTENDED, EXPIRED, DECLINED_THEN_EXPIRED, CANCELLED, REFUNDED }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (asblService.count() > 0) {
            log.info("Demo data already present, skipping the seed");
            return;
        }
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        passwordHash = passwordEncoder.encode(PASSWORD);

        account(DemoData.person("Équipe asbl.club", "fr"), "admin@" + DemoData.EMAIL_DOMAIN, daysAgo(400), true);

        // The demo association's people, by role in the demo.
        Account sophie = account(DemoData.person("Sophie Lambert", "fr"), daysAgo(370));
        Account marc = account(DemoData.person("Marc Janssens", "nl"), daysAgo(362));
        Account nadia = account(DemoData.person("Nadia El Amrani", "fr"), daysAgo(358));
        Account thomas = account(DemoData.person("Thomas Dubois", "fr"), daysAgo(355));
        Account julie = account(DemoData.person("Julie Peeters", "fr"), daysAgo(350));
        Account kevin = account(DemoData.person("Kevin Maes", "nl"), daysAgo(345));

        Set<String> named = Set.of("Sophie Lambert", "Marc Janssens", "Nadia El Amrani", "Thomas Dubois",
                "Julie Peeters", "Kevin Maes");
        List<Account> pool = new ArrayList<>();
        for (Person person : DemoData.people(72, named, 7)) {
            pool.add(account(person, daysAgo(20 + random.nextInt(340))));
        }

        Club main = mainAssociation(sophie, marc, nadia, thomas, julie, kevin, pool);
        int otherNumber = 0;
        for (Association association : DemoData.OTHERS) {
            otherAssociation(association, ++otherNumber, pool);
        }

        closeAccount(kevin, daysAgo(40));
        securityHistory(sophie, marc, thomas, julie, pool);
        jdbc.update("""
                UPDATE ticket_categories c SET sold_seats = (SELECT count(*) FROM registrations r
                WHERE r.ticket_category_id = c.id AND r.status IN ('PAID', 'ATTENDED'))""");

        log.info("Demo data seeded. Password for every account: {}", PASSWORD);
        log.info("  {} (administrator of {}), {} (member), {} (member with payments), admin@{} (platform)",
                sophie.person().email(), main.association().slug(), thomas.person().email(),
                julie.person().email(), DemoData.EMAIL_DOMAIN);
    }

    // ---- The demo association --------------------------------------------------------------------------------

    private Club mainAssociation(Account sophie, Account marc, Account nadia, Account thomas, Account julie,
            Account kevin, List<Account> pool) {
        Club club = association(DemoData.MAIN, 4_123_456, sophie, daysAgo(365));
        jdbc.update("UPDATE asbls SET join_token = ? WHERE id = ?", OneTimeTokens.newToken(), club.id());
        audit(sophie, club, "JOIN_LINK_CREATED", null, null, null, daysAgo(364));
        audit(sophie, club, "DUES_FEE_CHANGED", "Asbl", club.id(), Map.of("annualFee", club.association().annualFee()),
                daysAgo(363));

        Map<Account, LocalDate> joined = new LinkedHashMap<>();
        joined.put(sophie, date(daysAgo(365)));
        joinOnRequest(marc, club, sophie, "TREASURER", "FULL", daysAgo(360), joined);
        joinOnRequest(nadia, club, sophie, "VIEWER", "FULL", daysAgo(356), joined);
        joinOnRequest(thomas, club, sophie, "MEMBER", "FULL", daysAgo(352), joined);
        joinOnRequest(julie, club, sophie, "MEMBER", "FULL", daysAgo(348), joined);
        joinOnRequest(kevin, club, sophie, "MEMBER", "FULL", daysAgo(344), joined);
        audit(sophie, club, "MEMBER_ROLE_CHANGED", "User", marc.id(), Map.of("from", "MEMBER", "to", "TREASURER"),
                daysAgo(359));
        audit(sophie, club, "MEMBER_ROLE_CHANGED", "User", nadia.id(), Map.of("from", "MEMBER", "to", "VIEWER"),
                daysAgo(355));

        // 33 more members, three of them through a personal invitation; then two pending requests, one member who
        // left and one excluded.
        List<Account> members = pool.subList(0, 37);
        for (int i = 0; i < 33; i++) {
            Account member = members.get(i);
            Instant when = later(max(member.createdAt(), club.createdAt()), daysAgo(25));
            String category = i % 11 == 0 ? "SUPPORTER" : i % 16 == 5 ? "HONORARY" : "FULL";
            if (i % 12 == 3) {
                joinByInvitation(member, club, sophie, category, when, joined);
            } else {
                joinOnRequest(member, club, sophie, "MEMBER", category, when, joined);
            }
        }
        pendingRequest(members.get(33), club, daysAgo(3));
        pendingRequest(members.get(34), club, daysAgo(1));
        Account leaver = members.get(35);
        joinOnRequest(leaver, club, sophie, "MEMBER", "FULL", later(leaver.createdAt(), daysAgo(30)), joined);
        Instant left = later(startOf(joined.remove(leaver)).plus(Duration.ofDays(5)), daysAgo(1));
        jdbc.update("UPDATE memberships SET status = 'LEFT', updated_at = ? WHERE user_id = ? AND asbl_id = ?",
                ts(left), leaver.id(), club.id());
        audit(leaver, club, "MEMBER_LEFT", "User", leaver.id(), null, left);
        Account excluded = members.get(36);
        joinOnRequest(excluded, club, sophie, "MEMBER", "FULL", later(excluded.createdAt(), daysAgo(30)), joined);
        Instant exclusion = later(startOf(joined.remove(excluded)).plus(Duration.ofDays(5)), daysAgo(1));
        jdbc.update("""
                UPDATE memberships SET status = 'EXCLUDED', excluded_at = ?, updated_at = ?
                WHERE user_id = ? AND asbl_id = ?""",
                date(exclusion), ts(exclusion), excluded.id(), club.id());
        audit(sophie, club, "MEMBER_EXCLUDED", "User", excluded.id(), null, exclusion);

        invitations(club, sophie);

        // Dues: last year's, then this year's (Thomas hasn't paid this year's yet: he can in the demo).
        int year = LocalDate.now(BRUSSELS).getYear();
        List<Account> memberList = new ArrayList<>(joined.keySet());
        Set<Account> alwaysPaid = Set.of(sophie, marc, thomas, julie, kevin);
        for (Account member : memberList) {
            if (joined.get(member).isBefore(LocalDate.of(year - 1, 12, 1))
                    && (alwaysPaid.contains(member) || random.nextInt(10) < 8)) {
                payDues(club, member, year - 1, duesDate(joined.get(member), year - 1), true);
            }
        }
        List<Account> unpaid = new ArrayList<>();
        for (Account member : memberList) {
            boolean pays = member.equals(julie) || member.equals(sophie) || member.equals(marc)
                    || (!member.equals(thomas) && !member.equals(kevin) && random.nextInt(10) < 6);
            if (pays) {
                payDues(club, member, year, duesDate(joined.get(member), year), true);
            } else if (!member.equals(thomas) && !member.equals(kevin)) {
                unpaid.add(member);
            }
        }
        // Two members started paying this year's dues and gave up halfway.
        payDues(club, unpaid.get(0), year, daysAgo(12), false);
        payDues(club, unpaid.get(1), year, daysAgo(4), false);

        // Events: two held, two coming up, one cancelled, one draft.
        List<Account> buyers = new ArrayList<>(memberList);
        buyers.add(kevin);
        buyers.addAll(pool.subList(40, 55)); // public events also sell to people outside the association

        Event games = event(club, sophie, "Soirée jeux de société",
                "Une soirée conviviale autour de jeux de plateau, du plus simple au plus stratégique. "
                        + "Jeux fournis, boissons et petite restauration au bar.",
                at(-75, 19, 0), "Maison des Associations, Place Colignon 1, 1030 Schaerbeek", "PUBLIC", daysAgo(110));
        Category gamesEntry = category(games, sophie, "Entrée", "5.00", 40);
        publish(games, sophie, daysAgo(108));
        sell(games, gamesEntry, pick(buyers, 28, thomas, julie, kevin), daysAgo(100), at(-76, 12, 0),
                List.of(Outcome.ATTENDED, Outcome.ATTENDED, Outcome.ATTENDED, Outcome.ATTENDED, Outcome.ATTENDED,
                        Outcome.ATTENDED, Outcome.PAID), 2, sophie);

        Event visit = event(club, sophie, "Visite guidée de la Maison Autrique",
                "Visite commentée de cette maison bourgeoise Art nouveau, première réalisation de Victor Horta, "
                        + "suivie d'un verre dans le quartier.",
                at(-30, 14, 0), "Chaussée de Haecht 266, 1030 Schaerbeek", "MEMBERS", daysAgo(60));
        Category visitTicket = category(visit, sophie, "Membre", "8.00", 25);
        publish(visit, sophie, daysAgo(59));
        sell(visit, visitTicket, pick(memberList, 18, julie), daysAgo(55), at(-31, 12, 0),
                List.of(Outcome.ATTENDED, Outcome.ATTENDED, Outcome.ATTENDED, Outcome.ATTENDED, Outcome.ATTENDED,
                        Outcome.ATTENDED, Outcome.ATTENDED, Outcome.PAID), 1, marc);

        Event concert = event(club, sophie, "Concert de gala : musiques de films",
                "L'orchestre amateur du Cercle interprète les grandes musiques de film, de John Williams à "
                        + "Ennio Morricone. Placement libre, ouverture des portes à 19 h 30.",
                at(38, 20, 0), "Salle des fêtes communale, Rue de la Ruche 30, 1030 Schaerbeek", "PUBLIC",
                daysAgo(30));
        Category presale = category(concert, sophie, "Prévente", "12.00", 80);
        Category student = category(concert, sophie, "Étudiant", "8.00", 20);
        publish(concert, sophie, daysAgo(29));
        List<Account> concertBuyers = pick(buyers, 34, julie);
        concertBuyers.remove(thomas); // he buys his ticket live, in the demo
        sell(concert, presale, concertBuyers.subList(0, 27), daysAgo(27), daysAgo(1), List.of(Outcome.PAID), 2, null);
        sell(concert, student, concertBuyers.subList(27, 33), daysAgo(20), daysAgo(2), List.of(Outcome.PAID), 0, null);

        Event watercolour = event(club, sophie, "Atelier aquarelle pour débutants",
                "Trois heures pour apprendre les bases de l'aquarelle avec une illustratrice du quartier. "
                        + "Matériel compris, aucune expérience requise.",
                at(18, 10, 0), "Local du Cercle, Avenue Louis Bertrand 35, 1030 Schaerbeek", "MEMBERS", daysAgo(21));
        Category workshop = category(watercolour, sophie, "Participation", "15.00", 12);
        publish(watercolour, sophie, daysAgo(20));
        sell(watercolour, workshop, pick(memberList, 11, julie), daysAgo(19), daysAgo(3), List.of(Outcome.PAID), 0,
                null);

        Event fleaMarket = event(club, sophie, "Brocante de quartier",
                "Brocante annuelle dans les allées du parc. Un emplacement de 3 mètres par réservation.",
                at(9, 8, 0), "Parc Josaphat (allée centrale), 1030 Schaerbeek", "PUBLIC", daysAgo(40));
        Category pitch = category(fleaMarket, sophie, "Emplacement", "10.00", 30);
        publish(fleaMarket, sophie, daysAgo(39));
        sell(fleaMarket, pitch, pick(buyers, 6), daysAgo(35), daysAgo(8),
                List.of(Outcome.PAID, Outcome.PAID, Outcome.PAID, Outcome.CANCELLED, Outcome.CANCELLED,
                        Outcome.REFUNDED), 0, null);
        jdbc.update("UPDATE events SET status = 'CANCELLED', updated_at = ? WHERE id = ?", ts(daysAgo(7)),
                fleaMarket.id());
        audit(sophie, club, "EVENT_CANCELLED", "Event", fleaMarket.id(), null, daysAgo(7));

        Event barbecue = event(club, sophie, "Barbecue d'été",
                "Le barbecue de fin de saison, ouvert aux membres et à leur famille.",
                at(230, 12, 0), "Jardin de la Maison des Associations, 1030 Schaerbeek", "MEMBERS", daysAgo(6));
        category(barbecue, sophie, "Adulte", "15.00", 60);
        category(barbecue, sophie, "Enfant (moins de 12 ans)", "7.00", 30);

        audit(marc, club, "ATTENDEES_EXPORTED", "Event", games.id(), Map.of("rows", 26), at(-74, 10, 0));
        audit(marc, club, "DUES_EXPORTED", "Asbl", club.id(), Map.of("rows", memberList.size(), "year", year),
                daysAgo(15));
        return club;
    }

    private void invitations(Club club, Account by) {
        invitation(club, by, "claire.hubert@" + DemoData.EMAIL_DOMAIN, daysAgo(5), null);
        invitation(club, by, "jonas.verhoeven@" + DemoData.EMAIL_DOMAIN, daysAgo(2), null);
        invitation(club, by, "laurent.mathieu@" + DemoData.EMAIL_DOMAIN, daysAgo(40), null); // expired, never used
        // A typo in the address: the mail server refused it.
        Instant typo = daysAgo(9);
        String wrong = "amandine.roux@demo.asbl.clbu";
        invitation(club, by, wrong, typo, null);
        jdbc.update("""
                INSERT INTO email_outbox (recipient, subject, status, attempts, next_attempt_at, last_error,
                created_at) VALUES (?, ?, 'FAILED', 5, ?, ?, ?)""",
                wrong, subject("email.invitation.subject", "fr", by.person().name(), club.association().denomination()),
                ts(typo.plus(Duration.ofHours(3))), "550 5.1.1 Recipient address rejected: domain not found",
                ts(typo));
    }

    // ---- The other associations -------------------------------------------------------------------------------

    private void otherAssociation(Association association, int number, List<Account> pool) {
        List<Account> members = pick(pool.subList(37, pool.size()), association.members());
        Account admin = members.getFirst();
        Club club = association(association, 4_200_000 + number * 37_911, admin, later(admin.createdAt(), daysAgo(200)));
        Map<Account, LocalDate> joined = new LinkedHashMap<>();
        joined.put(admin, date(club.createdAt()));
        for (int i = 1; i < members.size(); i++) {
            String role = association.acceptsPayments() && i == 1 ? "TREASURER" : "MEMBER";
            joinOnRequest(members.get(i), club, admin, role, i % 7 == 0 ? "SUPPORTER" : "FULL",
                    later(max(members.get(i).createdAt(), club.createdAt()), daysAgo(15)), joined);
        }
        if (association.annualFee() != null) {
            audit(admin, club, "DUES_FEE_CHANGED", "Asbl", club.id(), Map.of("annualFee", association.annualFee()),
                    club.createdAt().plus(Duration.ofDays(1)));
            int year = LocalDate.now(BRUSSELS).getYear();
            for (Account member : joined.keySet()) {
                if (random.nextInt(10) < 7) {
                    payDues(club, member, year, duesDate(joined.get(member), year), true);
                }
            }
        }
        List<Account> buyers = new ArrayList<>(joined.keySet());
        buyers.addAll(pool.subList(0, 20));
        switch (association.slug()) {
            case "rfc-evere" -> {
                Event tournament = event(club, admin, "Tournoi de mini-foot inter-quartiers",
                        "Équipes de cinq, matchs de dix minutes, finale en soirée. Inscription individuelle, "
                                + "les équipes sont formées sur place.",
                        at(-45, 10, 0), "Complexe sportif d'Evere, Rue Stroobants 51, 1140 Evere", "PUBLIC",
                        daysAgo(80));
                Category player = category(tournament, admin, "Joueur", "10.00", 60);
                publish(tournament, admin, daysAgo(79));
                sell(tournament, player, pick(buyers, 30), daysAgo(70), at(-46, 18, 0),
                        List.of(Outcome.ATTENDED, Outcome.ATTENDED, Outcome.ATTENDED, Outcome.ATTENDED, Outcome.PAID),
                        1, admin);
                Event dinner = event(club, admin, "Souper annuel du club",
                        "Le souper du club avec remise des trophées de la saison. Menu boulettes-frites ou "
                                + "végétarien, dessert compris.",
                        at(25, 19, 30), "Cafétéria du club, Rue Stroobants 51, 1140 Evere", "PUBLIC", daysAgo(26));
                Category adult = category(dinner, admin, "Adulte", "28.00", 100);
                Category child = category(dinner, admin, "Enfant (moins de 12 ans)", "14.00", 30);
                publish(dinner, admin, daysAgo(25));
                List<Account> diners = pick(buyers, 32);
                sell(dinner, adult, diners.subList(0, 25), daysAgo(24), daysAgo(1), List.of(Outcome.PAID), 1, null);
                sell(dinner, child, diners.subList(25, 32), daysAgo(24), daysAgo(1), List.of(Outcome.PAID), 0, null);
            }
            case "bbc-leuven-noord" -> {
                Event camp = event(club, admin, "Jeugdkamp krokusvakantie",
                        "Een dag basketbal voor jongeren van 10 tot 16 jaar, met training, wedstrijdjes en lunch.",
                        at(-120, 9, 0), "Sporthal Ridderstraat, 3000 Leuven", "MEMBERS", daysAgo(160));
                Category day = category(camp, admin, "Kampdag", "25.00", 30);
                publish(camp, admin, daysAgo(159));
                sell(camp, day, pick(new ArrayList<>(joined.keySet()), 12), daysAgo(150), at(-121, 12, 0),
                        List.of(Outcome.ATTENDED, Outcome.ATTENDED, Outcome.ATTENDED, Outcome.PAID), 0, admin);
                Event party = event(club, admin, "Clubfeest",
                        "Het jaarlijkse clubfeest met dj, bar en tombola ten voordele van de jeugdwerking.",
                        at(32, 20, 0), "Sporthal Ridderstraat, 3000 Leuven", "PUBLIC", daysAgo(18));
                Category entry = category(party, admin, "Toegang", "10.00", 120);
                publish(party, admin, daysAgo(17));
                sell(party, entry, pick(buyers, 24), daysAgo(16), daysAgo(1), List.of(Outcome.PAID), 1, null);
            }
            case "voix-du-midi" -> {
                Event christmas = event(club, admin, "Concert de Noël",
                        "Chants de Noël traditionnels et gospel, suivis d'un vin chaud offert.",
                        at(70, 18, 0), "Église Saint-Gilles, Parvis de Saint-Gilles, 1060 Saint-Gilles", "PUBLIC",
                        daysAgo(4));
                category(christmas, admin, "Entrée", "10.00", 150);
            }
            case "repair-cafe-ixelles" -> {
                Event repair = event(club, admin, "Repair Café du mois",
                        "Apportez vos objets en panne : des bénévoles vous aident à les réparer.",
                        at(12, 14, 0), "Rue Gray 101, 1050 Ixelles", "PUBLIC", daysAgo(9));
                category(repair, admin, "Participation aux frais", "2.00", 40);
            }
            case "toneelgroep-kameleon" -> {
                Event play = event(club, admin, "Première: De Vrek (Molière)",
                        "De nieuwe productie van de toneelgroep, in een eigentijdse vertaling.",
                        at(95, 20, 0), "Zaal Kerkstraat 14, 9000 Gent", "PUBLIC", daysAgo(3));
                category(play, admin, "Volwassene", "14.00", 90);
                category(play, admin, "Student", "9.00", 30);
            }
            case "brussels-book-club" -> {
                Event evening = event(club, admin, "Author evening: crime fiction in Brussels",
                        "A conversation with a Brussels crime writer, followed by a signing.",
                        at(45, 19, 0), "Rue du Trône 60, 1050 Ixelles", "MEMBERS", daysAgo(2));
                category(evening, admin, "Member", "6.00", 35);
            }
            default -> {
                // The community garden has no events yet.
            }
        }
    }

    // ---- Accounts ----------------------------------------------------------------------------------------------

    private Account account(Person person, Instant createdAt) {
        return account(person, person.email(), createdAt, false);
    }

    private Account account(Person person, String email, Instant createdAt, boolean superAdmin) {
        Instant verified = createdAt.plus(Duration.ofMinutes(4 + random.nextInt(50)));
        long id = insert("""
                INSERT INTO users (name, email, password, language, email_verified_at, created_at, updated_at,
                super_admin) VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                person.name(), email, passwordHash, person.language(), ts(verified), ts(createdAt), ts(verified),
                superAdmin);
        Account account = new Account(id, new Person(person.name(), email, person.language()), createdAt);
        ipOf.put(id, randomIp());
        // Signing up: the confirmation email, its one-time link (used), and the audit entry.
        jdbc.update("""
                INSERT INTO email_verification_tokens (user_id, token_hash, expires_at, used_at, created_at)
                VALUES (?, ?, ?, ?, ?)""",
                id, OneTimeTokens.hash(OneTimeTokens.newToken()), ts(createdAt.plus(Duration.ofHours(24))),
                ts(verified), ts(createdAt));
        sentEmail(email, subject("email.verifyEmail.subject", person.language()), createdAt);
        auditSecurity(account, "EMAIL_VERIFIED", null, verified);
        return account;
    }

    // Closing an account: anonymised and marked deleted, memberships removed; payments keep who paid (10-year
    // accounting retention, GDPR art. 17.3.b).
    private void closeAccount(Account account, Instant when) {
        jdbc.update("""
                UPDATE users SET name = 'Deleted account', email = ?, password = ?, deleted_at = ?, updated_at = ?
                WHERE id = ?""",
                "deleted-" + account.id() + "@deleted.asbl.club", passwordEncoder.encode(UUID.randomUUID().toString()),
                ts(when), ts(when), account.id());
        jdbc.update("DELETE FROM memberships WHERE user_id = ?", account.id());
        jdbc.update("""
                INSERT INTO audit_logs (user_id, action, entity_type, entity_id, ip, created_at)
                VALUES (?, 'ACCOUNT_DELETED', 'User', ?, ?, ?)""",
                account.id(), account.id(), ipOf.get(account.id()), ts(when));
    }

    // Logins (and a few failures), sessions, a password reset.
    private void securityHistory(Account sophie, Account marc, Account thomas, Account julie, List<Account> pool) {
        for (Account account : List.of(sophie, marc, thomas, julie)) {
            for (int day : new int[] {21, 9, 2}) {
                Instant when = daysAgo(day);
                auditLogin(account, "LOGIN_SUCCEEDED", account.person().email(), when);
                // The session of that login: rotated since, so revoked.
                jdbc.update("""
                        INSERT INTO refresh_tokens (user_id, token_hash, family_id, expires_at, created_at, revoked_at)
                        VALUES (?, ?, ?, ?, ?, ?)""",
                        account.id(), OneTimeTokens.hash(OneTimeTokens.newToken()), UUID.randomUUID(),
                        ts(when.plus(Duration.ofDays(30))), ts(when), ts(when.plus(Duration.ofMinutes(15))));
            }
        }
        auditLogin(thomas, "LOGIN_FAILED", thomas.person().email(), daysAgo(9).minus(Duration.ofMinutes(3)));
        auditLogin(null, "LOGIN_FAILED", "s.lambert@" + DemoData.EMAIL_DOMAIN, daysAgo(6));
        auditLogin(null, "LOGIN_FAILED", "s.lambert@" + DemoData.EMAIL_DOMAIN, daysAgo(6).plus(Duration.ofSeconds(40)));

        // Julie forgot her password two months ago and chose a new one; someone else asked and never used the link.
        Instant asked = daysAgo(62);
        jdbc.update("""
                INSERT INTO password_reset_tokens (user_id, token_hash, expires_at, used_at, created_at)
                VALUES (?, ?, ?, ?, ?)""",
                julie.id(), OneTimeTokens.hash(OneTimeTokens.newToken()), ts(asked.plus(Duration.ofMinutes(30))),
                ts(asked.plus(Duration.ofMinutes(6))), ts(asked));
        sentEmail(julie.person().email(), subject("email.passwordReset.subject", julie.person().language()), asked);
        auditSecurity(julie, "PASSWORD_RESET_REQUESTED", null, asked);
        auditSecurity(julie, "PASSWORD_RESET", Map.of("sessionsEnded", true), asked.plus(Duration.ofMinutes(6)));
        Account forgetful = pool.get(50);
        Instant unused = daysAgo(11);
        jdbc.update("""
                INSERT INTO password_reset_tokens (user_id, token_hash, expires_at, created_at) VALUES (?, ?, ?, ?)""",
                forgetful.id(), OneTimeTokens.hash(OneTimeTokens.newToken()), ts(unused.plus(Duration.ofMinutes(30))),
                ts(unused));
        sentEmail(forgetful.person().email(),
                subject("email.passwordReset.subject", forgetful.person().language()), unused);
        auditSecurity(forgetful, "PASSWORD_RESET_REQUESTED", null, unused);
    }

    // ---- Associations and members ------------------------------------------------------------------------------

    private Club association(Association association, long enterpriseDigits, Account founder, Instant createdAt) {
        String bce = DemoData.enterpriseNumber(enterpriseDigits);
        long id = insert("""
                INSERT INTO asbls (denomination, bce_number, address, slug, default_language, stripe_account_id,
                annual_fee, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                association.denomination(), bce, association.address(), association.slug(), association.language(),
                association.acceptsPayments() ? DEMO_STRIPE_ACCOUNT : null, association.annualFee(), ts(createdAt),
                ts(createdAt));
        Club club = new Club(id, association, createdAt);
        membership(founder, club, "ADMIN", "FULL", "ACTIVE", createdAt);
        audit(founder, club, "ASBL_CREATED", "Asbl", id,
                Map.of("denomination", association.denomination(), "bceNumber", bce, "slug", association.slug()),
                createdAt);
        return club;
    }

    private void joinOnRequest(Account member, Club club, Account admin, String role, String category,
            Instant requested, Map<Account, LocalDate> joined) {
        Instant approved = awake(requested.plus(Duration.ofHours(2 + random.nextInt(40))));
        membership(member, club, role, category, "ACTIVE", approved);
        audit(member, club, "JOIN_REQUESTED", "User", member.id(), null, requested);
        audit(admin, club, "JOIN_APPROVED", "User", member.id(), null, approved);
        joined.put(member, date(approved));
    }

    private void joinByInvitation(Account member, Club club, Account admin, String category, Instant invited,
            Map<Account, LocalDate> joined) {
        Instant accepted = awake(invited.plus(Duration.ofHours(5 + random.nextInt(60))));
        invitation(club, admin, member.person().email(), invited, accepted);
        membership(member, club, "MEMBER", category, "ACTIVE", accepted);
        audit(member, club, "MEMBER_JOINED_BY_INVITATION", "User", member.id(), Map.of("invitedBy", admin.id()),
                accepted);
        joined.put(member, date(accepted));
    }

    private void pendingRequest(Account member, Club club, Instant requested) {
        jdbc.update("""
                INSERT INTO memberships (user_id, asbl_id, role, category, status, created_at, updated_at)
                VALUES (?, ?, 'MEMBER', 'FULL', 'PENDING', ?, ?)""",
                member.id(), club.id(), ts(requested), ts(requested));
        audit(member, club, "JOIN_REQUESTED", "User", member.id(), null, requested);
    }

    private void membership(Account member, Club club, String role, String category, String status, Instant since) {
        jdbc.update("""
                INSERT INTO memberships (user_id, asbl_id, role, category, status, joined_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                member.id(), club.id(), role, category, status, date(since), ts(since), ts(since));
    }

    private void invitation(Club club, Account by, String email, Instant sent, Instant accepted) {
        jdbc.update("""
                INSERT INTO invitations (asbl_id, email, token_hash, invited_by, created_at, expires_at, accepted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
                club.id(), email, OneTimeTokens.hash(OneTimeTokens.newToken()), by.id(), ts(sent),
                ts(sent.plus(Duration.ofDays(14))), accepted == null ? null : ts(accepted));
        if (!email.endsWith(".clbu")) {
            sentEmail(email, subject("email.invitation.subject", club.association().language(), by.person().name(),
                    club.association().denomination()), sent);
        }
        audit(by, club, "MEMBER_INVITED", "Asbl", club.id(), Map.of("email", email), sent);
    }

    // ---- Events and tickets ------------------------------------------------------------------------------------

    private Event event(Club club, Account by, String title, String description, Instant startsAt, String location,
            String visibility, Instant createdAt) {
        long id = insert("""
                INSERT INTO events (asbl_id, title, description, starts_at, location, visibility, status, created_at,
                updated_at) VALUES (?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?)""",
                club.id(), title, description, ts(startsAt), location, visibility, ts(createdAt), ts(createdAt));
        audit(by, club, "EVENT_CREATED", "Event", id, Map.of("title", title), createdAt);
        return new Event(id, club, title, startsAt, createdAt);
    }

    // Ticket categories are added right after the event is created.
    private Category category(Event event, Account by, String label, String price, int seats) {
        BigDecimal amount = new BigDecimal(price);
        long id = insert("INSERT INTO ticket_categories (event_id, label, price, total_seats) VALUES (?, ?, ?, ?)",
                event.id(), label, amount, seats);
        audit(by, event.club(), "TICKET_ADDED", "Event", event.id(),
                Map.of("label", label, "price", amount, "seats", seats),
                event.createdAt().plus(Duration.ofMinutes(2 + random.nextInt(8))));
        return new Category(id, label, amount);
    }

    private void publish(Event event, Account by, Instant when) {
        jdbc.update("UPDATE events SET status = 'PUBLISHED', updated_at = ? WHERE id = ?", ts(when), event.id());
        audit(by, event.club(), "EVENT_PUBLISHED", "Event", event.id(), null, when);
    }

    /**
     * Bookings for one ticket category, spread between two dates. Outcomes cycle through the pattern given (e.g.
     * mostly scanned at the door, some paid but absent); then {@code expired} more bookings that were never paid
     * (the first after a declined card). Scans are made by {@code doorkeeper}, at the event.
     */
    private void sell(Event event, Category category, List<Account> buyers, Instant from, Instant to,
            List<Outcome> pattern, int expired, Account doorkeeper) {
        for (int i = 0; i < buyers.size(); i++) {
            booking(event, category, buyers.get(i), bookedBetween(from, to), pattern.get(i % pattern.size()),
                    doorkeeper);
        }
        for (int i = 0; i < expired; i++) {
            Account buyer = buyers.get((i * 5 + 3) % buyers.size());
            Outcome outcome = i == 0 ? Outcome.DECLINED_THEN_EXPIRED : Outcome.EXPIRED;
            booking(event, category, buyer, bookedBetween(from, to), outcome, null);
        }
    }

    // Moving a night-time booking to the day may pass the end of the sales: it stays before it.
    private Instant bookedBetween(Instant from, Instant to) {
        Instant booked = between(from, to);
        return booked.isAfter(to) ? to.minus(Duration.ofMinutes(5 + random.nextInt(120))) : booked;
    }

    private void booking(Event event, Category category, Account buyer, Instant booked, Outcome outcome,
            Account doorkeeper) {
        Club club = event.club();
        long payable = insert("INSERT INTO payables (type, amount, currency) VALUES ('REGISTRATION', ?, 'EUR')",
                category.price());
        boolean paid = outcome == Outcome.PAID || outcome == Outcome.ATTENDED;
        Instant paidAt = booked.plus(Duration.ofSeconds(40 + random.nextInt(200)));
        // Scanned at the door, from a quarter of an hour before the start.
        Instant checkin = outcome == Outcome.ATTENDED
                ? event.startsAt().minus(Duration.ofMinutes(15)).plus(Duration.ofMinutes(random.nextInt(50)))
                : null;
        String status = switch (outcome) {
            case PAID -> "PAID";
            case ATTENDED -> "ATTENDED";
            case EXPIRED, DECLINED_THEN_EXPIRED -> "EXPIRED";
            case CANCELLED -> "CANCELLED";
            case REFUNDED -> "REFUNDED";
        };
        jdbc.update("""
                INSERT INTO registrations (id, event_id, ticket_category_id, user_id, status, qr_token, registered_at,
                checkin_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                payable, event.id(), category.id(), buyer.id(), status,
                paid ? UUID.randomUUID().toString().replace("-", "") : null, ts(booked),
                checkin == null ? null : ts(checkin));
        audit(buyer, club, "BOOKING_CREATED", "Registration", payable,
                Map.of("event", event.id(), "ticket", category.label()), booked);

        String paymentStatus = switch (outcome) {
            case PAID, ATTENDED -> "SUCCEEDED";
            case DECLINED_THEN_EXPIRED -> "FAILED";
            case EXPIRED, CANCELLED -> "INITIATED";
            case REFUNDED -> "REFUNDED";
        };
        long payment = payment(club, buyer, payable, category.price(), paymentStatus,
                paid || outcome == Outcome.REFUNDED ? paidAt : null);
        String intent = jdbc.queryForObject("SELECT stripe_payment_intent_id FROM payments WHERE id = ?",
                String.class, payment);

        switch (outcome) {
            case PAID, ATTENDED -> {
                auditSystem(club, "PAYMENT_SUCCEEDED", "Payment", payment,
                        Map.of("paymentIntentId", intent, "amount", category.price()), paidAt);
                webhook("payment_intent.succeeded", paidAt);
                sentEmail(buyer.person().email(),
                        subject("email.ticketReady.subject", buyer.person().language(), buyer.person().name(),
                                event.title()), paidAt);
                if (checkin != null && doorkeeper != null) {
                    audit(doorkeeper, club, "TICKET_CHECKED_IN", "Registration", payable,
                            Map.of("ticket", category.label()), checkin);
                }
            }
            case DECLINED_THEN_EXPIRED, EXPIRED -> {
                if (outcome == Outcome.DECLINED_THEN_EXPIRED) {
                    auditSystem(club, "PAYMENT_FAILED", "Payment", payment, Map.of("paymentIntentId", intent),
                            paidAt);
                    webhook("payment_intent.payment_failed", paidAt);
                }
                auditSystem(club, "BOOKING_EXPIRED", "Registration", payable,
                        Map.of("event", event.id(), "ticket", category.label()), booked.plus(Duration.ofMinutes(31)));
            }
            case REFUNDED -> {
                // Paid while the event was being cancelled: refunded at once, commission included.
                auditSystem(club, "PAYMENT_REFUNDED", "Payment", payment,
                        Map.of("paymentIntentId", intent, "amount", category.price(), "booking", "CANCELLED"),
                        paidAt);
                webhook("payment_intent.succeeded", paidAt);
                sentEmail(buyer.person().email(), subject("email.refundEventCancelled.subject",
                        buyer.person().language(), buyer.person().name(), event.title()), paidAt);
            }
            case CANCELLED -> {
                // Not paid when the event was cancelled: the booking is cancelled with it.
            }
        }
    }

    // ---- Dues and payments -------------------------------------------------------------------------------------

    private void payDues(Club club, Account member, int year, Instant when, boolean succeeded) {
        BigDecimal fee = club.association().annualFee();
        long payable = insert("INSERT INTO payables (type, amount, currency) VALUES ('MEMBERSHIP', ?, 'EUR')", fee);
        jdbc.update("INSERT INTO dues (id, asbl_id, user_id, year) VALUES (?, ?, ?, ?)", payable, club.id(),
                member.id(), year);
        long payment = payment(club, member, payable, fee, succeeded ? "SUCCEEDED" : "INITIATED",
                succeeded ? when : null);
        if (succeeded) {
            String intent = jdbc.queryForObject("SELECT stripe_payment_intent_id FROM payments WHERE id = ?",
                    String.class, payment);
            auditSystem(club, "PAYMENT_SUCCEEDED", "Payment", payment, Map.of("paymentIntentId", intent, "amount", fee),
                    when);
            webhook("payment_intent.succeeded", when);
            sentEmail(member.person().email(), subject("email.duesPaid.subject", member.person().language(),
                    member.person().name(), String.valueOf(year), club.association().denomination()), when);
        }
    }

    private long payment(Club club, Account payer, long payable, BigDecimal amount, String status, Instant paidAt) {
        BigDecimal commission = amount.multiply(COMMISSION_RATE).add(COMMISSION_FIXED)
                .setScale(2, RoundingMode.HALF_UP);
        return insert("""
                INSERT INTO payments (asbl_id, user_id, payer_name, payer_email, payable_id, stripe_payment_intent_id,
                idempotency_key, amount, commission, status, paid_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                club.id(), payer.id(), payer.person().name(), payer.person().email(), payable,
                "pi_3" + stripeId(20), "payable-" + payable, amount, commission, status,
                paidAt == null ? null : ts(paidAt));
    }

    // Stripe's messages are kept 30 days (to recognise a repeat), then pruned: only recent ones remain.
    private void webhook(String type, Instant received) {
        if (received.isAfter(now.minus(Duration.ofDays(30)))) {
            jdbc.update("INSERT INTO processed_webhook_events (event_id, type, received_at) VALUES (?, ?, ?)",
                    "evt_3" + stripeId(20), type, ts(received.plus(Duration.ofSeconds(2))));
        }
    }

    // ---- Emails and audit --------------------------------------------------------------------------------------

    // A sent email: the body is cleared once sent (it may hold a one-time link or a ticket code), the row stays.
    private void sentEmail(String recipient, String subject, Instant created) {
        jdbc.update("""
                INSERT INTO email_outbox (recipient, subject, status, attempts, next_attempt_at, created_at, sent_at)
                VALUES (?, ?, 'SENT', 1, ?, ?, ?)""",
                recipient, subject, ts(created), ts(created), ts(created.plus(Duration.ofSeconds(5 + random.nextInt(20)))));
    }

    // The subject as the application writes it, in the recipient's language, with the same arguments as the real
    // email (some subjects only use the later ones).
    private String subject(String key, String language, Object... args) {
        return messages.getMessage(key, args, Locale.forLanguageTag(language));
    }

    private void audit(Account actor, Club club, String action, String entityType, Long entityId,
            Map<String, Object> payload, Instant when) {
        jdbc.update("""
                INSERT INTO audit_logs (user_id, asbl_id, action, entity_type, entity_id, payload, ip, created_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)""",
                actor == null ? null : actor.id(), club == null ? null : club.id(), action, entityType, entityId,
                toJson(payload), actor == null ? null : ipOf.get(actor.id()), ts(when));
    }

    // Done by the application itself (a Stripe message, the expiry job): no actor, no IP.
    private void auditSystem(Club club, String action, String entityType, Long entityId, Map<String, Object> payload,
            Instant when) {
        jdbc.update("""
                INSERT INTO audit_logs (asbl_id, action, entity_type, entity_id, payload, created_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?)""",
                club.id(), action, entityType, entityId, toJson(payload), ts(when));
    }

    private void auditSecurity(Account account, String action, Map<String, Object> payload, Instant when) {
        audit(account, null, action, null, null, payload, when);
    }

    private void auditLogin(Account account, String action, String email, Instant when) {
        String ip = account == null ? "203.0.113.77" : ipOf.get(account.id());
        jdbc.update("""
                INSERT INTO audit_logs (user_id, action, payload, ip, created_at) VALUES (?, ?, ?::jsonb, ?, ?)""",
                account == null ? null : account.id(), action, toJson(Map.of("email", email)), ip, ts(when));
    }

    // ---- Small helpers -----------------------------------------------------------------------------------------

    private long insert(String sql, Object... args) {
        Long id = jdbc.queryForObject(sql + " RETURNING id", Long.class, args);
        return id == null ? 0 : id;
    }

    private String toJson(Map<String, Object> payload) {
        return payload == null ? null : json.writeValueAsString(payload);
    }

    // Some moment that day, between 9:00 and 21:00 (never in the future).
    private Instant daysAgo(int days) {
        Instant moment = LocalDate.now(BRUSSELS).minusDays(days).atTime(9, 0).atZone(BRUSSELS).toInstant()
                .plus(Duration.ofMinutes(random.nextInt(12 * 60)));
        return moment.isBefore(now) ? moment : now.minus(Duration.ofMinutes(30));
    }

    // A day relative to today (negative = past), at a local Brussels time.
    private Instant at(int days, int hour, int minute) {
        return LocalDate.now(BRUSSELS).plusDays(days).atTime(hour, minute).atZone(BRUSSELS).toInstant();
    }

    private Instant between(Instant from, Instant to) {
        long span = Math.max(1, to.getEpochSecond() - from.getEpochSecond());
        return awake(from.plusSeconds((long) (random.nextDouble() * span)));
    }

    // People act during the day: a time at night moves forward to some time that day or the next (never into the
    // future).
    private Instant awake(Instant instant) {
        var local = instant.atZone(BRUSSELS);
        if (local.getHour() >= 8 && local.getHour() < 22) {
            return instant;
        }
        LocalDate day = local.getHour() < 8 ? local.toLocalDate() : local.toLocalDate().plusDays(1);
        Instant daytime = day.atTime(9, 0).atZone(BRUSSELS).toInstant().plus(Duration.ofMinutes(random.nextInt(12 * 60)));
        return daytime.isBefore(now.minus(Duration.ofHours(1))) ? daytime : instant;
    }

    // Some time after a starting point, but before a limit (and never in the future).
    private Instant later(Instant start, Instant limit) {
        Instant end = limit.isBefore(now) ? limit : now;
        return start.isBefore(end) ? between(start, end) : start;
    }

    // When a member pays a year's dues: a few days after joining, or early in the year; never in the future.
    private Instant duesDate(LocalDate joined, int year) {
        LocalDate earliest = joined.getYear() == year ? joined.plusDays(1) : LocalDate.of(year, 1, 5);
        LocalDate latest = earliest.plusDays(30 + random.nextInt(60));
        Instant start = earliest.atTime(9, 0).atZone(BRUSSELS).toInstant();
        Instant end = latest.atTime(21, 0).atZone(BRUSSELS).toInstant();
        Instant limit = now.minus(Duration.ofHours(6));
        if (start.isAfter(limit)) {
            return limit;
        }
        return between(start, end.isBefore(limit) ? end : limit);
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant startOf(LocalDate day) {
        return day.atTime(20, 0).atZone(BRUSSELS).toInstant();
    }

    private static LocalDate date(Instant instant) {
        return instant.atZone(BRUSSELS).toLocalDate();
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    // A few people, always included, then random others; no duplicates.
    private List<Account> pick(List<Account> from, int count, Account... always) {
        List<Account> chosen = new ArrayList<>(List.of(always));
        List<Account> shuffled = new ArrayList<>(from);
        Collections.shuffle(shuffled, random);
        for (Account account : shuffled) {
            if (chosen.size() >= count) {
                break;
            }
            if (!chosen.contains(account)) {
                chosen.add(account);
            }
        }
        return chosen;
    }

    // Addresses from the ranges reserved for documentation (RFC 5737): they look real but belong to no one.
    private String randomIp() {
        String[] ranges = {"192.0.2.", "198.51.100.", "203.0.113."};
        return ranges[random.nextInt(ranges.length)] + (2 + random.nextInt(250));
    }

    private String stripeId(int length) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < length; i++) {
            id.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return id.toString();
    }
}
