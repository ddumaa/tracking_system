package com.project.tracking_system.service.track;

import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.customer.CustomerNameService;
import com.project.tracking_system.utils.PhoneUtils;
import com.project.tracking_system.utils.TrackNumberUtils;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.service.track.TypeDefinitionTrackPostService;
import com.project.tracking_system.service.track.InvalidTrack;
import com.project.tracking_system.service.track.InvalidTrackReason;
import com.project.tracking_system.service.track.PreRegistrationMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    /**
     * Временное представление трека в процессе валидации.
     * <p>
     * Хранит данные, необходимые для проверки и нормализации,
     * до применения лимитов на сохранение новых треков.
     * </p>
     */
    private static record TempMeta(String number, Long storeId, String phone, boolean isNew) {
    }

    /** Причина некорректной строки – номер отсутствует. */
    private static final InvalidTrackReason REASON_EMPTY = InvalidTrackReason.EMPTY_NUMBER;
    /** Причина некорректной строки – неверный формат номера. */
    private static final InvalidTrackReason REASON_FORMAT = InvalidTrackReason.WRONG_FORMAT;
    /** Причина некорректной строки – дубликат в загруженных данных. */
    private static final InvalidTrackReason REASON_DUPLICATE = InvalidTrackReason.DUPLICATE;

    private final TrackParcelService trackParcelService;
    private final SubscriptionService subscriptionService;
    private final StoreService storeService;
    /** Сервис определения почтовой службы трека. */
    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    /** Сервис обновления ФИО покупателя. */
    private final CustomerNameService customerNameService;

/**
 * Валидирует сырые строки и преобразует их в {@link TrackMeta}.
 * <p>
 * Процесс включает нормализацию номера, проверку принадлежности
 * почтовой службе, устранение дубликатов и применение лимитов
 * пользователя. Все некорректные строки собираются в список
 * {@link InvalidTrack} и вместе с успешно обработанными номерами
 * возвращаются в {@link TrackMetaValidationResult}. Строки, отмеченные
 * как предрегистрация, выделяются в отдельный список и не влияют на лимиты.
 * </p>
 *
 * @param rows   строки из XLS-файла
 * @param userId идентификатор пользователя, не {@code null}
 * @return результат валидации
 */
    public TrackMetaValidationResult validate(List<TrackExcelRow> rows, Long userId) {
        Objects.requireNonNull(userId, "User ID не может быть null");
        StringBuilder messageBuilder = new StringBuilder();

        Long defaultStoreId = storeService.getDefaultStoreId(userId);

        // Шаг 1: разделяем строки на валидные и некорректные
        List<InvalidTrack> invalidTracks = new ArrayList<>();
        List<TrackExcelRow> validRows = new ArrayList<>();
        List<PreRegistrationMeta> preRegistered = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (TrackExcelRow row : rows) {
            if (row.preRegistered()) {
                String normalized = row.number() == null ? null : TrackNumberUtils.normalize(row.number());
                Long storeId = parseStoreId(row.store(), defaultStoreId, userId);
                preRegistered.add(new PreRegistrationMeta(normalized, storeId));
                continue;
            }
            String raw = row.number();
            if (raw == null || raw.isBlank()) {
                invalidTracks.add(new InvalidTrack(null, REASON_EMPTY));
                continue;
            }
            String normalized = TrackNumberUtils.normalize(raw);
            if (typeDefinitionTrackPostService.detectPostalService(normalized) == PostalServiceType.UNKNOWN) {
                invalidTracks.add(new InvalidTrack(raw, REASON_FORMAT));
                continue;
            }
            if (!seen.add(normalized)) {
                invalidTracks.add(new InvalidTrack(raw, REASON_DUPLICATE));
                continue;
            }
            validRows.add(row);
        }

        int maxLimit = subscriptionService.canUploadTracks(userId, validRows.size());

        // Временная структура для подготовки данных
        List<TempMeta> tempMetaList = new ArrayList<>();
        int processed = 0;

        for (TrackExcelRow row : validRows) {
            if (processed >= maxLimit) break;

            String number = TrackNumberUtils.normalize(row.number());
            Long storeId = parseStoreId(row.store(), defaultStoreId, userId);
            String phone = normalizePhone(row.phone());
            String fullName = row.fullName();
            if (phone != null && fullName != null && !fullName.isBlank()) {
                // При наличии телефона и ФИО обновляем данные покупателя
                customerNameService.upsertFromStore(phone, fullName);
            }

            boolean isNew = trackParcelService.isNewTrack(number, storeId);
            tempMetaList.add(new TempMeta(number, storeId, phone, isNew));
            processed++;
        }

        // Шаг 2: запрашиваем лимит сохранения только для новых треков
        long totalNew = tempMetaList.stream().filter(TempMeta::isNew).count();
        int saveSlots = subscriptionService.canSaveMoreTracks(userId, (int) totalNew);

        // Шаг 3: формируем финальный список TrackMeta с учётом лимита
        List<String> skipped = new ArrayList<>();
        List<TrackMeta> result = applySaveLimit(tempMetaList, saveSlots, skipped);

        // Сообщения о превышениях
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
        return new TrackMetaValidationResult(result, invalidTracks, message, preRegistered);
    }

    /**
     * Разбирает значение магазина из файла и возвращает корректный ID магазина.
     * Если магазин не найден или не принадлежит пользователю, возвращается ID магазина по умолчанию.
     */
    private Long parseStoreId(String rawStore, Long defaultStoreId, Long userId) {
        Long storeId = defaultStoreId;
        if (rawStore != null && !rawStore.isBlank()) {
            try {
                storeId = Long.parseLong(rawStore);
            } catch (NumberFormatException e) {
                Long byName = storeService.findStoreIdByName(rawStore, userId);
                if (byName != null) {
                    storeId = byName;
                } else {
                    log.warn("Магазин '{}' не найден, используем дефолтный", rawStore);
                }
            }
        }

        if (storeId != null && !storeService.userOwnsStore(storeId, userId)) {
            log.warn("Магазин ID={} не принадлежит пользователю ID={}", storeId, userId);
            storeId = defaultStoreId;
        }
        return storeId;
    }

    /**
     * Нормализует телефонный номер получателя.
     * В случае ошибки возвращает {@code null} и пишет предупреждение в лог.
     */
    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        try {
            return PhoneUtils.normalizePhone(phone);
        } catch (Exception e) {
            log.warn("Некорректный телефон '{}' пропущен", PhoneUtils.maskPhone(phone));
            return null;
        }
    }

    /**
     * Применяет лимит сохранения новых треков.
     * Возвращает итоговый список метаданных и заполняет список пропущенных номеров.
     */
    private List<TrackMeta> applySaveLimit(List<TempMeta> metaList, int saveSlots, List<String> skipped) {
        List<TrackMeta> result = new ArrayList<>();
        int savedNew = 0;

        for (TempMeta meta : metaList) {
            boolean canSave = true;

            if (meta.isNew()) {
                if (savedNew < saveSlots) {
                    savedNew++;
                } else {
                    canSave = false;
                    skipped.add(meta.number());
                }
            }

            PostalServiceType type = typeDefinitionTrackPostService.detectPostalService(meta.number());
            result.add(new TrackMeta(meta.number(), meta.storeId(), meta.phone(), canSave, type));
        }

        return result;
    }

}