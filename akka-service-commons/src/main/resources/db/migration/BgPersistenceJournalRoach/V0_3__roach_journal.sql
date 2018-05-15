CREATE TABLE journal_events (
   "tag" STRING NOT NULL,
   "id" STRING NOT NULL,
   "seq" BIGINT NOT NULL,
   "manifest" STRING NOT NULL,
   "writer_uuid" STRING NOT NULL,
   "sender" STRING,
   "event" JSONB,
   "event_class" STRING,
   "deleted" BOOL,
   "created" TIMESTAMP DEFAULT CURRENT_TIMESTAMP(),
   primary key (tag,id,seq)
);