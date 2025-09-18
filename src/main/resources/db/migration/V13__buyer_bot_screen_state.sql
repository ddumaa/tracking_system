-- Таблица с последними экранами покупательского Telegram-бота
CREATE TABLE tb_buyer_bot_screen_states (
    chat_id BIGINT PRIMARY KEY,
    anchor_message_id INTEGER,
    last_screen VARCHAR(50)
);

