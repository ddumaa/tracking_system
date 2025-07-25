package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackProcessingStartedDTO;
import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.service.belpost.BelPostTrackQueueService;
import com.project.tracking_system.service.belpost.QueuedTrack;
import com.project.tracking_system.service.track.TrackSource;
import com.project.tracking_system.service.track.TrackExcelParser;
import com.project.tracking_system.service.track.TrackExcelRow;
import com.project.tracking_system.service.track.TrackMetaValidator;
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
    /** Сервис валидации загружаемых треков. */
    private final TrackMetaValidator trackMetaValidator;
    /** Сервис агрегации прогресса разных обработчиков. */
    private final ProgressAggregatorService progressAggregatorService;
    /** Проверяет возможность обновления треков перед постановкой в очередь. */
    private final TrackUpdateEligibilityService trackUpdateEligibilityService;

    /**
     * Принимает Excel-файл, валидирует строки и конвертирует их
     * в {@link QueuedTrack}, отправляя полученные задания в очередь.
     * Если при валидации выявлены превышения лимитов,
     * пользователю отправляется отдельное уведомление.
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

        List<QueuedTrack> queued;
        String limitMessage = null;

        if (userId != null) {
            // Валидация данных и применение пользовательских лимитов
            TrackMetaValidationResult validationResult = trackMetaValidator.validate(rows, userId);
            limitMessage = validationResult.limitExceededMessage();

            // Преобразуем валидные метаданные в задания на обновление
            queued = validationResult.validTracks().stream()
                    .map(m -> new QueuedTrack(
                            m.number(),
                            userId,
                            m.storeId(),
                            TrackSource.EXCEL,
                            batchId,
                            m.phone()))
                    .filter(q -> trackUpdateEligibilityService.canUpdate(q.trackNumber(), q.userId()))
                    .toList();
        } else {
            // Для неавторизованных пользователей обработка не выполняется
            queued = List.of();
        }

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

        // Дополнительное уведомление о превышении лимитов, если есть
        if (limitMessage != null) {
            webSocketController.sendUpdateStatus(userId, limitMessage, true);
        }

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

}