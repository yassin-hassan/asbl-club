package club.asbl.asbl_club.payment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RegistrationRepository extends JpaRepository<Registration, Long> {

    @Query("select r from Registration r join fetch r.event e join fetch e.asbl where r.id = :id")
    Optional<Registration> findByIdWithEventAndAsbl(@Param("id") Long id);
}
