ALTER TABLE subscription_plans
    ADD COLUMN allow_telegram_notifications BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE subscription_plans SET allow_telegram_notifications = TRUE WHERE code = 'PREMIUM';
