package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DTO с информацией о версии приложения.
 */
@Getter
@AllArgsConstructor
public class AppVersionDTO {
    /**
     * Текущая версия приложения.
     */
    private final String version;
}
