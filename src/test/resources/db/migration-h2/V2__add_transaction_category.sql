-- Feature: regulatory-dispute-eligibility (Task 2.5)
-- H2-compatible migration for the test (h2) profile; mirrors the prod V2 intent.
-- Adds the transaction category column used to classify disputable vs non-disputable transactions.
-- The DEFAULT backfills existing rows so the NOT NULL constraint holds and ddl-auto=validate stays consistent.
-- Production uses the PostgreSQL version in src/main/resources/db/migration.
ALTER TABLE transactions
    ADD COLUMN category VARCHAR(50) NOT NULL DEFAULT 'ONLINE_RETAIL';
