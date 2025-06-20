CREATE TABLE tb_scheduled_task_config (
    id BIGINT PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    cron VARCHAR(100) NOT NULL,
    zone VARCHAR(50)
);

INSERT INTO tb_scheduled_task_config(id, description, cron, zone) VALUES
 (1, 'Агрегация статистики', '0 0 2 * * *', 'UTC'),
 (2, 'Напоминания Telegram', '0 0 8 * * *', 'UTC'),
 (3, 'Обновление JWT токенов', '0 0 0 * * ?', NULL),
 (4, 'Проверка подписок', '0 0 3 * * *', 'UTC'),
 (5, 'Очистка токенов', '0 0 * * * *', NULL);
