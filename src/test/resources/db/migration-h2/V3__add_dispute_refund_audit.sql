-- Feature: regulatory-dispute-eligibility
-- H2-compatible migration (test h2 profile) mirroring prod V3.
-- Adds refund audit columns to disputes. Both nullable: existing rows have no refund recorded yet.
ALTER TABLE disputes ADD COLUMN refunded_amount DECIMAL(19,2);
ALTER TABLE disputes ADD COLUMN refunded_at     TIMESTAMP WITH TIME ZONE;
