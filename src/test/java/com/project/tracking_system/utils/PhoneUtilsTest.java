package com.project.tracking_system.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для {@link PhoneUtils}.
 */
class PhoneUtilsTest {

    @Test
    void normalizeValidNumberWithPlus() {
        String normalized = PhoneUtils.normalizePhone("+375 29-123-45-67");
        assertEquals("375291234567", normalized);
    }

    @Test
    void normalizeValidNumberWithEight() {
        String normalized = PhoneUtils.normalizePhone("8 (029) 123-45-67");
        assertEquals("375291234567", normalized);
    }

    @Test
    void invalidNumberShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> PhoneUtils.normalizePhone("12345"));
    }
}
