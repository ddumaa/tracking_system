package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.model.TrackingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Координирует загрузку и обработку XLS-файла с треками.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackUploadProcessorService {

    private final TrackExcelParser trackExcelParser;
    private final TrackMetaValidator trackMetaValidator;
    private final TrackUploadGroupingService trackUploadGroupingService;
    private final TrackBatchProcessingService trackBatchProcessingService;

    /**
     * Полный цикл загрузки файла: парсинг, валидация, группировка и отправка на обработку.
     *
     * @param file  загруженный файл
     * @param userId идентификатор пользователя
     * @return результат обработки для отображения в UI
     * @throws IOException при ошибке чтения файла
     */
    public TrackingResponse process(MultipartFile file, Long userId) throws IOException {
        List<TrackExcelRow> rows = trackExcelParser.parse(file);
        TrackMetaValidationResult validation = trackMetaValidator.validate(rows, userId);
        Map<PostalServiceType, List<TrackMeta>> grouped = trackUploadGroupingService.group(validation.validTracks());
        List<TrackingResultAdd> results = trackBatchProcessingService.processBatch(grouped, userId);
        return new TrackingResponse(results, validation.limitExceededMessage());
    }
}
