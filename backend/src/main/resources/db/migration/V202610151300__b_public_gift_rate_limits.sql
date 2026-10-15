CREATE TABLE public_gift_rate_limits (
    subject_hash CHAR(64) NOT NULL,
    window_started TIMESTAMP WITH TIME ZONE NOT NULL,
    request_count INTEGER NOT NULL CHECK (request_count > 0),
    PRIMARY KEY (subject_hash, window_started)
);
