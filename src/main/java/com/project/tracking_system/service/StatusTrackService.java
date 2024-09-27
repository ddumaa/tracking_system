package com.project.tracking_system.service;

import org.springframework.stereotype.Service;

@Service
public class StatusTrackService {

    public String setStatus(String statusTrack) {

        if (statusTrack.startsWith("Почтовое отправление выдано.") ||
                statusTrack.startsWith("Вручено")) {
            return "Вручена";

        } else if (statusTrack.startsWith("Почтовое отправление прибыло на ОПС выдачи") ||
                statusTrack.startsWith("Добрый день. Срок бесплатного хранения почтового отправления ") ||
                statusTrack.startsWith("Поступило в учреждение доставки")) {
            return "Ожидает клиента";

        } else if (statusTrack.equals("Почтовое отправление принято на ОПС") ||
                statusTrack.equals("Оплачено на ОПС") ||
                statusTrack.equals("Почтовое отправление подготовлено в ОПС к доставке") ||
                statusTrack.equals("Почтовое отправление прибыло на сортировочный пункт") ||
                statusTrack.equals("Почтовое отправление подготовлено в сортировочном пункте к доставке на ОПС назначения") ||
                statusTrack.startsWith("Отправлено") ||
                statusTrack.startsWith("Принято от отправителя") ||
                statusTrack.startsWith("Поступило в обработку")) {
            return "Впути к клиенту";

        } else if (statusTrack.equals("Заявка на почтовое отправление зарегистрирована")) {
            return statusTrack;

        } else if (statusTrack.matches("Добрый день. Отправление ([A-Z0-9]+) не востребовано получателем.*") ||
                statusTrack.matches("Отправление с ([0-9]{2}\\.[0-9]{2}\\.[0-9]{4}) ожидает вручения в Отделение*") ||
                statusTrack.matches("Добрый день. Ваше почтовое отправление ([A-Z0-9]+) будет возвращено отправителю через 10 дней.")) {
            return "Клиент не забирает посылку";

        } else if (statusTrack.equals("Почтовое отправление готово к возврату") ||
                statusTrack.equals("Почтовое отправление подготовлено в ОПС к доставке на сортировочный пункт")){
            return "Возврат впути";

        } else if (statusTrack.matches("Почтовое отправление прибыло на Отделение №([0-9]+)*")) {
            return statusTrack;

        } else if (statusTrack.equals("Почтовое отправление возвращено отправителю")) {
            return "Возврат забран";
        }
        return statusTrack;
    }

    public String getIcon(String status) {

        if (status.equals("Вручена")) {
            return "<i class=\"bi bi-check2-circle\"  style=\"font-size: 2rem; color: #008000\"></i>";

        } else if (status.equals("Почтовое отправление прибыло на ОПС выдачи")) {
            return "<i class=\"bi bi-clock-history\"  style=\"font-size: 2rem; color: #fff200\"></i>";

        } else if (status.equals("Почтовое отправление принято на ОПС")) {
            return "<i class=\"bi bi-truck\"  style=\"font-size: 2rem; color: #0000FF\"></i>";

        } else if (status.equals("Заявка на почтовое отправление зарегистрирована")) {
            return "<i class=\"bi bi-pass\"  style=\"font-size: 2rem; color: #000080\"></i>";

        } else if (status.matches("Добрый день. Отправление ([A-Z0-9]+) не востребовано получателем.*")) {
            return "<i class=\"bi bi-clock-history\"  style=\"font-size: 2rem; color: #ff7300\"></i>";

        } else if (status.equals("Почтовое отправление готово к возврату")){
            return "<i class=\"bi bi-truck\"  style=\"font-size: 2rem; color: #FF0000; transform: scaleX(-1)\"></i>";

        } else if (status.matches("Почтовое отправление прибыло на Отделение №([0-9]+)*")) {
            return "<i class=\"bi bi-clock-history\"  style=\"font-size: 2rem\"></i>";

        } else if (status.equals("Почтовое отправление возвращено отправителю")) {
            return "<i class=\"bi bi-check2-circle\"  style=\"font-size: 2rem; color: #FF0000\"></i>";
        }
        return "<i class=\"bi bi-tencent-qq\"  style=\"font-size: 2rem\"></i>";
    }

}