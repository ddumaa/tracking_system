ALTER TABLE subscription_plans ADD COLUMN position INT NOT NULL DEFAULT 0;

-- заполняем позиции для существующих записей
WITH ordered AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn
    FROM subscription_plans
)
UPDATE subscription_plans sp
SET position = o.rn
FROM ordered o
WHERE sp.id = o.id;
