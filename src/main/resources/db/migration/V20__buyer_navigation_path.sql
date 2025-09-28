-- Добавляет хранение пути навигации для экранов покупательского бота
ALTER TABLE tb_buyer_bot_screen_states
    ADD COLUMN navigation_path TEXT;
