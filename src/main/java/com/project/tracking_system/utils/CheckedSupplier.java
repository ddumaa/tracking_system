package com.project.tracking_system.utils;

/**
 * Функциональный интерфейс для операций, которые могут выбрасывать исключения.
 */
@FunctionalInterface
public interface CheckedSupplier<T> {
    T get() throws Exception;
}
