package com.project.tracking_system.model;

public enum GlobalStatus {

    DELIVERED("Вручена", "<i class=\"bi bi-check2-circle\" style=\"font-size: 2rem; color: #008000\"></i>"),
    WAITING_FOR_CUSTOMER("Ожидает клиента", "<i class=\"bi bi-clock-history\" style=\"font-size: 2rem; color: #fff200\"></i>"),
    IN_TRANSIT("В пути к клиенту", "<i class=\"bi bi-truck\" style=\"font-size: 2rem; color: #0000FF\"></i>"),
    CUSTOMER_NOT_PICKING_UP("Клиент не забирает посылку", "<i class=\"bi bi-clock-history\" style=\"font-size: 2rem; color: #ff7300\"></i>"),
    RETURN_IN_PROGRESS("Возврат в пути", "<i class=\"bi bi-truck\" style=\"font-size: 2rem; color: #FF0000; transform: scaleX(-1)\"></i>"),
    RETURNED_TO_SENDER("Возврат забран", "<i class=\"bi bi-check2-circle\" style=\"font-size: 2rem; color: #FF0000\"></i>"),
    REGISTERED("Заявка зарегистрирована", "<i class=\"bi bi-file-earmark-text\" style=\"font-size: 2rem; color: #007bff\"></i>");

    private final String description;
    private final String iconHtml;

    GlobalStatus(String description, String iconHtml) {
        this.description = description;
        this.iconHtml = iconHtml;
    }

    public String getDescription() {
        return description;
    }

    public String getIconHtml() {
        return iconHtml;
    }

}