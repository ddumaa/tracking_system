-- Добавляет тип заявки на возврат в состояние экранов покупательского бота
ALTER TABLE tb_buyer_bot_screen_states
    ADD COLUMN return_request_type VARCHAR(32);
