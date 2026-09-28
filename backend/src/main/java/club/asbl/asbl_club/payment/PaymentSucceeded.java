package club.asbl.asbl_club.payment;

// Published when Stripe confirms a payment, inside the transaction that records it. What was paid for reacts in its
// own code (dues send a receipt), so the payment flow doesn't need to know every kind of payable.
public record PaymentSucceeded(Long paymentId, Long payableId) {
}
