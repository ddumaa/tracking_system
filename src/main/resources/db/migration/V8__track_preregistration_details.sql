-- Значение по умолчанию для времени статуса/предрегистрации
ALTER TABLE tb_track_parcels
    ALTER COLUMN timestamp SET DEFAULT now() AT TIME ZONE 'UTC';
