create table if not exists attachment_upload_sessions (
  session_id varchar(128) primary key,
  upload_token varchar(128) not null,
  conversation_id varchar(128) not null,
  session_json text not null,
  expires_at_epoch_ms bigint not null,
  completed_at_epoch_ms bigint,
  storage_key varchar(256)
);

create index if not exists idx_attachment_upload_sessions_conversation_id
  on attachment_upload_sessions (conversation_id);
