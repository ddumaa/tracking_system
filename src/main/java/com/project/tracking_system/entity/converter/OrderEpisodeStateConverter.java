package com.project.tracking_system.entity.converter;

import com.project.tracking_system.entity.OrderEpisodeState;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Map;

/**
 * Конвертер состояния эпизода для хранения в базе данных.
 * <p>
 * Класс обеспечивает обратную совместимость: исторические значения
 * {@code DELIVERED}, {@code RETURNED} и {@code EXCHANGED} автоматически
 * сопоставляются новым сценариям состояния.
 * </p>
 */
@Converter(autoApply = true)
public class OrderEpisodeStateConverter implements AttributeConverter<OrderEpisodeState, String> {

    private static final Map<String, OrderEpisodeState> LEGACY_MAPPING = Map.of(
        "DELIVERED", OrderEpisodeState.SUCCESS_NO_EXCHANGE,
        "RETURNED", OrderEpisodeState.RETURNED_NO_REPLACEMENT,
        "EXCHANGED", OrderEpisodeState.SUCCESS_AFTER_EXCHANGE
    );

    /**
     * Преобразует состояние эпизода к строковому представлению для записи в БД.
     *
     * @param attribute текущее состояние эпизода
     * @return строка, которая будет сохранена в колонке, либо {@code null}
     */
    @Override
    public String convertToDatabaseColumn(OrderEpisodeState attribute) {
        return attribute == null ? null : attribute.name();
    }

    /**
     * Восстанавливает состояние эпизода из сохранённого строкового значения.
     *
     * @param dbData значение, считанное из базы данных
     * @return корректное состояние эпизода
     */
    @Override
    public OrderEpisodeState convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return OrderEpisodeState.OPEN;
        }

        try {
            return OrderEpisodeState.valueOf(dbData);
        } catch (IllegalArgumentException ex) {
            OrderEpisodeState legacyState = LEGACY_MAPPING.get(dbData);
            if (legacyState != null) {
                return legacyState;
            }
            throw ex;
        }
    }
}
