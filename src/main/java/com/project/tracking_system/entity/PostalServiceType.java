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

    public static PostalServiceType fromCode(String code) {
        for (PostalServiceType type : values()) {
            if (type.name().equalsIgnoreCase(code)) {
                return type;
            }
        }
        return UNKNOWN;
    }

}