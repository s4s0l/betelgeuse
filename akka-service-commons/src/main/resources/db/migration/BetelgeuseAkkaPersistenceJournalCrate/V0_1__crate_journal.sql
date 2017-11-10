create table crate_async_write_journal_entity (
  tag string  not null,
  id string  not null,
  seq long  not null,
  serialized string not null index off,
  event object,
  json string index off,
  created timestamp generated always as  CURRENT_TIMESTAMP,
  month timestamp GENERATED ALWAYS AS date_trunc('month', CURRENT_TIMESTAMP),
  primary key (tag,id,seq,month)
) clustered by (tag) into 30 shards PARTITIONED BY (month);
