package com.project.tracking_system.service;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.model.GlobalStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Сервис для обработки статусов почтовых отправлений.
 * Предоставляет методы для установки статуса на основе информации о трекинге
 * и возвращает соответствующие иконки для каждого статуса.
 *
 * @author Dmitriy Anisimov
 * @date Добавлено 07.01.2025
 */
@Service
public class StatusTrackService {

    /**
     * Карта, которая сопоставляет регулярные выражения для статусов с их соответствующими значениями.
     */
    private static final Map<Pattern, GlobalStatus> statusPatterns = new HashMap<>();

    static {
        // Инициализация карты регулярных выражений и статусов
        statusPatterns.put(Pattern.compile("^Почтовое отправление выдано|^Вручено"), GlobalStatus.DELIVERED);
        statusPatterns.put(Pattern.compile("^Почтовое отправление прибыло на ОПС выдачи|^Добрый день\\. Срок бесплатного хранения|" +
                "^Поступило в учреждение доставки"), GlobalStatus.WAITING_FOR_CUSTOMER);
        statusPatterns.put(Pattern.compile("^Почтовое отправление принято на ОПС|" +
                        "^Оплачено на ОПС|^Отправлено|^Принято от отправителя|" +
                        "^Поступило в обработку|" +
                        "^Почтовое отправление подготовлено в ОПС к доставке на сортировочный пункт$|" +
                        "^Почтовое отправление прибыло на сортировочный пункт$|" +
                        "^Почтовое отправление подготовлено в сортировочном пункте к доставке на ОПС назначения$"),
                GlobalStatus.IN_TRANSIT);
        statusPatterns.put(Pattern.compile("^Почтовое отправление готово к возврату"),
                GlobalStatus.RETURN_IN_PROGRESS);
        statusPatterns.put(Pattern.compile("^Почтовое отправление прибыло на Отделение №\\d+.*для возврата.*"),
                GlobalStatus.RETURN_PENDING_PICKUP);
        statusPatterns.put(Pattern.compile("^Почтовое отправление возвращено отправителю$"), GlobalStatus.RETURNED_TO_SENDER);
        statusPatterns.put(Pattern.compile("^Заявка на почтовое отправление зарегистрирована$"), GlobalStatus.REGISTERED);
        statusPatterns.put(Pattern.compile("^Добрый день\\. Отправление [A-Z0-9]+ не востребовано получателем.*|" +
                        "^Отправление с [0-9]{2}\\.[0-9]{2}\\.[0-9]{4} ожидает вручения в Отделение.*|" +
                        "^Добрый день\\. Ваше почтовое отправление [A-Z0-9]+ будет возвращено отправителю через 10 дней\\."),
                GlobalStatus.CUSTOMER_NOT_PICKING_UP);
    }

    /**
     * Устанавливает статус для списка треков посылок.
     *
     * @param trackInfoDTOList Список объектов с информацией о трекинге.
     * @return Статус, который соответствует последнему трекинговому событию.
     */
    public GlobalStatus setStatus(List<TrackInfoDTO> trackInfoDTOList) {
        if (trackInfoDTOList.isEmpty()) {
            return GlobalStatus.UNKNOWN_STATUS; // Если список пустой, статус неизвестен
        }

        // Получаем последний статус
        String lastStatus = trackInfoDTOList.get(0).getInfoTrack();

        // Проверяем последний статус
        for (Map.Entry<Pattern, GlobalStatus> entry : statusPatterns.entrySet()) {

            if (entry.getKey().matcher(lastStatus).find()) {
                // Если последний статус соответствует определенному паттерну
                Pattern returnPattern = Pattern.compile("^Почтовое отправление подготовлено в ОПС к доставке на сортировочный пункт$|" +
                        "^Почтовое отправление прибыло на сортировочный пункт$|" +
                        "^Почтовое отправление подготовлено в сортировочном пункте к доставке на ОПС отправителя$");

                if (returnPattern.matcher(lastStatus).find()) {
                    // Проверяем историю на наличие статуса возврата
                    for (TrackInfoDTO trackInfoDTO : trackInfoDTOList) {
                        if (trackInfoDTO.getInfoTrack().equals("Почтовое отправление готово к возврату")) {
                            return GlobalStatus.RETURN_IN_PROGRESS;
                        }
                    }
                }
                return entry.getValue();
            }
        }
        // Дефолтный статус, если не найдено (отладка новых статусов)
        return GlobalStatus.UNKNOWN_STATUS;
    }

    /**
     * Получает HTML-иконку для заданного статуса.
     *
     * @param status Статус, для которого необходимо получить иконку.
     * @return HTML-код иконки для статуса.
     */
    public String getIcon(GlobalStatus status) {
        if (status != null) {
            return status.getIconHtml();
        }
        // Статус не найден, возвращаем иконку по умолчанию
        return GlobalStatus.UNKNOWN_STATUS.getIconHtml();
    }


}