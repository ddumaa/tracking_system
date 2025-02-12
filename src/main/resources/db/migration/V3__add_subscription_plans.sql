CREATE TABLE subscription_plans (
                                    id SERIAL PRIMARY KEY,
                                    name VARCHAR(255) NOT NULL,
                                    max_tracks_per_file INT DEFAULT NULL,
                                    max_saved_tracks INT DEFAULT NULL,
                                    max_track_updates INT DEFAULT NULL,
                                    allow_bulk_update BOOLEAN NOT NULL
);