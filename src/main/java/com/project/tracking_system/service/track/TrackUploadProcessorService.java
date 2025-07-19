package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.model.TrackingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;


/**
 * Координирует загрузку и обработку XLS-файла с треками.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackUploadProcessorService {

    private final TrackExcelParser parser;
    private final TrackMetaValidator validator;
    private final TrackUpdateService trackUpdateService;

    /**
     * Обрабатывает загруженный файл и возвращает агрегированные результаты.
     *
     * @param file   загруженный Excel-файл
     * @param userId идентификатор текущего пользователя (может быть {@code null})
     * @return объединённый ответ обработки
     * @throws IOException если произошла ошибка при чтении файла
     */
    public TrackingResponse process(MultipartFile file, Long userId) throws IOException {
        List<TrackExcelRow> rows = parser.parse(file);
        TrackMetaValidationResult validationResult = validator.validate(rows, userId);
        List<TrackingResultAdd> results = trackUpdateService.process(validationResult.validTracks(), userId);
        return new TrackingResponse(results, validationResult.limitExceededMessage());
    }

}