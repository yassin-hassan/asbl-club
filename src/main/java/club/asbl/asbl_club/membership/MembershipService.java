package club.asbl.asbl_club.membership;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblSummary;
import club.asbl.asbl_club.user.User;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {

    private final MembershipRepository membershipRepository;

    MembershipService(MembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    @Transactional
    public void createFoundingAdmin(Asbl asbl, User creator) {
        Membership membership = new Membership();
        membership.setUser(creator);
        membership.setAsbl(asbl);
        membership.setRole("ADMIN");
        membership.setCategory("FULL");
        membership.setStatus("ACTIVE");
        membership.setJoinedAt(LocalDate.now());
        membershipRepository.save(membership);
    }

    @Transactional(readOnly = true)
    public List<AsblSummary> membershipsOf(User user) {
        return membershipRepository.findByUser(user).stream()
                .map(m -> new AsblSummary(m.getAsbl().getId(), m.getAsbl().getDenomination(),
                        m.getAsbl().getSlug(), m.getRole()))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isMember(User user, Asbl asbl) {
        return membershipRepository.existsByUserAndAsbl(user, asbl);
    }

    @Transactional(readOnly = true)
    public List<MemberView> membersOf(Asbl asbl) {
        return membershipRepository.findByAsbl(asbl).stream()
                .map(m -> new MemberView(m.getUser().getName(), m.getUser().getEmail(), m.getRole(), m.getStatus()))
                .toList();
    }
}
