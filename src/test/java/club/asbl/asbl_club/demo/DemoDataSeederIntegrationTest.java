package club.asbl.asbl_club.demo;

import static org.assertj.core.api.Assertions.assertThat;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
class DemoDataSeederIntegrationTest {

    @Autowired
    AsblService asblService;

    @Test
    void seedsAnAssociationAlreadyLinkedToStripe() {
        Asbl club = asblService.findBySlug("club-demo").orElseThrow();
        assertThat(club.getStripeAccountId()).isEqualTo("acct_1TxApqRVezmODcDW");
    }
}
