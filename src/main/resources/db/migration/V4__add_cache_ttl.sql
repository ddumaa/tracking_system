ALTER TABLE tb_application_settings
    ADD COLUMN result_cache_expiration_ms BIGINT NOT NULL DEFAULT 60000;

UPDATE tb_application_settings
SET result_cache_expiration_ms = 60000
WHERE id = 1;
