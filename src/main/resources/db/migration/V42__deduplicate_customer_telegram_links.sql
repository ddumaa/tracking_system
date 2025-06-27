-- Объединяем дубликаты перед добавлением уникального ограничения
WITH dedup AS (
    SELECT MIN(id)            AS keep_id,
           customer_id,
           store_id,
           MAX(telegram_chat_id)      AS telegram_chat_id,
           BOOL_OR(notifications_enabled) AS notifications_enabled,
           BOOL_OR(telegram_confirmed) AS telegram_confirmed,
           MIN(linked_at)        AS linked_at
    FROM tb_customer_telegram_links
    GROUP BY customer_id, store_id
)
UPDATE tb_customer_telegram_links t
SET telegram_chat_id = d.telegram_chat_id,
    notifications_enabled = d.notifications_enabled,
    telegram_confirmed = d.telegram_confirmed,
    linked_at = d.linked_at
FROM dedup d
WHERE t.id = d.keep_id;

-- Удаляем лишние записи
DELETE FROM tb_customer_telegram_links t
USING dedup d
WHERE t.customer_id = d.customer_id
  AND t.store_id = d.store_id
  AND t.id <> d.keep_id;

-- Добавляем уникальное ограничение
ALTER TABLE tb_customer_telegram_links
    ADD CONSTRAINT uk_customer_telegram_links_customer_store UNIQUE (customer_id, store_id);

