package com.project.tracking_system.model;

import lombok.Getter;

@Getter
public enum GlobalStatus {

    DELIVERED("Вручена", "<i class=\"bi bi-check2-circle\" style=\"font-size: 2rem; color: #008000\"></i>"),
    WAITING_FOR_CUSTOMER("Ожидает клиента", "<i class=\"bi bi-clock-history\" style=\"font-size: 2rem; color: #fff200\"></i>"),
    IN_TRANSIT("В пути", "<i class=\"bi bi-truck\" style=\"font-size: 2rem; color: #0000FF\"></i>"),
    CUSTOMER_NOT_PICKING_UP("Клиент не забирает посылку", "<i class=\"bi bi-clock-history\" style=\"font-size: 2rem; color: #ff7300\"></i>"),
    RETURN_IN_PROGRESS("Возврат в пути", "<i class=\"bi bi-truck\" style=\"font-size: 2rem; color: #FF0000; transform: scaleX(-1)\"></i>"),
    RETURN_PENDING_PICKUP("Возврат ожидает забора в отделении", "<i class=\"bi bi-box-arrow-in-down-right\" style=\"font-size: 2rem; color: #FF7F00\"></i>"),
    RETURNED_TO_SENDER("Возврат забран", "<i class=\"bi bi-check2-circle\" style=\"font-size: 2rem; color: #FF0000\"></i>"),
    REGISTERED("Заявка зарегистрирована", "<i class=\"bi bi-file-earmark-text\" style=\"font-size: 2rem; color: #007bff\"></i>");

    private final String description;
    private final String iconHtml;

    GlobalStatus(String description, String iconHtml) {
        this.description = description;
        this.iconHtml = iconHtml;
    }

}