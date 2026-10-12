-- A payment left open at Stripe whose booking expired or was cancelled is cancelled at Stripe too, so it can't be paid
-- any more: it is then CANCELLED (the analysis's annulerPaiement(), state diagram of the booking).
ALTER TABLE payments DROP CONSTRAINT payments_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_status_check
    CHECK (status IN ('INITIATED', 'SUCCEEDED', 'FAILED', 'REFUNDED', 'CANCELLED'));
