package com.project.tracking_system.dto;

import java.time.LocalDate;

/**
 * @author Dmitriy Anisimov
 * @date 19.02.2025
 */
public record UpdateInfoDto(Integer updateCount, LocalDate resetDate) {
}