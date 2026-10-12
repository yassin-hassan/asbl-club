package club.asbl.asbl_club.payment;

import club.asbl.asbl_club.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPayableId(Long payableId);

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

    List<Payment> findByUser(User user);

    // Payments still open at Stripe (never paid) whose booking has expired or been cancelled: to cancel at Stripe.
    @Query(value = """
            SELECT p.id FROM payments p JOIN registrations r ON r.id = p.payable_id
            WHERE p.status IN ('INITIATED', 'FAILED') AND p.stripe_payment_intent_id IS NOT NULL
              AND r.status IN ('EXPIRED', 'CANCELLED')
            ORDER BY p.id""", nativeQuery = true)
    List<Long> findOpenOfAbandonedBookings();
}
