package com.project.tracking_system.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import com.project.tracking_system.utils.EmailUtils;
import com.project.tracking_system.utils.PhoneUtils;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Фильтр Logback, маскирующий персональные данные в логах.
 * <p>
 * Перед записью сообщения фильтр заменяет телефоны и email на маскированные значения.
 * Это снижает риск утечки PII даже при забытых масках в коде.
 * </p>
 */
public class PiiMaskingFilter extends Filter<ILoggingEvent> {

    /** Паттерн распознавания телефонного номера. */
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?\\d{10,15}");
    /** Паттерн распознавания email-адреса. */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /**
     * Оригинальные события, которые уже были обработаны.
     * Используется {@link WeakHashMap}, чтобы избежать утечек памяти.
     */
    private final Set<ILoggingEvent> processedEvents =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    /**
     * События, созданные фильтром и содержащие уже маскированные сообщения.
     */
    private final Set<ILoggingEvent> maskedEvents = ConcurrentHashMap.newKeySet();

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (maskedEvents.remove(event)) {
            // Это уже маскированное событие, просто пропускаем его.
            return FilterReply.NEUTRAL;
        }

        if (!processedEvents.add(event)) {
            // Оригинальное событие уже обработано, запрещаем повторную запись.
            return FilterReply.DENY;
        }

        String formatted = event.getFormattedMessage();
        String masked = mask(formatted);
        if (!formatted.equals(masked)) {
            // Создаём новое событие с маскированным сообщением и отправляем его в те же аппендеры.
            ILoggingEvent clone = cloneWithMessage(event, masked);

            maskedEvents.add(clone);
            Logger logger = (Logger) LoggerFactory.getLogger(event.getLoggerName());
            logger.callAppenders(clone);
            return FilterReply.DENY;
        }

        // Маскировка не понадобилась, удаляем из обработанных.
        processedEvents.remove(event);
        return FilterReply.NEUTRAL;
    }

    /**
     * Создаёт клон события с заменённым сообщением.
     *
     * @param original      исходное событие логирования
     * @param maskedMessage сообщение, в котором персональные данные скрыты
     * @return новое событие для передачи в аппендеры
     */
    private ILoggingEvent cloneWithMessage(ILoggingEvent original, String maskedMessage) {
        LoggingEvent clone = new LoggingEvent();
        clone.setLoggerContextRemoteView(original.getLoggerContextVO());
        clone.setLoggerName(original.getLoggerName());
        clone.setLevel(original.getLevel());
        clone.setTimeStamp(original.getTimeStamp());
        clone.setThreadName(original.getThreadName());
        clone.setMessage(maskedMessage);
        clone.setArgumentArray(null); // строка уже отформатирована
        clone.setThrowableProxy(original.getThrowableProxy());
        clone.setCallerData(original.getCallerData());
        clone.setMarker(original.getMarker());
        clone.setMDCPropertyMap(original.getMDCPropertyMap());
        return clone;
    }

    /**
     * Маскирует телефоны и email в переданном сообщении.
     *
     * @param message исходное сообщение
     * @return сообщение с маскированными персональными данными
     */
    private String mask(String message) {
        if (message == null) {
            return null;
        }
        String result = PHONE_PATTERN.matcher(message)
                .replaceAll(m -> PhoneUtils.maskPhone(m.group()));
        result = EMAIL_PATTERN.matcher(result)
                .replaceAll(m -> EmailUtils.maskEmail(m.group()));
        return result;
    }
}
