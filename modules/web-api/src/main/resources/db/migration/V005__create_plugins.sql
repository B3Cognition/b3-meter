CREATE TABLE plugins (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    jar_path VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    installed_by VARCHAR(36),
    installed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(name, version)
);
