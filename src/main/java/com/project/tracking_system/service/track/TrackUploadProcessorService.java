package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackProcessingStartedDTO;
import com.project.tracking_system.model.queue.QueuedTrack;
import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.service.track.TrackExcelParser;
import com.project.tracking_system.service.track.TrackExcelRow;
import com.project.tracking_system.service.track.BelPostTrackQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
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
        String batchId = UUID.randomUUID().toString();

        List<QueuedTrack> queued = rows.stream()
                .map(r -> new QueuedTrack(r.number(), r.store(), r.phone(), userId, batchId, "EXCEL"))
                .toList();

        belPostTrackQueueService.enqueue(queued);

        long seconds = queued.size() * 2L; // условно 2 секунды на трек
        Duration duration = Duration.ofSeconds(seconds);
        String eta = String.format("%d:%02d", duration.toMinutes(), duration.toSecondsPart());
        webSocketController.sendTrackProcessingStarted(userId,
                new TrackProcessingStartedDTO(queued.size(), eta));
    }

}