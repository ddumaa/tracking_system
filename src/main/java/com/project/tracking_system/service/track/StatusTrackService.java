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

    /**
     * Определяет итоговый статус по последней записи, учитывая контекст возврата во всей истории.
     * @param trackInfoDTOList события трекинга (0 — самое свежее)
     */
    public GlobalStatus setStatus(List<TrackInfoDTO> trackInfoDTOList) {
        if (trackInfoDTOList == null || trackInfoDTOList.isEmpty()) {
            return GlobalStatus.UNKNOWN_STATUS;
        }

        final String last = norm(trackInfoDTOList.get(0).getInfoTrack());
        final boolean isReturnFlow = hasReturnStartStatus(trackInfoDTOList);

        // 1) Приоритетные
        if (CANCEL_ISSUANCE.matcher(last).matches()) return GlobalStatus.WAITING_FOR_CUSTOMER;
        if (DELIVERED.matcher(last).matches())      return GlobalStatus.DELIVERED;
        if (RETURNED.matcher(last).matches())       return GlobalStatus.RETURNED;

        // 2) Возврат: явные статусы прибытия/ожидания с отметкой возврата
        if (RETURN_PICKUP_EUROPOST.matcher(last).matches() || RETURN_PICKUP_BELPOST.matcher(last).matches()) {
            return isReturnFlow ? GlobalStatus.RETURN_PENDING_PICKUP : GlobalStatus.WAITING_FOR_CUSTOMER;
        }

        // 3) Ожидание вручения в конкретном ОПС/Отделении (без явных слов о возврате)
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

        // 7) Двусмысленные сортер-этапы: в зав-ти от контекста — «возврат в пути» или «в пути»
        if (SORTING_CENTER_STEPS.matcher(last).matches()) {
            return isReturnFlow ? GlobalStatus.RETURN_IN_PROGRESS : GlobalStatus.IN_TRANSIT;
        }

        // 8) В пути
        if (IN_TRANSIT_GROUP.matcher(last).matches()) return GlobalStatus.IN_TRANSIT;

        // 9) Прочее
        if (REGISTERED.matcher(last).matches()) return GlobalStatus.REGISTERED;
        if (CUSTOMER_NOT_PICKING_UP.matcher(last).matches()) return GlobalStatus.CUSTOMER_NOT_PICKING_UP;

        return GlobalStatus.UNKNOWN_STATUS;
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
     * Нормализует строку: NBSP/NNBSP → пробел, схлопывает пробелы, срезает завершающие скобки/точки/многоточие.
     */
    private String norm(String rawStatus) {
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

    /**
     * Возвращает HTML-иконку для статуса.
     */
    public String getIcon(GlobalStatus status) {
        return status != null ? status.getIconHtml() : GlobalStatus.UNKNOWN_STATUS.getIconHtml();
    }
}