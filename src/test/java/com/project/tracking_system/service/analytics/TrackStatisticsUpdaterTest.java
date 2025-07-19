package com.project.tracking_system.service.analytics;

import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.track.TypeDefinitionTrackPostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TrackStatisticsUpdater}.
 */
@ExtendWith(MockitoExtension.class)
class TrackStatisticsUpdaterTest {

    @Mock
    private StoreAnalyticsRepository storeAnalyticsRepository;
    @Mock
    private PostalServiceStatisticsRepository postalServiceStatisticsRepository;
    @Mock
    private StoreDailyStatisticsRepository storeDailyStatisticsRepository;
    @Mock
    private PostalServiceDailyStatisticsRepository postalServiceDailyStatisticsRepository;
    @Mock
    private TypeDefinitionTrackPostService typeDefinitionTrackPostService;

    @InjectMocks
    private TrackStatisticsUpdater updater;

    private TrackParcel parcel;
    private Store store;

    @BeforeEach
    void setUp() {
        store = new Store();
        store.setId(2L);
        parcel = new TrackParcel();
        parcel.setStore(store);
        parcel.setNumber("AA111");
        parcel.setTimestamp(ZonedDateTime.now());
    }

    @Test
    void updateStatistics_NewParcel_Increments() {
        StoreStatistics st = new StoreStatistics();
        st.setStore(store);
        when(typeDefinitionTrackPostService.detectPostalService(anyString()))
                .thenReturn(PostalServiceType.BELPOST);
        when(storeAnalyticsRepository.findByStoreId(2L)).thenReturn(java.util.Optional.of(st));
        when(storeAnalyticsRepository.incrementTotalSent(2L, 1)).thenReturn(1);
        when(storeDailyStatisticsRepository.incrementSent(eq(2L), any(LocalDate.class), eq(1)))
                .thenReturn(1);
        when(postalServiceStatisticsRepository.incrementTotalSent(2L, PostalServiceType.BELPOST, 1))
                .thenReturn(1);
        when(postalServiceDailyStatisticsRepository.incrementSent(eq(2L), eq(PostalServiceType.BELPOST), any(LocalDate.class), eq(1)))
                .thenReturn(1);

        updater.updateStatistics(parcel, true, null, null);

        verify(storeAnalyticsRepository).incrementTotalSent(2L, 1);
        verify(postalServiceStatisticsRepository).incrementTotalSent(2L, PostalServiceType.BELPOST, 1);
        verify(storeDailyStatisticsRepository).incrementSent(eq(2L), any(LocalDate.class), eq(1));
        verify(postalServiceDailyStatisticsRepository)
                .incrementSent(eq(2L), eq(PostalServiceType.BELPOST), any(LocalDate.class), eq(1));
        verify(storeAnalyticsRepository, never()).findByStoreId(1L);
    }

    @Test
    void updateStatistics_StoreChanged_DecrementsOld() {
        StoreStatistics newStats = new StoreStatistics();
        newStats.setStore(store);
        StoreStatistics oldStats = new StoreStatistics();
        oldStats.setStore(new Store());
        oldStats.setTotalSent(1);
        when(typeDefinitionTrackPostService.detectPostalService(anyString()))
                .thenReturn(PostalServiceType.BELPOST);
        when(storeAnalyticsRepository.findByStoreId(2L)).thenReturn(java.util.Optional.of(newStats));
        when(storeAnalyticsRepository.incrementTotalSent(2L, 1)).thenReturn(1);
        when(storeAnalyticsRepository.findByStoreId(1L)).thenReturn(java.util.Optional.of(oldStats));
        when(storeDailyStatisticsRepository.incrementSent(eq(2L), any(LocalDate.class), eq(1))).thenReturn(1);
        when(postalServiceStatisticsRepository.incrementTotalSent(2L, PostalServiceType.BELPOST, 1)).thenReturn(1);
        when(postalServiceDailyStatisticsRepository.incrementSent(eq(2L), eq(PostalServiceType.BELPOST), any(LocalDate.class), eq(1))).thenReturn(1);
        StoreDailyStatistics oldDaily = new StoreDailyStatistics();
        oldDaily.setSent(1);
        when(storeDailyStatisticsRepository.findByStoreIdAndDate(eq(1L), any(LocalDate.class))).thenReturn(java.util.Optional.of(oldDaily));
        PostalServiceStatistics oldPs = new PostalServiceStatistics();
        oldPs.setTotalSent(1);
        when(postalServiceStatisticsRepository.findByStoreIdAndPostalServiceType(1L, PostalServiceType.BELPOST))
                .thenReturn(java.util.Optional.of(oldPs));
        PostalServiceDailyStatistics oldPsDaily = new PostalServiceDailyStatistics();
        oldPsDaily.setSent(1);
        when(postalServiceDailyStatisticsRepository.findByStoreIdAndPostalServiceTypeAndDate(eq(1L), eq(PostalServiceType.BELPOST), any(LocalDate.class)))
                .thenReturn(java.util.Optional.of(oldPsDaily));

        ZonedDateTime prev = ZonedDateTime.now().minusDays(1);
        updater.updateStatistics(parcel, false, 1L, prev);

        verify(storeAnalyticsRepository).incrementTotalSent(2L, 1);
        verify(storeAnalyticsRepository).save(oldStats);
        verify(postalServiceStatisticsRepository).save(oldPs);
        verify(storeDailyStatisticsRepository).save(oldDaily);
        verify(postalServiceDailyStatisticsRepository).save(oldPsDaily);
    }
}
