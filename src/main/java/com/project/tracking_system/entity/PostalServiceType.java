package com.project.tracking_system.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author Dmitriy Anisimov
 * @date 15.03.2025
 */
@Getter
@RequiredArgsConstructor
public enum PostalServiceType {

    BELPOST("Белпочта"),
    EVROPOST("Европочта"),
    UNKNOWN("Неизвестный");

    private final String displayName;

}