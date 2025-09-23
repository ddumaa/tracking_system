package com.project.tracking_system.dto;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.NameSource;
import com.project.tracking_system.entity.RouteDirection;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.utils.NameUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TrackParcelDTO {
    private Long id;
    private String number;
    private String status;
    private RouteDirection routeDirection;
    private String timestamp;
    private transient String iconHtml;
    private Long storeId;
    private String customerName;
    /**
     * Сокращённое представление ФИО покупателя для компактного вывода.
     */
    private String shortCustomerName;
    private String customerPhone;
    private NameSource nameSource;

    /**
     * Конструктор DTO на основе сущности TrackParcel.
     * <p>
     * Преобразует дату в часовую зону пользователя и извлекает
     * данные о покупателе, если они связаны с посылкой.
     * </p>
     *
     * @param trackParcel сущность посылки
     * @param userZone    часовой пояс пользователя
     */
    public TrackParcelDTO(TrackParcel trackParcel, ZoneId userZone) {
        this.id = trackParcel.getId();
        this.number = trackParcel.getNumber();
        this.status = trackParcel.getStatus().getDescription();
        this.routeDirection = trackParcel.getRouteDirection();
        this.storeId = trackParcel.getStore().getId();
        this.timestamp = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                .withZone(userZone)
                .format(trackParcel.getTimestamp());

        Customer customer = trackParcel.getCustomer();
        if (customer != null) {
            this.customerName = customer.getFullName();
            // Формируем сокращённое имя на основе полного ФИО
            this.shortCustomerName = NameUtils.shortenName(this.customerName);
            this.customerPhone = customer.getPhone();
            this.nameSource = customer.getNameSource();
        }
    }
}