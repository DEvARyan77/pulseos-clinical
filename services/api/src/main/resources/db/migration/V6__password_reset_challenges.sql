CREATE TABLE pending_password_resets (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    verification_hash VARCHAR(64) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    sent_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_password_resets_expiry ON pending_password_resets(expires_at);
