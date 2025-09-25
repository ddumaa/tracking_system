package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.entity.GlobalStatus;
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

    // ====== Публичный API остался тем же ======
    /** Принимаем только историю (0-й элемент — самый свежий). */
    public GlobalStatus setStatus(List<TrackInfoDTO> events) {
        return StatusResolver.INSTANCE.resolveStatus(events);
    }

    /** Возвращает HTML-иконку для статуса. */
    public String getIcon(GlobalStatus status) {
        return status != null ? status.getIconHtml() : GlobalStatus.UNKNOWN_STATUS.getIconHtml();
    }

    // ====== Вся логика инкапсулирована внутри резолвера ======
    private static final class StatusResolver {

        private static final StatusResolver INSTANCE = new StatusResolver();

        private static final int F = Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE;

        // --- Приоритетные/терминальные ---
        private static final Pattern CANCEL_ISSUANCE = Pattern.compile("^Аннулирование операции вручения$", F);
        private static final Pattern DELIVERED = Pattern.compile(
                "^(?:Почтовое отправление\\s+)?(?:выдано|вручено)(?!\\s+отправителю)(?:\\s+(?:получателю|адресату))?(?:\\b|\\.|\\s).*?$",
                F
        );
        private static final Pattern RETURNED = Pattern.compile(
                "^(?:Почтовое отправление\\s+)?(?:(?:вручено|выдано)\\s+отправителю|возвращено\\s+отправителю)(?:\\b|\\.|\\s).*?$",
                F
        );
        private static final Pattern REGISTERED      = Pattern.compile("^Заявка на почтовое отправление зарегистрирована$", F);
        private static final Pattern CUSTOMER_NOT_PICKING_UP = Pattern.compile(
                "^(?:"
                        + "Добрый день\\. Отправление [A-Z0-9]+ не востребовано получателем.*|"
                        + "Отправление с \\d{2}\\.\\d{2}\\.\\d{4} ожидает вручения в Отделение.*|"
                        + "Добрый день\\.\\s*Ваше почтовое отправление [A-Z0-9]+ будет возвращено отправителю через 10 дней(?:\\.|…)?(?:\\s.*)?"
                        + ")$",
                F
        );
        private static final Pattern REGISTRATION_CANCELLED =
                Pattern.compile("^Заявка отменена, срок предоставления почтового отправления истек$", F);


        // --- Явные возвратные (по последнему статусу) ---
        private static final Pattern RETURN_START_PATTERN = Pattern.compile(
                "^(Почтовое отправление готово к возврату|Подготовлено для возврата)$", F
        );
        private static final Pattern RETURN_PICKUP_PATTERN = Pattern.compile(
                "^(?:"
                        + "Отправление [A-Z]{2}[A-Z0-9]+ (?:(?:прибыло.*для возврата.*)|(?:ожидает вручения .*для возврата.*))"
                        + "|"
                        + "Почтовое отправление прибыло на отделение №\\s*\\d+.*(?:для возврата|для выдачи отправителю|возврат[а-я]*).*"
                        + ")$",
                F
        );

        // --- Спорные группы: требуют проверки истории на «возвратные» триггеры ---
        private static final Pattern PICKUP_WAITING_PATTERN = Pattern.compile(
                "^(?:Отправление [A-Z]{2}[A-Z0-9]+ )?.*ожидает вручения в (?:ОПС|Отделение) №\\s*\\d+.*$", F
        );
        private static final Pattern BRANCH_ARRIVED_GENERIC = Pattern.compile(
                "^Почтовое отправление прибыло на отделение №\\s*\\d+.*$", F
        );
        private static final Pattern SORTING_CENTER_STEPS = Pattern.compile(
                "^(Почтовое отправление подготовлено в ОПС к доставке на сортировочный пункт|"
                        + "Почтовое отправление прибыло на сортировочный пункт|"
                        + "Почтовое отправление подготовлено в сортировочном пункте к доставке на ОПС (?:назначения|отправителя))$",
                F
        );

        // --- Прочие группы ---
        private static final Pattern WAITING_GROUP = Pattern.compile(
                "^(Почтовое отправление прибыло (?:на ОПС выдачи|для выдачи)|"
                        + "Добрый день\\.\\s*Срок бесплатного хранения.*|"
                        + "Поступило в учреждение доставки.*)$",
                F
        );
        private static final Pattern IN_TRANSIT_GROUP = Pattern.compile(
                "^(Почтовое отправление принято на ОПС|Оплачено на ОПС|Отправлено|Принято от отправителя|Поступило в обработку)$",
                F
        );

        // --- Триггеры возврата для проверки истории ---
        private static final Pattern[] RETURN_HISTORY_TRIGGERS = new Pattern[] {
                RETURN_START_PATTERN,
                Pattern.compile("\\bдля возврата\\b", F),
                Pattern.compile("выдачи отправителю", F),
                Pattern.compile("возврат[а-я]*", F),
                Pattern.compile("ожидает вручения .*для возврата.*", F),
                RETURNED
        };

        private StatusResolver() {}

        GlobalStatus resolveStatus(List<TrackInfoDTO> events) {
            if (events == null || events.isEmpty()) return GlobalStatus.UNKNOWN_STATUS;

            // 0-й элемент — самый свежий; нормализуем текст
            final String last = norm(events.get(0).getInfoTrack());

            // === 1) Жёсткий приоритет: TERMINAL ===
            if (CANCEL_ISSUANCE.matcher(last).matches())         return GlobalStatus.WAITING_FOR_CUSTOMER;
            if (DELIVERED.matcher(last).matches())               return GlobalStatus.DELIVERED;
            if (RETURNED.matcher(last).matches())                return GlobalStatus.RETURNED;
            if (REGISTERED.matcher(last).matches())              return GlobalStatus.REGISTERED;
            if (REGISTRATION_CANCELLED.matcher(last).matches())  return GlobalStatus.REGISTRATION_CANCELLED;
            if (CUSTOMER_NOT_PICKING_UP.matcher(last).matches()) return GlobalStatus.CUSTOMER_NOT_PICKING_UP;

            // === 2) Явные возвратные шаги (без истории) ===
            if (RETURN_START_PATTERN.matcher(last).matches())    return GlobalStatus.RETURN_IN_PROGRESS;
            if (RETURN_PICKUP_PATTERN.matcher(last).matches())   return GlobalStatus.RETURN_PENDING_PICKUP;

            // === 3) Спорные группы — ONE-SHOT заглядываем в историю ===
            if (PICKUP_WAITING_PATTERN.matcher(last).matches()
                    || BRANCH_ARRIVED_GENERIC.matcher(last).matches()) {
                return hasReturnTriggerInHistory(events)
                        ? GlobalStatus.RETURN_PENDING_PICKUP
                        : GlobalStatus.WAITING_FOR_CUSTOMER;
            }
            if (SORTING_CENTER_STEPS.matcher(last).matches()) {
                return hasReturnTriggerInHistory(events)
                        ? GlobalStatus.RETURN_IN_PROGRESS
                        : GlobalStatus.IN_TRANSIT;
            }

            // === 4) Прочие явные группы ===
            if (WAITING_GROUP.matcher(last).matches())           return GlobalStatus.WAITING_FOR_CUSTOMER;
            if (IN_TRANSIT_GROUP.matcher(last).matches())        return GlobalStatus.IN_TRANSIT;

            // === 5) Фоллбек ===
            return GlobalStatus.UNKNOWN_STATUS;
        }

        // --- Вспомогательное ---

        /** Пробегает всю историю (в любом порядке) и ищет любой возвратный триггер. */
        private static boolean hasReturnTriggerInHistory(List<TrackInfoDTO> events) {
            if (events == null || events.isEmpty()) return false;
            for (TrackInfoDTO dto : events) {
                final String s = norm(dto.getInfoTrack());
                for (Pattern p : RETURN_HISTORY_TRIGGERS) {
                    if (p.matcher(s).find()) return true;
                }
            }
            return false;
        }

        /** Нормализует строку: NBSP/NNBSP → пробел, схлопывает пробелы, обрезает хвостовые скобки/точки/многоточия. */
        private static String norm(String rawStatus) {
            if (rawStatus == null) return "";
            String normalized = rawStatus
                    .replace('\u00A0', ' ')
                    .replace('\u202F', ' ')
                    .trim()
                    .replaceAll("\\s{2,}", " ");

            String prev;
            do {
                prev = normalized;
                normalized = normalized.replaceAll("\\s*\\([^)]*\\)$", "").trim(); // «… (подробности)»
                normalized = normalized.replaceAll("[\\.\\)…]+$", "").trim();     // точки/многоточия/скобки в конце
            } while (!normalized.equals(prev));

            return normalized;
        }
    }

}