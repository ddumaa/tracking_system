package com.project.tracking_system.controller;

import com.project.tracking_system.entity.ReturnRequestAction;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Перечисление, описывающее команды, которые может инициировать клиентский интерфейс
 * для управления жизненным циклом заявки на возврат или обмен.
 */
enum ReturnRequestCommandType {

    /**
     * Активирует классический сценарий возврата и отключает привязанный обмен.
     */
    SET_MODE_RETURN(ReturnRequestAction.SET_MODE_RETURN),

    /**
     * Переводит заявку в режим обмена и запускает соответствующий бизнес-процесс.
     */
    SET_MODE_EXCHANGE(ReturnRequestAction.SET_MODE_EXCHANGE),

    /**
     * Отменяет ранее запущенный обмен и возвращает заявку к сценарию возврата.
     */
    CANCEL_EXCHANGE(ReturnRequestAction.CANCEL_EXCHANGE),

    /**
     * Привязывает к заявке обменную посылку, зарегистрированную вручную оператором.
     */
    REGISTER_EXCHANGE_PARCEL(ReturnRequestAction.REGISTER_EXCHANGE_PARCEL),

    /**
     * Подтверждает передачу обменной посылки службе доставки со склада магазина.
     */
    MARK_EXCHANGE_SENT(ReturnRequestAction.MARK_EXCHANGE_SENT),

    /**
     * Фиксирует успешную доставку обменной посылки покупателю.
     */
    MARK_EXCHANGE_DELIVERED(ReturnRequestAction.MARK_EXCHANGE_DELIVERED),

    /**
     * Обновляет обратный трек и комментарии в заявке для информирования клиента.
     */
    UPDATE_REVERSE_TRACK(ReturnRequestAction.UPDATE_REVERSE_TRACK),

    /**
     * Подтверждает отправку возвратной посылки покупателем и старт отслеживания.
     */
    MARK_OUTBOUND_SENT(ReturnRequestAction.MARK_OUTBOUND_SENT),

    /**
     * Фиксирует прибытие возврата на пункт приёма магазина для дальнейшей обработки.
     */
    MARK_INBOUND_ARRIVED(ReturnRequestAction.MARK_INBOUND_ARRIVED),

    /**
     * Регистрирует вручную факт приёма возврата сотрудником магазина.
     */
    MARK_INBOUND_PICKED_UP(ReturnRequestAction.MARK_INBOUND_PICKED_UP),

    /**
     * Закрывает заявку после завершения всех обязательных этапов.
     */
    CLOSE_REQUEST(ReturnRequestAction.CLOSE_REQUEST);

    private static final Map<String, ReturnRequestCommandType> CODE_INDEX;
    private static final Map<String, ReturnRequestCommandType> LEGACY_CODES;

    private final ReturnRequestAction action;
    private final String code;

    ReturnRequestCommandType(ReturnRequestAction action) {
        this.action = action;
        this.code = action.getCode();
    }

    /**
     * Возвращает строковый код команды, совпадающий с кодом {@link ReturnRequestAction}.
     *
     * @return текстовый код для сериализации команды
     */
    public String getCode() {
        return code;
    }

    /**
     * Возвращает исходное действие, на основе которого построена команда.
     *
     * @return действие возвратной заявки
     */
    public ReturnRequestAction getAction() {
        return action;
    }

    /**
     * Восстанавливает тип команды по строковому коду без учёта регистра и с поддержкой легаси-значений.
     *
     * @param code код команды из внешнего интерфейса
     * @return найденный тип или {@code null}, если код неизвестен
     */
    public static ReturnRequestCommandType fromCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.toUpperCase(Locale.ROOT);
        ReturnRequestCommandType type = CODE_INDEX.get(normalized);
        if (type != null) {
            return type;
        }
        ReturnRequestCommandType legacy = LEGACY_CODES.get(code);
        if (legacy != null) {
            return legacy;
        }
        return LEGACY_CODES.get(normalized);
    }

    static {
        Map<String, ReturnRequestCommandType> codeIndex = new HashMap<>();
        for (ReturnRequestCommandType type : values()) {
            codeIndex.put(type.code.toUpperCase(Locale.ROOT), type);
        }
        CODE_INDEX = Collections.unmodifiableMap(codeIndex);

        Map<String, ReturnRequestCommandType> legacy = new HashMap<>();
        legacy.put("START_EXCHANGE", SET_MODE_EXCHANGE);
        legacy.put("CREATE_EXCHANGE_PARCEL", REGISTER_EXCHANGE_PARCEL);
        legacy.put("CANCEL_EXCHANGE", CANCEL_EXCHANGE);
        legacy.put("REOPEN_RETURN", SET_MODE_RETURN);
        legacy.put("CONFIRM_RECEIPT", MARK_INBOUND_PICKED_UP);
        legacy.put("UPDATE_DETAILS", UPDATE_REVERSE_TRACK);
        legacy.put("CLOSE", CLOSE_REQUEST);
        legacy.put("MARK_EXCHANGE_REGISTERED", SET_MODE_EXCHANGE);
        LEGACY_CODES = Collections.unmodifiableMap(legacy);
    }
}
