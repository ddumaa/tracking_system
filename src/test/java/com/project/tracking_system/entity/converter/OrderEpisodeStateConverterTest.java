package com.project.tracking_system.entity.converter;

import com.project.tracking_system.entity.OrderEpisodeState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Модульные тесты для {@link OrderEpisodeStateConverter} проверяют поддержку
 * исторических значений и корректность прямого преобразования.
 */
class OrderEpisodeStateConverterTest {

    private OrderEpisodeStateConverter converter;

    /**
     * Подготавливает экземпляр конвертера перед каждым тестом.
     */
    @BeforeEach
    void setUp() {
        converter = new OrderEpisodeStateConverter();
    }

    /**
     * Проверяет, что при сохранении используется строковое имя enum.
     */
    @Test
    void convertToDatabaseColumn_returnsEnumName() {
        String stored = converter.convertToDatabaseColumn(OrderEpisodeState.SUCCESS_NO_EXCHANGE);

        assertThat(stored).isEqualTo("SUCCESS_NO_EXCHANGE");
    }

    /**
     * Убеждаемся, что старое значение DELIVERED корректно сопоставляется.
     */
    @Test
    void convertToEntityAttribute_supportsLegacyDelivered() {
        OrderEpisodeState state = converter.convertToEntityAttribute("DELIVERED");

        assertThat(state).isEqualTo(OrderEpisodeState.SUCCESS_NO_EXCHANGE);
    }

    /**
     * Убеждаемся, что старое значение RETURNED корректно сопоставляется.
     */
    @Test
    void convertToEntityAttribute_supportsLegacyReturned() {
        OrderEpisodeState state = converter.convertToEntityAttribute("RETURNED");

        assertThat(state).isEqualTo(OrderEpisodeState.RETURNED_NO_REPLACEMENT);
    }

    /**
     * Убеждаемся, что старое значение EXCHANGED корректно сопоставляется.
     */
    @Test
    void convertToEntityAttribute_supportsLegacyExchanged() {
        OrderEpisodeState state = converter.convertToEntityAttribute("EXCHANGED");

        assertThat(state).isEqualTo(OrderEpisodeState.SUCCESS_AFTER_EXCHANGE);
    }

    /**
     * Проверяет, что неизвестное значение приводит к исключению.
     */
    @Test
    void convertToEntityAttribute_throwsForUnknownValue() {
        assertThrows(IllegalArgumentException.class, () -> converter.convertToEntityAttribute("UNKNOWN"));
    }
}
