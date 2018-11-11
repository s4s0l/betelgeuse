CREATE TABLE auth_audit_log (
   "uuid" STRING NOT NULL,
   "ts" TIMESTAMP DEFAULT CURRENT_TIMESTAMP(),
   "event" JSONB,
   INVERTED INDEX events_idx (event),
    primary key (uuid)
);
