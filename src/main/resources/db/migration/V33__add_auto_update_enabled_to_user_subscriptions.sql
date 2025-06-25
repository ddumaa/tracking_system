-- Добавление флага разрешения автообновления треков
ALTER TABLE tb_user_subscriptions
    ADD COLUMN auto_update_enabled BOOLEAN DEFAULT TRUE;
