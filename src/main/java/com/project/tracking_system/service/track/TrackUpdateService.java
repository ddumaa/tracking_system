package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.entity.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Сервис для обновления посылок и асинхронной обработки треков.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TrackUpdateService {

    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    private final TrackPersistenceService trackPersistenceService;
    private final TrackNotificationService trackNotificationService;

    /**
     * Обрабатывает одиночный трек.
     *
     * @param number  номер трека
     * @param storeId магазин
     * @param userId  владелец
     * @param canSave можно ли сохранять
     * @return полученную информацию о треке
     */
    @Transactional
    public TrackInfoListDTO processTrack(String number, Long storeId, Long userId, boolean canSave) {
        TrackInfoListDTO info = typeDefinitionTrackPostService.getTypeDefinitionTrackPostService(userId, number);
        if (info == null || info.getList().isEmpty()) {
            return info;
        }
        if (userId != null && canSave) {
            trackPersistenceService.save(number, info, storeId, userId);
        }
        return info;
    }

    /**
     * Обновляет выбранные посылки пользователя.
     */
    @Transactional
    public UpdateResult updateSelectedParcels(Long userId, List<String> numbers) {
        List<TrackParcelDTO> parcels = trackPersistenceService.findAllByUserTracks(userId)
                .stream()
                .filter(p -> numbers.contains(p.getNumber()))
                .toList();
        processUpdatesAsync(userId, parcels.size(), parcels);
        return new UpdateResult(true, parcels.size(), parcels.size(), "Обновление запущено...");
    }

    /**
     * Обновляет все посылки пользователя.
     */
    @Transactional
    public UpdateResult updateAllParcels(Long userId) {
        List<TrackParcelDTO> parcels = trackPersistenceService.findAllByUserTracks(userId);
        processUpdatesAsync(userId, parcels.size(), parcels);
        return new UpdateResult(true, parcels.size(), parcels.size(), "Обновление запущено...");
    }

    @Async
    @Transactional
    public void processUpdatesAsync(Long userId, int total, List<TrackParcelDTO> parcels) {
        AtomicInteger updated = new AtomicInteger();
        List<CompletableFuture<Void>> futures = parcels.stream()
                .map(p -> CompletableFuture.runAsync(() -> {
                    try {
                        TrackInfoListDTO info = processTrack(p.getNumber(), p.getStoreId(), userId, true);
                        if (info != null && !info.getList().isEmpty()) {
                            updated.incrementAndGet();
                        }
                    } catch (Exception e) {
                        log.error("Ошибка обновления трека {}", p.getNumber(), e);
                    }
                }))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
            int count = updated.get();
            trackNotificationService.notifyDetailed(userId, new UpdateResult(true, count, total, "Обновлено " + count));
            trackPersistenceService.incrementUpdateCount(userId, count);
        });
    }
}
