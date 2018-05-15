CREATE TABLE journal_single_events (
   "tag" STRING NOT NULL,
   "id" STRING NOT NULL,
   "seq" BIGINT NOT NULL,
   "manifest" STRING NOT NULL,
   "writer_uuid" STRING NOT NULL,
   "event" JSONB,
   "event_class" STRING,
   "deleted" BOOL,
   "created" TIMESTAMP DEFAULT CURRENT_TIMESTAMP(),
   INVERTED INDEX events_idx (event),
    primary key (tag,id)
);