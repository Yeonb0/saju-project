CREATE TABLE person_owner_locks (
    owner_user_id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO person_owner_locks (owner_user_id, created_at)
SELECT DISTINCT owner_user_id, CURRENT_TIMESTAMP FROM people;
