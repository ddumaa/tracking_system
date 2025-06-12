package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.model.TrackingResponse;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.store.StoreService;
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

    private final TrackParcelService trackParcelService;
    private final SubscriptionService subscriptionService;
    private final StoreService storeService;
    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    /**
     * Обрабатывает номера отслеживания, загруженные в формате XLS.
     * <p>
     * Файл должен содержать два столбца:
     * - Первый столбец (A): номер трека (обязателен)
     * - Второй столбец (B): название магазина ИЛИ его ID (не обязательно)
     *   Если второй столбец пуст, для авторизованного пользователя используется магазин по умолчанию.
     * <p>
     * Первая строка файла (заголовок) не обрабатывается.
     * Если пользователь не авторизован, применяется гостевой лимит,
     * и магазин не обрабатывается.
     * </p>
     *
     * @param file   XLS-файл с данными
     * @param userId ID авторизованного пользователя (null, если гость)
     * @return TrackingResponse с результатом обработки (список успешно/неуспешно обработанных треков)
     * @throws IOException при ошибках чтения файла
     */
    public TrackingResponse processTrackingNumber(MultipartFile file, Long userId) throws IOException {
        // Результаты обработки для каждого трека
        List<TrackingResultAdd> trackingResult = new ArrayList<>();
        // Сообщение для уведомления пользователя об ограничениях или ошибках
        StringBuilder messageBuilder = new StringBuilder();

        // Флаг, указывающий, авторизован ли пользователь
        boolean isUserAuthorized = (userId != null);

        // 1. Получаем лимит для обработки треков
        // Если пользователь авторизован, используем подписочный лимит, иначе гостевой лимит 5 треков
        int maxTrackingLimit = isUserAuthorized
                ? subscriptionService.canUploadTracks(userId, Integer.MAX_VALUE)
                : 5;

        // Лимит на сохранение новых треков (применяется только для авторизованных)
        int availableSaveSlots = isUserAuthorized
                ? subscriptionService.canSaveMoreTracks(userId, Integer.MAX_VALUE)
                : 0;

        log.info("Лимит на обработку (maxTrackingLimit): {}, Лимит на сохранение (availableSaveSlots): {}",
                maxTrackingLimit, availableSaveSlots);

        // Получаем магазин по умолчанию только для авторизованных пользователей,
        // для гостей магазин не нужен (будет null)
        Long defaultStoreId = isUserAuthorized
                ? storeService.getDefaultStoreId(userId)
                : null;

        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0); // Берём первый лист

            int physicalRows = sheet.getPhysicalNumberOfRows();
            // Проверяем, что в файле есть хотя бы заголовок
            if (physicalRows < 1) {
                throw new IOException("Файл не содержит данных.");
            }
            // Вычисляем количество треков (без учета заголовка)
            int rowsToProcess = Math.min(physicalRows - 1, maxTrackingLimit);

            // Если треков больше, чем лимит, формируем сообщение о пропуске
            if (physicalRows - 1 > maxTrackingLimit) {
                int skipped = (physicalRows - 1) - maxTrackingLimit;
                messageBuilder.append(String.format(
                        "Вы загрузили %d треков, но можете проверить только %d. Пропущено %d треков.%n",
                        physicalRows - 1, rowsToProcess, skipped
                ));
            }

            // Обрабатываем строки асинхронно
            List<CompletableFuture<TrackingResultAdd>> futures = new ArrayList<>();
            int checkedCount = 0;           // Счетчик обработанных треков
            int savedNewCount = 0;          // Счетчик успешно сохраненных новых треков
            List<String> skippedSaves = new ArrayList<>(); // Треки, которые не удалось сохранить из-за ограничения

            // Цикл начинается со второй строки (индекс 1), так как первая строка – заголовок
            for (int rowIndex = 1; rowIndex < rowsToProcess + 1; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                // Первый столбец: номер трека (обязателен)
                Cell trackCell = row.getCell(0);
                // Второй столбец: магазин (название или ID) – обрабатывается только для авторизованных
                Cell storeCell = row.getCell(1);

                if (trackCell == null) continue; // Если ячейка с треком отсутствует, пропускаем

                String trackingNumber = trackCell.getStringCellValue().trim();
                if (trackingNumber.isEmpty()) {
                    log.warn("⚠ Пустой номер трека в строке {}", rowIndex + 1);
                    continue;
                }

                // Если пользователь авторизован, обрабатываем магазин, иначе оставляем storeId равным null
                Long storeId = null;
                if (isUserAuthorized) {
                    // Изначально присваиваем магазин по умолчанию
                    storeId = defaultStoreId;
                    if (storeCell != null) {
                        // Определяем тип ячейки для корректного чтения значения
                        if (storeCell.getCellType() == CellType.NUMERIC) {
                            storeId = (long) storeCell.getNumericCellValue();
                            log.debug("В строке {} указан ID магазина: {}", rowIndex + 1, storeId);
                        } else if (storeCell.getCellType() == CellType.STRING) {
                            String storeName = storeCell.getStringCellValue().trim();
                            if (!storeName.isEmpty()) {
                                Long storeIdByName = storeService.findStoreIdByName(storeName, userId);
                                if (storeIdByName != null) {
                                    storeId = storeIdByName;
                                    log.debug("В строке {} указан магазин по имени '{}', ID={}", rowIndex + 1, storeName, storeId);
                                } else {
                                    log.warn("⚠ Магазин '{}' не найден у пользователя, используем дефолтный.", storeName);
                                }
                            }
                        } else {
                            log.warn("⚠ Неподдерживаемый тип ячейки для магазина в строке {}", rowIndex + 1);
                        }
                    }
                    // Проверяем, принадлежит ли указанный магазин пользователю
                    if (storeId != null && !storeService.userOwnsStore(storeId, userId)) {
                        log.warn("⚠ Магазин ID={} не принадлежит пользователю ID={}, используем дефолтный.", storeId, userId);
                        storeId = defaultStoreId;
                    }
                }

                log.info("Трек={}, магазин={} (userId={})", trackingNumber, storeId, userId);

                // Для авторизованных пользователей проверяем, является ли трек новым в рамках выбранного магазина
                boolean isNewTrack = isUserAuthorized && trackParcelService.isNewTrack(trackingNumber, storeId);
                boolean canSaveThis;
                if (isNewTrack) {
                    if (savedNewCount < availableSaveSlots) {
                        canSaveThis = true;
                        savedNewCount++;
                    } else {
                        canSaveThis = false;
                        skippedSaves.add(trackingNumber);
                    }
                } else {
                    // Если трек уже существует, обновляем его (сохранение не требует использования слота)
                    canSaveThis = true;
                }

                // Создаем финальную копию storeId для использования в лямбда-выражении
                final Long finalStoreId = storeId;

                // Асинхронно обрабатываем трек с обработкой исключений
                CompletableFuture<TrackingResultAdd> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        return processSingleTracking(trackingNumber, finalStoreId, userId, canSaveThis);
                    } catch (Exception e) {
                        log.error("Ошибка обработки трека {}: {}", trackingNumber, e.getMessage());
                        // Возвращаем объект с сообщением об ошибке, передавая статус как строку
                        return new TrackingResultAdd(trackingNumber, "ERROR: " + e.getMessage());
                    }
                }, executor);

                futures.add(future);
                checkedCount++;
            }

            // Ждем завершения всех асинхронных задач
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Собираем результаты обработки
            futures.forEach(f -> trackingResult.add(f.join()));

            // Если некоторые треки не удалось сохранить из-за лимита, формируем соответствующее сообщение
            if (!skippedSaves.isEmpty()) {
                messageBuilder.append(String.format(
                        "Из %d обработанных треков не удалось сохранить %d из-за лимита подписки: %s%n",
                        checkedCount, skippedSaves.size(), skippedSaves
                ));
            }

            String limitExceededMessage = !messageBuilder.isEmpty() ? messageBuilder.toString().trim() : null;
            log.info("📋 Итоговое сообщение: {}", limitExceededMessage);

            return new TrackingResponse(trackingResult, limitExceededMessage);
        }
    }


    private TrackingResultAdd processSingleTracking(String trackingNumber, Long storeId, Long userId, boolean canSave) {
        try {
            // Используем processTrack для получения данных и сохранения, если необходимо
            TrackInfoListDTO trackInfo = trackParcelService.processTrack(trackingNumber, storeId, userId, canSave);

            String lastStatus = trackInfo.getList().get(0).getInfoTrack();
            log.debug("Трек-номер: {}, последний статус: {}", trackingNumber, lastStatus);

            return new TrackingResultAdd(trackingNumber, lastStatus);
        } catch (IllegalArgumentException e) {
            log.warn("Ошибка обработки {}: {}", trackingNumber, e.getMessage());
            return new TrackingResultAdd(trackingNumber, "Нет данных");
        } catch (Exception e) {
            log.error("Ошибка обработки {}: {}", trackingNumber, e.getMessage(), e);
            return new TrackingResultAdd(trackingNumber, "Ошибка обработки");
        }
    }

}