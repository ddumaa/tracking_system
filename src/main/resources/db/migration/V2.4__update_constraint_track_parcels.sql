ALTER TABLE tb_track_parcels DROP CONSTRAINT IF EXISTS tb_track_parcels_tracking_number_key;

ALTER TABLE tb_track_parcels
ADD CONSTRAINT tb_track_parcels_tracking_number_user_id_key UNIQUE (tracking_number, user_id);
