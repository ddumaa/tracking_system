-- 1. Добавляем временное поле для хранения значения с часовым поясом
ALTER TABLE tb_track_parcels ADD COLUMN data_temp TIMESTAMPTZ;

-- 2. Копируем значения, парсим строку как локальное время и приводим к UTC
UPDATE tb_track_parcels
SET data_temp = to_timestamp(data, 'DD.MM.YYYY HH24:MI:SS') AT TIME ZONE 'Europe/Minsk';

-- 3. Удаляем старое строковое поле
ALTER TABLE tb_track_parcels DROP COLUMN data;

-- 4. Переименовываем колонку обратно
ALTER TABLE tb_track_parcels RENAME COLUMN data_temp TO data;

-- 5. Устанавливаем NOT NULL
ALTER TABLE tb_track_parcels ALTER COLUMN data SET NOT NULL;
