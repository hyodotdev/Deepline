-- V7: Drop DEFAULT 0 from joined_at_epoch_ms to enforce explicit timestamps
--
-- The DEFAULT 0 in V3 was only needed for existing rows during migration.
-- Now that all rows have been backfilled, remove the default so future INSERTs
-- that omit joined_at_epoch_ms will fail rather than silently corrupt ordering.

ALTER TABLE conversation_members
  ALTER COLUMN joined_at_epoch_ms DROP DEFAULT;
