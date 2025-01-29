package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author Dmitriy Anisimov
 * @date 29.01.2025
 */
@Data
@AllArgsConstructor
public class ResolvedCredentialsDTO {

    private final String jwt;
    private final String serviceNumber;

}