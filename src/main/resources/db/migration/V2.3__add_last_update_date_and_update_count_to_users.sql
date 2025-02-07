ALTER TABLE tb_users
    ADD COLUMN last_update_date timestamp with time zone,
    ADD COLUMN update_count int DEFAULT 0;

ALTER TABLE tb_users OWNER TO postgres;
