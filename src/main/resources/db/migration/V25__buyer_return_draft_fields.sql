-- Расширяет хранение состояния покупательского бота полями возврата
ALTER TABLE tb_buyer_bot_screen_states
    ADD COLUMN return_parcel_id BIGINT,
    ADD COLUMN return_parcel_track VARCHAR(64),
    ADD COLUMN return_reason VARCHAR(255),
    ADD COLUMN return_comment TEXT,
    ADD COLUMN return_requested_at TIMESTAMPTZ,
    ADD COLUMN return_reverse_track VARCHAR(64),
    ADD COLUMN return_idempotency_key VARCHAR(64);
