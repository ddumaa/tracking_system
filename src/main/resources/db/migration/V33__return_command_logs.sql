CREATE TABLE tb_return_command_logs (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES tb_order_return_requests (id) ON DELETE CASCADE,
    idempotency_key VARCHAR(255) NOT NULL,
    action VARCHAR(100) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    response_snapshot TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC')
);

ALTER TABLE tb_return_command_logs
    ADD CONSTRAINT ux_return_command_log_key UNIQUE (request_id, idempotency_key);

CREATE INDEX idx_return_command_logs_request ON tb_return_command_logs (request_id);
