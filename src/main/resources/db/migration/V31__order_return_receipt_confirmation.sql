ALTER TABLE tb_order_return_requests
    ADD COLUMN return_receipt_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN return_receipt_confirmed_at TIMESTAMPTZ;
