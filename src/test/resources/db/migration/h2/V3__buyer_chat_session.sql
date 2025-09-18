-- Таблица состояний покупательского бота для H2
CREATE TABLE IF NOT EXISTS tb_buyer_bot_screen_states (
    chat_id BIGINT PRIMARY KEY,
    anchor_message_id INTEGER,
    last_screen VARCHAR(50),
    chat_state VARCHAR(50) DEFAULT 'IDLE' NOT NULL
);

ALTER TABLE tb_buyer_bot_screen_states
    ADD COLUMN IF NOT EXISTS chat_state VARCHAR(50) DEFAULT 'IDLE' NOT NULL;
