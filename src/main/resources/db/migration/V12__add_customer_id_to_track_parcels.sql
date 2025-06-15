ALTER TABLE tb_track_parcels
    ADD COLUMN customer_id BIGINT NULL REFERENCES tb_customers(id) ON DELETE SET NULL;
