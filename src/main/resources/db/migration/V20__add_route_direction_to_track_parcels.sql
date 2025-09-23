ALTER TABLE tb_track_parcels
    ADD COLUMN route_direction VARCHAR(32) NOT NULL DEFAULT 'TO_CUSTOMER';

UPDATE tb_track_parcels
SET route_direction = 'TO_STORE'
WHERE status IN ('RETURN_IN_PROGRESS', 'RETURN_PENDING_PICKUP', 'RETURNED');

CREATE INDEX idx_track_parcels_route_direction
    ON tb_track_parcels(route_direction);
