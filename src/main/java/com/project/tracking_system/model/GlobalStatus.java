package com.project.tracking_system.model;

import lombok.Getter;

/**
 * Перечисление, представляющее глобальные статусы посылки.
 * <p>
 * Этот enum содержит различные статусы посылки в системе отслеживания. Каждый статус имеет описание
 * и иконку для отображения в пользовательском интерфейсе.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@Getter
public enum GlobalStatus {

    /**
     * Статус "Вручена". Указывает, что посылка была доставлена получателю.
     * Иконка: зеленый галочка.
     */
    DELIVERED("Вручена", "<i class=\"bi bi-check2-circle\" style=\"font-size: 2rem; color: #008000\"></i>"),

    /**
     * Статус "Ожидает клиента". Указывает, что посылка ожидает забора клиентом.
     * Иконка: желтые часы.
     */
    WAITING_FOR_CUSTOMER("Ожидает клиента", "<i class=\"bi bi-clock-history\" style=\"font-size: 2rem; color: #fff200\"></i>"),

    /**
     * Статус "В пути". Указывает, что посылка находится в пути.
     * Иконка: синий грузовик.
     */
    IN_TRANSIT("В пути", "<i class=\"bi bi-truck\" style=\"font-size: 2rem; color: #0000FF\"></i>"),

    /**
     * Статус "Клиент не забирает посылку". Указывает, что клиент не забрал посылку.
     * Иконка: оранжевые часы.
     */
    CUSTOMER_NOT_PICKING_UP("Клиент не забирает посылку", "<i class=\"bi bi-clock-history\" style=\"font-size: 2rem; color: #ff7300\"></i>"),

    /**
     * Статус "Возврат в пути". Указывает, что посылка возвращается.
     * Иконка: красный грузовик с зеркальным эффектом.
     */
    RETURN_IN_PROGRESS("Возврат в пути", "<i class=\"bi bi-truck\" style=\"font-size: 2rem; color: #FF0000; transform: scaleX(-1)\"></i>"),

    /**
     * Статус "Возврат ожидает забора в отделении". Указывает, что посылка ожидает забора для возврата.
     * Иконка: оранжевая стрелка вниз.
     */
    RETURN_PENDING_PICKUP("Возврат ожидает забора в отделении", "<i class=\"bi bi-box-arrow-in-down-right\" style=\"font-size: 2rem; color: #FF7F00\"></i>"),

    /**
     * Статус "Возврат забран". Указывает, что посылка была забрана отправителем.
     * Иконка: красная галочка.
     */
    RETURNED_TO_SENDER("Возврат забран", "<i class=\"bi bi-check2-circle\" style=\"font-size: 2rem; color: #FF0000\"></i>"),

    /**
     * Статус "Заявка зарегистрирована". Указывает, что заявка на посылку была зарегистрирована в системе.
     * Иконка: синяя иконка документа.
     */
    REGISTERED("Заявка зарегистрирована", "<i class=\"bi bi-file-earmark-text\" style=\"font-size: 2rem; color: #007bff\"></i>");

    private final String description;
    private final String iconHtml;

    /**
     * Конструктор для инициализации статуса.
     *
     * @param description Описание статуса.
     * @param iconHtml HTML-строка для иконки статуса.
     */
    GlobalStatus(String description, String iconHtml) {
        this.description = description;
        this.iconHtml = iconHtml;
    }
}