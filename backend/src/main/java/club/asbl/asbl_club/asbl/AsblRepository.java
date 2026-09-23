package club.asbl.asbl_club.asbl;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface AsblRepository extends JpaRepository<Asbl, Long> {

    boolean existsBySlug(String slug);

    boolean existsByBceNumber(String bceNumber);

    Optional<Asbl> findBySlug(String slug);
}
