ALTER TABLE tb_track_parcels
    ADD COLUMN route_direction VARCHAR(32) NOT NULL DEFAULT 'TO_CUSTOMER';

CREATE INDEX idx_track_parcels_route_direction
    ON tb_track_parcels(route_direction);
