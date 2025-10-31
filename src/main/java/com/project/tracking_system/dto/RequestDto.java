package com.project.tracking_system.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO заявки на возврат или обмен для REST-API.
 * <p>
 * Запись содержит только идентификаторы и текущие технические атрибуты,
 * необходимые для последующих запросов клиентских приложений.
 * </p>
 *
 * @param id            идентификатор заявки
 * @param mode          активный режим (возврат или обмен)
 * @param stage         код текущего этапа обработки
 * @param storeId       идентификатор магазина
 * @param orderId       идентификатор заказа или эпизода, к которому относится заявка
 * @param parcelId      идентификатор исходной посылки
 * @param userId        идентификатор пользователя, создавшего заявку
 * @param responsibleId идентификатор ответственного менеджера
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record RequestDto(Long id,
                         String mode,
                         String stage,
                         Long storeId,
                         Long orderId,
                         Long parcelId,
                         Long userId,
                         Long responsibleId) {
}
