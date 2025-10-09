package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackDetailsDto;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.OrderEpisode;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.OrderReturnRequestStatus;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.OrderReturnRequestActionRequestRepository;
import com.project.tracking_system.repository.OrderReturnRequestRepository;
import com.project.tracking_system.service.admin.ApplicationSettingsService;
import com.project.tracking_system.service.order.OrderEpisodeLifecycleService;
import com.project.tracking_system.service.order.OrderExchangeService;
import com.project.tracking_system.service.order.OrderReturnRequestService;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.service.track.TrackParcelService;
import com.project.tracking_system.service.track.TrackStatusEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Интеграционный тест проверяет, что модалка трека получает актуальные данные после инвалидации кэша.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TrackViewCacheEvictionIntegrationTest.Config.class)
class TrackViewCacheEvictionIntegrationTest {

    @Autowired
    private TrackViewService trackViewService;
    @Autowired
    private OrderReturnRequestService orderReturnRequestService;
    @Autowired
    private TrackParcelService trackParcelService;
    @Autowired
    private TrackStatusEventService trackStatusEventService;
    @Autowired
    private UserService userService;
    @Autowired
    private ApplicationSettingsService applicationSettingsService;
    @Autowired
    private OrderReturnRequestRepository orderReturnRequestRepository;
    @Autowired
    private OrderExchangeService orderExchangeService;

    /**
     * Моделирует сценарий «открыли модалку → одобрили обмен → снова открыли модалку».
     * <p>
     * Первый вызов {@link TrackViewService#getTrackDetails(Long, Long)} кэшируется. После
     * изменения заявки через {@link OrderReturnRequestService#approveExchange(Long, Long, User)}
     * проверяем, что повторный вызов возвращает статус обмена и признак обмена в DTO.
     * </p>
     */
    @Test
    void getTrackDetails_RecalculatedAfterExchangeApprovalWhenCacheEvicted() {
        Long parcelId = 42L;
        Long userId = 7L;

        User owner = new User();
        owner.setId(userId);

        TrackParcel parcel = new TrackParcel();
        parcel.setId(parcelId);
        parcel.setNumber("TRK42");
        parcel.setStatus(GlobalStatus.DELIVERED);
        parcel.setTimestamp(ZonedDateTime.now(ZoneOffset.UTC).minusHours(2));
        parcel.setLastUpdate(ZonedDateTime.now(ZoneOffset.UTC).minusHours(1));
        parcel.setUser(owner);
        OrderEpisode episode = new OrderEpisode();
        episode.setId(900L);
        parcel.setEpisode(episode);

        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(555L);
        request.setParcel(parcel);
        request.setEpisode(episode);
        request.setStatus(OrderReturnRequestStatus.REGISTERED);
        request.setCreatedAt(ZonedDateTime.now(ZoneOffset.UTC).minusHours(3));
        request.setCreatedBy(owner);

        when(trackParcelService.findOwnedById(parcelId, userId)).thenReturn(Optional.of(parcel));
        when(trackParcelService.findEpisodeParcels(episode.getId(), userId)).thenReturn(List.of(parcel));
        when(trackStatusEventService.findEvents(parcelId)).thenReturn(List.of());
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(4);
        when(userService.getUserZone(userId)).thenReturn(ZoneId.of("UTC"));
        when(orderReturnRequestRepository.findFirstByParcel_IdAndStatusIn(eq(parcelId), anyCollection()))
                .thenReturn(Optional.of(request));
        when(orderReturnRequestRepository.existsByEpisode_IdAndStatus(episode.getId(),
                OrderReturnRequestStatus.EXCHANGE_APPROVED)).thenReturn(false);
        when(orderReturnRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(orderReturnRequestRepository.save(any(OrderReturnRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderExchangeService.findLatestExchangeParcel(any())).thenReturn(Optional.empty());
        when(orderExchangeService.getLatestExchangeParcelOrThrowIfTracked(any())).thenReturn(Optional.empty());

        TrackDetailsDto initialDetails = trackViewService.getTrackDetails(parcelId, userId);

        assertThat(initialDetails.returnRequest()).isNotNull();
        assertThat(initialDetails.returnRequest().status())
                .isEqualTo(OrderReturnRequestStatus.REGISTERED.getDisplayName());
        assertThat(initialDetails.returnRequest().exchangeApproved()).isFalse();

        orderReturnRequestService.approveExchange(request.getId(), parcelId, owner);

        TrackDetailsDto refreshedDetails = trackViewService.getTrackDetails(parcelId, userId);

        assertThat(refreshedDetails.returnRequest()).isNotNull();
        assertThat(refreshedDetails.returnRequest().status())
                .isEqualTo(OrderReturnRequestStatus.EXCHANGE_APPROVED.getDisplayName());
        assertThat(refreshedDetails.returnRequest().exchangeApproved()).isTrue();

        verify(orderReturnRequestRepository, times(2)).findFirstByParcel_IdAndStatusIn(eq(parcelId), anyCollection());
    }

    /**
     * Тестовая конфигурация поднимает минимальный контекст с включённым кэшированием.
     */
    @Configuration
    @EnableCaching
    static class Config {

        /**
         * Предоставляет потокобезопасный менеджер кэшей для тестов.
         */
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("track-details");
        }

        /**
         * Создаёт инвалидатор, который будет очищать кэш после изменений заявок.
         */
        @Bean
        TrackViewCacheInvalidator trackViewCacheInvalidator(CacheManager cacheManager) {
            return new TrackViewCacheInvalidator(cacheManager);
        }

        /**
         * Возвращает мок сервиса посылок для подстановки тестовых данных.
         */
        @Bean
        TrackParcelService trackParcelService() {
            return mock(TrackParcelService.class);
        }

        /**
         * Возвращает мок сервиса статусов, чтобы тест не зависел от БД.
         */
        @Bean
        TrackStatusEventService trackStatusEventService() {
            return mock(TrackStatusEventService.class);
        }

        /**
         * Возвращает мок сервиса пользователей.
         */
        @Bean
        UserService userService() {
            return mock(UserService.class);
        }

        /**
         * Возвращает мок сервиса настроек приложения.
         */
        @Bean
        ApplicationSettingsService applicationSettingsService() {
            return mock(ApplicationSettingsService.class);
        }

        /**
         * Возвращает мок репозитория заявок для контроля возвращаемых данных.
         */
        @Bean
        OrderReturnRequestRepository orderReturnRequestRepository() {
            return mock(OrderReturnRequestRepository.class);
        }

        /**
         * Возвращает мок репозитория запросов действий.
         */
        @Bean
        OrderReturnRequestActionRequestRepository orderReturnRequestActionRequestRepository() {
            return mock(OrderReturnRequestActionRequestRepository.class);
        }

        /**
         * Возвращает мок сервиса управления эпизодами.
         */
        @Bean
        OrderEpisodeLifecycleService orderEpisodeLifecycleService() {
            return mock(OrderEpisodeLifecycleService.class);
        }

        /**
         * Возвращает мок сервиса обменных посылок.
         */
        @Bean
        OrderExchangeService orderExchangeService() {
            return mock(OrderExchangeService.class);
        }

        /**
         * Создаёт сервис заявок с внедрёнными зависимостями.
         */
        @Bean
        OrderReturnRequestService orderReturnRequestService(OrderReturnRequestRepository repository,
                                                            OrderReturnRequestActionRequestRepository actionRepository,
                                                            TrackParcelService trackParcelService,
                                                            OrderEpisodeLifecycleService episodeLifecycleService,
                                                            OrderExchangeService orderExchangeService,
                                                            TrackViewCacheInvalidator trackViewCacheInvalidator) {
            return new OrderReturnRequestService(repository, actionRepository, trackParcelService,
                    episodeLifecycleService, orderExchangeService, trackViewCacheInvalidator);
        }

        /**
         * Создаёт сервис просмотра треков, который будет участвовать в тесте кэширования.
         */
        @Bean
        TrackViewService trackViewService(TrackParcelService trackParcelService,
                                          TrackStatusEventService trackStatusEventService,
                                          UserService userService,
                                          ApplicationSettingsService applicationSettingsService,
                                          OrderReturnRequestService orderReturnRequestService,
                                          OrderExchangeService orderExchangeService) {
            return new TrackViewService(trackParcelService, trackStatusEventService, userService,
                    applicationSettingsService, orderReturnRequestService, orderExchangeService);
        }
    }
}
