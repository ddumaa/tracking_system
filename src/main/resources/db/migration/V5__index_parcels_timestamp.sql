-- flyway:transactional=false
-- Add index to speed up queries by timestamp when searching parcels
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_parcels_timestamp
    ON tb_track_parcels(timestamp);
