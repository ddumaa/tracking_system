CREATE TABLE tb_order_episodes (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT REFERENCES tb_customers (id) ON DELETE SET NULL,
    store_id BIGINT NOT NULL REFERENCES tb_stores (id) ON DELETE CASCADE,
    started_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    final_outcome VARCHAR(50),
    exchanges_count INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    legacy_parcel_id BIGINT UNIQUE
);

CREATE INDEX idx_tb_order_episodes_customer_id ON tb_order_episodes (customer_id);
CREATE INDEX idx_tb_order_episodes_store_id ON tb_order_episodes (store_id);
CREATE INDEX idx_tb_order_episodes_customer_store ON tb_order_episodes (customer_id, store_id);

ALTER TABLE tb_track_parcels
    ADD COLUMN episode_id BIGINT REFERENCES tb_order_episodes (id) ON DELETE RESTRICT,
    ADD COLUMN exchange BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN replacement_of_id BIGINT REFERENCES tb_track_parcels (id) ON DELETE SET NULL;

CREATE INDEX idx_track_parcels_episode_id ON tb_track_parcels (episode_id);
CREATE INDEX idx_track_parcels_exchange ON tb_track_parcels (exchange);
CREATE INDEX idx_track_parcels_replacement_of_id ON tb_track_parcels (replacement_of_id);

WITH episode_source AS (
    SELECT
        p.id AS parcel_id,
        p.customer_id,
        p.store_id,
        COALESCE(p."timestamp", p.last_update, NOW()) AS started_at,
        CASE
            WHEN p.status = 'DELIVERED' THEN 'SUCCESS_NO_EXCHANGE'
            WHEN p.status = 'RETURNED' THEN 'RETURNED_NO_REPLACEMENT'
            WHEN p.status = 'REGISTRATION_CANCELLED' THEN 'CANCELLED'
            ELSE 'OPEN'
        END AS outcome,
        CASE
            WHEN p.status IN ('DELIVERED', 'RETURNED', 'REGISTRATION_CANCELLED')
                THEN COALESCE(p."timestamp", p.last_update, NOW())
            ELSE NULL
        END AS closed_at
    FROM tb_track_parcels p
), inserted_episodes AS (
    INSERT INTO tb_order_episodes (
        customer_id,
        store_id,
        started_at,
        closed_at,
        final_outcome,
        exchanges_count,
        version,
        legacy_parcel_id
    )
    SELECT
        customer_id,
        store_id,
        started_at,
        closed_at,
        outcome,
        0,
        0,
        parcel_id
    FROM episode_source
    RETURNING id, legacy_parcel_id
)
UPDATE tb_track_parcels tp
SET episode_id = ie.id
FROM inserted_episodes ie
WHERE tp.id = ie.legacy_parcel_id;

ALTER TABLE tb_track_parcels
    ALTER COLUMN episode_id SET NOT NULL;

ALTER TABLE tb_order_episodes
    DROP COLUMN legacy_parcel_id;
