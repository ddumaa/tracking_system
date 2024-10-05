package com.project.tracking_system.service;

import com.project.tracking_system.model.GlobalStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class StatusTrackService {

    private static final Map<Pattern, GlobalStatus> statusPatterns = new HashMap<>();

    static {
        statusPatterns.put(Pattern.compile("^Почтовое отправление выдано.|^Вручено"), GlobalStatus.DELIVERED);
        statusPatterns.put(Pattern.compile("^Почтовое отправление прибыло на ОПС выдачи|^Добрый день\\. Срок бесплатного хранения|" +
                "^Поступило в учреждение доставки"), GlobalStatus.WAITING_FOR_CUSTOMER);
        statusPatterns.put(Pattern.compile("^Почтовое отправление принято на ОПС|" +
                "^Оплачено на ОПС|^Почтовое отправление подготовлено в ОПС к доставке|^Почтовое отправление прибыло на сортировочный пункт|" +
                "^Почтовое отправление подготовлено в сортировочном пункте к доставке на ОПС назначения|^Отправлено|^Принято от отправителя|" +
                "^Поступило в обработку"), GlobalStatus.IN_TRANSIT);
        statusPatterns.put(Pattern.compile("^Заявка на почтовое отправление зарегистрирована$"), null);  // Возврат оригинального статуса
        statusPatterns.put(Pattern.compile("^Добрый день\\. Отправление [A-Z0-9]+ не востребовано получателем.*|" +
                "^Отправление с [0-9]{2}\\.[0-9]{2}\\.[0-9]{4} ожидает вручения в Отделение.*|" +
                "^Добрый день\\. Ваше почтовое отправление [A-Z0-9]+ будет возвращено отправителю через 10 дней\\."), GlobalStatus.CUSTOMER_NOT_PICKING_UP);
        statusPatterns.put(Pattern.compile("^Почтовое отправление готово к возврату|" +
                "^Почтовое отправление подготовлено в ОПС к доставке на сортировочный пункт"), GlobalStatus.RETURN_IN_PROGRESS);
        statusPatterns.put(Pattern.compile("^Почтовое отправление возвращено отправителю$"), GlobalStatus.RETURNED_TO_SENDER);
    }

    public String setStatus(String statusTrack) {
        for (Map.Entry<Pattern, GlobalStatus> entry : statusPatterns.entrySet()) {
            if (entry.getKey().matcher(statusTrack).find()) {
                return entry.getValue() != null ? entry.getValue().getDescription() : statusTrack;
            }
        }
        return statusTrack;
    }

    public String getIcon(String status) {
        for (GlobalStatus globalStatus : GlobalStatus.values()) {
            if (globalStatus.getDescription().equals(status)) {
                return globalStatus.getIconHtml();
            }
        }
        // Статус не найден, возвращаем иконку по умолчанию (отладка новых статусов)
        return "<i class=\"bi bi-tencent-qq\" style=\"font-size: 2rem\"></i>";
    }

}