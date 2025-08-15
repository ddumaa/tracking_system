package com.project.tracking_system.dto;

/**
 * Информация о результатах предварительной проверки треков перед обновлением.
 * <p>
 * Позволяет фронтенду отобразить количество треков,
 * допущенных к обновлению и исключённых по различным причинам.
 * </p>
 *
 * @param totalRequested       общее количество запрошенных к обновлению треков
 * @param readyToUpdate        сколько треков будет обновлено
 * @param finalStatusCount     сколько треков пропущено из-за финального статуса
 * @param recentlyUpdatedCount сколько треков пропущено из-за ограничения по времени
 * @param preRegisteredCount   сколько предрегистраций без номера пропущено
 * @param message              человекочитаемое сообщение о запуске
 */
public record TrackUpdateResponse(int totalRequested,
                                  int readyToUpdate,
                                  int finalStatusCount,
                                  int recentlyUpdatedCount,
                                  int preRegisteredCount,
                                  String message) {
}
