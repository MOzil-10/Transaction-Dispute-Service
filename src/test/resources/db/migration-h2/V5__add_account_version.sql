-- Optimistic-locking version column for accounts.
-- Backs the @Version field on AccountEntity so concurrent balance updates (e.g. two refunds
-- crediting the same account) cannot silently overwrite each other. DEFAULT 0 backfills existing
-- rows so the NOT NULL constraint holds and ddl-auto=validate stays consistent.
ALTER TABLE accounts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;