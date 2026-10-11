CREATE TABLE purchase_request_bindings (
    actor_id UUID NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (actor_id, key_hash)
);
