-- Добавление отметки последней активности покупателя и таблиц состояния объявлений

ALTER TABLE tb_customers
    ADD COLUMN last_active_at TIMESTAMPTZ;

CREATE TABLE tb_buyer_announcement_states (
    chat_id BIGINT PRIMARY KEY,
    current_notification_id BIGINT,
    announcement_seen BOOLEAN NOT NULL DEFAULT FALSE,
    anchor_message_id INTEGER
);

CREATE TABLE tb_buyer_seen_announcements (
    chat_id BIGINT NOT NULL REFERENCES tb_buyer_announcement_states(chat_id) ON DELETE CASCADE,
    notification_id BIGINT NOT NULL,
    PRIMARY KEY (chat_id, notification_id)
);

