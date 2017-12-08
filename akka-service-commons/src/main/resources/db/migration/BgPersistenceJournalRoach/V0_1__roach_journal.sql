CREATE TABLE roach_async_write_journal_entity (
   "tag" STRING NOT NULL,
   "id" STRING NOT NULL,
   "seq" BIGINT NOT NULL,
   "serialized" STRING NOT NULL,
   "event" BYTEA,
   "json" STRING,
   "created" TIMESTAMP DEFAULT CURRENT_TIMESTAMP(),
   "month" INT DEFAULT  EXTRACT('month', CURRENT_TIMESTAMP()::TIMESTAMP),
   primary key (tag,id,seq,month)
);