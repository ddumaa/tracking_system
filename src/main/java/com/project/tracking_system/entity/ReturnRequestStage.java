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
     * Покупатель отправляет товар обратно магазину.
     */
    CUSTOMER_RETURN(
            "CUSTOMER_RETURN",
            "Возврат от покупателя",
            "Покупатель",
            "Покупатель оформляет заявку и отправляет посылку обратно магазину.",
            EnumSet.of(ReturnRequestMode.RETURN, ReturnRequestMode.EXCHANGE),
            "Обратный трек"
    ),

    /**
     * Менеджер магазина проверяет возврат и принимает решение.
     */
    MERCHANT_ACCEPT_RETURN(
            "MERCHANT_ACCEPT_RETURN",
            "Приём возврата магазином",
            "Магазин",
            "Менеджер проверяет возврат и принимает решение: закрыть заявку или запустить обмен.",
            EnumSet.of(ReturnRequestMode.RETURN, ReturnRequestMode.EXCHANGE),
            null
    ),

    /**
     * Магазин формирует и отправляет обменную посылку.
     */
    EXCHANGE_SHIPMENT(
            "EXCHANGE_SHIPMENT",
            "Отправление обмена",
            "Магазин",
            "После подтверждения возврата магазин создаёт обменную посылку.",
            EnumSet.of(ReturnRequestMode.EXCHANGE),
            "Обменная посылка"
    ),

    /**
     * Покупатель получает обменную посылку и цикл завершается.
     */
    EXCHANGE_DELIVERY(
            "EXCHANGE_DELIVERY",
            "Получение обмена",
            "Покупатель",
            "Покупатель забирает новую посылку. Цикл завершается до следующей заявки.",
            EnumSet.of(ReturnRequestMode.EXCHANGE),
            "Обменная посылка"
    );

    private final String code;
    private final String title;
    private final String actor;
    private final String description;
    private final EnumSet<ReturnRequestMode> applicableModes;
    private final String trackContextLabel;

    ReturnRequestStage(String code,
                       String title,
                       String actor,
                       String description,
                       EnumSet<ReturnRequestMode> applicableModes,
                       String trackContextLabel) {
        this.code = code;
        this.title = title;
        this.actor = actor;
        this.description = description;
        this.applicableModes = EnumSet.copyOf(applicableModes);
        this.trackContextLabel = trackContextLabel;
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
}
