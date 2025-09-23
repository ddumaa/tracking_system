package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.RouteDirection;
import org.springframework.stereotype.Service;

import java.util.List;
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

    // ===================== ПАТТЕРНЫ =====================

    // === Приоритетные/терминальные ===
    private static final Pattern CANCEL_ISSUANCE = Pattern.compile("^Аннулирование операции вручения$");
    private static final Pattern DELIVERED = Pattern.compile("^(Почтовое отправление выдано|Вручено)$");
    private static final Pattern RETURNED = Pattern.compile("^Почтовое отправление возвращено отправителю$");
    private static final Pattern REGISTERED = Pattern.compile("^Заявка на почтовое отправление зарегистрирована$");

    // === Контекст возврата (ищется по всей истории) ===
    private static final Pattern RETURN_START_PATTERN = Pattern.compile(
            "^(Почтовое отправление готово к возврату|Подготовлено для возврата)$"
    );

    // === Возврат: явные статусы «прибыло/ожидает … для возврата» (Европочта/Белпочта) ===
    private static final Pattern RETURN_PICKUP_EUROPOST = Pattern.compile(
            "^Отправление [A-Z]{2}[A-Z0-9]+ (?:(?:прибыло.*для возврата.*)|(?:ожидает вручения .*для возврата.*))$",
            Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RETURN_PICKUP_BELPOST = Pattern.compile(
            "^Почтовое отправление прибыло на отделение №\\s*\\d+.*(?:для возврата|для выдачи отправителю|возврат[а-я]*).*$",
            Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE
    );

    // === Ожидание вручения в конкретном ОПС/Отделении (без явной фразы «для возврата») ===
    //   Если контекст возврата был — трактуем как RETURN_PENDING_PICKUP, иначе WAITING_FOR_CUSTOMER
    private static final Pattern PICKUP_WAITING_OPS = Pattern.compile(
            "^Отправление [A-Z]{2}[A-Z0-9]+ .*ожидает вручения в ОПС №\\s*\\d+.*$",
            Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PICKUP_WAITING_BRANCH = Pattern.compile(
            "^Отправление [A-Z]{2}[A-Z0-9]+ .*ожидает вручения в Отделение №\\s*\\d+.*$",
            Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE
    );

    // === Прибыло в отделение (общая формулировка Белпочты) ===
    private static final Pattern BRANCH_ARRIVED_GENERIC = Pattern.compile(
            "^Почтовое отправление прибыло на отделение №\\s*\\d+.*$",
            Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE
    );

    // === Ожидание клиента (без возврата) ===
    private static final Pattern WAITING_GROUP = Pattern.compile(
            "^(Почтовое отправление прибыло на ОПС выдачи|" +
                    "Почтовое отправление прибыло для выдачи|" +
                    "Добрый день\\. Срок бесплатного хранения|" +
                    "Поступило в учреждение доставки.*)$"
    );

    // === Двусмысленные «сортер/перевалка» (в зав-ти от контекста возврата) ===
    private static final Pattern SORTING_CENTER_STEPS = Pattern.compile(
            "^(Почтовое отправление подготовлено в ОПС к доставке на сортировочный пункт|" +
                    "Почтовое отправление прибыло на сортировочный пункт|" +
                    "Почтовое отправление подготовлено в сортировочном пункте к доставке на ОПС (?:назначения|отправителя))$"
    );

    // === В пути (однозначные) ===
    private static final Pattern IN_TRANSIT_GROUP = Pattern.compile(
            "^(Почтовое отправление принято на ОПС|Оплачено на ОПС|Отправлено|Принято от отправителя|Поступило в обработку)$"
    );

    // === «Клиент не забирает» ===
    private static final Pattern CUSTOMER_NOT_PICKING_UP = Pattern.compile(
            "^(Добрый день\\. Отправление [A-Z0-9]+ не востребовано получателем.*|" +
                    "Отправление с [0-9]{2}\\.[0-9]{2}\\.[0-9]{4} ожидает вручения в Отделение.*|" +
                    "Добрый день\\. Ваше почтовое отправление [A-Z0-9]+ будет возвращено отправителю через 10 дней\\.)$"
    );

    // ======= ТРИГГЕРЫ НАПРАВЛЕНИЯ (применяются при импорте/обновлении) =======

    /** Любая из формулировок, которая однозначно говорит: уже возврат. */
    private static final Pattern[] RETURN_DIRECTION_MARKERS = new Pattern[] {
            RETURN_START_PATTERN,
            Pattern.compile("\\bдля возврата\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("выдачи отправителю", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("возврат[а-я]*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("ожидает вручения .*для возврата.*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
            Pattern.compile("^Почтовое отправление возвращено отправителю$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
    };

    // ===================== ПУБЛИЧНЫЕ МЕТОДЫ =====================

    /**
     * Новый основной метод: определяет глобальный статус по последнему событию с учётом направления.
     * Вызывай везде, где есть доступ к parcel.getRouteDirection().
     */
    public GlobalStatus setStatus(List<TrackInfoDTO> events, RouteDirection direction) {
        if (events == null || events.isEmpty()) return GlobalStatus.UNKNOWN_STATUS;

        final String last = norm(events.get(0).getInfoTrack());
        final boolean isReturnFlow = (direction == RouteDirection.RETURN_TO_SENDER);

        // 1) Приоритетные
        if (CANCEL_ISSUANCE.matcher(last).matches()) return GlobalStatus.WAITING_FOR_CUSTOMER;
        if (DELIVERED.matcher(last).matches())       return GlobalStatus.DELIVERED;
        if (RETURNED.matcher(last).matches())        return GlobalStatus.RETURNED;

        // 2) Возврат: явные статусы прибытия/ожидания с отметкой возврата
        if (RETURN_PICKUP_EUROPOST.matcher(last).matches() || RETURN_PICKUP_BELPOST.matcher(last).matches()) {
            return isReturnFlow ? GlobalStatus.RETURN_PENDING_PICKUP : GlobalStatus.WAITING_FOR_CUSTOMER;
        }

        // 3) Ожидает вручения в конкретном ОПС/Отделении (без явных слов о возврате)
        if (PICKUP_WAITING_OPS.matcher(last).matches() || PICKUP_WAITING_BRANCH.matcher(last).matches()) {
            return isReturnFlow ? GlobalStatus.RETURN_PENDING_PICKUP : GlobalStatus.WAITING_FOR_CUSTOMER;
        }

        // 4) Прибыло в отделение (общая формулировка)
        if (BRANCH_ARRIVED_GENERIC.matcher(last).matches()) {
            return isReturnFlow ? GlobalStatus.RETURN_PENDING_PICKUP : GlobalStatus.WAITING_FOR_CUSTOMER;
        }

        // 5) Ожидание клиента
        if (WAITING_GROUP.matcher(last).matches())  return GlobalStatus.WAITING_FOR_CUSTOMER;

        // 6) Явный старт возврата (если это и есть последний статус)
        if (RETURN_START_PATTERN.matcher(last).matches()) return GlobalStatus.RETURN_IN_PROGRESS;

        // 7) Сортер/перевалка (двусмысленно)
        if (SORTING_CENTER_STEPS.matcher(last).matches()) {
            return isReturnFlow ? GlobalStatus.RETURN_IN_PROGRESS : GlobalStatus.IN_TRANSIT;
        }

        // 8) В пути (однозначные)
        if (IN_TRANSIT_GROUP.matcher(last).matches()) return GlobalStatus.IN_TRANSIT;

        // 9) Прочее
        if (REGISTERED.matcher(last).matches())             return GlobalStatus.REGISTERED;
        if (CUSTOMER_NOT_PICKING_UP.matcher(last).matches()) return GlobalStatus.CUSTOMER_NOT_PICKING_UP;

        return GlobalStatus.UNKNOWN_STATUS;
    }

    /**
     * Легаси-перегрузка для совместимости: если пока не пробросили direction,
     * определяем его по истории (как раньше) и делегируем в новый метод.
     * Рекомендуется убрать, когда всё будет переведено на новую сигнатуру.
     */
    public GlobalStatus setStatus(List<TrackInfoDTO> events) {
        if (events == null || events.isEmpty()) return GlobalStatus.UNKNOWN_STATUS;
        // fallback: если в истории есть триггеры — считаем возвратом
        boolean hasReturn = hasReturnTriggerInHistory(events);
        return setStatus(events, hasReturn ? RouteDirection.RETURN_TO_SENDER : RouteDirection.TO_CUSTOMER);
    }

    /**
     * Импорт посылки с историей: один вызов перед первым расчётом статуса.
     * Если в истории есть возвратные триггеры — сразу переводим направление в RETURN_TO_SENDER.
     */
    public RouteDirection resolveDirectionOnImport(List<TrackInfoDTO> history, RouteDirection current) {
        if (current == RouteDirection.RETURN_TO_SENDER) return current;
        return hasReturnTriggerInHistory(history) ? RouteDirection.RETURN_TO_SENDER : RouteDirection.TO_CUSTOMER;
    }

    /**
     * Обновление истории: одноразовая промоция направления при первом «возвратном» статусе.
     * Вызывай при каждом новом событии ДО пересчёта глобального статуса.
     */
    public RouteDirection maybePromoteDirectionOnAppend(RouteDirection currentDirection, String newEventRawText) {
        if (currentDirection == RouteDirection.RETURN_TO_SENDER) return currentDirection;
        String normalized = norm(newEventRawText);
        return isReturnTrigger(normalized) ? RouteDirection.RETURN_TO_SENDER : RouteDirection.TO_CUSTOMER;
    }

    /**
     * Проверяет наличие стартового статуса возврата в истории.
     */
    private boolean hasReturnStartStatus(List<TrackInfoDTO> trackInfoDTOList) {
        for (TrackInfoDTO dto : trackInfoDTOList) {
            final String s = norm(dto.getInfoTrack());
            if (RETURN_START_PATTERN.matcher(s).matches()) return true;
        }
        return false;
    }

    /**
     * Возвращает HTML-иконку для статуса.
     */
    public String getIcon(GlobalStatus status) {
        return status != null ? status.getIconHtml() : GlobalStatus.UNKNOWN_STATUS.getIconHtml();
    }

    // ===================== ВНУТРЕННИЕ УТИЛИТЫ =====================

    /** Нормализует строку: NBSP/NNBSP → пробел, схлопывает пробелы, срезает завершающие скобки/точки/многоточие. */
    public static String norm(String rawStatus) {
        if (rawStatus == null) return "";
        String normalized = rawStatus
                .replace('\u00A0', ' ')   // NBSP
                .replace('\u202F', ' ')   // NNBSP (узкий неразрывный)
                .trim();

        // схлопываем повторяющиеся пробелы
        normalized = normalized.replaceAll("\\s{2,}", " ");

        String prev;
        do {
            prev = normalized;
            // сносим завершающиеся скобки (...)
            normalized = normalized.replaceAll("\\s*\\([^)]*\\)$", "").trim();
            // точки, многоточие, закрывающая скобка/кавычка на конце
            normalized = normalized.replaceAll("[\\.\\)…]+$", "").trim();
        } while (!normalized.equals(prev));

        return normalized;
    }

    /** True, если строка — явный триггер возврата. */
    private boolean isReturnTrigger(String normalized) {
        if (normalized == null || normalized.isBlank()) return false;
        for (Pattern p : RETURN_DIRECTION_MARKERS) {
            if (p.matcher(normalized).find()) return true; // для RETURN_START_PATTERN сработает и find(), и matches()
        }
        return false;
    }

    /** Пробегает историю и ищет любой возвратный триггер. */
    private boolean hasReturnTriggerInHistory(List<TrackInfoDTO> events) {
        if (events == null) return false;
        for (TrackInfoDTO dto : events) {
            if (isReturnTrigger(norm(dto.getInfoTrack()))) return true;
        }
        return false;
    }

}