-- Добавление колонки версии и индексов для покупателей
ALTER TABLE tb_customers
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Индекс для быстрого поиска по идентификатору Telegram-чата
CREATE INDEX IF NOT EXISTS idx_customers_telegram_chat_id
    ON tb_customers(telegram_chat_id);

-- Индекс для фильтрации по репутации покупателя
CREATE INDEX IF NOT EXISTS idx_customers_reputation
    ON tb_customers(reputation);
