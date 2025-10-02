package com.project.tracking_system.service.order;

import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.OrderEpisodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Сервис управления жизненным циклом эпизодов заказа.
 * <p>
 * Следит за тем, чтобы у каждой посылки был эпизод, фиксирует финальные исходы
 * и количество выполненных обменов.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEpisodeLifecycleService {

    private final OrderEpisodeRepository orderEpisodeRepository;

    /**
     * Гарантирует, что у посылки есть эпизод, создавая его при необходимости.
     *
     * @param parcel посылка, для которой требуется эпизод
     * @return существующий или вновь созданный эпизод
     */
    @Transactional
    public OrderEpisode ensureEpisode(TrackParcel parcel) {
        OrderEpisode episode = parcel.getEpisode();
        if (episode == null) {
            episode = createEpisode(parcel.getStore(), parcel.getCustomer());
            parcel.setEpisode(episode);
        } else {
            syncCustomer(episode, parcel.getCustomer());
        }
        return episode;
    }

    /**
     * Открывает новый эпизод для переданных магазина и покупателя.
     *
     * @param store    магазин
     * @param customer покупатель (может быть {@code null})
     * @return созданный эпизод
     */
    @Transactional
    public OrderEpisode createEpisode(Store store, Customer customer) {
        OrderEpisode episode = new OrderEpisode();
        episode.setStore(store);
        episode.setCustomer(customer);
        episode.setStartedAt(ZonedDateTime.now(ZoneOffset.UTC));
        episode.setExchangesCount(0);
        OrderEpisode saved = orderEpisodeRepository.save(episode);
        log.debug("Создан эпизод заказа ID={} для магазина {}", saved.getId(),
                store != null ? store.getId() : null);
        return saved;
    }

    /**
     * Увеличивает счётчик обменов и связывает заменяющую посылку с эпизодом оригинала.
     *
     * @param replacement     посылка, созданная как обмен
     * @param originalParcel  исходная посылка, инициировавшая обмен
     */
    @Transactional
    public void registerExchange(TrackParcel replacement, TrackParcel originalParcel) {
        if (originalParcel == null) {
            throw new IllegalArgumentException("Исходная посылка для обмена не задана");
        }
        OrderEpisode episode = ensureEpisode(originalParcel);
        replacement.setEpisode(episode);
        replacement.setExchange(true);
        replacement.setReplacementOf(originalParcel);
        episode.setExchangesCount(episode.getExchangesCount() + 1);
        orderEpisodeRepository.save(episode);
        log.debug("Для эпизода {} зарегистрирован обмен, всего обменов: {}", episode.getId(), episode.getExchangesCount());
    }

    /**
     * Фиксирует финальный исход эпизода на основе статуса посылки.
     *
     * @param parcel посылка, получившая финальный статус
     * @param status финальный статус посылки
     */
    @Transactional
    public void registerFinalOutcome(TrackParcel parcel, GlobalStatus status) {
        if (parcel == null || status == null) {
            return;
        }
        OrderEpisode episode = parcel.getEpisode();
        if (episode == null) {
            log.debug("У посылки {} отсутствует эпизод, создаём перед фиксацией финала", parcel.getId());
            episode = ensureEpisode(parcel);
        }

        OrderFinalOutcome outcome = switch (status) {
            case DELIVERED -> OrderFinalOutcome.DELIVERED;
            case RETURNED -> OrderFinalOutcome.RETURNED;
            case REGISTRATION_CANCELLED -> OrderFinalOutcome.CANCELLED;
            default -> null;
        };

        if (outcome == null) {
            return;
        }

        if (episode.getFinalOutcome() == outcome && episode.getClosedAt() != null) {
            return;
        }

        episode.setFinalOutcome(outcome);
        ZonedDateTime closedAt = parcel.getTimestamp();
        episode.setClosedAt(closedAt != null ? closedAt : ZonedDateTime.now(ZoneOffset.UTC));
        orderEpisodeRepository.save(episode);
        log.debug("Эпизод {} закрыт с исходом {}", episode.getId(), outcome);
    }

    /**
     * Сбрасывает финальный исход эпизода, если статус посылки вернулся в незавершённое состояние.
     *
     * @param parcel посылка, у которой откатывается финальный статус
     */
    @Transactional
    public void reopenEpisode(TrackParcel parcel) {
        if (parcel == null || parcel.getEpisode() == null) {
            return;
        }
        OrderEpisode episode = parcel.getEpisode();
        if (episode.getFinalOutcome() != null) {
            episode.setFinalOutcome(null);
            episode.setClosedAt(null);
            orderEpisodeRepository.save(episode);
            log.debug("Эпизод {} повторно открыт", episode.getId());
        }
    }

    /**
     * Синхронизирует покупателя эпизода с покупателем посылки.
     *
     * @param parcel посылка, чьи данные считаются актуальными
     */
    @Transactional
    public void syncEpisodeCustomer(TrackParcel parcel) {
        if (parcel == null) {
            return;
        }
        OrderEpisode episode = ensureEpisode(parcel);
        syncCustomer(episode, parcel.getCustomer());
    }

    private void syncCustomer(OrderEpisode episode, Customer customer) {
        if (episode == null) {
            return;
        }
        if (!Objects.equals(episode.getCustomer(), customer)) {
            episode.setCustomer(customer);
            orderEpisodeRepository.save(episode);
        }
    }
}
