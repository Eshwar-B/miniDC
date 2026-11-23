-- MiniDC Phase 8 & Phase 9: Persistent Volumes, State Snapshots & Credit Wallets

CREATE TABLE volumes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(100) NOT NULL,
    size_gb INT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    mount_path VARCHAR(255) NOT NULL DEFAULT '/home/borrower/workspace',
    active_lease_id UUID REFERENCES leases(id),
    last_snapshot_ref VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_volumes_user_id ON volumes(user_id);

CREATE TABLE volume_snapshots (
    id UUID PRIMARY KEY,
    volume_id UUID NOT NULL REFERENCES volumes(id) ON DELETE CASCADE,
    snapshot_ref VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_snapshots_volume_id ON volume_snapshots(volume_id);

CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id),
    balance_credits BIGINT NOT NULL DEFAULT 500,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE wallet_transactions (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
    lease_id UUID REFERENCES leases(id),
    amount BIGINT NOT NULL,
    type VARCHAR(40) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_wallet_tx_wallet_id ON wallet_transactions(wallet_id);
