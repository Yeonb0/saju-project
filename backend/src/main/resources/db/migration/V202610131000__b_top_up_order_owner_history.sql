CREATE INDEX top_up_orders_owner_history_idx
    ON top_up_orders(user_id, created_at DESC, id DESC);
