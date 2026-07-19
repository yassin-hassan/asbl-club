package club.asbl.asbl_club.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

    @Mock
    MembershipRepository membershipRepository;

    @InjectMocks
    MembershipService membershipService;

    @Test
    void createFoundingAdmin_savesAnAdminActiveMembership() {
        Asbl asbl = mock(Asbl.class);
        User creator = mock(User.class);

        membershipService.createFoundingAdmin(asbl, creator);

        ArgumentCaptor<Membership> captor = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).save(captor.capture());
        Membership membership = captor.getValue();
        assertThat(membership.getUser()).isSameAs(creator);
        assertThat(membership.getAsbl()).isSameAs(asbl);
        assertThat(membership.getRole()).isEqualTo("ADMIN");
        assertThat(membership.getCategory()).isEqualTo("FULL");
        assertThat(membership.getStatus()).isEqualTo("ACTIVE");
        assertThat(membership.getJoinedAt()).isNotNull();
    }
}
