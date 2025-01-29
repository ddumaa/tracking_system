package com.project.tracking_system.service;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.User;
import org.apache.poi.ss.usermodel.*;
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
     * @return список результатов добавления, включая номер отслеживания и статус добавления или ошибку
     * @throws IOException если не удалось прочитать файл
     */
    public List<TrackingResultAdd> processTrackingNumber(MultipartFile file, User user) throws IOException {
        List<TrackingResultAdd> trackingResult = new ArrayList<>();

        // Чтение файла XLS
        try(InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<CompletableFuture<TrackingResultAdd>> futures = new ArrayList<>();

            // Асинхронная обработка каждого номера отслеживания
            for (Row row : sheet) {
                Cell cell = row.getCell(0);
                if (cell != null) {
                    String trackingNumber  = cell.getStringCellValue();

                    CompletableFuture<TrackingResultAdd> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            TrackInfoListDTO trackInfo = typeDefinitionTrackPostService.getTypeDefinitionTrackPostService(user, trackingNumber);

                            if (user != null) {
                                trackParcelService.save(trackingNumber, trackInfo, user);
                            }

                            return new TrackingResultAdd(trackingNumber, "Добавлен");
                        } catch (Exception e) {
                            return new TrackingResultAdd(trackingNumber, "Ошибка: " + e.getMessage());
                        }
                    });
                    futures.add(future);
                }
            }

            // Ожидание завершения всех асинхронных задач
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Получение результатов из всех CompletableFuture
            for (CompletableFuture<TrackingResultAdd> future : futures) {
                trackingResult.add(future.join());
            }
        }
        return trackingResult;
    }

}