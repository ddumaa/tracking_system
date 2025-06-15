package com.project.tracking_system.service.track;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.SubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Сервис обновления треков пользователей.
 * <p>
 * Для асинхронной обработки используется отдельный пул {@code trackExecutor},
 * что разгружает общий исполнитель задач и повышает масштабируемость.
 * </p>
 */
@Slf4j
@Service
public class TrackUpdateService {

    private final WebSocketController webSocketController;
    private final TrackProcessingService trackProcessingService;
    private final SubscriptionService subscriptionService;
    private final StoreRepository storeRepository;
    private final TrackParcelRepository trackParcelRepository;
    private final TrackParcelService trackParcelService;
    private final TaskExecutor taskExecutor;

    public TrackUpdateService(WebSocketController webSocketController,
                              TrackProcessingService trackProcessingService,
                              SubscriptionService subscriptionService,
                              StoreRepository storeRepository,
                              TrackParcelRepository trackParcelRepository,
                              TrackParcelService trackParcelService,
                              @Qualifier("trackExecutor") TaskExecutor taskExecutor) {
        this.webSocketController = webSocketController;
        this.trackProcessingService = trackProcessingService;
        this.subscriptionService = subscriptionService;
        this.storeRepository = storeRepository;
        this.trackParcelRepository = trackParcelRepository;
        this.trackParcelService = trackParcelService;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Обновляет историю всех посылок пользователя.
     *
     * @param userId идентификатор пользователя
     * @return результат запуска обновления
     */
    @Transactional
    public UpdateResult updateAllParcels(Long userId) {
        if (!subscriptionService.canUseBulkUpdate(userId)) {
            String msg = "Обновление всех треков доступно только в премиум-версии.";
            log.warn("Отказано в доступе для пользователя ID: {}", userId);

            webSocketController.sendUpdateStatus(userId, msg, false);
            log.debug("📡 WebSocket отправлено: {}", msg);

            return new UpdateResult(false, 0, 0, msg);
        }

        int count = storeRepository.countByOwnerId(userId);
        if (count == 0) {
            log.warn("У пользователя ID={} нет магазинов для обновления треков.", userId);
            return new UpdateResult(false, 0, 0, "У вас нет магазинов с посылками.");
        }

        List<TrackParcelDTO> allParcels = trackParcelService.findAllByUserTracks(userId);

        List<TrackParcelDTO> parcelsToUpdate = allParcels.stream()
                .filter(dto -> !GlobalStatus.fromDescription(dto.getStatus()).isFinal())
                .toList();

        log.info("📦 Запущено обновление всех {} треков для userId={}", parcelsToUpdate.size(), userId);

        webSocketController.sendUpdateStatus(userId, "Обновление всех треков запущено...", true);

        processAllTrackUpdatesAsync(userId, parcelsToUpdate);

        return new UpdateResult(true, parcelsToUpdate.size(), allParcels.size(),
                "Запущено обновление " + parcelsToUpdate.size() + " треков из " + allParcels.size());
    }

    /**
     * Асинхронно обновляет все треки пользователя.
     */
    @Async("trackExecutor")
    @Transactional
    public void processAllTrackUpdatesAsync(Long userId, List<TrackParcelDTO> parcelsToUpdate) {
        try {
            AtomicInteger successfulUpdates = new AtomicInteger(0);

            List<CompletableFuture<Void>> futures = parcelsToUpdate.stream()
                    .map(trackParcelDTO -> CompletableFuture.runAsync(() -> {
                        try {
                            TrackInfoListDTO trackInfo = trackProcessingService.processTrack(
                                    trackParcelDTO.getNumber(),
                                    trackParcelDTO.getStoreId(),
                                    userId,
                                    true
                            );

                            if (trackInfo != null && !trackInfo.getList().isEmpty()) {
                                successfulUpdates.incrementAndGet();
                                log.debug("Трек {} обновлён для пользователя ID={}", trackParcelDTO.getNumber(), userId);
                            } else {
                                log.warn("Нет данных по треку {} (userId={})", trackParcelDTO.getNumber(), userId);
                            }

                        } catch (IllegalArgumentException e) {
                            log.warn("Ошибка обновления трека {}: {}", trackParcelDTO.getNumber(), e.getMessage());
                        } catch (Exception e) {
                            log.error("Ошибка обработки трека {}: {}", trackParcelDTO.getNumber(), e.getMessage(), e);
                        }
                    }, taskExecutor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                int updatedCount = successfulUpdates.get();
                int totalCount = parcelsToUpdate.size();

                log.info("Итог обновления всех треков для userId={}: {} обновлено, {} не изменено",
                        userId, updatedCount, totalCount - updatedCount);

                String message;
                if (updatedCount == 0) {
                    message = "Обновление завершено, но все треки уже были в финальном статусе.";
                } else {
                    message = "Обновление завершено! " + updatedCount + " из " + totalCount + " треков обновлено.";
                }

                webSocketController.sendDetailUpdateStatus(
                        userId,
                        new UpdateResult(true, updatedCount, totalCount, message)
                );
            });

        } catch (Exception e) {
            log.error("Ошибка при обновлении всех треков для пользователя {}: {}", userId, e.getMessage());
            webSocketController.sendUpdateStatus(userId, "Ошибка при обновлении всех треков: " + e.getMessage(), false);
        }
    }

    /**
     * Обновляет выбранные посылки пользователя.
     */
    @Transactional
    public UpdateResult updateSelectedParcels(Long userId, List<String> selectedNumbers) {
        List<TrackParcel> selectedParcels = trackParcelRepository.findByNumberInAndUserId(selectedNumbers, userId);

        int totalRequested = selectedParcels.size();
        List<TrackParcel> updatableParcels = selectedParcels.stream()
                .filter(parcel -> !parcel.getStatus().isFinal())
                .toList();
        int nonUpdatableCount = totalRequested - updatableParcels.size();

        log.info("Фильтрация завершена: {} треков можно обновить из {}, {} уже в финальном статусе",
                updatableParcels.size(), totalRequested, nonUpdatableCount);

        if (updatableParcels.isEmpty()) {
            String msg = "Все выбранные треки уже в финальном статусе, обновление не требуется.";
            log.warn(msg);
            webSocketController.sendUpdateStatus(userId, msg, true);
            return new UpdateResult(false, 0, selectedNumbers.size(), msg);
        }

        int remainingUpdates = subscriptionService.canUpdateTracks(userId, updatableParcels.size());

        if (remainingUpdates <= 0) {
            String msg = "Ваш лимит обновлений на сегодня исчерпан.";
            log.info("Лимит обновлений исчерпан для пользователя ID: {}", userId);
            webSocketController.sendUpdateStatus(userId, msg, true);
            return new UpdateResult(false, 0, updatableParcels.size(), msg);
        }

        int updatesToProcess = Math.min(updatableParcels.size(), remainingUpdates);
        List<TrackParcel> parcelsToUpdate = updatableParcels.subList(0, updatesToProcess);

        log.info("Запущено обновление {} треков для пользователя ID={}", updatesToProcess, userId);

        processTrackUpdatesAsync(userId, parcelsToUpdate, totalRequested, nonUpdatableCount);

        return new UpdateResult(true, updatesToProcess, selectedNumbers.size(),
                "Обновление запущено...");
    }

    /**
     * Асинхронно обновляет выбранный список посылок пользователя.
     */
    @Async("trackExecutor")
    @Transactional
    public void processTrackUpdatesAsync(Long userId, List<TrackParcel> parcelsToUpdate, int totalRequested, int nonUpdatableCount) {
        try {
            AtomicInteger successfulUpdates = new AtomicInteger(0);

            log.info("Начато обновление {} треков для userId={}", parcelsToUpdate.size(), userId);

            List<CompletableFuture<Void>> futures = parcelsToUpdate.stream()
                    .map(parcel -> CompletableFuture.runAsync(() -> {
                        try {
                            TrackInfoListDTO trackInfo = trackProcessingService.processTrack(parcel.getNumber(), parcel.getStore().getId(), userId, true);
                            if (trackInfo != null && !trackInfo.getList().isEmpty()) {
                                successfulUpdates.incrementAndGet();
                            }
                        } catch (Exception e) {
                            log.error("Ошибка обновления трека {}: {}", parcel.getNumber(), e.getMessage());
                        }
                    }, taskExecutor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                int updatedCount = successfulUpdates.get();
                log.info("Итог обновления для userId={}: {} обновлено, {} в финальном статусе",
                        userId, updatedCount, nonUpdatableCount);

                if (updatedCount > 0) {
                    log.info("Финальное обновление updateCount для userId={}, добавляем={}", userId, updatedCount);
                    trackParcelService.incrementUpdateCount(userId, updatedCount);
                }

                String message;
                if (updatedCount == 0 && nonUpdatableCount == 0) {
                    message = "Все треки уже были обновлены ранее.";
                } else if (updatedCount == 0) {
                    message = "Обновление завершено, но все треки уже в финальном статусе.";
                } else {
                    message = "Обновление завершено! " + updatedCount + " из " + totalRequested + " треков обновлено.";
                    if (nonUpdatableCount > 0) {
                        message += " " + nonUpdatableCount + " треков уже были в финальном статусе.";
                    }
                }

                webSocketController.sendDetailUpdateStatus(
                        userId,
                        new UpdateResult(true, updatedCount, totalRequested, message)
                );
            });

        } catch (Exception e) {
            log.error("Ошибка при обновлении посылок для пользователя {}: {}", userId, e.getMessage());
            webSocketController.sendUpdateStatus(userId, "Ошибка обновления: " + e.getMessage(), false);
        }
    }

}