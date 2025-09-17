-- Добавляет хранение сценарного состояния чата покупателя
ALTER TABLE tb_buyer_bot_screen_states
    ADD COLUMN chat_state VARCHAR(50) DEFAULT 'IDLE';

UPDATE tb_buyer_bot_screen_states
SET chat_state = 'IDLE'
WHERE chat_state IS NULL;

ALTER TABLE tb_buyer_bot_screen_states
    ALTER COLUMN chat_state SET NOT NULL;
