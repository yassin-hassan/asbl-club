-- What a refund did, for the association's finances: when it happened, and whether the platform's commission went
-- back too. It does when the payment is refunded because of the association or of the timing (event cancelled, a
-- payment arriving after its booking expired); it doesn't when the buyer cancels their own ticket (the association
-- chose to offer cancellations, so it bears the commission).
ALTER TABLE payments
    ADD COLUMN refunded_at         TIMESTAMPTZ,
    ADD COLUMN commission_refunded BOOLEAN NOT NULL DEFAULT false;

-- Refunds made before this migration: the audit log has always recorded each one, with its reason.
UPDATE payments p SET
    refunded_at = COALESCE(
        (SELECT max(a.created_at) FROM audit_logs a
         WHERE a.action = 'PAYMENT_REFUNDED' AND a.entity_type = 'Payment' AND a.entity_id = p.id),
        p.paid_at),
    commission_refunded = NOT EXISTS (
        SELECT 1 FROM audit_logs a
        WHERE a.action = 'PAYMENT_REFUNDED' AND a.entity_type = 'Payment' AND a.entity_id = p.id
          AND a.payload ->> 'reason' = 'cancelled by buyer')
WHERE p.status = 'REFUNDED';

-- A refunded payment says when.
ALTER TABLE payments ADD CONSTRAINT payments_refund_dated CHECK (status <> 'REFUNDED' OR refunded_at IS NOT NULL);
