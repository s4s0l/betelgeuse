insert into journal_events
  (tag, id, seq, manifest, writer_uuid,sender,  event, event_class, deleted)
values
  ('warmup', '0', 0, 'manifest','1', '','{"key":"value"}', 'none' ,true);

delete from journal_events where tag='warmup' and id='0' and seq = 0;