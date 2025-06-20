package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Элемент навигационных хлебных крошек.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BreadcrumbItemDTO {
    private String label;
    private String url;
}
