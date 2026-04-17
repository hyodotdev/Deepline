create table if not exists users (
  user_id varchar(128) primary key,
  identity_fingerprint text not null,
  profile_ciphertext text,
  created_at_epoch_ms bigint not null
);

create table if not exists devices (
  user_id varchar(128) not null,
  device_id varchar(128) not null,
  bundle_json text not null,
  created_at_epoch_ms bigint not null,
  last_seen_at_epoch_ms bigint not null,
  primary key (user_id, device_id)
);

create table if not exists prekey_bundles (
  user_id varchar(128) not null,
  device_id varchar(128) not null,
  bundle_json text not null,
  published_at_epoch_ms bigint not null,
  primary key (user_id, device_id)
);

create table if not exists conversations (
  conversation_id varchar(128) primary key,
  protocol_type varchar(32) not null,
  encrypted_title text,
  created_at_epoch_ms bigint not null,
  updated_at_epoch_ms bigint not null
);

create table if not exists conversation_members (
  conversation_id varchar(128) not null,
  user_id varchar(128) not null,
  primary key (conversation_id, user_id)
);

create table if not exists messages (
  message_id varchar(128) primary key,
  conversation_id varchar(128) not null,
  envelope_json text not null,
  created_at_epoch_ms bigint not null
);

create table if not exists message_receipts (
  message_id varchar(128) not null,
  conversation_id varchar(128) not null,
  user_id varchar(128) not null,
  device_id varchar(128) not null,
  receipt_json text not null,
  created_at_epoch_ms bigint not null,
  primary key (message_id, user_id, device_id)
);

create table if not exists encrypted_attachments (
  attachment_id varchar(128) primary key,
  conversation_id varchar(128) not null,
  metadata_json text not null,
  created_at_epoch_ms bigint not null
);

create table if not exists invite_codes (
  invite_code varchar(128) primary key,
  owner_user_id varchar(128) not null,
  record_json text not null,
  created_at_epoch_ms bigint not null
);

create table if not exists contacts (
  owner_user_id varchar(128) not null,
  peer_user_id varchar(128) not null,
  record_json text not null,
  created_at_epoch_ms bigint not null,
  primary key (owner_user_id, peer_user_id)
);

create index if not exists idx_conversation_members_user_id
  on conversation_members (user_id);

create index if not exists idx_messages_conversation_id_created_at
  on messages (conversation_id, created_at_epoch_ms);

create index if not exists idx_message_receipts_message_id
  on message_receipts (message_id);

create index if not exists idx_attachments_conversation_id_created_at
  on encrypted_attachments (conversation_id, created_at_epoch_ms);
