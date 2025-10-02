package com.project.tracking_system.service.order;

import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.OrderEpisodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Набор модульных тестов для {@link OrderEpisodeLifecycleService}.
 * <p>
 * Проверяем корректность выбора финальных исходов и переходов между состояниями
 * эпизода в зависимости от количества обменов и статусов посылок.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OrderEpisodeLifecycleServiceTest {

    @Mock
    private OrderEpisodeRepository orderEpisodeRepository;

    private OrderEpisodeLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new OrderEpisodeLifecycleService(orderEpisodeRepository);
        when(orderEpisodeRepository.save(any(OrderEpisode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void registerFinalOutcome_withoutExchanges_marksSuccessWithoutExchange() {
        ZonedDateTime deliveredAt = ZonedDateTime.now(ZoneOffset.UTC);
        OrderEpisode episode = buildEpisode(0);
        TrackParcel parcel = buildParcel(episode, deliveredAt);

        service.registerFinalOutcome(parcel, GlobalStatus.DELIVERED);

        assertThat(episode.getEpisodeState()).isEqualTo(OrderEpisodeState.SUCCESS_NO_EXCHANGE);
        assertThat(episode.getClosedAt()).isEqualTo(deliveredAt);
    }

    @Test
    void registerFinalOutcome_withExchange_marksSuccessAfterExchange() {
        ZonedDateTime deliveredAt = ZonedDateTime.now(ZoneOffset.UTC);
        OrderEpisode episode = buildEpisode(2);
        TrackParcel parcel = buildParcel(episode, deliveredAt);

        service.registerFinalOutcome(parcel, GlobalStatus.DELIVERED);

        assertThat(episode.getEpisodeState()).isEqualTo(OrderEpisodeState.SUCCESS_AFTER_EXCHANGE);
    }

    @Test
    void registerFinalOutcome_returned_marksReturnedNoReplacement() {
        ZonedDateTime returnedAt = ZonedDateTime.now(ZoneOffset.UTC);
        OrderEpisode episode = buildEpisode(0);
        TrackParcel parcel = buildParcel(episode, returnedAt);

        service.registerFinalOutcome(parcel, GlobalStatus.RETURNED);

        assertThat(episode.getEpisodeState()).isEqualTo(OrderEpisodeState.RETURNED_NO_REPLACEMENT);
    }

    @Test
    void registerFinalOutcome_cancelled_marksCancelled() {
        ZonedDateTime cancelledAt = ZonedDateTime.now(ZoneOffset.UTC);
        OrderEpisode episode = buildEpisode(0);
        TrackParcel parcel = buildParcel(episode, cancelledAt);

        service.registerFinalOutcome(parcel, GlobalStatus.REGISTRATION_CANCELLED);

        assertThat(episode.getEpisodeState()).isEqualTo(OrderEpisodeState.CANCELLED);
        assertThat(episode.getClosedAt()).isEqualTo(cancelledAt);
    }

    @Test
    void registerExchange_incrementsCounterAndReopensEpisode() {
        OrderEpisode episode = buildEpisode(0);
        episode.setEpisodeState(OrderEpisodeState.SUCCESS_NO_EXCHANGE);
        episode.setClosedAt(ZonedDateTime.now(ZoneOffset.UTC));

        TrackParcel original = new TrackParcel();
        original.setEpisode(episode);
        TrackParcel replacement = new TrackParcel();

        service.registerExchange(replacement, original);

        assertThat(episode.getExchangesCount()).isEqualTo(1);
        assertThat(episode.getEpisodeState()).isEqualTo(OrderEpisodeState.OPEN);
        assertThat(episode.getClosedAt()).isNull();
        assertThat(replacement.getEpisode()).isSameAs(episode);
        assertThat(replacement.isExchange()).isTrue();
        assertThat(replacement.getReplacementOf()).isSameAs(original);
    }

    @Test
    void registerExchange_withoutReplacementThrows() {
        TrackParcel original = new TrackParcel();
        original.setEpisode(buildEpisode(0));

        assertThrows(IllegalArgumentException.class, () -> service.registerExchange(null, original));
    }

    @Test
    void reopenEpisode_resetsFinalOutcomeToOpen() {
        OrderEpisode episode = buildEpisode(0);
        episode.setEpisodeState(OrderEpisodeState.CANCELLED);
        episode.setClosedAt(ZonedDateTime.now(ZoneOffset.UTC));

        TrackParcel parcel = new TrackParcel();
        parcel.setEpisode(episode);

        service.reopenEpisode(parcel);

        ArgumentCaptor<OrderEpisode> captor = ArgumentCaptor.forClass(OrderEpisode.class);
        verify(orderEpisodeRepository).save(captor.capture());

        assertThat(captor.getValue().getEpisodeState()).isEqualTo(OrderEpisodeState.OPEN);
        assertThat(captor.getValue().getClosedAt()).isNull();
    }

    private OrderEpisode buildEpisode(int exchangesCount) {
        OrderEpisode episode = new OrderEpisode();
        episode.setExchangesCount(exchangesCount);
        return episode;
    }

    private TrackParcel buildParcel(OrderEpisode episode, ZonedDateTime timestamp) {
        TrackParcel parcel = new TrackParcel();
        parcel.setEpisode(episode);
        parcel.setTimestamp(timestamp);
        return parcel;
    }
}
