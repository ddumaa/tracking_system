package com.project.tracking_system.service.analytics;

import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.customer.CustomerService;
import com.project.tracking_system.service.customer.CustomerStatsService;
import com.project.tracking_system.service.track.StatusTrackService;
import com.project.tracking_system.service.track.TypeDefinitionTrackPostService;
import com.project.tracking_system.service.telegram.TelegramNotificationService;
import com.project.tracking_system.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

import java.util.Optional;

/**
 * Тесты для {@link DeliveryHistoryService}.
 */
@ExtendWith(MockitoExtension.class)
class DeliveryHistoryServiceTest {

    @Mock
    private StoreAnalyticsRepository storeAnalyticsRepository;
    @Mock
    private DeliveryHistoryRepository deliveryHistoryRepository;
    @Mock
    private TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    @Mock
    private StatusTrackService statusTrackService;
    @Mock
    private TrackParcelRepository trackParcelRepository;
    @Mock
    private PostalServiceStatisticsRepository postalServiceStatisticsRepository;
    @Mock
    private StoreDailyStatisticsRepository storeDailyStatisticsRepository;
    @Mock
    private PostalServiceDailyStatisticsRepository postalServiceDailyStatisticsRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private CustomerStatsService customerStatsService;
    @Mock
    private TelegramNotificationService telegramNotificationService;
    @Mock
    private CustomerNotificationLogRepository customerNotificationLogRepository;
    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private DeliveryHistoryService deliveryHistoryService;

    /**
     * Проверяет, что метод не выбрасывает исключение,
     * если история доставки для посылки отсутствует.
     */
    @Test
    void registerFinalStatus_HistoryAbsent_NoException() {
        Long parcelId = 1L;
        when(deliveryHistoryRepository.findByTrackParcelId(parcelId)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> deliveryHistoryService.registerFinalStatus(parcelId));
    }
}
