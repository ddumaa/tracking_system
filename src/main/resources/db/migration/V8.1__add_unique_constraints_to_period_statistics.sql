-- Add unique constraints to prevent duplicate period statistics entries
ALTER TABLE tb_store_statistics_weekly
    ADD CONSTRAINT IF NOT EXISTS uk_store_stats_weekly
        UNIQUE (store_id, period_year, period_number);

ALTER TABLE tb_store_statistics_monthly
    ADD CONSTRAINT IF NOT EXISTS uk_store_stats_monthly
        UNIQUE (store_id, period_year, period_number);

ALTER TABLE tb_store_statistics_yearly
    ADD CONSTRAINT IF NOT EXISTS uk_store_stats_yearly
        UNIQUE (store_id, period_year, period_number);

ALTER TABLE tb_postal_service_statistics_weekly
    ADD CONSTRAINT IF NOT EXISTS uk_postal_stats_weekly
        UNIQUE (store_id, postal_service_type, period_year, period_number);

ALTER TABLE tb_postal_service_statistics_monthly
    ADD CONSTRAINT IF NOT EXISTS uk_postal_stats_monthly
        UNIQUE (store_id, postal_service_type, period_year, period_number);

ALTER TABLE tb_postal_service_statistics_yearly
    ADD CONSTRAINT IF NOT EXISTS uk_postal_stats_yearly
        UNIQUE (store_id, postal_service_type, period_year, period_number);
