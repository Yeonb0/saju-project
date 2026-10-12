CREATE TABLE operations_incidents (
    id UUID PRIMARY KEY,
    kind VARCHAR(40) NOT NULL,
    resource_id UUID NOT NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN ('OPEN','RESOLVED')),
    generation INTEGER NOT NULL CHECK (generation > 0),
    detected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (kind, resource_id)
);
CREATE TABLE operations_alert_deliveries (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES operations_incidents(id),
    generation INTEGER NOT NULL,
    state VARCHAR(24) NOT NULL CHECK (state IN ('READY','IN_FLIGHT','RETRY','DELIVERED','REVIEW_REQUIRED','CANCELED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    lease_token UUID,
    lease_until TIMESTAMP WITH TIME ZONE,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (incident_id, generation)
);
CREATE INDEX operations_alert_due ON operations_alert_deliveries(state, next_attempt_at);
CREATE INDEX operations_incident_scan ON operations_incidents(state, id);
