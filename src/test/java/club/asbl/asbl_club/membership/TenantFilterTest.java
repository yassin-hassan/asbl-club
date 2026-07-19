package club.asbl.asbl_club.membership;

import static org.assertj.core.api.Assertions.assertThat;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@Import(TestcontainersConfiguration.class)
@Transactional
class TenantFilterTest {

    @Autowired
    MembershipRepository membershipRepository;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    @Autowired
    EntityManager entityManager;

    @Test
    void filterScopesABroadQueryToTheCurrentAssociation() {
        User alice = userService.register("Alice", "alice@club.test", "password123");
        User bob = userService.register("Bob", "bob@club.test", "password123");
        Asbl clubA = asblService.createAsbl(alice, "Club A", "0111.111.111", "club-a", "fr");
        asblService.createAsbl(bob, "Club B", "0222.222.222", "club-b", "fr");

        // Without the filter, a broad query sees every association's memberships.
        assertThat(membershipRepository.findAll()).hasSize(2);

        // With the filter enabled for club A, the same broad query is scoped to it.
        entityManager.unwrap(Session.class)
                .enableFilter("asblFilter")
                .setParameter("asblId", clubA.getId());

        assertThat(membershipRepository.findAll()).hasSize(1);
    }
}
