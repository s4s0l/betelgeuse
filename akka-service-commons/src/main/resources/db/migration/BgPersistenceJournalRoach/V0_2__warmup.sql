insert into roach_async_write_journal_entity (tag, id, seq, serialized, event, json) values
('warmup', '0', 0, 'CnUIARJxrO0ABXNyADZvcmcuczRzMGwuYmV0ZWxnZXVzZS5ha2thY29tbW9ucy5wZXJzaXN0ZW5jZS5jcmF0ZS5FdnToLUOAWcBd... (232)', null, null);

delete from roach_async_write_journal_entity where tag='warmup' and id='0' and seq = 0;