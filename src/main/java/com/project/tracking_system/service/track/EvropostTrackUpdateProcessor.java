package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.service.track.TrackConstants;
import com.project.tracking_system.service.track.TrackProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Процессор обновления треков для службы Европочты.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvropostTrackUpdateProcessor implements TrackUpdateProcessor {

    /**
     * Сервис низкого уровня, выполняющий обработку и сохранение треков.
     */
    private final TrackProcessingService trackProcessingService;

    /**
     * Асинхронный исполнитель для отправки запросов к сервису Европочты.
     */
    private final TaskExecutor batchUploadExecutor;

    /**
     * Возвращает тип почтовой службы, поддерживаемой данным процессором.
     */
    @Override
    public PostalServiceType supportedType() {
        return PostalServiceType.EVROPOST;
    }

    /**
     * Обрабатывает список треков асинхронно, используя европейский сервис.
     *
     * @param tracks список треков
     * @param userId идентификатор пользователя, инициировавшего обработку
     * @return список результатов обработки
     */
    @Override
    public List<TrackingResultAdd> process(List<TrackMeta> tracks, Long userId) {
        List<TrackingResultAdd> results = new ArrayList<>();
        if (tracks == null || tracks.isEmpty()) {
            return results;
        }
        List<CompletableFuture<TrackingResultAdd>> futures = tracks.stream()
                .map(meta -> CompletableFuture.supplyAsync(() -> {
                    TrackInfoListDTO info = trackProcessingService.processTrack(
                            meta.number(), meta.storeId(), userId, meta.canSave(), meta.phone());
                    boolean hasStatus = !info.getList().isEmpty();
                    // Информируем о результате обработки без персональных данных
                    log.debug(hasStatus ? "Статусы получены" : "Статусы отсутствуют");
                    String status = hasStatus
                            ? info.getList().get(0).getInfoTrack()
                            : TrackConstants.NO_DATA_STATUS;
                    return new TrackingResultAdd(meta.number(), status);
                }, batchUploadExecutor))
                .toList();
        futures.forEach(f -> results.add(f.join()));
        return results;
    }

    /**
     * Обрабатывает один трек синхронно.
     *
     * @param meta метаданные трек-номера
     * @return результат обработки
     */
    @Override
    public TrackingResultAdd process(TrackMeta meta) {
        if (meta == null) {
            return new TrackingResultAdd(null, TrackConstants.NO_DATA_STATUS, new TrackInfoListDTO());
        }
        TrackInfoListDTO info = trackProcessingService.processTrack(
                meta.number(), meta.storeId(), null, meta.canSave(), meta.phone());
        boolean hasStatus = !info.getList().isEmpty();
        // Информируем о результате обработки без персональных данных
        log.debug(hasStatus ? "Статусы получены" : "Статусы отсутствуют");
        String status = hasStatus
                ? info.getList().get(0).getInfoTrack()
                : TrackConstants.NO_DATA_STATUS;
        return new TrackingResultAdd(meta.number(), status, info);
    }

}