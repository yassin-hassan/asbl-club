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
        return userRepository.save(user);
    }

    @Transactional
    public User registerSuperAdmin(String name, String email, String rawPassword) {
        User user = register(name, email, rawPassword);
        user.setSuperAdmin(true);
        return userRepository.save(user);
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
