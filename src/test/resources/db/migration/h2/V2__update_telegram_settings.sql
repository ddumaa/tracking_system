ALTER TABLE tb_store_telegram_settings
    ADD COLUMN reminder_template TEXT;

ALTER TABLE tb_store_telegram_settings
    DROP COLUMN custom_signature;