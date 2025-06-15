package com.project.tracking_system.customer;

import com.project.tracking_system.utils.PhoneUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Дополнительные сценарии нормализации номера телефона.
 */
class PhoneNormalizationEdgeCasesTest {

    @Test
    void normalizeNumberStartingWithZero() {
        String normalized = PhoneUtils.normalizePhone("044 123-45-67");
        assertEquals("375441234567", normalized);
    }

    @Test
    void normalizeNumberWithoutPrefix() {
        String normalized = PhoneUtils.normalizePhone("291234567");
        assertEquals("375291234567", normalized);
    }

    @Test
    void nullNumberShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> PhoneUtils.normalizePhone(null));
    }

    @Test
    void blankNumberShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> PhoneUtils.normalizePhone("   "));
    }
}
