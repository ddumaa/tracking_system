package com.project.tracking_system.service.analytics;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.DeliveryHistory;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.PostalServiceDailyStatistics;
import com.project.tracking_system.entity.PostalServiceStatistics;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreDailyStatistics;
import com.project.tracking_system.entity.StoreStatistics;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.repository.PostalServiceDailyStatisticsRepository;
import com.project.tracking_system.repository.PostalServiceStatisticsRepository;
import com.project.tracking_system.repository.StoreAnalyticsRepository;
import com.project.tracking_system.repository.StoreDailyStatisticsRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.service.customer.CustomerService;
import com.project.tracking_system.service.customer.CustomerStatsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Тесты для {@link DeliveryMetricsRollbackService} проверяют корректность восстановления статистики.
 */
@ExtendWith(MockitoExtension.class)
class DeliveryMetricsRollbackServiceTest {

    @Mock
    private StoreAnalyticsRepository storeAnalyticsRepository;
    @Mock
    private PostalServiceStatisticsRepository postalServiceStatisticsRepository;
    @Mock
    private StoreDailyStatisticsRepository storeDailyStatisticsRepository;
    @Mock
    private PostalServiceDailyStatisticsRepository postalServiceDailyStatisticsRepository;
    @Mock
    private TrackParcelRepository trackParcelRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private CustomerStatsService customerStatsService;

    @InjectMocks
    private DeliveryMetricsRollbackService deliveryMetricsRollbackService;

    @Test
    void rollbackFinalStatusMetrics_DeliveredStatus_UpdatesAggregatesAndCounters() {
        Store store = new Store();
        store.setId(10L);

        TrackParcel trackParcel = new TrackParcel();
        trackParcel.setId(1L);
        trackParcel.setNumber("RB123");
        trackParcel.setStore(store);
        trackParcel.setIncludedInStatistics(true);

        Customer customer = new Customer();
        customer.setId(5L);
        customer.setSentCount(2);
        trackParcel.setCustomer(customer);

        DeliveryHistory history = new DeliveryHistory();
        history.setStore(store);
        history.setTrackParcel(trackParcel);
        history.setPostalService(PostalServiceType.BELPOST);
        ZonedDateTime sendDate = ZonedDateTime.now(ZoneOffset.UTC).minusDays(5);
        ZonedDateTime arrivedDate = sendDate.plusDays(3);
        ZonedDateTime receivedDate = arrivedDate.plusDays(1);
        history.setSendDate(sendDate);
        history.setArrivedDate(arrivedDate);
        history.setReceivedDate(receivedDate);

        when(storeAnalyticsRepository.incrementDelivered(eq(store.getId()), eq(-1), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(1);
        when(postalServiceStatisticsRepository.incrementDelivered(
                eq(store.getId()),
                eq(PostalServiceType.BELPOST),
                eq(-1),
                any(BigDecimal.class),
                any(BigDecimal.class)))
                .thenReturn(1);
        when(storeDailyStatisticsRepository.incrementDelivered(
                eq(store.getId()),
                eq(receivedDate.toLocalDate()),
                eq(-1),
                any(BigDecimal.class),
                any(BigDecimal.class)))
                .thenReturn(1);
        when(postalServiceDailyStatisticsRepository.incrementDelivered(
                eq(store.getId()),
                eq(PostalServiceType.BELPOST),
                eq(receivedDate.toLocalDate()),
                eq(-1),
                any(BigDecimal.class),
                any(BigDecimal.class)))
                .thenReturn(1);
        when(trackParcelRepository.save(trackParcel)).thenReturn(trackParcel);
        when(customerStatsService.incrementSent(customer)).thenReturn(customer);
        doNothing().when(customerService).rollbackStatsOnTrackDelete(any(TrackParcel.class));

        deliveryMetricsRollbackService.rollbackFinalStatusMetrics(history, trackParcel, GlobalStatus.DELIVERED);

        assertFalse(trackParcel.isIncludedInStatistics());
        assertNull(history.getReceivedDate());
        assertNull(history.getReturnedDate());

        verify(storeAnalyticsRepository).incrementDelivered(eq(store.getId()), eq(-1), any(BigDecimal.class), any(BigDecimal.class));
        verify(postalServiceStatisticsRepository).incrementDelivered(
                eq(store.getId()),
                eq(PostalServiceType.BELPOST),
                eq(-1),
                any(BigDecimal.class),
                any(BigDecimal.class));
        verify(storeDailyStatisticsRepository).incrementDelivered(
                eq(store.getId()),
                eq(receivedDate.toLocalDate()),
                eq(-1),
                any(BigDecimal.class),
                any(BigDecimal.class));
        verify(postalServiceDailyStatisticsRepository).incrementDelivered(
                eq(store.getId()),
                eq(PostalServiceType.BELPOST),
                eq(receivedDate.toLocalDate()),
                eq(-1),
                any(BigDecimal.class),
                any(BigDecimal.class));
        verify(trackParcelRepository).save(trackParcel);

        ArgumentCaptor<TrackParcel> captor = ArgumentCaptor.forClass(TrackParcel.class);
        verify(customerService).rollbackStatsOnTrackDelete(captor.capture());
        TrackParcel synthetic = captor.getValue();
        assertEquals(GlobalStatus.DELIVERED, synthetic.getStatus());
        assertEquals(customer, synthetic.getCustomer());
        verify(customerStatsService).incrementSent(customer);
    }

    @Test
    void rollbackFinalStatusMetrics_ReturnedStatus_FallbackUpdatesEntities() {
        Store store = new Store();
        store.setId(20L);

        TrackParcel trackParcel = new TrackParcel();
        trackParcel.setId(2L);
        trackParcel.setNumber("RB999");
        trackParcel.setStore(store);
        trackParcel.setIncludedInStatistics(true);

        Customer customer = new Customer();
        customer.setId(7L);
        customer.setSentCount(0);
        trackParcel.setCustomer(customer);

        DeliveryHistory history = new DeliveryHistory();
        history.setStore(store);
        history.setTrackParcel(trackParcel);
        history.setPostalService(PostalServiceType.BELPOST);
        ZonedDateTime sendDate = ZonedDateTime.now(ZoneOffset.UTC).minusDays(4);
        ZonedDateTime arrivedDate = sendDate.plusDays(2);
        ZonedDateTime returnedDate = arrivedDate.plusDays(1);
        history.setSendDate(sendDate);
        history.setArrivedDate(arrivedDate);
        history.setReturnedDate(returnedDate);

        StoreStatistics storeStats = new StoreStatistics();
        storeStats.setStore(store);
        storeStats.setTotalReturned(1);
        storeStats.setSumDeliveryDays(new BigDecimal("1.0"));

        PostalServiceStatistics serviceStats = new PostalServiceStatistics();
        serviceStats.setStore(store);
        serviceStats.setPostalServiceType(PostalServiceType.BELPOST);
        serviceStats.setTotalReturned(1);
        serviceStats.setSumDeliveryDays(new BigDecimal("1.0"));

        StoreDailyStatistics storeDaily = new StoreDailyStatistics();
        storeDaily.setStore(store);
        storeDaily.setDate(returnedDate.toLocalDate());
        storeDaily.setReturned(1);
        storeDaily.setSumDeliveryDays(new BigDecimal("1.0"));

        PostalServiceDailyStatistics serviceDaily = new PostalServiceDailyStatistics();
        serviceDaily.setStore(store);
        serviceDaily.setPostalServiceType(PostalServiceType.BELPOST);
        serviceDaily.setDate(returnedDate.toLocalDate());
        serviceDaily.setReturned(1);
        serviceDaily.setSumDeliveryDays(new BigDecimal("1.0"));

        when(storeAnalyticsRepository.incrementReturned(eq(store.getId()), eq(-1), any(BigDecimal.class), eq(BigDecimal.ZERO)))
                .thenReturn(0);
        when(postalServiceStatisticsRepository.incrementReturned(
                eq(store.getId()),
                eq(PostalServiceType.BELPOST),
                eq(-1),
                any(BigDecimal.class),
                eq(BigDecimal.ZERO)))
                .thenReturn(0);
        when(storeDailyStatisticsRepository.incrementReturned(
                eq(store.getId()),
                eq(returnedDate.toLocalDate()),
                eq(-1),
                any(BigDecimal.class),
                eq(BigDecimal.ZERO)))
                .thenReturn(0);
        when(postalServiceDailyStatisticsRepository.incrementReturned(
                eq(store.getId()),
                eq(PostalServiceType.BELPOST),
                eq(returnedDate.toLocalDate()),
                eq(-1),
                any(BigDecimal.class),
                eq(BigDecimal.ZERO)))
                .thenReturn(0);

        when(storeAnalyticsRepository.findByStoreId(store.getId())).thenReturn(Optional.of(storeStats));
        when(postalServiceStatisticsRepository.findByStoreIdAndPostalServiceType(store.getId(), PostalServiceType.BELPOST))
                .thenReturn(Optional.of(serviceStats));
        when(storeDailyStatisticsRepository.findByStoreIdAndDate(store.getId(), returnedDate.toLocalDate()))
                .thenReturn(Optional.of(storeDaily));
        when(postalServiceDailyStatisticsRepository.findByStoreIdAndPostalServiceTypeAndDate(
                store.getId(), PostalServiceType.BELPOST, returnedDate.toLocalDate()))
                .thenReturn(Optional.of(serviceDaily));
        when(trackParcelRepository.save(trackParcel)).thenReturn(trackParcel);
        doNothing().when(customerService).rollbackStatsOnTrackDelete(any(TrackParcel.class));

        deliveryMetricsRollbackService.rollbackFinalStatusMetrics(history, trackParcel, GlobalStatus.RETURNED);

        assertFalse(trackParcel.isIncludedInStatistics());
        assertNull(history.getReceivedDate());
        assertNull(history.getReturnedDate());

        assertEquals(0, storeStats.getTotalReturned());
        assertTrue(storeStats.getSumDeliveryDays().compareTo(BigDecimal.ZERO) == 0);
        assertEquals(0, serviceStats.getTotalReturned());
        assertTrue(serviceStats.getSumDeliveryDays().compareTo(BigDecimal.ZERO) == 0);
        assertEquals(0, storeDaily.getReturned());
        assertTrue(storeDaily.getSumDeliveryDays().compareTo(BigDecimal.ZERO) == 0);
        assertEquals(0, serviceDaily.getReturned());
        assertTrue(serviceDaily.getSumDeliveryDays().compareTo(BigDecimal.ZERO) == 0);

        verify(storeAnalyticsRepository).save(storeStats);
        verify(postalServiceStatisticsRepository).save(serviceStats);
        verify(storeDailyStatisticsRepository).save(storeDaily);
        verify(postalServiceDailyStatisticsRepository).save(serviceDaily);
        verify(trackParcelRepository).save(trackParcel);
        verify(customerService).rollbackStatsOnTrackDelete(any(TrackParcel.class));
        verify(customerStatsService, never()).incrementSent(any(Customer.class));
    }
}
