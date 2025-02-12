package com.project.tracking_system.service;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.model.TrackingResponse;
import com.project.tracking_system.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
@Slf4j
@RequiredArgsConstructor
@Service
public class TrackingNumberServiceXLS {

    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    private final TrackParcelService trackParcelService;
    private final UserService userService;
    private final SubscriptionService subscriptionService;
    private final ExecutorService executor = Executors.newFixedThreadPool(5);

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
    public TrackingResponse processTrackingNumber(MultipartFile file, Long userId) throws IOException {
        List<TrackingResultAdd> trackingResult = new ArrayList<>();
        String limitExceededMessage = null;

        int maxTrackingLimit = 5;

        if (userId != null) {
            // Получаем лимит загрузки для пользователя (сколько треков можно загрузить за раз)
            maxTrackingLimit = subscriptionService.canUploadTracks(userId, Integer.MAX_VALUE);

            // Для анонимных пользователей, если лимит превышен, возвращаем сообщение
            if (maxTrackingLimit == 0) {
                limitExceededMessage = "Вы не можете загрузить больше треков, так как превышен лимит для вашего аккаунта.";
            }
        }

        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<CompletableFuture<TrackingResultAdd>> futures = new ArrayList<>();

            int rowCount = Math.min(sheet.getPhysicalNumberOfRows(), maxTrackingLimit);

            for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                Cell cell = row.getCell(0);
                if (cell != null) {
                    String trackingNumber = cell.getStringCellValue();
                    log.info("Обрабатываем трек-номер: {}", trackingNumber);

                    CompletableFuture<TrackingResultAdd> future = CompletableFuture.supplyAsync(
                            () -> processSingleTracking(trackingNumber, userId), executor);
                    futures.add(future);
                }
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            futures.forEach(future -> trackingResult.add(future.join()));

            // Если анонимный пользователь превысил лимит → передаем сообщение в отдельную переменную
            if (sheet.getPhysicalNumberOfRows() > maxTrackingLimit) {
                if (userId == null) {
                    limitExceededMessage = "Превышен лимит на количество треков для вашего аккаунта. Для анонимных пользователей лимит составляет " + maxTrackingLimit + " треков.";
                }
            }
        }

        return new TrackingResponse(trackingResult, limitExceededMessage);
    }

    private TrackingResultAdd processSingleTracking(String trackingNumber, Long userId) {
        try {
            TrackInfoListDTO trackInfo;

            if (userId == null) {
                log.debug("Трек-номер {} обрабатывается анонимным пользователем.", trackingNumber);
                trackInfo = typeDefinitionTrackPostService.getTypeDefinitionTrackPostService(null, trackingNumber);
            } else {
                trackInfo = typeDefinitionTrackPostService.getTypeDefinitionTrackPostService(userId, trackingNumber);
            }

            if (trackInfo == null || trackInfo.getList() == null || trackInfo.getList().isEmpty()) {
                log.debug("Нет данных по трек-номеру {}", trackingNumber);
                return new TrackingResultAdd(trackingNumber, "Нет данных");
            }

            // Сохраняем посылку только для авторизованных пользователей
            if (userId != null) {
                trackParcelService.save(trackingNumber, trackInfo, userId);
            } else {
                log.debug("Анонимный пользователь обработал трек-номер {} без сохранения.", trackingNumber);
            }

            String lastStatus = trackInfo.getList().get(0).getInfoTrack();
            log.debug("Трек-номер: {}, последний статус: {}", trackingNumber, lastStatus);

            return new TrackingResultAdd(trackingNumber, lastStatus);
        } catch (Exception e) {
            log.error("Ошибка обработки трек-номера {}: {}", trackingNumber, e.getMessage(), e);
            return new TrackingResultAdd(trackingNumber, "Ошибка: " + e.getMessage());
        }
    }

}