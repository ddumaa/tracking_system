-- Update existing RETURNED_TO_SENDER values to RETURNED and fix constraint
UPDATE tb_track_parcels SET status = 'RETURNED'
WHERE status = 'RETURNED_TO_SENDER';

ALTER TABLE tb_track_parcels DROP CONSTRAINT IF EXISTS chk_status;
ALTER TABLE tb_track_parcels
    ADD CONSTRAINT chk_status CHECK (
        status IN ('DELIVERED', 'WAITING_FOR_CUSTOMER', 'IN_TRANSIT',
                   'CUSTOMER_NOT_PICKING_UP', 'RETURN_IN_PROGRESS',
                   'RETURN_PENDING_PICKUP', 'RETURNED', 'REGISTERED',
                   'UNKNOWN_STATUS')
    );
