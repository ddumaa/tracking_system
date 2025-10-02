package com.project.tracking_system.dto;

import java.util.List;

/**
 * Подробное описание сохранённого трека для отображения в модальном окне.
 *
 * @param id             идентификатор посылки
 * @param number         трек-номер (может отсутствовать для предрегистраций)
 * @param deliveryService название почтовой службы
 * @param systemStatus   глобальный статус посылки в системе
 * @param lastUpdateAt   дата последнего обновления статуса
 * @param currentStatus  последний статус с отметкой времени
 * @param history        сохранённые события трека в обратном хронологическом порядке
 * @param refreshAllowed признак доступности принудительного обновления
 * @param nextRefreshAt  момент следующей попытки обновления (ISO-строка или {@code null})
 * @param canEditTrack   доступно ли редактирование трека пользователю
 * @param timeZone       предпочтительный часовой пояс пользователя
 * @param episodeNumber  номер эпизода заказа, к которому относится посылка
 * @param exchange       признак оформления посылки как обмена
 * @param chain          цепочка связанных посылок в рамках эпизода
 */
public record TrackDetailsDto(Long id,
                              String number,
                              String deliveryService,
                              String systemStatus,
                              String lastUpdateAt,
                              TrackStatusEventDto currentStatus,
                              List<TrackStatusEventDto> history,
                              boolean refreshAllowed,
                              String nextRefreshAt,
                              boolean canEditTrack,
                              String timeZone,
                              Long episodeNumber,
                              boolean exchange,
                              List<TrackChainItemDto> chain) {
}
