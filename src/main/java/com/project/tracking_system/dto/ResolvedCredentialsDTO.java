package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Dmitriy Anisimov
 * @date 29.01.2025
 */
@Getter
@Setter
@AllArgsConstructor
public class ResolvedCredentialsDTO {

    private final String jwt;
    private final String serviceNumber;

}