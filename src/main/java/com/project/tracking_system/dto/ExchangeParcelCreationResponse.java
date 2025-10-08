package com.project.tracking_system.dto;

/**
 * DTO ответа при создании обменной посылки вручную.
 *
 * @param details обновлённые данные исходной посылки
 * @param exchange элемент цепочки с созданной обменной посылкой
 */
public record ExchangeParcelCreationResponse(TrackDetailsDto details,
                                             TrackChainItemDto exchange) {
}
