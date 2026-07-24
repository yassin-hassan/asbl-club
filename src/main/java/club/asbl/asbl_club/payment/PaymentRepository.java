package club.asbl.asbl_club.payment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPayableId(Long payableId);

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);
}
