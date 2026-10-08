package club.asbl.asbl_club.demo;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * The raw material of the demo data: Belgian first and last names, associations, and valid enterprise numbers.
 * Kept apart from {@link DemoDataSeeder}, which only decides what happens to whom and when.
 */
final class DemoData {

    static final String EMAIL_DOMAIN = "demo.asbl.club";

    private DemoData() {
    }

    // First names by language, so a Dutch-speaking person gets a Dutch first name and Dutch emails.
    private static final List<String> FRENCH_FIRST_NAMES = List.of(
            "Antoine", "Camille", "Lucas", "Manon", "Hugo", "Chloé", "Nathan", "Léa", "Maxime", "Sarah", "Julien",
            "Élise", "Romain", "Inès", "Olivier", "Charlotte", "Mehdi", "Amélie", "Pierre", "Laura", "Yasmine",
            "Alexandre", "Céline", "Benoît", "Aurélie", "Samir", "Gaëlle", "François", "Isabelle", "Karim");
    private static final List<String> DUTCH_FIRST_NAMES = List.of(
            "Pieter", "Lotte", "Jens", "Sanne", "Wouter", "Eline", "Bram", "Fien", "Thijs", "Lies", "Joris", "Hanne",
            "Stijn", "Nele", "Ruben", "Femke", "Koen", "Ilse", "Dries", "Lien");
    private static final List<String> ENGLISH_FIRST_NAMES = List.of(
            "Emma", "Daniel", "Anna", "Sofia", "Marco", "Elena", "James", "Olivia");
    private static final List<String> LAST_NAMES = List.of(
            "Dubois", "Lambert", "Peeters", "Janssens", "Maes", "Jacobs", "Mertens", "Willems", "Claes", "Goossens",
            "Wouters", "De Smet", "Dupont", "Martin", "Lejeune", "Leroy", "Renard", "Simon", "Laurent", "Lemaire",
            "Hermans", "Vermeulen", "Van den Broeck", "Michiels", "Leclercq", "Fontaine", "Collard", "Denis",
            "El Amrani", "Benali", "Yilmaz", "Kowalski", "Rossi", "Nguyen", "Diallo", "Haddad", "Bertrand", "Gérard",
            "Pirard", "Delvaux");

    record Person(String name, String email, String language) {
    }

    // Dues need online payments (the application refuses a fee without a Stripe account), so only associations that
    // accept payments have an annual fee.
    record Association(String denomination, String slug, String address, String language, BigDecimal annualFee,
            boolean acceptsPayments, int members) {
    }

    // The association the demo is given with, then smaller ones so the platform isn't a single club.
    static final Association MAIN = new Association("Cercle Culturel Josaphat", "cercle-josaphat",
            "Avenue Louis Bertrand 35, 1030 Schaerbeek", "fr", new BigDecimal("30.00"), true, 0);

    static final List<Association> OTHERS = List.of(
            new Association("Royal Football Club Evere", "rfc-evere", "Rue Stroobants 51, 1140 Evere", "fr",
                    new BigDecimal("120.00"), true, 18),
            new Association("Basketbalclub Leuven Noord", "bbc-leuven-noord", "Ridderstraat 112, 3000 Leuven", "nl",
                    new BigDecimal("85.00"), true, 16),
            new Association("Chorale Les Voix du Midi", "voix-du-midi", "Rue de la Victoire 96, 1060 Saint-Gilles",
                    "fr", null, false, 12),
            new Association("Repair Café Ixelles", "repair-cafe-ixelles", "Rue Gray 101, 1050 Ixelles", "fr",
                    null, false, 9),
            new Association("Toneelgroep De Kameleon", "toneelgroep-kameleon", "Kerkstraat 14, 9000 Gent", "nl",
                    null, false, 11),
            new Association("Brussels International Book Club", "brussels-book-club",
                    "Rue du Trône 60, 1050 Ixelles", "en", null, false, 9),
            new Association("Potager Collectif de Forest", "potager-forest", "Avenue du Globe 73, 1190 Forest", "fr",
                    null, false, 8));

    /**
     * A pool of distinct people, the same on every run (fixed seed): roughly two thirds French-speaking, a quarter
     * Dutch-speaking, the rest English-speaking, as on a Brussels platform.
     */
    static List<Person> people(int count, Set<String> takenNames, long seed) {
        Random random = new Random(seed);
        List<Person> people = new ArrayList<>();
        Set<String> names = new HashSet<>(takenNames);
        while (people.size() < count) {
            int draw = random.nextInt(100);
            String language = draw < 65 ? "fr" : draw < 90 ? "nl" : "en";
            List<String> firstNames = switch (language) {
                case "nl" -> DUTCH_FIRST_NAMES;
                case "en" -> ENGLISH_FIRST_NAMES;
                default -> FRENCH_FIRST_NAMES;
            };
            String name = firstNames.get(random.nextInt(firstNames.size())) + " "
                    + LAST_NAMES.get(random.nextInt(LAST_NAMES.size()));
            if (names.add(name)) {
                people.add(person(name, language));
            }
        }
        return people;
    }

    static Person person(String name, String language) {
        return new Person(name, emailOf(name), language);
    }

    // "Élise Van den Broeck" -> elise.vandenbroeck@demo.asbl.club
    static String emailOf(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String[] parts = ascii.toLowerCase(Locale.ROOT).split(" ", 2);
        return parts[0] + "." + parts[1].replaceAll("[^a-z]", "") + "@" + EMAIL_DOMAIN;
    }

    /**
     * A valid Belgian enterprise number (BCE/KBO) built from eight digits: the last two digits are the check
     * number, 97 minus the first eight modulo 97. Formatted as the application expects, 0123.456.789.
     */
    static String enterpriseNumber(long firstEightDigits) {
        long check = 97 - firstEightDigits % 97;
        String digits = String.format("%08d%02d", firstEightDigits, check);
        return digits.substring(0, 4) + "." + digits.substring(4, 7) + "." + digits.substring(7);
    }
}
