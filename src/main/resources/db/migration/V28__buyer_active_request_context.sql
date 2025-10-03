-- Добавляет хранение выбранной заявки и режима редактирования для Telegram-бота покупателя
ALTER TABLE tb_buyer_bot_screen_states
    ADD COLUMN active_request_id BIGINT,
    ADD COLUMN active_request_parcel_id BIGINT,
    ADD COLUMN active_request_edit_mode VARCHAR(32);
