DO $$
    BEGIN
        -- Добавляем evropost_username, если его нет
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'tb_users' AND column_name = 'evropost_username'
        ) THEN
            ALTER TABLE tb_users ADD COLUMN evropost_username VARCHAR(255);
        END IF;

        -- Добавляем evropost_password, если его нет
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'tb_users' AND column_name = 'evropost_password'
        ) THEN
            ALTER TABLE tb_users ADD COLUMN evropost_password TEXT;
        END IF;

        -- Добавляем jwt_token, если его нет
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'tb_users' AND column_name = 'jwt_token'
        ) THEN
            ALTER TABLE tb_users ADD COLUMN jwt_token TEXT;
        END IF;

        -- Добавляем service_number, если его нет
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'tb_users' AND column_name = 'service_number'
        ) THEN
            ALTER TABLE tb_users ADD COLUMN service_number VARCHAR(255);
        END IF;

        -- Добавляем token_created_at, если его нет
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'tb_users' AND column_name = 'token_created_at'
        ) THEN
            ALTER TABLE tb_users ADD COLUMN token_created_at TIMESTAMP;
        END IF;
    END $$;
