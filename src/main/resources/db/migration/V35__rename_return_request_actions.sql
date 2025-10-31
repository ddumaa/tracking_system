-- Обновляет сохранённые действия возвратов на новые имена перечисления
UPDATE tb_return_request_action_requests
SET action = 'CLOSE_REQUEST'
WHERE action = 'CANCEL_RETURN';

UPDATE tb_return_request_action_requests
SET action = 'SET_MODE_RETURN'
WHERE action = 'CONVERT_TO_RETURN';
