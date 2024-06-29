package com.project.tracking_system.service;

import org.springframework.stereotype.Service;

@Service
public class StatusIconService {

    public String getIcon(String status) {
        if (status.startsWith("Почтовое отправление выдано.") ||
                status.startsWith("Вручено")) {
            return "<i class=\"bi bi-check2-circle\"  style=\"font-size: 2rem; color: #008000\"></i>";
        } else if (status.startsWith("Почтовое отправление прибыло на ОПС выдачи") ||
                status.startsWith("Добрый день. Срок бесплатного хранения почтового отправления ") ||
                status.startsWith("Поступило в учреждение доставки")) {
            return "<i class=\"bi bi-clock-history\"  style=\"font-size: 2rem; color: #fff200\"></i>";
        } else if (status.equals("Почтовое отправление принято на ОПС") ||
                status.equals("Оплачено на ОПС") ||
                status.equals("Почтовое отправление подготовлено в ОПС к доставке") ||
                status.equals("Почтовое отправление прибыло на сортировочный пункт") ||
                status.equals("Почтовое отправление подготовлено в сортировочном пункте к доставке на ОПС назначения") ||
                status.startsWith("Отправлено") ||
                status.startsWith("Принято от отправителя") ||
                status.startsWith("Поступило в обработку")) {
            return "<i class=\"bi bi-truck\"  style=\"font-size: 2rem; color: #0000FF\"></i>";
        } else if (status.equals("Заявка на почтовое отправление зарегистрирована")) {
            return "<i class=\"bi bi-pass\"  style=\"font-size: 2rem; color: #000080\"></i>";
        } else if (status.matches("Добрый день. Отправление ([A-Z0-9]+) не востребовано получателем.*") ||
                status.matches("Отправление с ([0-9]{2}\\.[0-9]{2}\\.[0-9]{4}) ожидает вручения в Отделение*") ||
                status.matches("Добрый день. Ваше почтовое отправление ([A-Z0-9]+) будет возвращено отправителю через 10 дней.")) {
            return "<i class=\"bi bi-clock-history\"  style=\"font-size: 2rem; color: #ff7300\"></i>";
        } else if (status.equals("Почтовое отправление готово к возврату") ||
                status.equals("Почтовое отправление подготовлено в ОПС к доставке на сортировочный пункт")){
            return "<i class=\"bi bi-truck\"  style=\"font-size: 2rem; color: #FF0000; transform: scaleX(-1)\"></i>";
        } else if (status.matches("Почтовое отправление прибыло на Отделение №([0-9]+)*")) {
            return "<i class=\"bi bi-clock-history\"  style=\"font-size: 2rem\"></i>";
        } else if (status.equals("Почтовое отправление возвращено отправителю")) {
            return "<i class=\"bi bi-check2-circle\"  style=\"font-size: 2rem; color: #FF0000\"></i>";
        }
        return "<i class=\"bi bi-tencent-qq\"  style=\"font-size: 2rem\"></i>";
    }
}