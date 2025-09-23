ALTER TABLE tb_track_parcels
    ADD COLUMN route_direction VARCHAR(32) DEFAULT 'TO_CUSTOMER' NOT NULL;

CREATE INDEX idx_track_parcels_route_direction
    ON tb_track_parcels(route_direction);
