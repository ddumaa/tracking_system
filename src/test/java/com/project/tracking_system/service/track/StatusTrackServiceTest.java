package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.entity.GlobalStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link StatusTrackService} verifying correct mapping
 * of raw status strings to {@link GlobalStatus} values.
 */
class StatusTrackServiceTest {

    private final StatusTrackService service = new StatusTrackService();

    /**
     * Arrival status from Belpost should map to {@link GlobalStatus#WAITING_FOR_CUSTOMER}.
     * Leading and trailing spaces must not affect the result.
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
     * Status text about delivery must result in {@link GlobalStatus#DELIVERED}.
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
