package club.asbl.asbl_club.membership;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblSummary;
import club.asbl.asbl_club.audit.AuditService;
import club.asbl.asbl_club.user.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {

    private final MembershipRepository membershipRepository;
    private final AuditService auditService;

    MembershipService(MembershipRepository membershipRepository, AuditService auditService) {
        this.membershipRepository = membershipRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void createFoundingAdmin(Asbl asbl, User creator) {
        Membership membership = new Membership();
        membership.setUser(creator);
        membership.setAsbl(asbl);
        membership.setRole(MembershipRole.ADMIN);
        membership.setCategory(MembershipCategory.FULL);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setJoinedAt(LocalDate.now());
        membershipRepository.save(membership);
    }

    // Every association the user is linked to, whatever the status: the dashboard shows pending requests too.
    @Transactional(readOnly = true)
    public List<AsblSummary> membershipsOf(User user) {
        return membershipRepository.findByUser(user).stream()
                .map(m -> new AsblSummary(m.getAsbl().getId(), m.getAsbl().getDenomination(),
                        m.getAsbl().getSlug(), m.getRole().name(), m.getStatus().name()))
                .toList();
    }

    // The user's role in this association, or empty when they aren't an ACTIVE member. Every access decision goes
    // through here or isAdmin: a pending request, an excluded member or someone who left gets no access.
    @Transactional(readOnly = true)
    public Optional<String> roleOf(User user, Asbl asbl) {
        return membershipRepository.findByUserAndAsbl(user, asbl)
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .map(m -> m.getRole().name());
    }

    @Transactional(readOnly = true)
    public boolean isAdmin(User user, Asbl asbl) {
        return membershipRepository.existsByUserAndAsblAndRoleAndStatus(user, asbl, MembershipRole.ADMIN,
                MembershipStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<MemberView> membersOf(Asbl asbl) {
        return membershipRepository.findByAsbl(asbl).stream()
                .map(m -> new MemberView(m.getUser().getPublicId(), m.getUser().getName(), m.getUser().getEmail(),
                        m.getRole().name(), m.getStatus().name()))
                .toList();
    }

    // Through the association's join link. Creates a PENDING request (an administrator approves it); asking again
    // changes nothing. Someone who left may ask again; an excluded member may not.
    @Transactional
    public MembershipStatus requestToJoin(User user, Asbl asbl) {
        Optional<Membership> existing = membershipRepository.findByUserAndAsbl(user, asbl);
        if (existing.isPresent()) {
            Membership membership = existing.get();
            switch (membership.getStatus()) {
                case ACTIVE, PENDING -> {
                    return membership.getStatus();
                }
                case EXCLUDED -> throw new JoinRefusedException();
                case LEFT -> {
                    membership.setStatus(MembershipStatus.PENDING);
                    membership.setRole(MembershipRole.MEMBER);
                    auditService.record("JOIN_REQUESTED", asbl, "User", user.getId(), Map.of("again", true));
                    return MembershipStatus.PENDING;
                }
            }
        }
        Membership membership = new Membership();
        membership.setUser(user);
        membership.setAsbl(asbl);
        membership.setRole(MembershipRole.MEMBER);
        membership.setCategory(MembershipCategory.FULL);
        membership.setStatus(MembershipStatus.PENDING);
        membershipRepository.save(membership);
        auditService.record("JOIN_REQUESTED", asbl, "User", user.getId(), null);
        return MembershipStatus.PENDING;
    }

    // An administrator accepts a pending request: the person becomes an active member from today.
    @Transactional
    public void approve(Asbl asbl, UUID userPublicId) {
        Membership membership = pendingRequest(asbl, userPublicId);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setJoinedAt(LocalDate.now());
        auditService.record("JOIN_APPROVED", asbl, "User", membership.getUser().getId(), null);
    }

    // An administrator turns a request down. The request disappears (the audit log keeps the trace), so the person
    // may ask again later.
    @Transactional
    public void decline(Asbl asbl, UUID userPublicId) {
        Membership membership = pendingRequest(asbl, userPublicId);
        membershipRepository.delete(membership);
        auditService.record("JOIN_DECLINED", asbl, "User", membership.getUser().getId(), null);
    }

    private Membership pendingRequest(Asbl asbl, UUID userPublicId) {
        return membershipRepository.findByAsblAndUser_PublicId(asbl, userPublicId)
                .filter(m -> m.getStatus() == MembershipStatus.PENDING)
                .orElseThrow(NoSuchJoinRequestException::new);
    }

    @Transactional
    public void removeAllFor(User user) {
        membershipRepository.deleteAll(membershipRepository.findByUser(user));
    }
}
