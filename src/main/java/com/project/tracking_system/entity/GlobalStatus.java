package com.project.tracking_system.entity;

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

    DELIVERED("Вручена", "<i class='bi bi-check2-circle status-icon delivered'></i>"),
    WAITING_FOR_CUSTOMER("Ожидает клиента", "<i class='bi bi-clock-history status-icon waiting'></i>"),
    IN_TRANSIT("В пути", "<i class='bi bi-truck status-icon transit'></i>"),
    CUSTOMER_NOT_PICKING_UP("Клиент не забирает", "<i class='bi bi-clock-history status-icon not-picking'></i>"),
    RETURN_IN_PROGRESS("Возврат в пути", "<i class='bi bi-truck status-icon return-progress'></i>"),
    RETURN_PENDING_PICKUP("Возврат ожидает забора", "<i class='bi bi-box-arrow-in-down-right status-icon return-pending'></i>"),
    RETURNED("Возврат забран", "<i class='bi bi-check2-circle status-icon returned'></i>"),
    PRE_REGISTERED("Предрегистрация", "<i class='bi bi-hourglass status-icon preregistered'></i>"),
    REGISTERED("Заявка зарегистрирована", "<i class='bi bi-file-earmark-text status-icon registered'></i>"),
    UNKNOWN_STATUS("Неизвестный статус", "<i class='bi bi-question-circle status-icon unknown'></i>");

    private final String description;
    private final String iconHtml;

    GlobalStatus(String description, String iconHtml) {
        this.description = description;
        this.iconHtml = iconHtml;
    }

    public static GlobalStatus fromDescription(String description) {
        for (GlobalStatus status : values()) {
            if (status.description.equals(description)) {
                return status;
            }
        }
        return UNKNOWN_STATUS; // Если не найдено, возвращаем UNKNOWN_STATUS
    }

    /**
     * Проверяет, является ли статус финальным.
     * <p>
     * Финальными считаются только {@link #DELIVERED} и {@link #RETURNED}.
     * Статус {@link #WAITING_FOR_CUSTOMER} не является финальным и
     * используется лишь для фиксации даты прибытия на пункт выдачи.
     * </p>
     *
     * @return {@code true}, если статус {@code DELIVERED} или {@code RETURNED}
     */
    public boolean isFinal() {
        return this == DELIVERED || this == RETURNED;
    }

}