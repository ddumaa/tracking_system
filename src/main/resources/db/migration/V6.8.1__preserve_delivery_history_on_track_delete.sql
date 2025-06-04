-- Удаляем старый внешний ключ, если есть
ALTER TABLE tb_delivery_history
    DROP CONSTRAINT IF EXISTS fk_delivery_history_track_parcel;

-- Делаем колонку track_parcel_id nullable
ALTER TABLE tb_delivery_history
    ALTER COLUMN track_parcel_id DROP NOT NULL;

-- Добавляем внешний ключ с ON DELETE SET NULL
ALTER TABLE tb_delivery_history
    ADD CONSTRAINT fk_delivery_history_track_parcel
        FOREIGN KEY (track_parcel_id)
            REFERENCES tb_track_parcels(id)
            ON DELETE SET NULL;
