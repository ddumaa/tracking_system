INSERT INTO subscription_plans (name, max_tracks_per_file, max_saved_tracks, max_track_updates, allow_bulk_update)
VALUES
    ('FREE', 10, 10, 10, false),
    ('PREMIUM', NULL, NULL, NULL, true);
