-- How long before an event its buyers may cancel their ticket and be refunded, in days, chosen by the association
-- for each event (the analysis' Evenement.delaiAnnulationJours). 0: tickets aren't refundable on request. Existing
-- events get 0: a refund policy is a promise, and the association never made it for them.
ALTER TABLE events ADD COLUMN cancellation_days INTEGER NOT NULL DEFAULT 0
    CHECK (cancellation_days BETWEEN 0 AND 365);
