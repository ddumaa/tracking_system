ALTER TABLE tb_users
    ADD COLUMN IF NOT EXISTS role_expiration_date TIMESTAMP WITH TIME ZONE;

CREATE TABLE IF NOT EXISTS tb_user_roles (
                                             user_id BIGINT NOT NULL,
                                             role VARCHAR(50) NOT NULL,
                                             CONSTRAINT pk_tb_user_roles PRIMARY KEY (user_id, role),
                                             CONSTRAINT fk_tb_user_roles_user FOREIGN KEY (user_id) REFERENCES tb_users(id)
);