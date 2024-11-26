package com.project.tracking_system.service;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.model.GlobalStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class StatusTrackService {

    private static final Map<Pattern, GlobalStatus> statusPatterns = new HashMap<>();

    static {
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

    public String setStatus(List<TrackInfoDTO> trackInfoDTOList) {
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
                            return GlobalStatus.RETURN_IN_PROGRESS.getDescription();
                        }
                    }
                }
                return entry.getValue() != null ? entry.getValue().getDescription() : lastStatus;
            }
        }
        return lastStatus;
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