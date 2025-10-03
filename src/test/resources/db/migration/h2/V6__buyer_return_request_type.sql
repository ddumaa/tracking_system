-- Добавляет тип заявки на возврат для состояний бота в H2
ALTER TABLE tb_buyer_bot_screen_states
    ADD COLUMN IF NOT EXISTS return_request_type VARCHAR(32);
