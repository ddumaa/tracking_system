package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.entity.GlobalStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
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
    // LinkedHashMap обеспечивает стабильный порядок перебора,
    // поэтому специфичные шаблоны должны добавляться первыми
    private static final Map<Pattern, GlobalStatus> statusPatterns = new LinkedHashMap<>();

    /**
     * Шаблон для статусов, связанных с процессом возврата отправителю,
     * включая подготовку и дальнейшее перемещение отправления.
     */
    private static final Pattern RETURN_PATTERN = Pattern.compile(
            "^Подготовлено для возврата$|" +
            "^Почтовое отправление подготовлено в ОПС к доставке на сортировочный пункт$|" +
            "^Почтовое отправление прибыло на сортировочный пункт$|" +
            "^Почтовое отправление подготовлено в сортировочном пункте к доставке на ОПС отправителя$");

    /**
     * Шаблон начальных статусов, сигнализирующих о подготовке отправления к возврату.
     */
    private static final Pattern RETURN_START_PATTERN = Pattern.compile(
            "^Почтовое отправление готово к возврату$|^Подготовлено для возврата$");

    /**
     * Специальный шаблон для статусов Европочты о прибытии отправления в ОПС
     * для выдачи отправителю, когда начинается ожидание на возврат.
     */
    private static final Pattern EUROPOST_RETURN_PICKUP_PATTERN = Pattern.compile(
            "^Отправление [A-Z]{2}[A-Z0-9]+ прибыло для возврата в ОПС №\\s*\\d+.*$",
            Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);

    /**
     * Шаблон для статусов Белпочты, указывающих на прибытие отправления в конкретное
     * отделение для возврата отправителю и ожидания выдачи.
     */
    private static final Pattern RETURN_BRANCH_PICKUP_PATTERN = Pattern.compile(
            "^Почтовое отправление прибыло на отделение №\\s*\\d+.*(?:для возврата|для выдачи отправителю|возврат[а-я]*).*$",
            Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);

    /**
     * Шаблон для статусов о прибытии на отделение без признаков возврата,
     * которые должны трактоваться как ожидание клиента.
     */
    private static final Pattern BRANCH_WAITING_PATTERN = Pattern.compile(
            "^Почтовое отправление прибыло на отделение №\\s*\\d+.*$",
            Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);

    static {
        // Инициализация карты регулярных выражений и статусов
        // Специальное правило для отмены выдачи, чтобы оно имело приоритет над успешным вручением
        statusPatterns.put(Pattern.compile("^Аннулирование операции вручения$"), GlobalStatus.WAITING_FOR_CUSTOMER);
        statusPatterns.put(Pattern.compile("^Почтовое отправление выдано|^Вручено"), GlobalStatus.DELIVERED);
        statusPatterns.put(Pattern.compile("^Почтовое отправление прибыло на ОПС выдачи|" +
                "^Почтовое отправление прибыло для выдачи|" +
                "^Добрый день\\. Срок бесплатного хранения|" +
                "^Поступило в учреждение доставки.*"),
                GlobalStatus.WAITING_FOR_CUSTOMER);
        statusPatterns.put(Pattern.compile("^Почтовое отправление принято на ОПС|" +
                        "^Оплачено на ОПС|^Отправлено|^Принято от отправителя|" +
                        "^Поступило в обработку|" +
                        "^Почтовое отправление подготовлено в ОПС к доставке на сортировочный пункт$|" +
                        "^Почтовое отправление прибыло на сортировочный пункт$|" +
                        "^Почтовое отправление подготовлено в сортировочном пункте к доставке на ОПС назначения$"),
                GlobalStatus.IN_TRANSIT);
        statusPatterns.put(RETURN_START_PATTERN, GlobalStatus.RETURN_IN_PROGRESS);
        statusPatterns.put(EUROPOST_RETURN_PICKUP_PATTERN, GlobalStatus.RETURN_PENDING_PICKUP);
        statusPatterns.put(RETURN_BRANCH_PICKUP_PATTERN, GlobalStatus.RETURN_PENDING_PICKUP);
        statusPatterns.put(Pattern.compile("^Почтовое отправление возвращено отправителю$"), GlobalStatus.RETURNED);
        statusPatterns.put(Pattern.compile("^Заявка на почтовое отправление зарегистрирована$"), GlobalStatus.REGISTERED);
        statusPatterns.put(Pattern.compile("^Добрый день\\. Отправление [A-Z0-9]+ не востребовано получателем.*|" +
                        "^Отправление с [0-9]{2}\\.[0-9]{2}\\.[0-9]{4} ожидает вручения в Отделение.*|" +
                        "^Добрый день\\. Ваше почтовое отправление [A-Z0-9]+ будет возвращено отправителю через 10 дней\\."),
                GlobalStatus.CUSTOMER_NOT_PICKING_UP);
    }

    /**
     * Определяет итоговый статус для списка трекинговых записей.
     * Метод предварительно нормализует последний статус, устраняя лишние пробелы
     * и служебные символы в конце строки, после чего проверяет его соответствие
     * известным шаблонам.
     *
     * @param trackInfoDTOList список событий трекинга в обратном хронологическом порядке
     * @return статус, соответствующий последнему событию
     */
    public GlobalStatus setStatus(List<TrackInfoDTO> trackInfoDTOList) {
        if (trackInfoDTOList.isEmpty()) {
            return GlobalStatus.UNKNOWN_STATUS; // Если список пустой, статус неизвестен
        }

        // Получаем последний статус и нормализуем его
        String lastStatus = norm(trackInfoDTOList.get(0).getInfoTrack());

        boolean returnStartChecked = false;
        boolean hasReturnStart = false;

        // Проверяем последний статус
        for (Map.Entry<Pattern, GlobalStatus> entry : statusPatterns.entrySet()) {
            if (entry.getKey().matcher(lastStatus).find()) {
                if (RETURN_PATTERN.matcher(lastStatus).find()) {
                    if (!returnStartChecked) {
                        hasReturnStart = hasReturnStartStatus(trackInfoDTOList);
                        returnStartChecked = true;
                    }
                    if (hasReturnStart) {
                        return GlobalStatus.RETURN_IN_PROGRESS;
                    }
                }

                GlobalStatus matchedStatus = entry.getValue();
                if (matchedStatus == GlobalStatus.RETURN_PENDING_PICKUP) {
                    if (!returnStartChecked) {
                        hasReturnStart = hasReturnStartStatus(trackInfoDTOList);
                        returnStartChecked = true;
                    }
                    if (!hasReturnStart) {
                        return GlobalStatus.WAITING_FOR_CUSTOMER;
                    }
                }
                return matchedStatus;
            }
        }

        if (BRANCH_WAITING_PATTERN.matcher(lastStatus).find()) {
            return GlobalStatus.WAITING_FOR_CUSTOMER;
        }

        // Дефолтный статус, если не найдено (отладка новых статусов)
        return GlobalStatus.UNKNOWN_STATUS;
    }

    /**
     * Проверяет, содержит ли история трекинга события, указывающие на старт процесса возврата.
     *
     * @param trackInfoDTOList список событий трекинга
     * @return {@code true}, если найдено событие из {@link #RETURN_START_PATTERN}, иначе {@code false}
     */
    private boolean hasReturnStartStatus(List<TrackInfoDTO> trackInfoDTOList) {
        for (TrackInfoDTO trackInfoDTO : trackInfoDTOList) {
            String status = norm(trackInfoDTO.getInfoTrack());
            if (RETURN_START_PATTERN.matcher(status).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Нормализует строку статуса: заменяет неразрывные пробелы на обычные,
     * схлопывает повторяющиеся пробелы и удаляет завершающие точки или
     * скобки вместе с их содержимым.
     *
     * @param rawStatus исходное текстовое представление статуса
     * @return очищенная строка, готовая к сопоставлению с паттернами
     */
    private String norm(String rawStatus) {
        if (rawStatus == null) {
            return "";
        }

        String normalized = rawStatus.replace('\u00A0', ' ').trim();
        normalized = normalized.replaceAll(" {2,}", " ");

        String previous;
        do {
            previous = normalized;
            normalized = normalized.replaceAll("\\s*\\([^)]*\\)$", "").trim();
            normalized = normalized.replaceAll("[\\.)]+$", "").trim();
        } while (!normalized.equals(previous));

        return normalized;
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