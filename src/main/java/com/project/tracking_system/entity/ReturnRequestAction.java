package com.project.tracking_system.entity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Доступные действия с активной заявкой на возврат или обмен.
 * <p>
 * Перечисление используется в боте и административном интерфейсе для
 * отображения актуальных кнопок, а также при сериализации команд, которые
 * инициируют переходы между этапами жизненного цикла заявки.
 * </p>
 */
public enum ReturnRequestAction {

    /**
     * Переводит заявку в режим классического возврата без запуска обмена.
     */
    SET_MODE_RETURN("SET_MODE_RETURN", "Перевести в возврат", true),

    /**
     * Запускает обработку обмена и переводит заявку в соответствующий режим.
     */
    SET_MODE_EXCHANGE("SET_MODE_EXCHANGE", "Перевести в обмен", false),

    /**
     * Фиксирует передачу возвратной посылки в доставку покупателем.
     */
    MARK_OUTBOUND_SENT("MARK_OUTBOUND_SENT", "Отметить отправку возврата", false),

    /**
     * Фиксирует прибытие возвратной посылки в пункт назначения магазина.
     */
    MARK_INBOUND_ARRIVED("MARK_INBOUND_ARRIVED", "Отметить прибытие возврата", false),

    /**
     * Фиксирует ручное подтверждение приёма возврата магазином.
     */
    MARK_INBOUND_PICKED_UP("MARK_INBOUND_PICKED_UP", "Отметить приём возврата", false),

    /**
     * Регистрирует или привязывает обменную посылку к заявке.
     */
    REGISTER_EXCHANGE_PARCEL("REGISTER_EXCHANGE_PARCEL", "Зарегистрировать обменную посылку", true),

    /**
     * Отмечает отправку обменной посылки со склада магазина.
     */
    MARK_EXCHANGE_SENT("MARK_EXCHANGE_SENT", "Отметить отправку обмена", true),

    /**
     * Отмечает доставку обменной посылки покупателю.
     */
    MARK_EXCHANGE_DELIVERED("MARK_EXCHANGE_DELIVERED", "Отметить доставку обмена", true),

    /**
     * Обновляет обратный трек и комментарий в карточке заявки.
     */
    UPDATE_REVERSE_TRACK("UPDATE_REVERSE_TRACK", "Обновить обратный трек", false),

    /**
     * Закрывает заявку после завершения обработки.
     */
    CLOSE_REQUEST("CLOSE_REQUEST", "Закрыть обращение", false);

    private static final Map<String, ReturnRequestAction> LEGACY_CODE_MAP;

    private final String code;
    private final String displayName;
    private final boolean exchangeOnly;

    ReturnRequestAction(String code, String displayName, boolean exchangeOnly) {
        this.code = code;
        this.displayName = displayName;
        this.exchangeOnly = exchangeOnly;
    }

    static {
        Map<String, ReturnRequestAction> legacyMap = new HashMap<>();
        legacyMap.put("START_EXCHANGE", SET_MODE_EXCHANGE);
        legacyMap.put("CREATE_EXCHANGE_PARCEL", REGISTER_EXCHANGE_PARCEL);
        legacyMap.put("MARK_EXCHANGE_REGISTERED", SET_MODE_EXCHANGE);
        legacyMap.put("CANCEL_EXCHANGE", CLOSE_REQUEST);
        legacyMap.put("REOPEN_RETURN", SET_MODE_RETURN);
        legacyMap.put("CONFIRM_RECEIPT", MARK_INBOUND_PICKED_UP);
        legacyMap.put("UPDATE_DETAILS", UPDATE_REVERSE_TRACK);
        legacyMap.put("CLOSE", CLOSE_REQUEST);
        LEGACY_CODE_MAP = Collections.unmodifiableMap(legacyMap);
    }

    /**
     * Возвращает строковый код действия для сериализации в callback-данные.
     */
    public String getCode() {
        return code;
    }

    /**
     * Возвращает человеко-читаемое название действия.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Проверяет, доступно ли действие только для обменных заявок.
     */
    public boolean isExchangeOnly() {
        return exchangeOnly;
    }

    /**
     * Восстанавливает действие по его строковому коду.
     *
     * @param code текстовый код, пришедший из интерфейса
     * @return найденное действие или {@code null}, если код неизвестен
     */
    public static ReturnRequestAction fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ReturnRequestAction action : values()) {
            if (Objects.equals(action.code, code) || action.code.equalsIgnoreCase(code)) {
                return action;
            }
        }
        ReturnRequestAction legacy = LEGACY_CODE_MAP.get(code);
        if (legacy != null) {
            return legacy;
        }
        String normalized = code.toUpperCase();
        return LEGACY_CODE_MAP.get(normalized);
    }
}
