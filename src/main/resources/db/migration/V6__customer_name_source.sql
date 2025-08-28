-- Добавление полей для хранения ФИО и источника имени покупателя
ALTER TABLE tb_customers
    ADD COLUMN full_name TEXT,
    ADD COLUMN name_source VARCHAR(50) NOT NULL DEFAULT 'MERCHANT_PROVIDED',
    ADD COLUMN name_updated_at TIMESTAMPTZ;

-- Индекс для быстрого поиска по дате обновления имени
CREATE INDEX IF NOT EXISTS idx_customers_name_updated_at
    ON tb_customers(name_updated_at);
