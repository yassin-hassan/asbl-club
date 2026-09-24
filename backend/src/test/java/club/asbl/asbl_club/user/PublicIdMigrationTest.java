package club.asbl.asbl_club.user;

import static org.assertj.core.api.Assertions.assertThat;

import club.asbl.asbl_club.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

// Runs V11 against a database that already has users, the situation production is in.
// Uses its own schema so it doesn't touch the one the application runs on.
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@Import(TestcontainersConfiguration.class)
class PublicIdMigrationTest {

    private static final String SCHEMA = "public_id_migration_test";

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void dropSchema() {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void existingUsersAreBackfilledWithDistinctPublicIds() {
        migrateTo("10");
        jdbcTemplate.update("INSERT INTO " + SCHEMA + ".users (name, email, password) VALUES "
                + "('Alice', 'alice@club.test', 'x'), ('Bob', 'bob@club.test', 'x')");

        migrateTo("11");

        List<UUID> publicIds = jdbcTemplate.queryForList("SELECT public_id FROM " + SCHEMA + ".users", UUID.class);
        assertThat(publicIds).hasSize(2).doesNotContainNull().doesNotHaveDuplicates();
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }
}
