package com.project.tracking_system.dto;

import com.project.tracking_system.entity.AdminNotification;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Форма создания или редактирования административного уведомления.
 */
@Getter
@Setter
@NoArgsConstructor
public class AdminNotificationForm {

    private Long id;
    private String title;
    private String body;

    /**
     * Преобразует тело уведомления в список строк для хранения в БД.
     */
    public List<String> toBodyLines() {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        return Arrays.stream(body.split("\r?\n"))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    /**
     * Создаёт форму на основе сохранённого уведомления.
     */
    public static AdminNotificationForm fromEntity(AdminNotification notification) {
        AdminNotificationForm form = new AdminNotificationForm();
        form.setId(notification.getId());
        form.setTitle(notification.getTitle());
        form.setBody(String.join("\n", notification.getBodyLines()));
        return form;
    }
}
