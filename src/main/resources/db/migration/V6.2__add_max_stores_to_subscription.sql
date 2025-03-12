ALTER TABLE subscription_plans ADD COLUMN max_stores INT NOT NULL DEFAULT 1;

UPDATE subscription_plans SET max_stores = 1 WHERE name = 'FREE';
UPDATE subscription_plans SET max_stores = 10 WHERE name = 'PREMIUM';
