CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    user_email      VARCHAR(255) NOT NULL,
    organization_id VARCHAR(36),
    event_type      VARCHAR(30)  NOT NULL,
    resource_type   VARCHAR(255),
    resource_id     VARCHAR(255),
    action          VARCHAR(500),
    ip_address      VARCHAR(45),
    result          VARCHAR(10)  NOT NULL,
    details         TEXT,
    timestamp       TIMESTAMP NOT NULL
);

CREATE INDEX idx_audit_user ON audit_logs (user_email);
CREATE INDEX idx_audit_time ON audit_logs (timestamp);
CREATE INDEX idx_audit_org  ON audit_logs (organization_id);