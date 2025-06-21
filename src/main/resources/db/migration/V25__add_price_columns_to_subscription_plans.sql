ALTER TABLE subscription_plans
    ADD COLUMN monthly_price DECIMAL(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN annual_price DECIMAL(10,2) NOT NULL DEFAULT 0;
