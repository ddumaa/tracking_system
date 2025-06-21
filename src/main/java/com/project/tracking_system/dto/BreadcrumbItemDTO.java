package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Элемент навигационных хлебных крошек.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BreadcrumbItemDTO {
    private String label;
    private String url;
}
