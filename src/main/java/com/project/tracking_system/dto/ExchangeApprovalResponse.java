package com.project.tracking_system.dto;

/**
 * DTO ответа при запуске обменной посылки.
 *
 * @param details   обновлённые данные исходной посылки
 * @param exchange  элемент цепочки с новой обменной посылкой
 */
public record ExchangeApprovalResponse(TrackDetailsDto details,
                                       TrackChainItemDto exchange) {
}
