package com.project.tracking_system.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import com.project.tracking_system.utils.EmailUtils;
import com.project.tracking_system.utils.PhoneUtils;

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

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (!(event instanceof LoggingEvent loggingEvent)) {
            return FilterReply.NEUTRAL;
        }
        String formatted = loggingEvent.getFormattedMessage();
        String masked = mask(formatted);
        if (!formatted.equals(masked)) {
            loggingEvent.setMessage(masked);
            loggingEvent.setArgumentArray(null);
        }
        return FilterReply.NEUTRAL;
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
