package club.asbl.asbl_club.user;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;

    UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
    }

    // The logged-in user, however they logged in: server session (name = email) or API access token
    // (name = the public ID in the token's "sub").
    @Transactional(readOnly = true)
    public User getAuthenticated(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return userRepository.findByPublicId(UUID.fromString(jwt.getName()))
                    .orElseThrow(() -> new IllegalStateException("No user for this access token"));
        }
        return getByEmail(authentication.getName());
    }

    @Transactional(readOnly = true)
    public User getByEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalStateException("No user found for email " + email));
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT));
    }

    // What a sign-up led to. alreadyVerified: the address already belongs to an account in use (the form still says
    // "check your inbox"; the email then says "you already have an account").
    public record SignUp(User user, boolean alreadyVerified) {
    }

    // The public sign-up: creates an account that can't log in until its email is confirmed. The password is hashed
    // in every case, so the answer takes as long whether or not the address is taken (no timing hint). An address
    // with an unconfirmed account is simply signed up again (new name and password): until someone proves they own
    // the inbox, the account is nobody's.
    @Transactional
    public SignUp signUp(String name, String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String passwordHash = passwordEncoder.encode(rawPassword);
        Optional<User> existing = userRepository.findByEmail(normalizedEmail);
        if (existing.isPresent() && existing.get().getEmailVerifiedAt() != null) {
            return new SignUp(existing.get(), true);
        }
        User user = existing.orElseGet(User::new);
        user.setName(name.trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordHash);
        user.setLanguage("fr");
        return new SignUp(userRepository.save(user), false);
    }

    @Transactional
    public void markEmailVerified(User user) {
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);
    }

    // An account whose email counts as confirmed from the start: demo data, and the tests' own accounts. People
    // signing up on the site go through signUp.
    @Transactional
    public User register(String name, String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyUsedException(normalizedEmail);
        }
        User user = new User();
        user.setName(name.trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setLanguage("fr");
        user.setEmailVerifiedAt(Instant.now());
        return userRepository.save(user);
    }

    @Transactional
    public User registerSuperAdmin(String name, String email, String rawPassword) {
        User user = register(name, email, rawPassword);
        user.setSuperAdmin(true);
        return userRepository.save(user);
    }

    // The name shown to association administrators and in emails; the email address and identity don't change.
    @Transactional
    public void changeName(User user, String name) {
        user.setName(name.trim());
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(User user, String rawPassword) {
        user.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }

    @Transactional
    public void anonymizeAndClose(User user) {
        user.setName("Deleted account");
        user.setEmail("deleted-" + user.getId() + "@deleted.asbl.club");
        user.setPhotoPath(null);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
        events.publishEvent(new AccountClosed(user.getId()));
    }
}
