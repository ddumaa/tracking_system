-- Изменение значения по умолчанию для флага повторного показа административных уведомлений

ALTER TABLE tb_admin_notifications
    ALTER COLUMN reset_requested SET DEFAULT FALSE;

UPDATE tb_admin_notifications
SET reset_requested = FALSE
WHERE reset_requested IS NULL;
