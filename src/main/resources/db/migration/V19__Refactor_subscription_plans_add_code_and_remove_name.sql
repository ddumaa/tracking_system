-- 1. Добавляем новое поле code
ALTER TABLE subscription_plans ADD COLUMN code VARCHAR(20);

-- 2. Переносим данные из name в code (на основе текущих значений)
UPDATE subscription_plans SET code = 'FREE' WHERE name = 'FREE';
UPDATE subscription_plans SET code = 'PREMIUM' WHERE name = 'PREMIUM';

-- 3. Устанавливаем ограничения
ALTER TABLE subscription_plans ALTER COLUMN code SET NOT NULL;
ALTER TABLE subscription_plans ADD CONSTRAINT uq_subscription_plan_code UNIQUE (code);

-- 4. Удаляем старое поле name
ALTER TABLE subscription_plans DROP COLUMN name;
