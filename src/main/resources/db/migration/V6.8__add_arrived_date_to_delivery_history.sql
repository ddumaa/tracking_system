ALTER TABLE tb_delivery_history
ADD COLUMN arrived_date TIMESTAMPTZ;

ALTER TABLE tb_store_statistics
    ADD COLUMN avg_pickup_days DOUBLE PRECISION;

