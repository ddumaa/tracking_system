ALTER TABLE tb_order_return_requests
    ADD COLUMN mode VARCHAR(32),
    ADD COLUMN stage VARCHAR(64),
    ADD COLUMN store_id BIGINT,
    ADD COLUMN responsible_id BIGINT,
    ADD COLUMN exchange_track_number VARCHAR(64),
    ADD COLUMN manual_stage_override BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN manual_track_override BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN stage_started_at TIMESTAMPTZ,
    ADD COLUMN stage_updated_at TIMESTAMPTZ,
    ADD COLUMN exchange_track_assigned_at TIMESTAMPTZ;

UPDATE tb_order_return_requests r
SET mode = CASE
        WHEN r.exchange_requested OR r.status = 'EXCHANGE_APPROVED' THEN 'EXCHANGE'
        ELSE 'RETURN'
    END,
    stage = CASE
        WHEN r.return_receipt_confirmed THEN 'MERCHANT_ACCEPT_RETURN'
        WHEN r.status = 'EXCHANGE_APPROVED' THEN 'EXCHANGE_SHIPMENT'
        WHEN r.status = 'CLOSED_NO_EXCHANGE' THEN 'MERCHANT_ACCEPT_RETURN'
        ELSE 'CUSTOMER_RETURN'
    END,
    store_id = p.store_id,
    responsible_id = r.created_by,
    manual_track_override = r.reverse_track_number IS NOT NULL,
    manual_stage_override = CASE WHEN r.status <> 'REGISTERED' OR r.return_receipt_confirmed THEN TRUE ELSE FALSE END,
    stage_started_at = COALESCE(r.requested_at, r.created_at),
    stage_updated_at = COALESCE(r.return_receipt_confirmed_at, r.decision_at, r.closed_at, r.requested_at, r.created_at),
    exchange_track_number = NULL,
    exchange_track_assigned_at = CASE
        WHEN r.status = 'EXCHANGE_APPROVED' THEN COALESCE(r.decision_at, r.created_at)
        ELSE NULL
    END
FROM tb_track_parcels p
WHERE p.id = r.parcel_id;

ALTER TABLE tb_order_return_requests
    ALTER COLUMN mode SET NOT NULL,
    ALTER COLUMN stage SET NOT NULL,
    ALTER COLUMN store_id SET NOT NULL,
    ALTER COLUMN stage_started_at SET NOT NULL,
    ALTER COLUMN stage_updated_at SET NOT NULL;

ALTER TABLE tb_order_return_requests
    ADD CONSTRAINT fk_order_return_requests_store
        FOREIGN KEY (store_id) REFERENCES tb_stores (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_order_return_requests_responsible
        FOREIGN KEY (responsible_id) REFERENCES tb_users (id) ON DELETE SET NULL;

CREATE INDEX idx_order_return_requests_mode ON tb_order_return_requests (mode);
CREATE INDEX idx_order_return_requests_stage ON tb_order_return_requests (stage);
CREATE INDEX idx_order_return_requests_store ON tb_order_return_requests (store_id);

CREATE TABLE tb_order_return_request_history (
    id BIGSERIAL PRIMARY KEY,
    return_request_id BIGINT NOT NULL REFERENCES tb_order_return_requests (id) ON DELETE CASCADE,
    mode VARCHAR(32) NOT NULL,
    stage VARCHAR(64) NOT NULL,
    reverse_track_number VARCHAR(64),
    exchange_track_number VARCHAR(64),
    manual_transition BOOLEAN NOT NULL DEFAULT FALSE,
    changed_by BIGINT REFERENCES tb_users (id) ON DELETE SET NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC')
);

CREATE INDEX idx_return_request_history_request ON tb_order_return_request_history (return_request_id);
CREATE INDEX idx_return_request_history_stage ON tb_order_return_request_history (stage);

INSERT INTO tb_order_return_request_history (return_request_id, mode, stage, reverse_track_number, exchange_track_number, manual_transition, changed_by, changed_at)
SELECT r.id,
       r.mode,
       r.stage,
       r.reverse_track_number,
       r.exchange_track_number,
       FALSE,
       r.created_by,
       r.stage_started_at
FROM tb_order_return_requests r;
