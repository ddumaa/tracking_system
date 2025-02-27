-- Добавляем новое поле для роли в таблицу пользователей
ALTER TABLE tb_users
    ADD COLUMN role VARCHAR(50);

-- Переносим роли из таблицы tb_user_roles в tb_users
UPDATE tb_users u
SET role = (SELECT role FROM tb_user_roles r WHERE r.user_id = u.id LIMIT 1);

-- Удаляем таблицу ролей, так как она больше не нужна
DROP TABLE IF EXISTS tb_user_roles;
