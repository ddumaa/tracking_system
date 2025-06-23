-- Добавление функции автоматического обновления треков
INSERT INTO subscription_features (subscription_plan_id, feature_key, enabled)
SELECT id, 'AUTO_UPDATE', false FROM subscription_plans;

-- Планировщик автоматического обновления треков
INSERT INTO tb_scheduled_task_config(id, description, cron, zone)
VALUES (6, 'Автообновление треков', '0 0 6 * * *', 'UTC');
