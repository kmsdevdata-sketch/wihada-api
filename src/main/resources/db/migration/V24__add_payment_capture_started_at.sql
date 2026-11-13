ALTER TABLE payment_attempts
    ADD COLUMN capture_started_at TIMESTAMPTZ,
    ADD COLUMN reconciliation_checked_at TIMESTAMPTZ;

UPDATE payment_attempts
SET capture_started_at = COALESCE(completed_at, created_at)
WHERE status IN ('IN_PROGRESS', 'NEEDS_CONFIRMATION');

CREATE INDEX ix_payment_attempts_recovery
    ON payment_attempts (status, reconciliation_checked_at, capture_started_at)
    WHERE status IN ('IN_PROGRESS', 'NEEDS_CONFIRMATION');
