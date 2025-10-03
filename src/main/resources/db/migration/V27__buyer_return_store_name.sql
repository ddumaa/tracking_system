-- Добавляет хранение названия магазина для возврата
ALTER TABLE tb_buyer_bot_screen_states
    ADD COLUMN return_store_name VARCHAR(255);
