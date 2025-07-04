ALTER TABLE tb_store_telegram_settings
  ADD COLUMN reminder_template TEXT,
  DROP COLUMN custom_signature;
