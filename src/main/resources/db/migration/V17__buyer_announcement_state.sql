-- Добавление таблиц состояния объявлений для покупателей в Telegram

CREATE TABLE tb_buyer_announcement_states (
    chat_id BIGINT PRIMARY KEY,
    current_notification_id BIGINT,
    announcement_seen BOOLEAN NOT NULL DEFAULT FALSE,
    anchor_message_id INTEGER,
    notification_updated_at TIMESTAMP WITH TIME ZONE
);
