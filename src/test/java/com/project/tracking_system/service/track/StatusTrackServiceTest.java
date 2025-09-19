package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.entity.GlobalStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Юнит-тесты для {@link StatusTrackService}, проверяющие корректное
 * сопоставление сырых строк статуса значениям {@link GlobalStatus}.
 */
class StatusTrackServiceTest {

    private final StatusTrackService service = new StatusTrackService();

    /**
     * Статус прибытия от Белпочты должен преобразовываться в
     * {@link GlobalStatus#WAITING_FOR_CUSTOMER}. Наличие пробелов в начале
     * и конце строки не должно влиять на результат.
     */
    @Test
    void setStatus_MapsArrivalToWaiting() {
        List<TrackInfoDTO> list = List.of(
                new TrackInfoDTO("18.07.2025, 10:36", " Поступило в учреждение доставки ")
        );

        GlobalStatus status = service.setStatus(list);

        assertEquals(GlobalStatus.WAITING_FOR_CUSTOMER, status);
    }

    /**
     * Строка со статусом о выдаче должна приводить к статусу
     * {@link GlobalStatus#DELIVERED}.
     */
    @Test
    void setStatus_MapsDelivered() {
        List<TrackInfoDTO> list = List.of(
                new TrackInfoDTO("19.07.2025, 11:00", "Почтовое отправление выдано")
        );

        GlobalStatus status = service.setStatus(list);

        assertEquals(GlobalStatus.DELIVERED, status);
    }

    /**
     * Если вручение отменено, то итоговый статус должен вернуться к ожиданию клиента,
     * даже если ранее отправление отмечалось как вручённое.
     */
    @Test
    void setStatus_AnnulmentOverridesDelivered() {
        List<TrackInfoDTO> list = List.of(
                new TrackInfoDTO("20.07.2025, 15:30", "Аннулирование операции вручения"),
                new TrackInfoDTO("20.07.2025, 14:00", "Вручено")
        );

        GlobalStatus status = service.setStatus(list);

        assertEquals(GlobalStatus.WAITING_FOR_CUSTOMER, status);
    }

    /**
     * Проверяет, что статус «Подготовлено для возврата» приводит к
     * {@link GlobalStatus#RETURN_IN_PROGRESS}.
     */
    @Test
    void setStatus_MapsPreparedForReturn() {
        List<TrackInfoDTO> list = List.of(
                new TrackInfoDTO(null, "Подготовлено для возврата")
        );

        GlobalStatus status = service.setStatus(list);

        assertEquals(GlobalStatus.RETURN_IN_PROGRESS, status);
    }

    /**
     * Убеждается, что после подготовки к возврату последующие статусы
     * корректно классифицируются как возврат в процессе.
     */
    @Test
    void setStatus_MapsIntermediateAfterReturnStart() {
        List<TrackInfoDTO> list = List.of(
                new TrackInfoDTO(null, "Почтовое отправление прибыло на сортировочный пункт"),
                new TrackInfoDTO(null, "Подготовлено для возврата")
        );

        GlobalStatus status = service.setStatus(list);

        assertEquals(GlobalStatus.RETURN_IN_PROGRESS, status);
    }

    /**
     * Проверяет, что формулировка о прибытии отправления для выдачи
     * относится к статусу {@link GlobalStatus#WAITING_FOR_CUSTOMER}.
     */
    @Test
    void setStatus_MapsArrivalForIssuance() {
        List<TrackInfoDTO> list = List.of(
                new TrackInfoDTO("20.07.2025, 12:00", "Почтовое отправление прибыло для выдачи")
        );

        GlobalStatus status = service.setStatus(list);

        assertEquals(GlobalStatus.WAITING_FOR_CUSTOMER, status);
    }
}
