CREATE TABLE subscription_features (
    id SERIAL PRIMARY KEY,
    subscription_plan_id BIGINT NOT NULL REFERENCES subscription_plans(id),
    feature_key VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_plan_feature UNIQUE (subscription_plan_id, feature_key)
);

INSERT INTO subscription_features (subscription_plan_id, feature_key, enabled)
SELECT subscription_plan_id, 'BULK_UPDATE', allow_bulk_update
FROM subscription_limits;

INSERT INTO subscription_features (subscription_plan_id, feature_key, enabled)
SELECT subscription_plan_id, 'TELEGRAM_NOTIFICATIONS', allow_telegram_notifications
FROM subscription_limits;

ALTER TABLE subscription_limits
    DROP COLUMN IF EXISTS allow_bulk_update,
    DROP COLUMN IF EXISTS allow_telegram_notifications;
