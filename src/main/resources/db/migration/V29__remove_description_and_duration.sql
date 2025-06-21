ALTER TABLE subscription_plans
    DROP COLUMN IF EXISTS description,
    DROP COLUMN IF EXISTS duration_days;
