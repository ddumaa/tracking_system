package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserSubscriptionRepository;
import com.project.tracking_system.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Проверяем сортировку посылок по дате создания.
 */
@ExtendWith(MockitoExtension.class)
class TrackParcelServiceSortingTest {

    @Mock
    private UserService userService;
    @Mock
    private TrackParcelRepository trackParcelRepository;
    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    private TrackParcelService service;

    @BeforeEach
    void setUp() {
        service = new TrackParcelService(userService, trackParcelRepository, userSubscriptionRepository);
    }

    /**
     * Сортировка в порядке возрастания возвращает посылки от старой к новой.
     */
    @Test
    void getParcelsSortedByDate_Ascending_ReturnsAscendingList() {
        ZonedDateTime now = ZonedDateTime.now();
        TrackParcel older = buildParcel("P1", now.minusDays(1));
        TrackParcel newer = buildParcel("P2", now);
        Sort sort = Sort.by("timestamp").ascending();
        when(trackParcelRepository.findByUserId(1L, sort)).thenReturn(List.of(older, newer));
        when(userService.getUserZone(1L)).thenReturn(ZoneId.systemDefault());

        List<TrackParcelDTO> result = service.getParcelsSortedByDate(1L, "asc");

        assertEquals("P1", result.get(0).getNumber());
        assertEquals("P2", result.get(1).getNumber());
    }

    /**
     * Сортировка в порядке убывания возвращает посылки от новой к старой.
     */
    @Test
    void getParcelsSortedByDate_Descending_ReturnsDescendingList() {
        ZonedDateTime now = ZonedDateTime.now();
        TrackParcel older = buildParcel("P1", now.minusDays(1));
        TrackParcel newer = buildParcel("P2", now);
        Sort sort = Sort.by("timestamp").descending();
        when(trackParcelRepository.findByUserId(1L, sort)).thenReturn(List.of(newer, older));
        when(userService.getUserZone(1L)).thenReturn(ZoneId.systemDefault());

        List<TrackParcelDTO> result = service.getParcelsSortedByDate(1L, "desc");

        assertEquals("P2", result.get(0).getNumber());
        assertEquals("P1", result.get(1).getNumber());
    }

    /**
     * Создает тестовую посылку с указанным временем.
     */
    private static TrackParcel buildParcel(String number, ZonedDateTime timestamp) {
        TrackParcel parcel = new TrackParcel();
        parcel.setNumber(number);
        parcel.setStatus(GlobalStatus.IN_TRANSIT);
        parcel.setTimestamp(timestamp);
        parcel.setLastUpdate(timestamp);
        Store store = new Store();
        store.setId(1L);
        parcel.setStore(store);
        return parcel;
    }
}
