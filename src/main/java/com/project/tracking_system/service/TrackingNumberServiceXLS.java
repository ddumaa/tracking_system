package com.project.tracking_system.service;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.Role;
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

    private final static Logger logger = LoggerFactory.getLogger(TrackingNumberServiceXLS.class);

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

        // Проверка на роль пользователя и ограничение количества треков
        boolean isFreeUser = user != null && user.getRoles().contains(Role.ROLE_FREE_USER);
        boolean isAuthenticatedUser = user != null;
        boolean isAnonymousUser = !isAuthenticatedUser;

        // Ограничения для разных типов пользователей
        int maxTrackingLimit = Integer.MAX_VALUE; // Для платных пользователей без ограничений
        if (isFreeUser) {
            maxTrackingLimit = 10; // Для бесплатных пользователей — максимум 10 треков
        } else if (isAnonymousUser) {
            maxTrackingLimit = 5;  // Для анонимных пользователей — максимум 5 треков
        }

        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<CompletableFuture<TrackingResultAdd>> futures = new ArrayList<>();

            // Ограничиваем количество обрабатываемых треков в зависимости от типа пользователя
            int rowCount = Math.min(sheet.getPhysicalNumberOfRows(), maxTrackingLimit);

            for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

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

            // Если количество треков превышает ограничение для анонимных или бесплатных пользователей, добавляем ошибку
            if (isAnonymousUser && sheet.getPhysicalNumberOfRows() > 5) {
                trackingResult.add(new TrackingResultAdd("",
                        "Для анонимных пользователей доступно не более 5 треков. Зарегистрируйтесь, чтобы повысить лимит."));
            } else if (isFreeUser && sheet.getPhysicalNumberOfRows() > 10) {
                trackingResult.add(new TrackingResultAdd("",
                        "Для бесплатных пользователей доступно не более 10 треков. Перейдите на платный аккаунт, чтобы не было ограничений."));
            }

        }

        return trackingResult;
    }

}