package com.project.tracking_system.entity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Этап жизненного цикла заявки на возврат или обмен.
 * <p>
 * Описывает ключевые шаги процесса от отправки посылки покупателем до
 * формирования обмена и получения новой посылки. Используется для построения
 * таймлайна заявки в интерфейсе отслеживания.
 * </p>
 */
public enum ReturnRequestStage {

    /**
     * Заявка зарегистрирована и ожидает действий покупателя.
     */
    NEW(
            "NEW",
            "Заявка зарегистрирована",
            "Покупатель",
            "Заявка создана и ожидает, когда покупатель отправит товар обратно.",
            EnumSet.of(ReturnRequestMode.RETURN, ReturnRequestMode.EXCHANGE),
            null,
            false,
            false
    ),

    /**
     * Покупатель передал возврат в службу доставки.
     */
    OUTBOUND_SENT(
            "OUTBOUND_SENT",
            "Возврат отправлен",
            "Покупатель",
            "Покупатель передал посылку оператору доставки и предоставил обратный трек.",
            EnumSet.of(ReturnRequestMode.RETURN, ReturnRequestMode.EXCHANGE),
            "Обратный трек",
            false,
            false
    ),

    /**
     * Обратная посылка прибыла в пункт назначения магазина.
     */
    INBOUND_ARRIVED(
            "INBOUND_ARRIVED",
            "Возврат прибыл",
            "Логистика",
            "Обратная посылка прибыла в пункт назначения и ожидает проверки магазином.",
            EnumSet.of(ReturnRequestMode.RETURN, ReturnRequestMode.EXCHANGE),
            "Обратный трек",
            false,
            false
    ),

    /**
     * Магазин забрал посылку и подтвердил обработку возврата.
     */
    INBOUND_PICKED_UP(
            "INBOUND_PICKED_UP",
            "Возврат обработан",
            "Магазин",
            "Сотрудник магазина забрал посылку и подтвердил факт возврата товара.",
            EnumSet.of(ReturnRequestMode.RETURN, ReturnRequestMode.EXCHANGE),
            "Обратный трек",
            true,
            false
    ),

    /**
     * Магазин зарегистрировал обмен и готовит новое отправление.
     */
    EXCHANGE_REGISTERED(
            "EXCHANGE_REGISTERED",
            "Обмен зарегистрирован",
            "Магазин",
            "Менеджер одобрил обмен и приступил к подготовке новой посылки.",
            EnumSet.of(ReturnRequestMode.EXCHANGE),
            "Обменная посылка",
            false,
            false
    ),

    /**
     * Магазин передал обменную посылку в службу доставки.
     */
    EXCHANGE_SENT(
            "EXCHANGE_SENT",
            "Обмен отправлен",
            "Магазин",
            "Обменная посылка передана в доставку и движется к покупателю.",
            EnumSet.of(ReturnRequestMode.EXCHANGE),
            "Обменная посылка",
            false,
            false
    ),

    /**
     * Покупатель получил обменную посылку.
     */
    EXCHANGE_DELIVERED(
            "EXCHANGE_DELIVERED",
            "Обмен доставлен",
            "Покупатель",
            "Покупатель получил обменную посылку. Цикл заявки завершён.",
            EnumSet.of(ReturnRequestMode.EXCHANGE),
            "Обменная посылка",
            false,
            true
    );

    private final String code;
    private final String title;
    private final String actor;
    private final String description;
    private final EnumSet<ReturnRequestMode> applicableModes;
    private final String trackContextLabel;
    private final boolean finalForReturn;
    private final boolean finalForExchange;

    ReturnRequestStage(String code,
                       String title,
                       String actor,
                       String description,
                       EnumSet<ReturnRequestMode> applicableModes,
                       String trackContextLabel,
                       boolean finalForReturn,
                       boolean finalForExchange) {
        this.code = code;
        this.title = title;
        this.actor = actor;
        this.description = description;
        this.applicableModes = EnumSet.copyOf(applicableModes);
        this.trackContextLabel = trackContextLabel;
        this.finalForReturn = finalForReturn;
        this.finalForExchange = finalForExchange;
    }

    /**
     * Возвращает машинный код этапа для аналитики и API.
     */
    public String getCode() {
        return code;
    }

    /**
     * Возвращает заголовок этапа для отображения пользователю.
     */
    public String getTitle() {
        return title;
    }

    /**
     * Возвращает участника процесса, ответственного за этап.
     */
    public String getActor() {
        return actor;
    }

    /**
     * Возвращает описание действий, выполняемых на этапе.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Возвращает набор режимов заявок, для которых отображается этап.
     */
    public Set<ReturnRequestMode> getApplicableModes() {
        return Collections.unmodifiableSet(applicableModes);
    }

    /**
     * Проверяет, относится ли этап только к обменным заявкам.
     */
    public boolean isExchangeOnly() {
        return applicableModes.equals(EnumSet.of(ReturnRequestMode.EXCHANGE));
    }

    /**
     * Проверяет, применяется ли этап к указанному режиму заявки.
     */
    public boolean supportsMode(ReturnRequestMode mode) {
        if (mode == null) {
            return false;
        }
        return applicableModes.contains(mode);
    }

    /**
     * Возвращает подпись для отображения трек-номера, если он есть.
     */
    public String getTrackContextLabel() {
        return trackContextLabel;
    }

    /**
     * Определяет, является ли стадия финальной для режима возврата.
     */
    public boolean isFinalForReturn() {
        return finalForReturn;
    }

    /**
     * Определяет, является ли стадия финальной для режима обмена.
     */
    public boolean isFinalForExchange() {
        return finalForExchange;
    }

    /**
     * Проверяет, финальна ли стадия для указанного режима обработки.
     */
    public boolean isFinalForMode(ReturnRequestMode mode) {
        if (mode == null) {
            return false;
        }
        return switch (mode) {
            case RETURN -> finalForReturn;
            case EXCHANGE -> finalForExchange;
        };
    }
}
