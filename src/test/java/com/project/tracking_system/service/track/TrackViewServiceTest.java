package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackChainItemDto;
import com.project.tracking_system.dto.TrackDetailsDto;
import com.project.tracking_system.dto.TrackLifecycleStageDto;
import com.project.tracking_system.dto.TrackStatusEventDto;
import com.project.tracking_system.entity.DeliveryHistory;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.OrderEpisode;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.OrderReturnRequestStatus;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.TrackStatusEvent;
import com.project.tracking_system.service.admin.ApplicationSettingsService;
import com.project.tracking_system.service.order.OrderExchangeService;
import com.project.tracking_system.service.order.OrderReturnRequestService;
import com.project.tracking_system.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link TrackViewService}.
 */
@ExtendWith(MockitoExtension.class)
class TrackViewServiceTest {

    @Mock
    private TrackParcelService trackParcelService;
    @Mock
    private UserService userService;
    @Mock
    private ApplicationSettingsService applicationSettingsService;
    @Mock
    private TrackStatusEventService trackStatusEventService;
    @Mock
    private OrderReturnRequestService orderReturnRequestService;
    @Mock
    private OrderExchangeService orderExchangeService;

    private TrackViewService service;

    @BeforeEach
    void setUp() {
        service = new TrackViewService(trackParcelService, trackStatusEventService,
                userService, applicationSettingsService, orderReturnRequestService, orderExchangeService);
        when(orderReturnRequestService.findCurrentForParcel(anyLong())).thenReturn(Optional.empty());
        when(orderExchangeService.findLatestExchangeParcel(any())).thenReturn(Optional.empty());
        when(orderReturnRequestService.getExchangeCancellationBlockReason(any())).thenReturn(Optional.empty());
        when(orderReturnRequestService.canReopenAsReturn(any())).thenReturn(false);
        when(orderReturnRequestService.canCancelExchange(any())).thenReturn(false);
    }

    /**
     * Убеждаемся, что при наличии сохранённых событий возвращается
     * история из {@link TrackStatusEventService} без обращения к фолбеку.
     */
    @Test
    void getTrackDetails_UsesPersistedEventsWhenAvailable() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        TrackParcel parcel = buildParcel(10L, GlobalStatus.IN_TRANSIT, now.minusHours(5));
        when(trackParcelService.findOwnedById(10L, 1L)).thenReturn(Optional.of(parcel));
        stubEpisodeParcels(parcel, 1L);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(3);
        when(userService.getUserZone(1L)).thenReturn(ZoneId.of("UTC"));

        TrackStatusEvent first = buildEvent(parcel, now.minusHours(1), "Принят в отделении");
        TrackStatusEvent second = buildEvent(parcel, now.minusHours(3), "Передан перевозчику");
        when(trackStatusEventService.findEvents(10L)).thenReturn(List.of(first, second));

        TrackDetailsDto details = service.getTrackDetails(10L, 1L);

        verify(trackStatusEventService).findEvents(10L);
        assertThat(details.history())
                .extracting(TrackStatusEventDto::status)
                .containsExactly("Принят в отделении", "Передан перевозчику");
        assertThat(details.lifecycle()).isNotEmpty();
    }

    /**
     * Проверяем, что при отсутствии сохранённых событий возвращается только агрегированный
     * статус с корректной меткой времени, без добавления жёстко заданных этапов.
     */
    @Test
    void getTrackDetails_BuildsFallbackFromAggregateStatusOnly() {
        ZonedDateTime statusMoment = ZonedDateTime.now(ZoneOffset.UTC).minusHours(2);
        TrackParcel parcel = buildParcel(20L, GlobalStatus.IN_TRANSIT, statusMoment);
        DeliveryHistory deliveryHistory = new DeliveryHistory();
        deliveryHistory.setPostalService(PostalServiceType.BELPOST);
        parcel.setDeliveryHistory(deliveryHistory);

        when(trackParcelService.findOwnedById(20L, 2L)).thenReturn(Optional.of(parcel));
        stubEpisodeParcels(parcel, 2L);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(6);
        when(userService.getUserZone(2L)).thenReturn(ZoneId.of("UTC"));
        when(trackStatusEventService.findEvents(20L)).thenReturn(List.of());

        TrackDetailsDto details = service.getTrackDetails(20L, 2L);

        verify(trackStatusEventService).findEvents(20L);
        assertThat(details.history()).hasSize(1);
        TrackStatusEventDto fallbackEvent = details.history().get(0);
        assertThat(fallbackEvent.status()).isEqualTo(GlobalStatus.IN_TRANSIT.getDescription());
        assertThat(fallbackEvent.timestamp()).isEqualTo(statusMoment.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        assertThat(details.currentStatus()).isEqualTo(fallbackEvent);
    }

    /**
     * Проверяем, что без заявки жизненный цикл содержит только исходный этап.
     */
    @Test
    void getTrackDetails_WithoutReturnRequestLifecycleIsOutboundOnly() {
        ZonedDateTime update = ZonedDateTime.now(ZoneOffset.UTC).minusHours(3);
        TrackParcel parcel = buildParcel(24L, GlobalStatus.IN_TRANSIT, update);

        when(trackParcelService.findOwnedById(24L, 4L)).thenReturn(Optional.of(parcel));
        stubEpisodeParcels(parcel, 4L);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(4);
        when(userService.getUserZone(4L)).thenReturn(ZoneId.of("UTC"));
        when(trackStatusEventService.findEvents(24L)).thenReturn(List.of());
        when(orderReturnRequestService.findCurrentForParcel(24L)).thenReturn(Optional.empty());

        TrackDetailsDto details = service.getTrackDetails(24L, 4L);

        assertThat(details.lifecycle()).hasSize(1);
        assertThat(details.lifecycle().get(0).code()).isEqualTo("OUTBOUND");
    }

    /**
     * Если точная дата статуса не сохранена, используется отметка последнего обновления.
     */
    @Test
    void getTrackDetails_UsesLastUpdateWhenTimestampMissing() {
        ZonedDateTime lastUpdate = ZonedDateTime.now(ZoneOffset.UTC).minusHours(4);
        TrackParcel parcel = buildParcel(25L, GlobalStatus.WAITING_FOR_CUSTOMER, lastUpdate);
        parcel.setTimestamp(null);

        when(trackParcelService.findOwnedById(25L, 5L)).thenReturn(Optional.of(parcel));
        stubEpisodeParcels(parcel, 5L);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(12);
        when(userService.getUserZone(5L)).thenReturn(ZoneId.of("UTC"));
        when(trackStatusEventService.findEvents(25L)).thenReturn(List.of());

        TrackDetailsDto details = service.getTrackDetails(25L, 5L);

        assertThat(details.history()).hasSize(1);
        TrackStatusEventDto event = details.history().get(0);
        assertThat(event.status()).isEqualTo(GlobalStatus.WAITING_FOR_CUSTOMER.getDescription());
        assertThat(event.timestamp()).isEqualTo(lastUpdate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    /**
     * Финальный статус запрещает ручное обновление и обнуляет время следующей попытки.
     */
    @Test
    void getTrackDetails_FinalStatusDisablesRefresh() {
        ZonedDateTime lastUpdate = ZonedDateTime.now(ZoneOffset.UTC).minusHours(1);
        TrackParcel parcel = buildParcel(30L, GlobalStatus.DELIVERED, lastUpdate);
        when(trackParcelService.findOwnedById(30L, 3L)).thenReturn(Optional.of(parcel));
        stubEpisodeParcels(parcel, 3L);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(4);
        when(userService.getUserZone(3L)).thenReturn(ZoneId.of("UTC"));
        when(trackStatusEventService.findEvents(30L)).thenReturn(List.of());

        TrackDetailsDto details = service.getTrackDetails(30L, 3L);

        assertThat(details.refreshAllowed()).isFalse();
        assertThat(details.nextRefreshAt()).isNull();
    }

    /**
     * Проверяем, что редактирование разрешено только для статусов PRE_REGISTERED и ERROR.
     */
    @Test
    void getTrackDetails_DisablesEditForTransitStatus() {
        TrackParcel parcel = buildParcel(42L, GlobalStatus.IN_TRANSIT, ZonedDateTime.now(ZoneOffset.UTC));
        when(trackParcelService.findOwnedById(42L, 8L)).thenReturn(Optional.of(parcel));
        stubEpisodeParcels(parcel, 8L);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(4);
        when(userService.getUserZone(8L)).thenReturn(ZoneId.of("UTC"));
        when(trackStatusEventService.findEvents(42L)).thenReturn(List.of());

        TrackDetailsDto details = service.getTrackDetails(42L, 8L);

        assertThat(details.canEditTrack()).isFalse();
    }

    /**
     * Базовая посылка должна содержать номер эпизода и одиночную цепочку.
     */
    @Test
    void getTrackDetails_BaseParcelContainsEpisodeInfo() {
        ZonedDateTime update = ZonedDateTime.now(ZoneOffset.UTC).minusHours(2);
        TrackParcel parcel = buildParcel(55L, GlobalStatus.IN_TRANSIT, update);
        parcel.getEpisode().setId(777L);
        when(trackParcelService.findOwnedById(55L, 9L)).thenReturn(Optional.of(parcel));
        stubEpisodeParcels(parcel, 9L);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(6);
        when(userService.getUserZone(9L)).thenReturn(ZoneId.of("UTC"));
        when(trackStatusEventService.findEvents(55L)).thenReturn(List.of());

        TrackDetailsDto details = service.getTrackDetails(55L, 9L);

        assertThat(details.episodeNumber()).isEqualTo(777L);
        assertThat(details.exchange()).isFalse();
        assertThat(details.chain())
                .extracting(item -> List.of(item.id(), item.exchange(), item.current()))
                .containsExactly(List.of(55L, false, true));
    }

    /**
     * Посылка-обмен должна включать исходную посылку в цепочку и пометить текущий элемент.
     */
    @Test
    void getTrackDetails_ExchangeParcelBuildsChain() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        TrackParcel original = buildParcel(60L, GlobalStatus.RETURNED, now.minusDays(1));
        original.setExchange(false);
        original.getEpisode().setId(900L);

        TrackParcel replacement = buildParcel(61L, GlobalStatus.IN_TRANSIT, now);
        replacement.setExchange(true);
        replacement.setEpisode(original.getEpisode());

        when(trackParcelService.findOwnedById(61L, 11L)).thenReturn(Optional.of(replacement));
        stubEpisodeParcels(replacement, 11L, original);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(4);
        when(userService.getUserZone(11L)).thenReturn(ZoneId.of("UTC"));
        when(trackStatusEventService.findEvents(61L)).thenReturn(List.of());
        when(orderExchangeService.findLatestExchangeParcel(any())).thenReturn(Optional.empty());

        TrackDetailsDto details = service.getTrackDetails(61L, 11L);

        assertThat(details.exchange()).isTrue();
        assertThat(details.chain())
                .extracting(item -> List.of(item.id(), item.exchange(), item.current()))
                .containsExactly(
                        List.of(61L, true, true),
                        List.of(60L, false, false)
                );
    }

    @Test
    void getTrackDetails_IncludesExchangeStagesWhenApproved() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        TrackParcel parcel = buildParcel(82L, GlobalStatus.RETURNED, now);
        when(trackParcelService.findOwnedById(82L, 16L)).thenReturn(Optional.of(parcel));
        stubEpisodeParcels(parcel, 16L);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(4);
        when(userService.getUserZone(16L)).thenReturn(ZoneId.of("UTC"));
        when(trackStatusEventService.findEvents(82L)).thenReturn(List.of());

        OrderReturnRequest request = new OrderReturnRequest();
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStatus(OrderReturnRequestStatus.EXCHANGE_APPROVED);
        request.setDecisionAt(now.minusHours(2));
        when(orderReturnRequestService.findCurrentForParcel(82L)).thenReturn(Optional.of(request));
        TrackParcel exchangeParcel = buildParcel(83L, GlobalStatus.IN_TRANSIT, now.minusHours(1));
        exchangeParcel.setExchange(true);
        when(orderExchangeService.findLatestExchangeParcel(request)).thenReturn(Optional.of(exchangeParcel));

        TrackDetailsDto details = service.getTrackDetails(82L, 16L);

        assertThat(details.lifecycle())
                .extracting(stage -> stage.code())
                .contains("EXCHANGE_SHIPMENT", "EXCHANGE_DELIVERY");
    }

    /**
     * Проверяем, что этапы жизненного цикла содержат сведения о трек-номерах.
     */
    @Test
    void getTrackDetails_EnrichesLifecycleWithTrackInfo() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        TrackParcel parcel = buildParcel(91L, GlobalStatus.RETURNED, now.minusDays(1));
        parcel.setNumber("SHOP-TRACK");
        when(trackParcelService.findOwnedById(91L, 18L)).thenReturn(Optional.of(parcel));
        stubEpisodeParcels(parcel, 18L);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(4);
        when(userService.getUserZone(18L)).thenReturn(ZoneId.of("UTC"));
        when(trackStatusEventService.findEvents(91L)).thenReturn(List.of());

        OrderReturnRequest request = new OrderReturnRequest();
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStatus(OrderReturnRequestStatus.EXCHANGE_APPROVED);
        request.setRequestedAt(now.minusHours(6));
        request.setReverseTrackNumber("REV-123");
        when(orderReturnRequestService.findCurrentForParcel(91L)).thenReturn(Optional.of(request));

        TrackParcel exchangeParcel = buildParcel(92L, GlobalStatus.IN_TRANSIT, now);
        exchangeParcel.setNumber("EX-999");
        exchangeParcel.setExchange(true);
        when(orderExchangeService.findLatestExchangeParcel(request)).thenReturn(Optional.of(exchangeParcel));

        TrackDetailsDto details = service.getTrackDetails(91L, 18L);

        List<TrackLifecycleStageDto> lifecycle = details.lifecycle();
        TrackLifecycleStageDto outbound = lifecycle.stream()
                .filter(stage -> "OUTBOUND".equals(stage.code()))
                .findFirst()
                .orElseThrow();
        assertThat(outbound.trackContext()).isEqualTo("Исходная посылка");
        assertThat(outbound.trackNumber()).isEqualTo("SHOP-TRACK");

        TrackLifecycleStageDto customerReturn = lifecycle.stream()
                .filter(stage -> "CUSTOMER_RETURN".equals(stage.code()))
                .findFirst()
                .orElseThrow();
        assertThat(customerReturn.trackContext()).isEqualTo("Обратный трек");
        assertThat(customerReturn.trackNumber()).isEqualTo("REV-123");

        TrackLifecycleStageDto exchangeDelivery = lifecycle.stream()
                .filter(stage -> "EXCHANGE_DELIVERY".equals(stage.code()))
                .findFirst()
                .orElseThrow();
        assertThat(exchangeDelivery.trackContext()).isEqualTo("Обменная посылка");
        assertThat(exchangeDelivery.trackNumber()).isEqualTo("EX-999");
    }

    /**
     * Возврат без обмена формирует одиночную цепочку без признака обмена.
     */
    @Test
    void getTrackDetails_ReturnWithoutExchange() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        TrackParcel returned = buildParcel(70L, GlobalStatus.RETURNED, now);
        returned.setExchange(false);
        returned.getEpisode().setId(1000L);

        when(trackParcelService.findOwnedById(70L, 13L)).thenReturn(Optional.of(returned));
        stubEpisodeParcels(returned, 13L);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(4);
        when(userService.getUserZone(13L)).thenReturn(ZoneId.of("UTC"));
        when(trackStatusEventService.findEvents(70L)).thenReturn(List.of());

        TrackDetailsDto details = service.getTrackDetails(70L, 13L);

        assertThat(details.exchange()).isFalse();
        assertThat(details.chain()).hasSize(1);
        assertThat(details.chain().get(0).id()).isEqualTo(70L);
        assertThat(details.chain().get(0).current()).isTrue();
    }

    @Test
    void getTrackDetails_AllowsReturnRegistrationOnlyForDelivered() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        TrackParcel parcel = buildParcel(80L, GlobalStatus.DELIVERED, now);

        when(trackParcelService.findOwnedById(80L, 14L)).thenReturn(Optional.of(parcel));
        stubEpisodeParcels(parcel, 14L);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(4);
        when(userService.getUserZone(14L)).thenReturn(ZoneId.of("UTC"));
        when(trackStatusEventService.findEvents(80L)).thenReturn(List.of());
        when(orderReturnRequestService.findCurrentForParcel(80L)).thenReturn(Optional.empty());

        TrackDetailsDto details = service.getTrackDetails(80L, 14L);

        assertThat(details.canRegisterReturn()).isTrue();
        assertThat(details.returnRequest()).isNull();
        assertThat(details.requiresAction()).isFalse();
        assertThat(details.lifecycle())
                .extracting(TrackLifecycleStageDto::code)
                .containsExactly("OUTBOUND");
    }

    @Test
    void getTrackDetails_MapsReturnRequestIntoDto() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        TrackParcel parcel = buildParcel(81L, GlobalStatus.DELIVERED, now);
        OrderReturnRequest request = new OrderReturnRequest();
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStatus(OrderReturnRequestStatus.REGISTERED);
        request.setCreatedAt(now.minusHours(1));

        when(trackParcelService.findOwnedById(81L, 15L)).thenReturn(Optional.of(parcel));
        stubEpisodeParcels(parcel, 15L);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(4);
        when(userService.getUserZone(15L)).thenReturn(ZoneId.of("UTC"));
        when(trackStatusEventService.findEvents(81L)).thenReturn(List.of());
        when(orderReturnRequestService.findCurrentForParcel(81L)).thenReturn(Optional.of(request));
        when(orderReturnRequestService.canStartExchange(request)).thenReturn(true);
        when(orderExchangeService.findLatestExchangeParcel(request)).thenReturn(Optional.empty());

        TrackDetailsDto details = service.getTrackDetails(81L, 15L);

        assertThat(details.canRegisterReturn()).isFalse();
        assertThat(details.requiresAction()).isTrue();
        assertThat(details.returnRequest()).isNotNull();
        assertThat(details.returnRequest().requiresAction()).isTrue();
        assertThat(details.returnRequest().canStartExchange()).isTrue();
        assertThat(details.lifecycle())
                .extracting(stage -> stage.code())
                .contains("OUTBOUND", "CUSTOMER_RETURN", "MERCHANT_ACCEPT_RETURN");
    }

    @Test
    void toChainItem_BuildsDtoForExchangeParcel() {
        TrackParcel parcel = buildParcel(90L, GlobalStatus.PRE_REGISTERED, ZonedDateTime.now(ZoneOffset.UTC));
        parcel.setExchange(true);

        TrackChainItemDto dto = service.toChainItem(parcel, 10L);

        assertThat(dto.id()).isEqualTo(90L);
        assertThat(dto.exchange()).isTrue();
        assertThat(dto.current()).isFalse();
    }

    /**
     * Создаёт тестовую посылку.
     */
    private static TrackParcel buildParcel(Long id, GlobalStatus status, ZonedDateTime update) {
        TrackParcel parcel = new TrackParcel();
        parcel.setId(id);
        parcel.setNumber("A1");
        parcel.setStatus(status);
        parcel.setLastUpdate(update);
        parcel.setTimestamp(update);
        Store store = new Store();
        store.setId(1L);
        parcel.setStore(store);
        OrderEpisode episode = new OrderEpisode();
        episode.setId(100L);
        parcel.setEpisode(episode);
        return parcel;
    }

    /**
     * Формирует тестовое событие истории статусов.
     */
    private static TrackStatusEvent buildEvent(TrackParcel parcel, ZonedDateTime time, String description) {
        TrackStatusEvent event = new TrackStatusEvent();
        event.setTrackParcel(parcel);
        event.setEventTime(time);
        event.setDescription(description);
        return event;
    }

    /**
     * Настраивает мок сервиса посылок для возврата цепочки эпизода.
     */
    private void stubEpisodeParcels(TrackParcel parcel, Long userId, TrackParcel... additional) {
        OrderEpisode episode = parcel.getEpisode();
        List<TrackParcel> chain = new ArrayList<>();
        chain.add(parcel);
        chain.addAll(Arrays.asList(additional));
        when(trackParcelService.findEpisodeParcels(episode.getId(), userId)).thenReturn(chain);
    }
}
