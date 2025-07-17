package com.project.tracking_system.utils;

import lombok.extern.slf4j.Slf4j;

import com.project.tracking_system.utils.CheckedSupplier;

/**
 * Утилита для выполнения операций с повтором при возникновении ошибок.
 * <p>
 * Позволяет задать максимальное число попыток и начальную задержку между ними.
 * При неудаче ожидание возрастает по экспоненте.
 * </p>
 */
@Slf4j
public final class RetryUtils {

    private RetryUtils() {
    }

    /**
     * Выполняет операцию с указанным количеством попыток.
     *
     * @param supplier    выполняемая операция
     * @param maxAttempts максимальное количество попыток
     * @param baseDelayMs базовая задержка между попытками
     * @param retryOn     типы исключений, при которых следует повторить попытку
     * @param <T>         тип возвращаемого результата
     * @return результат выполнения операции
     * @throws Exception если все попытки завершились неудачей или произошла ошибка другого типа
     */
    @SafeVarargs
    public static <T> T runWithRetry(CheckedSupplier<T> supplier,
                                     int maxAttempts,
                                     long baseDelayMs,
                                     Class<? extends Exception>... retryOn) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.debug("Запуск попытки {} из {}", attempt, maxAttempts);
                return supplier.get();
            } catch (Exception e) {
                lastException = e;
                boolean shouldRetry = false;
                for (Class<? extends Exception> exClass : retryOn) {
                    if (exClass.isInstance(e)) {
                        shouldRetry = true;
                        break;
                    }
                }

                if (!shouldRetry || attempt == maxAttempts) {
                    break;
                }

                long delay = baseDelayMs * (1L << (attempt - 1));
                log.warn("Попытка {} завершилась ошибкой: {}", attempt, e.getMessage());
                log.info("Ожидание {} мс перед следующей попыткой", delay);
                Thread.sleep(delay);
            }
        }
        log.error("Операция не выполнена после {} попыток", maxAttempts);
        throw lastException;
    }
}
