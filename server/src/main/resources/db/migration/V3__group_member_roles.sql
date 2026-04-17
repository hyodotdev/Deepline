-- V3: Add group member roles and conversation settings for groups up to 1000 members

-- Add role and membership tracking to conversation_members
ALTER TABLE conversation_members
  ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'MEMBER';

ALTER TABLE conversation_members
  ADD COLUMN added_by_user_id VARCHAR(128);

-- NOTE: DEFAULT 0 is only for migration of existing rows. Application code
-- MUST always provide joined_at_epoch_ms when inserting new members.
-- The 0 value is backfilled below from conversations.created_at_epoch_ms.
ALTER TABLE conversation_members
  ADD COLUMN joined_at_epoch_ms BIGINT NOT NULL DEFAULT 0;

-- Add group settings to conversations
ALTER TABLE conversations
  ADD COLUMN max_members INT NOT NULL DEFAULT 1000;

ALTER TABLE conversations
  ADD COLUMN member_count INT NOT NULL DEFAULT 0;

-- Create index for efficient paginated member queries sorted by join time
CREATE INDEX IF NOT EXISTS idx_conversation_members_conv_joined
  ON conversation_members (conversation_id, joined_at_epoch_ms);

-- Create index for role-based lookups (e.g., finding all admins)
CREATE INDEX IF NOT EXISTS idx_conversation_members_conv_role
  ON conversation_members (conversation_id, role);

-- Backfill member_count for existing conversations
UPDATE conversations c
SET member_count = (
  SELECT COUNT(*) FROM conversation_members cm
  WHERE cm.conversation_id = c.conversation_id
);

-- Backfill joined_at_epoch_ms from conversation's creation time for legacy rows.
-- This ensures OWNER selection below has meaningful ordering, not arbitrary user_id ordering.
UPDATE conversation_members cm
SET joined_at_epoch_ms = c.created_at_epoch_ms
FROM conversations c
WHERE cm.conversation_id = c.conversation_id
  AND cm.joined_at_epoch_ms = 0;

-- Set the first member as OWNER for existing conversations (creator)
-- This uses a subquery to find the first member (lowest joined_at or user_id as tiebreaker)
UPDATE conversation_members cm
SET role = 'OWNER'
WHERE (cm.conversation_id, cm.user_id) IN (
  SELECT conversation_id, user_id
  FROM (
    SELECT conversation_id, user_id,
      ROW_NUMBER() OVER (PARTITION BY conversation_id ORDER BY joined_at_epoch_ms ASC, user_id ASC) as rn
    FROM conversation_members
  ) ranked
  WHERE rn = 1
);
