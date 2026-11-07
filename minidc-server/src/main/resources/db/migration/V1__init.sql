CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(200) UNIQUE NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(100),
    role VARCHAR(20) NOT NULL,
    banned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE invites (
    code VARCHAR(40) PRIMARY KEY,
    created_by UUID REFERENCES users(id),
    used_by UUID REFERENCES users(id),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE nodes (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(100) NOT NULL,
    token_hash VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_ram_mb INT,
    total_cores INT,
    total_disk_gb INT,
    address VARCHAR(100),
    last_heartbeat TIMESTAMPTZ
);

CREATE TABLE offers (
    id UUID PRIMARY KEY,
    node_id UUID NOT NULL REFERENCES nodes(id),
    max_ram_mb INT NOT NULL,
    max_cores INT NOT NULL,
    max_disk_gb INT NOT NULL,
    max_hours INT NOT NULL,
    window_start TIME,
    window_end TIME,
    auto_approve BOOLEAN NOT NULL DEFAULT FALSE,
    allowed_user_ids UUID[],
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE leases (
    id UUID PRIMARY KEY,
    offer_id UUID NOT NULL REFERENCES offers(id),
    borrower_id UUID NOT NULL REFERENCES users(id),
    node_id UUID NOT NULL REFERENCES nodes(id),
    ram_mb INT NOT NULL,
    cores INT NOT NULL,
    disk_gb INT NOT NULL,
    requested_hours INT NOT NULL,
    state VARCHAR(20) NOT NULL,
    vm_name VARCHAR(60),
    vm_port INT,
    vm_user VARCHAR(40),
    vm_password_enc TEXT,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    fail_reason TEXT
);

CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    at TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_id UUID,
    lease_id UUID,
    node_id UUID,
    event VARCHAR(60) NOT NULL,
    detail TEXT
);

CREATE INDEX idx_leases_state ON leases(state);
CREATE INDEX idx_leases_node_state ON leases(node_id, state);
CREATE INDEX idx_nodes_owner ON nodes(owner_id);
CREATE INDEX idx_offers_node ON offers(node_id);
