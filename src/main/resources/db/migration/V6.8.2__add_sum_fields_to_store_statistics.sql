ALTER TABLE tb_store_statistics
    DROP COLUMN IF EXISTS average_delivery_days,
    DROP COLUMN IF EXISTS avg_pickup_days,
    DROP COLUMN IF EXISTS delivery_success_rate,
    DROP COLUMN IF EXISTS return_rate;

ALTER TABLE tb_store_statistics
    ADD COLUMN sum_delivery_days numeric(12,2) NOT NULL DEFAULT 0.0,
    ADD COLUMN sum_pickup_days   numeric(12,2) NOT NULL DEFAULT 0.0;
