CREATE TABLE tb_track_number_audit (
    id BIGSERIAL PRIMARY KEY,
    track_parcel_id BIGINT NOT NULL REFERENCES tb_track_parcels (id) ON DELETE CASCADE,
    old_number VARCHAR(50),
    new_number VARCHAR(50) NOT NULL,
    changed_by BIGINT NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_track_number_audit_parcel ON tb_track_number_audit (track_parcel_id);
CREATE INDEX idx_track_number_audit_changed_at ON tb_track_number_audit (changed_at);
