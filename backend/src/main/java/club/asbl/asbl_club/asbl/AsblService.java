package club.asbl.asbl_club.asbl;

import club.asbl.asbl_club.audit.AuditService;
import club.asbl.asbl_club.membership.MembershipService;
import club.asbl.asbl_club.user.User;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AsblService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AsblRepository asblRepository;
    private final MembershipService membershipService;
    private final AuditService auditService;

    AsblService(AsblRepository asblRepository, MembershipService membershipService, AuditService auditService) {
        this.asblRepository = asblRepository;
        this.membershipService = membershipService;
        this.auditService = auditService;
    }

    @Transactional
    public Asbl createAsbl(User creator, String denomination, String bceNumber, String slug, String defaultLanguage) {
        if (asblRepository.existsBySlug(slug)) {
            throw new SlugAlreadyUsedException(slug);
        }
        if (asblRepository.existsByBceNumber(bceNumber)) {
            throw new BceAlreadyUsedException(bceNumber);
        }

        Asbl asbl = new Asbl();
        asbl.setDenomination(denomination);
        asbl.setBceNumber(bceNumber);
        asbl.setSlug(slug);
        asbl.setDefaultLanguage(defaultLanguage);
        asbl.setStatus(AsblStatus.PENDING);
        asblRepository.save(asbl);

        membershipService.createFoundingAdmin(asbl, creator);

        auditService.record("ASBL_CREATED", asbl, "Asbl", asbl.getId(),
                Map.of("denomination", denomination, "bceNumber", bceNumber, "slug", slug));
        return asbl;
    }

    @Transactional
    public void linkStripeAccount(Asbl asbl, String stripeAccountId) {
        asbl.setStripeAccountId(stripeAccountId);
        asblRepository.save(asbl);
    }

    @Transactional(readOnly = true)
    public Optional<Asbl> findBySlug(String slug) {
        return asblRepository.findBySlug(slug);
    }

    @Transactional(readOnly = true)
    public long count() {
        return asblRepository.count();
    }

    // A new join link for the association: 256 random bits, so it can't be guessed. Replaces the current one, which
    // stops working at once. The token itself is never written to the audit log.
    @Transactional
    public String newJoinLink(Asbl asbl) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        asbl.setJoinToken(token);
        asblRepository.save(asbl);
        auditService.record("JOIN_LINK_CREATED", asbl);
        return token;
    }

    @Transactional
    public void disableJoinLink(Asbl asbl) {
        asbl.setJoinToken(null);
        asblRepository.save(asbl);
        auditService.record("JOIN_LINK_DISABLED", asbl);
    }

    @Transactional(readOnly = true)
    public Optional<Asbl> findByJoinToken(String token) {
        return asblRepository.findByJoinToken(token);
    }
}
