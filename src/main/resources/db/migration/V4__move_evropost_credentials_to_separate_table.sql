-- Flyway миграция для создания таблицы tb_evropost_service_credentials и переноса данных

-- 1. Создание новой таблицы tb_evropost_service_credentials
CREATE TABLE tb_evropost_service_credentials (
                                                 id BIGSERIAL PRIMARY KEY,
                                                 user_id BIGINT NOT NULL REFERENCES tb_users(id) ON DELETE CASCADE,
                                                 username VARCHAR(255),
                                                 password TEXT,
                                                 jwt_token TEXT,
                                                 token_created_at TIMESTAMP,
                                                 service_number VARCHAR(255),
                                                 use_custom_credentials BOOLEAN DEFAULT FALSE NOT NULL
);

-- 2. Перенос данных из таблицы tb_users
INSERT INTO tb_evropost_service_credentials (
    user_id,
    username,
    password,
    jwt_token,
    token_created_at,
    service_number,
    use_custom_credentials
)
SELECT id,
       evropost_username,
       evropost_password,
       jwt_token,
       token_created_at,
       service_number,
       use_custom_credentials
FROM tb_users
WHERE use_custom_credentials IS NOT NULL;

-- 3. Удаление старых колонок из tb_users
ALTER TABLE tb_users
    DROP COLUMN evropost_username,
    DROP COLUMN evropost_password,
    DROP COLUMN jwt_token,
    DROP COLUMN token_created_at,
    DROP COLUMN service_number,
    DROP COLUMN use_custom_credentials;