CREATE TABLE state_snapshots (
  "tag"                STRING NOT NULL,
  "id"                 STRING NOT NULL,
  "seq"                BIGINT NOT NULL,
  "snapshot_timestamp" BIGINT NOT NULL,
  "snapshot"           JSONB,
  "snapshot_class"     STRING,
  "created"            TIMESTAMP DEFAULT CURRENT_TIMESTAMP(),
  primary key (tag, id, seq)
);