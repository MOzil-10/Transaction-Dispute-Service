-- Feature: regulatory-dispute-eligibility (Task 2.3)
-- Adds the transaction category column used to classify disputable vs non-disputable transactions.
-- The DEFAULT backfills existing rows so the NOT NULL constraint holds and ddl-auto=validate stays consistent.
ALTER TABLE transactions
    ADD COLUMN category VARCHAR(50) NOT NULL DEFAULT 'ONLINE_RETAIL';
