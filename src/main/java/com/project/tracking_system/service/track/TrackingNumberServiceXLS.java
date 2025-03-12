package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.model.TrackingResponse;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.TypeDefinitionTrackPostService;
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
    public TrackingResponse processTrackingNumber(MultipartFile file, Long storeId, Long userId) throws IOException {

        List<TrackingResultAdd> trackingResult = new ArrayList<>();
        StringBuilder messageBuilder = new StringBuilder();

        // 1. Получаем лимиты
        int maxTrackingLimit = (userId == null)
                ? 5
                : subscriptionService.canUploadTracks(userId, Integer.MAX_VALUE);

        int availableSaveSlots = (userId == null)
                ? 0
                : subscriptionService.canSaveMoreTracks(userId, Integer.MAX_VALUE);

        log.info("Лимит на обработку (maxTrackingLimit): {}, Лимит на сохранение (availableSaveSlots): {}",
                maxTrackingLimit, availableSaveSlots);

        // 2. Читаем Excel
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);

            int physicalRows = sheet.getPhysicalNumberOfRows();
            int rowCount = Math.min(physicalRows, maxTrackingLimit);

            if (physicalRows > maxTrackingLimit) {
                int skipped = physicalRows - maxTrackingLimit;
                messageBuilder.append(String.format(
                        "Вы загрузили %d треков, но можете проверить только %d. Пропущено %d треков.%n",
                        physicalRows, rowCount, skipped
                ));
            }

            List<CompletableFuture<TrackingResultAdd>> futures = new ArrayList<>();

            int checkedCount = 0;
            int savedNewCount = 0;
            List<String> skippedSaves = new ArrayList<>();

            for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                Cell cell = row.getCell(0);
                if (cell != null) {
                    String trackingNumber = cell.getStringCellValue();
                    log.info("Обрабатываем трек-номер: {}", trackingNumber);

                    // Проверяем, новый ли трек
                    boolean isNewTrack = (userId != null) && trackParcelService.isNewTrack(trackingNumber, userId);

                    // Решаем, можно ли сохранять
                    boolean canSaveThis;
                    if (isNewTrack) {
                        // Если трек действительно новый, проверяем слоты
                        if (savedNewCount < availableSaveSlots) {
                            canSaveThis = true;
                            savedNewCount++;  // Занимаем слот
                        } else {
                            canSaveThis = false;
                            skippedSaves.add(trackingNumber);
                        }
                    } else {
                        // Если трек уже есть в БД, мы его только обновляем — слот не нужен
                        canSaveThis = true;
                    }

                    CompletableFuture<TrackingResultAdd> future = CompletableFuture.supplyAsync(
                            () -> processSingleTracking(trackingNumber, storeId, userId, canSaveThis),
                            executor
                    );

                    futures.add(future);
                    checkedCount++;
                }
            }


            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            futures.forEach(f -> trackingResult.add(f.join())); // ✅ Теперь все обработанные треки добавлены

            if (!skippedSaves.isEmpty()) {
                messageBuilder.append(String.format(
                        "Из %d обработанных треков не удалось сохранить %d из-за лимита подписки: %s%n",
                        checkedCount, skippedSaves.size(), skippedSaves
                ));
            }

            String limitExceededMessage = messageBuilder.length() > 0
                    ? messageBuilder.toString().trim()
                    : null;

            log.info("Итоговое сообщение: {}", limitExceededMessage);

            return new TrackingResponse(trackingResult, limitExceededMessage);
        }
    }

    private TrackingResultAdd processSingleTracking(String trackingNumber, Long storeId, Long userId, boolean canSave) {
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

            // Если разрешено, сохраняем, иначе просто обрабатываем без сохранения
            if (userId != null && canSave) {
                trackParcelService.save(trackingNumber, trackInfo, storeId, userId);
            } else {
                log.warn("Трек {} обработан, но не сохранён (превышен лимит).", trackingNumber);
            }

            String lastStatus = trackInfo.getList().get(0).getInfoTrack();
            log.debug("Трек-номер: {}, последний статус: {}", trackingNumber, lastStatus);

            return new TrackingResultAdd(trackingNumber, lastStatus);
        } catch (Exception e) {
            log.error("Ошибка обработки трек-номера {}: {}", trackingNumber, e.getMessage(), e);
            return new TrackingResultAdd(trackingNumber, "Ошибка обработки");
        }
    }

}