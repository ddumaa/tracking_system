package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Фасад для работы с посылками пользователя.
 * <p>
 * Позволяет вызывать обработку, обновление и удаление треков
 * через единый интерфейс.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TrackFacade {

    private final TrackProcessingService trackProcessingService;
    private final TrackUpdateService trackUpdateService;
    private final TrackDeletionService trackDeletionService;

    /**
     * Обрабатывает трек-номер и при необходимости сохраняет его.
     *
     * @param number  номер трека
     * @param storeId идентификатор магазина
     * @param userId  идентификатор пользователя
     * @param canSave признак возможности сохранения
     * @return информация о треке
     */
    public TrackInfoListDTO processTrack(String number, Long storeId, Long userId, boolean canSave) {
        return trackProcessingService.processTrack(number, storeId, userId, canSave);
    }

    /**
     * Запускает обновление всех треков пользователя.
     *
     * @param userId идентификатор пользователя
     * @return результат обновления
     */
    public UpdateResult updateAllParcels(Long userId) {
        return trackUpdateService.updateAllParcels(userId);
    }

    /**
     * Запускает обновление выбранных треков пользователя.
     *
     * @param userId          идентификатор пользователя
     * @param selectedNumbers список номеров треков
     * @return результат обновления
     */
    public UpdateResult updateSelectedParcels(Long userId, List<String> selectedNumbers) {
        return trackUpdateService.updateSelectedParcels(userId, selectedNumbers);
    }

    /**
     * Удаляет треки пользователя.
     *
     * @param numbers список номеров треков
     * @param userId  идентификатор пользователя
     */
    public void deleteByNumbersAndUserId(List<String> numbers, Long userId) {
        trackDeletionService.deleteByNumbersAndUserId(numbers, userId);
    }
}
