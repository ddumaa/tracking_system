package com.project.tracking_system.service.track;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.belpost.BelPostTrackQueueService;
import com.project.tracking_system.service.belpost.QueuedTrack;
import com.project.tracking_system.service.track.ProgressAggregatorService;
import com.project.tracking_system.service.track.TrackingResultCacheService;
import com.project.tracking_system.service.track.TrackSource;
import com.project.tracking_system.service.admin.ApplicationSettingsService;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.dto.TrackProcessingProgressDTO;
import com.project.tracking_system.dto.TrackStatusUpdateDTO;
import com.project.tracking_system.dto.TrackUpdateResponse;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.PostalServiceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Сервис обновления треков пользователей.
 * <p>
 * Для асинхронной обработки используется отдельный пул {@code trackExecutor},
 * что разгружает общий исполнитель задач и повышает масштабируемость.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackUpdateService {

    private final WebSocketController webSocketController;
    private final SubscriptionService subscriptionService;
    private final StoreRepository storeRepository;
    private final TrackParcelRepository trackParcelRepository;
    private final TrackParcelService trackParcelService;
    private final TrackUploadGroupingService groupingService;
    private final TrackUpdateDispatcherService dispatcherService;
    /** Очередь Белпочты для централизованной обработки. */
    private final BelPostTrackQueueService belPostTrackQueueService;
    /** Сервис агрегации прогресса обработки. */
    private final ProgressAggregatorService progressAggregatorService;
    /** Кэш результатов трекинга для восстановления состояния страницы. */
    private final TrackingResultCacheService trackingResultCacheService;
    /** Сервис глобальных настроек приложения. */
    private final ApplicationSettingsService applicationSettingsService;
    /** Сервис управления пользователями для получения часового пояса. */
    private final UserService userService;

    /**
     * Обновляет историю всех посылок пользователя.
     *
     * @param userId идентификатор пользователя
     * @return результат запуска обновления
     */
    @Transactional
    public TrackUpdateResponse updateAllParcels(Long userId) {
        if (!subscriptionService.isFeatureEnabled(userId, FeatureKey.BULK_UPDATE)) {
            String msg = "Обновление всех треков доступно только в премиум-версии.";
            log.warn("Отказано в доступе для пользователя ID: {}", userId);

            webSocketController.sendUpdateStatus(userId, msg, false);
            log.debug("📡 WebSocket отправлено: {}", msg);

            return new TrackUpdateResponse(0, 0, 0, 0, 0, msg);
        }

        int count = storeRepository.countByOwnerId(userId);
        if (count == 0) {
            log.warn("У пользователя ID={} нет магазинов для обновления треков.", userId);
            return new TrackUpdateResponse(0, 0, 0, 0, 0, "У вас нет магазинов с посылками.");
        }

        List<TrackParcel> allParcels = trackParcelRepository.findByUserId(userId);

        int interval = applicationSettingsService.getTrackUpdateIntervalHours();
        ZonedDateTime threshold = ZonedDateTime.now(ZoneOffset.UTC).minusHours(interval);

        int preRegisteredCount = (int) allParcels.stream()
                .filter(this::isPreRegisteredWithoutNumber)
                // Логируем только ID посылки, чтобы не раскрывать персональные данные
                .peek(p -> log.debug("Пропуск предрегистрации без номера: id={}", p.getId()))
                .count();

        List<TrackParcel> filteredParcels = allParcels.stream()
                .filter(p -> !isPreRegisteredWithoutNumber(p))
                .toList();

        int finalStatusCount = (int) filteredParcels.stream()
                .filter(p -> p.getStatus().isFinal())
                .count();
        int recentlyUpdatedCount = (int) filteredParcels.stream()
                .filter(p -> !p.getStatus().isFinal())
                .filter(p -> p.getLastUpdate() != null && p.getLastUpdate().isAfter(threshold))
                .count();

        List<TrackParcel> parcelsToUpdate = filteredParcels.stream()
                .filter(p -> !p.getStatus().isFinal())
                .filter(p -> p.getLastUpdate() == null || p.getLastUpdate().isBefore(threshold))
                .toList();

        int totalRequested = allParcels.size();
        int readyToUpdateCount = parcelsToUpdate.size();

        log.info("📦 Фильтрация завершена: {} треков допущено к обновлению, {} в финальном статусе, {} недавно обновлялись",
                readyToUpdateCount, finalStatusCount, recentlyUpdatedCount);

        String message = buildUpdateMessage(readyToUpdateCount, finalStatusCount, recentlyUpdatedCount, preRegisteredCount);
        webSocketController.sendUpdateStatus(userId, message, readyToUpdateCount > 0);

        if (readyToUpdateCount > 0) {
            processAllTrackUpdatesAsync(userId, parcelsToUpdate);
        }

        return new TrackUpdateResponse(totalRequested, readyToUpdateCount, finalStatusCount,
                recentlyUpdatedCount, preRegisteredCount, message);
    }

    /**
     * Асинхронно обновляет все треки пользователя.
     */
    @Async("trackExecutor")
    @Transactional
    public void processAllTrackUpdatesAsync(Long userId, List<TrackParcel> parcelsToUpdate) {
        try {
            List<TrackMeta> metas = parcelsToUpdate.stream()
                    .map(parcel -> new TrackMeta(
                            parcel.getNumber(),
                            parcel.getStore().getId(),
                            null,
                            true,
                            parcel.getDeliveryHistory() != null ? parcel.getDeliveryHistory().getPostalService() : null))
                    .toList();

            List<TrackingResultAdd> results = process(metas, userId);

            int updatedCount = (int) results.stream()
                    .filter(r -> !TrackConstants.NO_DATA_STATUS.equals(r.getStatus()))
                    .count();

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

        } catch (Exception e) {
            log.error("Ошибка при обновлении всех треков для пользователя {}: {}", userId, e.getMessage());
            webSocketController.sendUpdateStatus(userId, "Ошибка при обновлении всех треков: " + e.getMessage(), false);
        }
    }

    /**
     * Обновляет выбранные посылки пользователя.
     * <p>
     * Если выбран только один номер и его последнее обновление произошло
     * менее чем за {@code interval} часов до текущего момента, метод не
     * запускает обновление. Пользователю отправляется сообщение о том,
     * когда будет доступно следующее обновление с учётом его часового пояса.
     * </p>
     */
    @Transactional
    public TrackUpdateResponse updateSelectedParcels(Long userId, List<String> selectedNumbers) {
        List<TrackParcel> selectedParcels = trackParcelRepository.findByNumberInAndUserId(selectedNumbers, userId);
        int totalRequested = selectedParcels.size();

        int preRegisteredCount = (int) selectedParcels.stream()
                .filter(this::isPreRegisteredWithoutNumber)
                // Логируем только ID посылки, не раскрывая личные данные
                .peek(p -> log.debug("Пропуск предрегистрации без номера: id={}", p.getId()))
                .count();

        List<TrackParcel> filteredParcels = selectedParcels.stream()
                .filter(p -> !isPreRegisteredWithoutNumber(p))
                .toList();

        int interval = applicationSettingsService.getTrackUpdateIntervalHours();
        ZonedDateTime threshold = ZonedDateTime.now(ZoneOffset.UTC).minusHours(interval);

        if (selectedNumbers.size() == 1 && !filteredParcels.isEmpty()) {
            TrackParcel parcel = filteredParcels.get(0);
            if (!parcel.getStatus().isFinal() && parcel.getLastUpdate() != null) {
                ZonedDateTime nextAllowed = parcel.getLastUpdate().plusHours(interval);
                if (nextAllowed.isAfter(ZonedDateTime.now(ZoneOffset.UTC))) {
                    String formatted = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                            .withZone(userService.getUserZone(userId))
                            .format(nextAllowed);
                    String msg = "Трек " + parcel.getNumber() + " обновлялся недавно, " +
                            "следующее обновление возможно после " + formatted;
                    webSocketController.sendUpdateStatus(userId, msg, false);
                    return new TrackUpdateResponse(1, 0, 0, 1, 0, msg);
                }
            }
        }

        int finalStatusCount = (int) filteredParcels.stream()
                .filter(p -> p.getStatus().isFinal())
                .count();
        int recentlyUpdatedCount = (int) filteredParcels.stream()
                .filter(p -> !p.getStatus().isFinal())
                .filter(p -> p.getLastUpdate() != null && p.getLastUpdate().isAfter(threshold))
                .count();
        List<TrackParcel> updatableParcels = filteredParcels.stream()
                .filter(p -> !p.getStatus().isFinal())
                .filter(p -> p.getLastUpdate() == null || p.getLastUpdate().isBefore(threshold))
                .toList();
        int readyToUpdateCount = updatableParcels.size();

        log.info("📦 Фильтрация завершена: {} треков допущено к обновлению, {} в финальном статусе, {} недавно обновлялись",
                readyToUpdateCount, finalStatusCount, recentlyUpdatedCount);

        if (readyToUpdateCount == 0) {
            String msg = buildUpdateMessage(0, finalStatusCount, recentlyUpdatedCount, preRegisteredCount);
            log.warn(msg);
            webSocketController.sendUpdateStatus(userId, msg, false);
            return new TrackUpdateResponse(totalRequested, 0, finalStatusCount, recentlyUpdatedCount, preRegisteredCount, msg);
        }

        int remainingUpdates = subscriptionService.canUpdateTracks(userId, updatableParcels.size());

        if (remainingUpdates <= 0) {
            String msg = "Ваш лимит обновлений на сегодня исчерпан.";
            log.info("Лимит обновлений исчерпан для пользователя ID: {}", userId);
            webSocketController.sendUpdateStatus(userId, msg, true);
            return new TrackUpdateResponse(totalRequested, 0, finalStatusCount, recentlyUpdatedCount, preRegisteredCount, msg);
        }

        int updatesToProcess = Math.min(readyToUpdateCount, remainingUpdates);
        List<TrackParcel> parcelsToUpdate = updatableParcels.subList(0, updatesToProcess);
        String msg = buildUpdateMessage(updatesToProcess, finalStatusCount, recentlyUpdatedCount, preRegisteredCount);
        log.info("📦 Запущено обновление {} треков для пользователя ID={}", updatesToProcess, userId);

        processTrackUpdatesAsync(userId, parcelsToUpdate, totalRequested, finalStatusCount + recentlyUpdatedCount);

        webSocketController.sendUpdateStatus(userId, msg, true);
        return new TrackUpdateResponse(totalRequested, updatesToProcess, finalStatusCount, recentlyUpdatedCount, preRegisteredCount, msg);
    }

    /**
     * Асинхронно обновляет выбранный список посылок пользователя.
     */
    @Async("trackExecutor")
    @Transactional
    public void processTrackUpdatesAsync(Long userId, List<TrackParcel> parcelsToUpdate, int totalRequested, int nonUpdatableCount) {
        try {
            List<TrackParcel> filteredParcels = parcelsToUpdate.stream()
                    .filter(p -> {
                        boolean skip = isPreRegisteredWithoutNumber(p);
                        if (skip) {
                            // Логируем только идентификатор, избегая персональных данных
                            log.debug("Пропуск предрегистрации без номера: id={}", p.getId());
                        }
                        return !skip;
                    })
                    .toList();

            log.info("Начато обновление {} треков для userId={}", filteredParcels.size(), userId);

            List<TrackMeta> metas = filteredParcels.stream()
                    .map(parcel -> new TrackMeta(
                            parcel.getNumber(),
                            parcel.getStore().getId(),
                            null,
                            true,
                            parcel.getDeliveryHistory() != null ? parcel.getDeliveryHistory().getPostalService() : null))
                    .toList();
            List<TrackingResultAdd> results = process(metas, userId);

            int updatedCount = (int) results.stream()
                    .filter(r -> !TrackConstants.NO_DATA_STATUS.equals(r.getStatus()))
                    .count();

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

        } catch (Exception e) {
            log.error("Ошибка при обновлении посылок для пользователя {}: {}", userId, e.getMessage());
            webSocketController.sendUpdateStatus(userId, "Ошибка обновления: " + e.getMessage(), false);
        }
    }

    /**
     * Обрабатывает набор треков для указанного пользователя.
     *
     * @param tracks список метаданных треков
     * @param userId идентификатор пользователя
     * @return список объединенных результатов
     */
    public List<TrackingResultAdd> process(List<TrackMeta> tracks, Long userId) {
        long batchId = System.currentTimeMillis();
        progressAggregatorService.registerBatch(batchId, tracks.size(), userId);
        Map<PostalServiceType, List<TrackMeta>> grouped = groupingService.group(tracks);

        // Отдельно обрабатываем номера Белпочты через централизованную очередь
        List<TrackMeta> belpost = grouped.remove(PostalServiceType.BELPOST);
        if (belpost != null && !belpost.isEmpty()) {
            // Для последующей обработки фиксируем источник как UPDATE
            // чтобы различать треки, добавленные при ручном обновлении
            List<QueuedTrack> queued = belpost.stream()
                    .map(m -> new QueuedTrack(
                            m.number(),
                            userId,
                            m.storeId(),
                            TrackSource.UPDATE,
                            batchId,
                            m.phone()))
                    .toList();
            belPostTrackQueueService.enqueue(queued);
        }

        if (grouped.isEmpty()) {
            return List.of();
        }

        List<TrackingResultAdd> results = dispatcherService.dispatch(grouped, userId);

        for (TrackingResultAdd r : results) {
            progressAggregatorService.trackProcessed(batchId);
            TrackProcessingProgressDTO p = progressAggregatorService.getProgress(batchId);
            TrackStatusUpdateDTO dto = new TrackStatusUpdateDTO(
                    batchId,
                    r.getTrackingNumber(),
                    r.getStatus(),
                    p.processed(),
                    p.total());
            trackingResultCacheService.addResult(userId, dto);
        }

        return results;
    }

    /**
     * Формирует человекочитаемое сообщение о запуске обновления.
     *
     * @param ready             количество треков, которые будут обновлены
     * @param finalStatus       сколько треков имеют финальный статус
     * @param recent            сколько треков пропущено из-за таймаута
     * @param preRegistered     сколько предрегистраций без номера пропущено
     * @return текст уведомления с эмодзи для пользователя
     */
    private String buildUpdateMessage(int ready, int finalStatus, int recent, int preRegistered) {
        int total = ready + finalStatus + recent + preRegistered;
        StringBuilder sb = new StringBuilder();
        if (ready == 0) {
            sb.append("Обновление не выполнено.");
        } else {
            sb.append("Запущено обновление ")
                    .append(ready)
                    .append(" из ")
                    .append(total)
                    .append(" треков");
        }
        if (finalStatus > 0) {
            sb.append("\n▪ ")
                    .append(finalStatus)
                    .append(" треков уже в финальном статусе");
        }
        if (recent > 0) {
            sb.append("\n▪ ")
                    .append(recent)
                    .append(" треков недавно обновлялись и пропущены");
        }
        if (preRegistered > 0) {
            sb.append("\n▪ Пропущено предрегистраций без номера: ")
                    .append(preRegistered);
        }
        return sb.toString();
    }

    /**
     * Проверяет, является ли посылка предварительно зарегистрированной без трек-номера.
     *
     * @param parcel объект посылки
     * @return {@code true}, если статус {@link GlobalStatus#PRE_REGISTERED} и номер отсутствует
     *
     * <p><strong>Безопасность:</strong> метод не должен логировать персональные данные или токены.</p>
     */
    private boolean isPreRegisteredWithoutNumber(TrackParcel parcel) {
        return parcel.getStatus() == GlobalStatus.PRE_REGISTERED &&
                (parcel.getNumber() == null || parcel.getNumber().isBlank());
    }

}