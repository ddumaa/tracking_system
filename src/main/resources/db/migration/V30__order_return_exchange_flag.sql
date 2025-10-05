ALTER TABLE tb_order_return_requests
    ADD COLUMN exchange_requested BOOLEAN NOT NULL DEFAULT FALSE;
