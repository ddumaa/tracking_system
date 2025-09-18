ALTER TABLE tb_buyer_bot_screen_states
    ADD COLUMN IF NOT EXISTS contact_request_sent BOOLEAN DEFAULT FALSE;

UPDATE tb_buyer_bot_screen_states
SET contact_request_sent = FALSE
WHERE contact_request_sent IS NULL;
