package com.project.tracking_system.service;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.User;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Сервис для обработки номеров отслеживания из XLS-файлов.
 * <p>
 * Этот сервис позволяет загружать файлы с номерами отслеживания и асинхронно обрабатывать каждый номер,
 * получая информацию о нем и сохраняем её в систему.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date Добавлено 07.01.2025
 */
@Service
public class TrackingNumberServiceXLS {

    Logger logger = LoggerFactory.getLogger(TrackingNumberServiceXLS.class);

    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    private final TrackParcelService trackParcelService;

    /**
     * Конструктор класса {@link TrackingNumberServiceXLS}.
     *
     * @param typeDefinitionTrackPostService сервис для получения информации о посылке по номеру отслеживания
     * @param trackParcelService сервис для сохранения информации о посылке
     */
    @Autowired
    public TrackingNumberServiceXLS(TypeDefinitionTrackPostService typeDefinitionTrackPostService, TrackParcelService trackParcelService) {
        this.typeDefinitionTrackPostService = typeDefinitionTrackPostService;
        this.trackParcelService = trackParcelService;
    }

    /**
     * Обрабатывает номера отслеживания, загруженные в формате XLS.
     * <p>
     * Для каждого номера отслеживания, загруженного из файла, сервис извлекает информацию о посылке,
     * сохраняет её в базу данных и возвращает результаты обработки.
     * </p>
     *
     * @param file              файл XLS с номерами отслеживания
     * @param authenticatedUser авторизованный пользователь, который загрузил файл
     * @return список результатов добавления, включая номер отслеживания и статус или ошибку
     * @throws IOException если не удалось прочитать файл
     */
    public List<TrackingResultAdd> processTrackingNumber(MultipartFile file, User user) throws IOException {
        List<TrackingResultAdd> trackingResult = new ArrayList<>();

        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<CompletableFuture<TrackingResultAdd>> futures = new ArrayList<>();

            for (Row row : sheet) {
                Cell cell = row.getCell(0);
                if (cell != null) {
                    String trackingNumber = cell.getStringCellValue();
                    logger.info("Обрабатываем трек-номер: {}", trackingNumber);

                    CompletableFuture<TrackingResultAdd> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            TrackInfoListDTO trackInfo = typeDefinitionTrackPostService.getTypeDefinitionTrackPostService(user, trackingNumber);

                            if (user != null) {
                                trackParcelService.save(trackingNumber, trackInfo, user);
                            }

                            // Получение последнего статуса
                            String lastStatus = "Нет данных";
                            if (trackInfo != null && trackInfo.getList() != null && !trackInfo.getList().isEmpty()) {
                                lastStatus = trackInfo.getList().get(0).getInfoTrack();
                            }

                            logger.info("Трек-номер: {}, последний статус: {}", trackingNumber, lastStatus);
                            return new TrackingResultAdd(trackingNumber, lastStatus);

                        } catch (Exception e) {
                            logger.error("Ошибка обработки трек-номера {}: {}", trackingNumber, e.getMessage());
                            return new TrackingResultAdd(trackingNumber, "Ошибка: " + e.getMessage());
                        }
                    });

                    futures.add(future);
                }
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            for (CompletableFuture<TrackingResultAdd> future : futures) {
                trackingResult.add(future.join());
            }
        }

        return trackingResult;
    }

}