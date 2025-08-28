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
     * Любой промежуточный статус доставки должен преобразовываться в
     * {@link GlobalStatus#IN_TRANSIT}.
     */
    @Test
    void setStatus_MapsInTransit() {
        List<TrackInfoDTO> list = List.of(
                new TrackInfoDTO("20.07.2025, 09:00", "Почтовое отправление принято на ОПС")
        );

        GlobalStatus status = service.setStatus(list);

        assertEquals(GlobalStatus.IN_TRANSIT, status);
    }

    /**
     * Когда отправление подготовлено для возврата, должен быть установлен
     * статус {@link GlobalStatus#RETURN_IN_PROGRESS}.
     */
    @Test
    void setStatus_MapsReturnInProgress() {
        List<TrackInfoDTO> list = List.of(
                new TrackInfoDTO("21.07.2025, 12:00", "Почтовое отправление готово к возврату")
        );

        GlobalStatus status = service.setStatus(list);

        assertEquals(GlobalStatus.RETURN_IN_PROGRESS, status);
    }

    /**
     * При прибытии отправления в отделение для возврата сервис должен
     * вернуть статус {@link GlobalStatus#RETURN_PENDING_PICKUP}.
     */
    @Test
    void setStatus_MapsReturnPendingPickup() {
        List<TrackInfoDTO> list = List.of(
                new TrackInfoDTO("22.07.2025, 08:45",
                        "Почтовое отправление прибыло на Отделение №1 для возврата отправителю")
        );

        GlobalStatus status = service.setStatus(list);

        assertEquals(GlobalStatus.RETURN_PENDING_PICKUP, status);
    }

    /**
     * После фактического возврата отправление должно получать статус
     * {@link GlobalStatus#RETURNED}.
     */
    @Test
    void setStatus_MapsReturned() {
        List<TrackInfoDTO> list = List.of(
                new TrackInfoDTO("23.07.2025, 14:30", "Почтовое отправление возвращено отправителю")
        );

        GlobalStatus status = service.setStatus(list);

        assertEquals(GlobalStatus.RETURNED, status);
    }

    /**
     * Уведомление о невостребованном отправлении должно приводить к статусу
     * {@link GlobalStatus#CUSTOMER_NOT_PICKING_UP}.
     */
    @Test
    void setStatus_MapsCustomerNotPickingUp() {
        List<TrackInfoDTO> list = List.of(
                new TrackInfoDTO("24.07.2025, 16:00",
                        "Добрый день. Отправление AB123456789BY не востребовано получателем")
        );

        GlobalStatus status = service.setStatus(list);

        assertEquals(GlobalStatus.CUSTOMER_NOT_PICKING_UP, status);
    }
}
