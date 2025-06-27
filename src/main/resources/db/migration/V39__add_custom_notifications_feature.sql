-- Добавление функции пользовательских уведомлений
INSERT INTO subscription_features (subscription_plan_id, feature_key, enabled)
SELECT id, 'CUSTOM_NOTIFICATIONS', false FROM subscription_plans;
