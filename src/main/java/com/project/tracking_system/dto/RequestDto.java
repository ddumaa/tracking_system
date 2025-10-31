package com.project.tracking_system.dto;

import java.util.List;
import java.util.Objects;

/**
 * DTO заявки на возврат или обмен для отображения в модальном окне и REST-API.
 * <p>
 * Запись содержит агрегированные сведения о статусе и доступных действиях, чтобы
 * фронтенд мог отрисовать карточку заявки без дополнительных запросов.
 * </p>
 *
 * @param id                        идентификатор заявки
 * @param status                    машинный код статуса
 * @param statusLabel               локализованное название статуса
 * @param reason                    причина оформления возврата
 * @param comment                   дополнительный комментарий
 * @param mode                      активный режим заявки (возврат/обмен)
 * @param stage                     текущий этап жизненного цикла
 * @param requiresAction            признак необходимости действий менеджера
 * @param reverseTrack              обратный трек, указанный покупателем или магазином
 * @param exchangeTrack             трек обменной посылки
 * @param manualInboundPick         флаг ручного подтверждения получения возврата
 * @param manualReverseTrack        флаг ручного указания обратного трека
 * @param exchangeRequested         признак, что обмен изначально запрошен покупателем
 * @param exchangeApproved          признак, что обмен был одобрен магазином
 * @param exchangeShipmentDispatched признак отправки обменной посылки
 * @param returnReceiptConfirmed    признак подтверждения возврата магазином
 * @param actions                   перечень доступных действий
 * @param timestamps                временные метки жизненного цикла
 * @param storeId                   идентификатор магазина
 * @param responsibleId             идентификатор ответственного менеджера
 */
public record RequestDto(Long id,
                         String status,
                         String statusLabel,
                         String reason,
                         String comment,
                         String mode,
                         String stage,
                         boolean requiresAction,
                         String reverseTrack,
                         String exchangeTrack,
                         boolean manualInboundPick,
                         boolean manualReverseTrack,
                         boolean exchangeRequested,
                         boolean exchangeApproved,
                         boolean exchangeShipmentDispatched,
                         boolean returnReceiptConfirmed,
                         List<AvailableActionsDto> actions,
                         ReturnRequestTimestampsDto timestamps,
                         Long storeId,
                         Long responsibleId) {

    public RequestDto {
        actions = actions == null ? List.of() : List.copyOf(actions);
        timestamps = Objects.requireNonNullElseGet(timestamps, () -> new ReturnRequestTimestampsDto(null, null, null, null, null, null, null, null));
    }

    /**
     * Возвращает признак, что заявка является обменом для обратной совместимости фронтенда.
     */
    public boolean isExchangeRequest() {
        if (exchangeRequested || exchangeApproved) {
            return true;
        }
        return mode != null && mode.equalsIgnoreCase("EXCHANGE");
    }
}
