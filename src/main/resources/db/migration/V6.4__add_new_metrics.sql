ALTER TABLE tb_store_statistics
    ADD COLUMN delivery_success_rate numeric(5, 2),
    ADD COLUMN return_rate numeric(5, 2);
