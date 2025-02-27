ALTER TABLE tb_users
    DROP COLUMN role_expiration_date,
    ADD COLUMN subscription_end_date TIMESTAMP WITH TIME ZONE;;
