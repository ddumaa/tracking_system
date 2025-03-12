ALTER TABLE tb_stores ADD COLUMN is_default BOOLEAN DEFAULT FALSE;

-- Установим магазин по умолчанию для пользователей, у которых только один магазин
UPDATE tb_stores
SET is_default = TRUE
WHERE user_id IN (
    SELECT user_id FROM tb_stores GROUP BY user_id HAVING COUNT(*) = 1
);
