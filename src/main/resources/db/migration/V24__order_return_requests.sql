CREATE TABLE tb_order_return_requests (
    id BIGSERIAL PRIMARY KEY,
    episode_id BIGINT NOT NULL REFERENCES tb_order_episodes (id) ON DELETE RESTRICT,
    parcel_id BIGINT NOT NULL REFERENCES tb_track_parcels (id) ON DELETE RESTRICT,
    created_by BIGINT NOT NULL REFERENCES tb_users (id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),
    reason VARCHAR(255) NOT NULL,
    comment TEXT,
    reverse_track_number VARCHAR(64),
    status VARCHAR(50) NOT NULL,
    decision_by BIGINT REFERENCES tb_users (id) ON DELETE SET NULL,
    decision_at TIMESTAMPTZ,
    closed_by BIGINT REFERENCES tb_users (id) ON DELETE SET NULL,
    closed_at TIMESTAMPTZ,
    idempotency_key VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE tb_order_return_requests
    ADD CONSTRAINT ux_order_return_requests_key UNIQUE (idempotency_key);

CREATE INDEX idx_order_return_requests_episode_id ON tb_order_return_requests (episode_id);
CREATE INDEX idx_order_return_requests_parcel_id ON tb_order_return_requests (parcel_id);
CREATE INDEX idx_order_return_requests_status ON tb_order_return_requests (status);
