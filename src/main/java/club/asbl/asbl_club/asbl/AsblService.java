package club.asbl.asbl_club.asbl;

import club.asbl.asbl_club.audit.AuditService;
import club.asbl.asbl_club.membership.MembershipService;
import club.asbl.asbl_club.user.User;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AsblService {

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
        asbl.setStatus("PENDING");
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
}
