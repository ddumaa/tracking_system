-- Создаёт таблицу запросов покупателей к магазину по обменным заявкам
CREATE TABLE tb_return_request_action_requests (
    id BIGSERIAL PRIMARY KEY,
    return_request_id BIGINT NOT NULL REFERENCES tb_order_return_requests (id) ON DELETE CASCADE,
    customer_id BIGINT NOT NULL REFERENCES tb_customers (id) ON DELETE RESTRICT,
    action VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),
    processed_at TIMESTAMPTZ
);

CREATE INDEX idx_return_request_action_requests_request
    ON tb_return_request_action_requests (return_request_id);

CREATE INDEX idx_return_request_action_requests_customer
    ON tb_return_request_action_requests (customer_id);
