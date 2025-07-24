package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackProcessingStartedDTO;
import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.service.belpost.BelPostTrackQueueService;
import com.project.tracking_system.service.belpost.QueuedTrack;
import com.project.tracking_system.service.track.TrackSource;
import com.project.tracking_system.service.track.TrackExcelParser;
import com.project.tracking_system.service.track.TrackExcelRow;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.track.ProgressAggregatorService;
import com.project.tracking_system.service.track.TrackUpdateEligibilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import com.project.tracking_system.utils.DurationUtils;
import java.util.List;
import java.util.UUID;


/**
 * Координирует загрузку и обработку XLS-файла с треками.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackUploadProcessorService {

    private final TrackExcelParser parser;
    private final BelPostTrackQueueService belPostTrackQueueService;
    private final WebSocketController webSocketController;
    private final StoreService storeService;
    /** Сервис агрегации прогресса разных обработчиков. */
    private final ProgressAggregatorService progressAggregatorService;
    /** Проверяет возможность обновления треков перед постановкой в очередь. */
    private final TrackUpdateEligibilityService trackUpdateEligibilityService;

    /**
     * Принимает Excel-файл, конвертирует строки в {@link QueuedTrack} и
     * отправляет их в очередь на обработку.
     * После постановки в очередь пользователю отправляется сообщение через WebSocket
     * о старте обработки с указанием предполагаемого времени выполнения.
     *
     * @param file   загруженный Excel-файл
     * @param userId идентификатор текущего пользователя (может быть {@code null})
     * @throws IOException если произошла ошибка при чтении файла
     */
    public void process(MultipartFile file, Long userId) throws IOException {
        List<TrackExcelRow> rows = parser.parse(file);
        long batchId = System.currentTimeMillis();

        Long defaultStoreId = userId != null ? storeService.getDefaultStoreId(userId) : null;

        List<QueuedTrack> queued = rows.stream()
                .map(r -> new QueuedTrack(
                        r.number(),
                        userId,
                        parseStoreId(r.store(), defaultStoreId, userId),
                        TrackSource.EXCEL,
                        batchId))
                .filter(q -> trackUpdateEligibilityService.canUpdate(q.trackNumber(), q.userId()))
                .toList();

        // Если после фильтрации не осталось треков, уведомляем пользователя и завершаем обработку
        if (queued.isEmpty()) {
            webSocketController.sendUpdateStatus(
                    userId,
                    "Файл не содержит подходящих треков для обработки",
                    false
            );
            return;
        }

        progressAggregatorService.registerBatch(batchId, queued.size(), userId);
        belPostTrackQueueService.enqueue(queued);

        webSocketController.sendUpdateStatus(
                userId,
                "В очередь Белпочты поставлено " + queued.size() + " треков",
                true
        );

        Duration wait = belPostTrackQueueService.estimateWaitTime(userId);
        String waitEta = DurationUtils.formatMinutesSeconds(wait);
        if (wait != null && !wait.isZero()) {
            webSocketController.sendUpdateStatus(userId,
                    "Треки Белпочты в очереди. Ожидание до начала обработки: " + waitEta,
                    true);
        }

        long seconds = queued.size() * BelPostTrackQueueService.PROCESSING_DELAY_SECONDS;
        Duration duration = Duration.ofSeconds(seconds);
        String eta = DurationUtils.formatMinutesSeconds(duration);
        webSocketController.sendTrackProcessingStarted(userId,
                new TrackProcessingStartedDTO(queued.size(), eta, waitEta));
    }

    /**
     * Преобразует значение магазина из Excel в корректный идентификатор.
     * Если парсинг не удался или магазин не принадлежит пользователю,
     * возвращается идентификатор магазина по умолчанию.
     */
    private Long parseStoreId(String rawStore, Long defaultStoreId, Long userId) {
        Long storeId = defaultStoreId;
        if (rawStore != null && !rawStore.isBlank()) {
            try {
                storeId = Long.parseLong(rawStore);
            } catch (NumberFormatException e) {
                Long byName = userId != null ? storeService.findStoreIdByName(rawStore, userId) : null;
                if (byName != null) {
                    storeId = byName;
                } else {
                    log.warn("\uD83D\uDD0D Магазин '{}' не найден, используется магазин по умолчанию", rawStore);
                }
            }
        }

        if (storeId != null && userId != null && !storeService.userOwnsStore(storeId, userId)) {
            log.warn("\u26A0\uFE0F Магазин ID={} не принадлежит пользователю ID={}", storeId, userId);
            storeId = defaultStoreId;
        }
        return storeId;
    }
}