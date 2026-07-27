package club.asbl.asbl_club.payment;

import club.asbl.asbl_club.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPayableId(Long payableId);

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

    List<Payment> findByUser(User user);
}
