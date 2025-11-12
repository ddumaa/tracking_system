package com.project.tracking_system.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.ZonedDateTime;

/**
 * DTO заявки на возврат или обмен для REST-API.
 * <p>
 * Структура отражает JSON Schema внешнего API ReturnsExchanges 1.0: помимо идентификаторов
 * добавлены атрибуты причины возврата, комментариев, треков и технические флаги
 * для ручных операций. UUID заявки передаётся в текстовом виде, чтобы клиенты
 * могли использовать его в распределённых интеграциях без потери точности.
 * </p>
 *
 * @param id                       публичный идентификатор заявки (UUID в текстовом виде)
 * @param mode                     активный режим (возврат или обмен)
 * @param stage                    код текущего этапа обработки
 * @param storeId                  идентификатор магазина
 * @param orderId                  идентификатор заказа или эпизода, к которому относится заявка
 * @param parcelId                 идентификатор исходной посылки
 * @param userId                   идентификатор пользователя, создавшего заявку
 * @param reason                   причина возврата в нормализованном виде
 * @param comment                  дополнительный комментарий пользователя
 * @param reverseTrack             трек обратной отправки
 * @param exchangeTrack            трек обменной посылки
 * @param manualInboundPick        флаг, что этап приёма возврата подтверждён вручную
 * @param createdAt                момент создания заявки
 * @param updatedAt                момент последнего обновления заявки
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record RequestDto(String id,
                         String mode,
                         String stage,
                         Long storeId,
                         Long orderId,
                         Long parcelId,
                         Long userId,
                         String reason,
                         String comment,
                         String reverseTrack,
                         String exchangeTrack,
                         boolean manualInboundPick,
                         ZonedDateTime createdAt,
                         ZonedDateTime updatedAt) {
}
