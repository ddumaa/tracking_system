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
import com.project.tracking_system.service.track.TrackUploadGroupingService;
import com.project.tracking_system.service.track.TrackUpdateDispatcherService;
import com.project.tracking_system.service.track.TrackingResultCacheService;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.dto.TrackStatusUpdateDTO;
import com.project.tracking_system.dto.TrackProcessingProgressDTO;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.service.track.TrackMeta;
import com.project.tracking_system.service.track.TrackMetaValidationResult;
import com.project.tracking_system.service.track.InvalidTrack;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import com.project.tracking_system.utils.DurationUtils;
import java.util.List;
import java.util.Map;


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
    /** Сервис группировки треков по почтовым службам. */
    private final TrackUploadGroupingService groupingService;
    /** Диспетчер обработки треков разных служб. */
    private final TrackUpdateDispatcherService dispatcherService;
    /** Кэш результатов обработки для восстановления таблицы. */
    private final TrackingResultCacheService trackingResultCacheService;

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
     * @return результат валидации, содержащий список обработанных и некорректных треков
     * @throws IOException если произошла ошибка при чтении файла
     */
    public TrackMetaValidationResult process(MultipartFile file, Long userId) throws IOException {
        List<TrackExcelRow> rows = parser.parse(file);
        long batchId = System.currentTimeMillis();

        List<TrackMeta> metas;
        List<InvalidTrack> invalid = List.of();
        String limitMessage = null;

        if (userId != null) {
            // Валидация данных и применение лимитов
            TrackMetaValidationResult validationResult = trackMetaValidator.validate(rows, userId);
            invalid = validationResult.invalidTracks();
            limitMessage = validationResult.limitExceededMessage();

            metas = validationResult.validTracks().stream()
                    .filter(m -> trackUpdateEligibilityService.canUpdate(m.number(), userId))
                    .toList();
        } else {
            metas = List.of();
        }

        if (metas.isEmpty()) {
            // отправляем пустой прогресс, чтобы скрыть статусбар
            progressAggregatorService.registerBatch(batchId, 0, userId);
            webSocketController.sendUpdateStatus(
                    userId,
                    "Ошибка — все треки невалидны",
                    false
            );
            return new TrackMetaValidationResult(List.of(), invalid, limitMessage);
        }

        progressAggregatorService.registerBatch(batchId, metas.size(), userId);
        Map<PostalServiceType, List<TrackMeta>> grouped = groupingService.group(metas);

        // Отдельно ставим в очередь треки Белпочты
        List<TrackMeta> belpost = grouped.remove(PostalServiceType.BELPOST);
        if (belpost != null && !belpost.isEmpty()) {
            List<QueuedTrack> queued = belpost.stream()
                    .map(m -> new QueuedTrack(
                            m.number(),
                            userId,
                            m.storeId(),
                            TrackSource.EXCEL,
                            batchId,
                            m.phone()))
                    .toList();
            belPostTrackQueueService.enqueue(queued);
            webSocketController.sendUpdateStatus(
                    userId,
                    "В очередь Белпочты поставлено " + queued.size() + " треков",
                    true
            );
        }

        // Обрабатываем остальные треки сразу
        List<TrackingResultAdd> processed = dispatcherService.dispatch(grouped, userId);
        for (TrackingResultAdd r : processed) {
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

        int belpostCount = belpost != null ? belpost.size() : 0;
        long seconds = belpostCount * BelPostTrackQueueService.PROCESSING_DELAY_SECONDS;
        Duration duration = Duration.ofSeconds(seconds);
        String eta = DurationUtils.formatMinutesSeconds(duration);
        webSocketController.sendTrackProcessingStarted(userId,
                new TrackProcessingStartedDTO(metas.size(), eta, waitEta));
        return new TrackMetaValidationResult(metas, invalid, limitMessage);
    }

}