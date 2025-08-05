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
}
