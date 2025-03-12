package com.project.tracking_system.utils;

import org.springframework.stereotype.Component;

/**
 * @author Dmitriy Anisimov
 * @date 13.03.2025
 */
@Component
public class UpperCaseString {

    public String normalizeTrackNumber(String number) {
        return number != null ? number.toUpperCase() : null;
    }

}
