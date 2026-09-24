package club.asbl.asbl_club.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import club.asbl.asbl_club.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@Import(TestcontainersConfiguration.class)
@Transactional
class UserPublicIdIntegrationTest {

    @Autowired
    UserService userService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void registeredUsers_getDistinctPublicIdsStoredInTheDatabase() {
        User alice = userService.register("Alice", "alice@club.test", "password123");
        User bob = userService.register("Bob", "bob@club.test", "password123");

        assertThat(alice.getPublicId()).isNotNull().isNotEqualTo(bob.getPublicId());
        assertThat(publicIdInDatabase(alice)).isEqualTo(alice.getPublicId());
    }

    @Test
    void databaseRejectsTwoUsersWithTheSamePublicId() {
        User alice = userService.register("Alice", "alice@club.test", "password123");
        User bob = userService.register("Bob", "bob@club.test", "password123");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE users SET public_id = ? WHERE id = ?", alice.getPublicId(), bob.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID publicIdInDatabase(User user) {
        return jdbcTemplate.queryForObject("SELECT public_id FROM users WHERE id = ?", UUID.class, user.getId());
    }
}
