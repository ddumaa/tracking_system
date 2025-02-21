-- Удаление устаревших ролей
DELETE FROM tb_user_roles WHERE role IN ('ROLE_FREE_USER', 'ROLE_PAID_USER');

-- Обновление оставшихся ролей (кроме ADMIN) на ROLE_USER
UPDATE tb_user_roles
SET role = 'ROLE_USER'
WHERE role NOT IN ('ROLE_ADMIN');

-- Добавление отсутствующего столбца subscription_plan_id в tb_users
ALTER TABLE tb_users ADD COLUMN IF NOT EXISTS subscription_plan_id bigint;

-- Добавление внешнего ключа для связи подписок с пользователями
ALTER TABLE tb_users
    ADD CONSTRAINT fk_subscription FOREIGN KEY (subscription_plan_id) REFERENCES subscription_plans(id);
