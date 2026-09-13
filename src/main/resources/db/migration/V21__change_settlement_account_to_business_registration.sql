ALTER TABLE seller_settlement_accounts
    ADD COLUMN encrypted_business_registration_number TEXT;

ALTER TABLE seller_settlement_accounts
    ALTER COLUMN provider_transaction_id DROP NOT NULL,
    ALTER COLUMN verified_at DROP NOT NULL;

UPDATE seller_settlement_accounts
SET account_holder_type = 'BUSINESS',
    provider_transaction_id = NULL,
    verified_at = NULL;

ALTER TABLE seller_settlement_accounts
    DROP CONSTRAINT IF EXISTS ck_seller_settlement_accounts_holder_type;

ALTER TABLE seller_settlement_accounts
    ADD CONSTRAINT ck_seller_settlement_accounts_holder_type
        CHECK (account_holder_type = 'BUSINESS');
