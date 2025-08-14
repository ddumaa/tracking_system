-- Добавление флага предварительной регистрации
ALTER TABLE tb_track_parcels
    ADD COLUMN pre_registered BOOLEAN NOT NULL DEFAULT FALSE;

-- Индекс для быстрого поиска предзарегистрированных посылок
CREATE INDEX idx_parcels_prereg ON tb_track_parcels(pre_registered);
