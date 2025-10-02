package com.project.tracking_system.dto;

/**
 * Описывает посылку, входящую в цепочку эпизода заказа.
 *
 * @param id       идентификатор связанной посылки
 * @param number   трек-номер (может отсутствовать для предрегистрации)
 * @param exchange признак, что посылка является обменом
 * @param current  является ли элемент текущим открытым треком
 */
public record TrackChainItemDto(Long id,
                                String number,
                                boolean exchange,
                                boolean current) {
}
