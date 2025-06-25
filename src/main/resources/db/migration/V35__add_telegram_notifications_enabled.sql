-- Включение глобального флага Telegram-уведомлений
ALTER TABLE tb_user_settings ADD COLUMN telegram_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;
