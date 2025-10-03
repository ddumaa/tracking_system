package com.project.tracking_system.dto;

import com.project.tracking_system.entity.OrderReturnRequestStatus;

import java.time.ZonedDateTime;

/**
 * Представление активной заявки на возврат или обмен для Telegram.
 * <p>
 * DTO содержит безопасные для отображения поля и избавляет слой бота от
 * зависимостей на JPA-сущности, что соответствует принципу единственной
 * ответственности.
 * </p>
 */
public record TelegramReturnRequestInfoDTO(Long requestId,
                                           Long parcelId,
                                           String trackNumber,
                                           String storeName,
                                           OrderReturnRequestStatus status,
                                           ZonedDateTime requestedAt) {
}

