package com.project.tracking_system.service.track;

import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.utils.PhoneUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Проверяет и нормализует сырые данные треков.
 * <p>
 * Также применяет лимиты пользователя на обработку и сохранение треков.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackMetaValidator {

    private final TrackParcelService trackParcelService;
    private final SubscriptionService subscriptionService;
    private final StoreService storeService;

    /**
     * Валидирует сырые строки и преобразует их в {@link TrackMeta}.
     *
     * @param rows  строки из XLS-файла
     * @param userId идентификатор пользователя, не {@code null}
     * @return результат валидации
     */
    public TrackMetaValidationResult validate(List<TrackExcelRow> rows, Long userId) {
        // Загрузка файлов доступна только авторизованным пользователям,
        // поэтому идентификатор пользователя обязателен
        Objects.requireNonNull(userId, "User ID Не может быть null");
        StringBuilder messageBuilder = new StringBuilder();

        int maxLimit = subscriptionService.canUploadTracks(userId, Integer.MAX_VALUE);
        int saveSlots = subscriptionService.canSaveMoreTracks(userId, Integer.MAX_VALUE);
        Long defaultStoreId = storeService.getDefaultStoreId(userId);

        int processed = 0;
        int savedNew = 0;
        List<String> skipped = new ArrayList<>();
        List<TrackMeta> result = new ArrayList<>();

        for (TrackExcelRow row : rows) {
            if (processed >= maxLimit) {
                break;
            }
            String number = row.number().toUpperCase();
            Long storeId = defaultStoreId;
            if (row.store() != null && !row.store().isBlank()) {
                try {
                    storeId = Long.parseLong(row.store());
                } catch (NumberFormatException e) {
                    Long byName = storeService.findStoreIdByName(row.store(), userId);
                    if (byName != null) {
                        storeId = byName;
                    } else {
                        log.warn("Магазин '{}' не найден, используем дефолтный", row.store());
                    }
                }
            }
            if (storeId != null && !storeService.userOwnsStore(storeId, userId)) {
                log.warn("Магазин ID={} не принадлежит пользователю ID={}", storeId, userId);
                storeId = defaultStoreId;
            }
            String phone = row.phone();
            if (phone != null && !phone.isBlank()) {
                try {
                    phone = PhoneUtils.normalizePhone(phone);
                } catch (Exception e) {
                    log.warn("Некорректный телефон '{}' пропущен", phone);
                    phone = null;
                }
            }
            boolean isNew = trackParcelService.isNewTrack(number, storeId);
            boolean canSave;
            if (isNew) {
                if (savedNew < saveSlots) {
                    canSave = true;
                    savedNew++;
                } else {
                    canSave = false;
                    skipped.add(number);
                }
            } else {
                canSave = true;
            }
            result.add(new TrackMeta(number, storeId, phone, canSave));
            processed++;
        }

        if (rows.size() > maxLimit) {
            int skippedRows = rows.size() - maxLimit;
            messageBuilder.append(String.format(
                    "Вы загрузили %d треков, но можете проверить только %d. Пропущено %d треков.%n",
                    rows.size(), maxLimit, skippedRows));
        }
        if (!skipped.isEmpty()) {
            messageBuilder.append(String.format(
                    "Из %d обработанных треков не удалось сохранить %d из-за лимита подписки: %s%n",
                    processed, skipped.size(), skipped));
        }

        String message = messageBuilder.isEmpty() ? null : messageBuilder.toString().trim();
        return new TrackMetaValidationResult(result, message);
    }

}