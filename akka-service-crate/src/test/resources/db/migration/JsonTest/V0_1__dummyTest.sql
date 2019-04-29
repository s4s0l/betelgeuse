CREATE TABLE json_test (
    id UUID DEFAULT uuid_v4()::UUID PRIMARY KEY,
    avalue JSONB );