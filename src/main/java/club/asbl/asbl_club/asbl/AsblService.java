package club.asbl.asbl_club.asbl;

import club.asbl.asbl_club.user.User;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AsblService {

    private final AsblRepository asblRepository;
    private final MembershipRepository membershipRepository;

    AsblService(AsblRepository asblRepository, MembershipRepository membershipRepository) {
        this.asblRepository = asblRepository;
        this.membershipRepository = membershipRepository;
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

        Membership membership = new Membership();
        membership.setUser(creator);
        membership.setAsbl(asbl);
        membership.setRole("ADMIN");
        membership.setCategory("FULL");
        membership.setStatus("ACTIVE");
        membership.setJoinedAt(LocalDate.now());
        membershipRepository.save(membership);

        return asbl;
    }
}
