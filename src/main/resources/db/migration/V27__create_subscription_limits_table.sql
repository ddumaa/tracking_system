CREATE TABLE subscription_limits (
    id SERIAL PRIMARY KEY,
    subscription_plan_id BIGINT NOT NULL UNIQUE REFERENCES subscription_plans(id),
    max_tracks_per_file INT,
    max_saved_tracks INT,
    max_track_updates INT,
    allow_bulk_update BOOLEAN NOT NULL DEFAULT FALSE,
    max_stores INT NOT NULL DEFAULT 1,
    allow_telegram_notifications BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO subscription_limits (subscription_plan_id, max_tracks_per_file, max_saved_tracks, max_track_updates, allow_bulk_update, max_stores, allow_telegram_notifications)
SELECT id, max_tracks_per_file, max_saved_tracks, max_track_updates, allow_bulk_update, max_stores, allow_telegram_notifications
FROM subscription_plans;

ALTER TABLE subscription_plans
    DROP COLUMN IF EXISTS max_tracks_per_file,
    DROP COLUMN IF EXISTS max_saved_tracks,
    DROP COLUMN IF EXISTS max_track_updates,
    DROP COLUMN IF EXISTS allow_bulk_update,
    DROP COLUMN IF EXISTS max_stores,
    DROP COLUMN IF EXISTS allow_telegram_notifications;
