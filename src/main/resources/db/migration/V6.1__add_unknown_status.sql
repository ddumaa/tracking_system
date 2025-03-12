-- 1. Убираем старый CHECK, так как в PostgreSQL нельзя его просто обновить
ALTER TABLE tb_track_parcels DROP CONSTRAINT chk_status;

-- 2. Добавляем новый CHECK с `UNKNOWN_STATUS`
ALTER TABLE tb_track_parcels
    ADD CONSTRAINT chk_status CHECK (status IN (
                                                'DELIVERED',
                                                'WAITING_FOR_CUSTOMER',
                                                'IN_TRANSIT',
                                                'CUSTOMER_NOT_PICKING_UP',
                                                'RETURN_IN_PROGRESS',
                                                'RETURN_PENDING_PICKUP',
                                                'RETURNED_TO_SENDER',
                                                'REGISTERED',
                                                'UNKNOWN_STATUS' -- Добавленный статус
        ));