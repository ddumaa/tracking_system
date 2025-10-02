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
        episode.setFinalOutcome(OrderFinalOutcome.OPEN);
        OrderEpisode saved = orderEpisodeRepository.save(episode);
        log.debug("Создан эпизод заказа ID={} для магазина {}", saved.getId(),
                store != null ? store.getId() : null);
        return saved;
    }

    /**
     * Увеличивает счётчик обменов и связывает заменяющую посылку с эпизодом оригинала.
     * <p>
     * Метод также возвращает эпизод в открытое состояние, чтобы финальный исход
     * был пересчитан после успешного вручения обменной посылки.
     * </p>
     *
     * @param replacement     посылка, созданная как обмен
     * @param originalParcel  исходная посылка, инициировавшая обмен
     */
    @Transactional
    public void registerExchange(TrackParcel replacement, TrackParcel originalParcel) {
        if (originalParcel == null) {
            throw new IllegalArgumentException("Исходная посылка для обмена не задана");
        }
        if (replacement == null) {
            throw new IllegalArgumentException("Посылка обмена не задана");
        }
        OrderEpisode episode = ensureEpisode(originalParcel);
        replacement.setEpisode(episode);
        replacement.setExchange(true);
        replacement.setReplacementOf(originalParcel);
        episode.setExchangesCount(episode.getExchangesCount() + 1);
        resetEpisodeToOpen(episode);
        orderEpisodeRepository.save(episode);
        log.debug("Для эпизода {} зарегистрирован обмен, всего обменов: {}", episode.getId(), episode.getExchangesCount());
    }

    /**
     * Фиксирует финальный исход эпизода на основе статуса посылки.
     * <p>
     * Логика выбора финала учитывает количество обменов в эпизоде и
     * соблюдает доменные инварианты: без обменов фиксация идёт как
     * {@link OrderFinalOutcome#SUCCESS_NO_EXCHANGE}, при наличии обменов —
     * {@link OrderFinalOutcome#SUCCESS_AFTER_EXCHANGE}, а возврат без
     * повторной отправки переводит эпизод в состояние
     * {@link OrderFinalOutcome#RETURNED_NO_REPLACEMENT}.
     * </p>
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

        OrderFinalOutcome outcome = resolveFinalOutcome(episode, status);

        if (outcome == null) {
            return;
        }

        if (Objects.equals(episode.getFinalOutcome(), outcome) && episode.getClosedAt() != null) {
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
        if (resetEpisodeToOpen(episode)) {
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

    /**
     * Определяет финальный исход для переданного статуса посылки.
     */
    private OrderFinalOutcome resolveFinalOutcome(OrderEpisode episode, GlobalStatus status) {
        return switch (status) {
            case DELIVERED -> (episode.getExchangesCount() > 0
                    ? OrderFinalOutcome.SUCCESS_AFTER_EXCHANGE
                    : OrderFinalOutcome.SUCCESS_NO_EXCHANGE);
            case RETURNED -> OrderFinalOutcome.RETURNED_NO_REPLACEMENT;
            case REGISTRATION_CANCELLED -> OrderFinalOutcome.CANCELLED;
            default -> null;
        };
    }

    /**
     * Переводит эпизод в открытое состояние, если он был закрыт.
     *
     * @param episode эпизод, который требуется открыть
     * @return {@code true}, если были внесены изменения
     */
    private boolean resetEpisodeToOpen(OrderEpisode episode) {
        if (episode == null) {
            return false;
        }
        boolean changed = false;
        if (episode.getFinalOutcome() != OrderFinalOutcome.OPEN) {
            episode.setFinalOutcome(OrderFinalOutcome.OPEN);
            changed = true;
        }
        if (episode.getClosedAt() != null) {
            episode.setClosedAt(null);
            changed = true;
        }
        return changed;
    }
}
