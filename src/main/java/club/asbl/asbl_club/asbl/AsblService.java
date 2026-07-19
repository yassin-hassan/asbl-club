package club.asbl.asbl_club.asbl;

import club.asbl.asbl_club.membership.MembershipService;
import club.asbl.asbl_club.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AsblService {

    private final AsblRepository asblRepository;
    private final MembershipService membershipService;

    AsblService(AsblRepository asblRepository, MembershipService membershipService) {
        this.asblRepository = asblRepository;
        this.membershipService = membershipService;
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
        return asbl;
    }
}
