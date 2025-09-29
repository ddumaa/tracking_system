package com.project.tracking_system.dto;

import java.util.List;

/**
 * Подробное описание сохранённого трека для отображения в модальном окне.
 *
 * @param id             идентификатор посылки
 * @param number         трек-номер (может отсутствовать для предрегистраций)
 * @param deliveryService название почтовой службы
 * @param currentStatus  последний статус с отметкой времени
 * @param history        сохранённые события трека в обратном хронологическом порядке
 * @param refreshAllowed признак доступности принудительного обновления
 * @param nextRefreshAt  момент следующей попытки обновления (ISO-строка или {@code null})
 * @param canEditTrack   доступно ли редактирование трека пользователю
 * @param timeZone       предпочтительный часовой пояс пользователя
 */
public record TrackDetailsDto(Long id,
                              String number,
                              String deliveryService,
                              TrackStatusEventDto currentStatus,
                              List<TrackStatusEventDto> history,
                              boolean refreshAllowed,
                              String nextRefreshAt,
                              boolean canEditTrack,
                              String timeZone) {
}

