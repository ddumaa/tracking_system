package com.project.tracking_system.service.order;

import com.project.tracking_system.entity.TrackParcel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Обрабатывает события трекинга, продвигая стадии заявок возврата и обмена.
 * <p>
 * Компонент выступает прослойкой между модулем обновления статусов и
 * {@link OrderReturnRequestService}, чтобы не смешивать ответственность
 * и упростить тестирование автоматических переходов.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnTrackingEventHandler {

    private final OrderReturnRequestService orderReturnRequestService;

    /**
     * Обрабатывает событие трекинга и делегирует переход стадий в сервис заявок.
     *
     * @param event доменное событие трекинга
     */
    public void handle(ReturnTrackingEvent event) {
        if (event == null) {
            return;
        }
        ReturnTrackingEventType type = event.type();
        TrackParcel parcel = event.parcel();
        if (type == null || parcel == null) {
            log.debug("Событие трекинга проигнорировано из-за отсутствующих данных");
            return;
        }
        switch (type) {
            case RETURN_ARRIVED_TO_STORE -> handleReturnArrival(parcel, event.occurredAt());
            case RETURN_PICKED_UP_BY_STORE -> handleReturnPickup(parcel, event.occurredAt());
            case EXCHANGE_DELIVERED_TO_CUSTOMER -> handleExchangeDelivery(parcel, event.occurredAt());
            default -> log.debug("Неизвестный тип события трекинга: {}", type);
        }
    }

    private void handleReturnArrival(TrackParcel parcel, ZonedDateTime moment) {
        Long parcelId = parcel.getId();
        if (parcelId == null) {
            log.debug("Невозможно зафиксировать прибытие возврата: не указан идентификатор посылки");
            return;
        }
        orderReturnRequestService.autoMarkInboundArrived(parcelId, moment);
    }

    private void handleReturnPickup(TrackParcel parcel, ZonedDateTime moment) {
        Long parcelId = parcel.getId();
        if (parcelId == null) {
            log.debug("Невозможно подтвердить получение возврата: не указан идентификатор посылки");
            return;
        }
        orderReturnRequestService.autoMarkInboundPickedUp(parcelId, moment);
    }

    private void handleExchangeDelivery(TrackParcel exchangeParcel, ZonedDateTime moment) {
        if (exchangeParcel.getReplacementOf() == null) {
            log.debug("Обменная посылка {} не связана с исходной, событие доставки пропущено", exchangeParcel.getId());
            return;
        }
        Long originalParcelId = Optional.ofNullable(exchangeParcel.getReplacementOf())
                .map(TrackParcel::getId)
                .orElse(null);
        if (originalParcelId == null) {
            log.debug("Не удалось определить исходную посылку для обмена {}, событие пропущено", exchangeParcel.getId());
            return;
        }
        orderReturnRequestService.autoMarkExchangeDelivered(originalParcelId, moment);
    }
}
