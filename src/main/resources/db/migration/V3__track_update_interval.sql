-- Create table for application-wide settings
CREATE TABLE tb_application_settings (
    id BIGINT PRIMARY KEY,
    track_update_interval_hours INT NOT NULL
);

-- Default settings row
INSERT INTO tb_application_settings(id, track_update_interval_hours)
VALUES (1, 3);

-- Add last_update column to parcels table
ALTER TABLE tb_track_parcels
    ADD COLUMN last_update TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC');
