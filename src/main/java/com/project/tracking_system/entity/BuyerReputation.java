package com.project.tracking_system.entity;

/**
 * Репутация покупателя в системе.
 */
public enum BuyerReputation {
    /** Репутация формируется. Недостаточно завершённых заказов. */
    NEW("Формируется", "reputation-new"),
    /** Надёжный покупатель. */
    RELIABLE("Надёжный", "reputation-reliable"),
    /** Нейтральный покупатель. */
    NEUTRAL("Нейтральный", "reputation-neutral"),
    /** Ненадёжный покупатель. */
    UNRELIABLE("Ненадёжный", "reputation-unreliable");

    private final String displayName;
    private final String colorClass;

    BuyerReputation(String displayName, String colorClass) {
        this.displayName = displayName;
        this.colorClass = colorClass;
    }

    /**
     * Возвращает отображаемое название репутации.
     *
     * @return русскоязычное название репутации
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Возвращает CSS-класс для отображения репутации.
     *
     * @return имя CSS-класса
     */
    public String getColorClass() {
        return colorClass;
    }
}
