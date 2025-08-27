-- Разрешить хранение NULL в колонке tracking_number для предрегистрации
ALTER TABLE tb_track_parcels
    ALTER COLUMN tracking_number DROP NOT NULL;
