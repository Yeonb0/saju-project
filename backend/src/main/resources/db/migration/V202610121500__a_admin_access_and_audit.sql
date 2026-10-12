CREATE TABLE admin_operator_grants (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    can_recover BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE admin_audit_events (
    id UUID PRIMARY KEY,
    operator_id UUID NOT NULL REFERENCES users(id),
    action VARCHAR(60) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id UUID NOT NULL,
    reason_code VARCHAR(40) NOT NULL,
    trace_id VARCHAR(100) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX admin_audit_actor_time ON admin_audit_events(operator_id, occurred_at, id);
CREATE INDEX admin_audit_target_time ON admin_audit_events(target_type, target_id, occurred_at, id);
CREATE INDEX top_up_orders_admin_scan ON top_up_orders(status, id);
