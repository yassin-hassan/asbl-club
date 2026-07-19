package club.asbl.asbl_club.asbl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import club.asbl.asbl_club.membership.MembershipService;
import club.asbl.asbl_club.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AsblServiceTest {

    @Mock
    AsblRepository asblRepository;

    @Mock
    MembershipService membershipService;

    @InjectMocks
    AsblService asblService;

    @Test
    void createAsbl_savesAsbl_andDelegatesFoundingAdmin() {
        User creator = mock(User.class);
        when(asblRepository.existsBySlug("mon-club")).thenReturn(false);
        when(asblRepository.existsByBceNumber("0123.456.789")).thenReturn(false);
        when(asblRepository.save(any(Asbl.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Asbl created = asblService.createAsbl(creator, "Mon Club", "0123.456.789", "mon-club", "fr");

        assertThat(created.getDenomination()).isEqualTo("Mon Club");
        assertThat(created.getStatus()).isEqualTo("PENDING");
        verify(membershipService).createFoundingAdmin(created, creator);
    }

    @Test
    void createAsbl_rejectsDuplicateSlug() {
        User creator = mock(User.class);
        when(asblRepository.existsBySlug("taken")).thenReturn(true);

        assertThatThrownBy(() -> asblService.createAsbl(creator, "X", "0123.456.789", "taken", "fr"))
                .isInstanceOf(SlugAlreadyUsedException.class);

        verify(membershipService, never()).createFoundingAdmin(any(), any());
    }
}
