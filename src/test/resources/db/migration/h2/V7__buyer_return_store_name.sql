-- Добавляет хранение названия магазина для возврата
ALTER TABLE tb_buyer_bot_screen_states
    ADD COLUMN IF NOT EXISTS return_store_name VARCHAR(255);
