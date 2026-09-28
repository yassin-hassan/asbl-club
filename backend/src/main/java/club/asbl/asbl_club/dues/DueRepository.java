package club.asbl.asbl_club.dues;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface DueRepository extends JpaRepository<Due, Long> {

    Optional<Due> findByAsblAndUserAndYear(Asbl asbl, User user, int year);

    // Dues of that year whose payment succeeded: "paid" comes from the payment itself, never from a copy.
    @Query("""
            SELECT new club.asbl.asbl_club.dues.PaidDue(d.user.id, d.amount, p.paidAt)
            FROM Payment p JOIN Due d ON p.payable.id = d.id
            WHERE d.asbl = :asbl AND d.year = :year
              AND p.status = club.asbl.asbl_club.payment.PaymentStatus.SUCCEEDED
            """)
    List<PaidDue> findPaid(Asbl asbl, int year);
}
