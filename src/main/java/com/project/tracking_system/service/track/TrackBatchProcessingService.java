package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.service.belpost.WebBelPostBatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Пакетная обработка треков после загрузки файла.
 * <p>
 * Сервис принимает подготовленные {@link TrackMeta} и выполняет запросы
 * к соответствующим почтовым службам, а при необходимости сохраняет
 * полученные данные через {@link TrackFacade}.
 * </p>
 */
@Slf4j
@Service
public class TrackBatchProcessingService {

    private final TrackFacade trackFacade;
    private final WebBelPostBatchService webBelPostBatchService;
    private final TaskExecutor batchUploadExecutor;

    /**
     * Создает сервис пакетной обработки треков.
     *
     * @param trackFacade           фасад для взаимодействия с почтовыми сервисами
     * @param webBelPostBatchService сервис обработки Белпочты
     * @param batchUploadExecutor   пул потоков для параллельной загрузки
     */
    public TrackBatchProcessingService(TrackFacade trackFacade,
                                       WebBelPostBatchService webBelPostBatchService,
                                       @Qualifier("batchUploadExecutor") TaskExecutor batchUploadExecutor) {
        this.trackFacade = trackFacade;
        this.webBelPostBatchService = webBelPostBatchService;
        this.batchUploadExecutor = batchUploadExecutor;
    }

    /**
     * Обрабатывает сгруппированные трек-номера.
     * <p>
     * Для треков Европочты запросы выполняются параллельно с использованием
     * {@link CompletableFuture}, что ускоряет обработку большого количества
     * номеров. Результаты собираются в общем списке аналогично последовательной
     * реализации.
     * </p>
     *
     * @param tracksByService карты, где ключ — тип почтовой службы,
     *                        значение — список метаданных треков
     * @param userId          идентификатор пользователя
     * @return список результатов обработки
     */
    public List<TrackingResultAdd> processBatch(Map<PostalServiceType, List<TrackMeta>> tracksByService,
                                                Long userId) {
        List<TrackingResultAdd> results = new ArrayList<>();
        if (tracksByService == null || tracksByService.isEmpty()) {
            return results;
        }

        List<TrackMeta> evroTracks =
                tracksByService.getOrDefault(PostalServiceType.EVROPOST, List.of());
        List<TrackMeta> belTracks =
                tracksByService.getOrDefault(PostalServiceType.BELPOST, List.of());

        results.addAll(processEvropostTracks(evroTracks, userId));
        results.addAll(processBelpostTracks(belTracks, userId));
        return results;
    }

    /**
     * Обработка треков Европочты.
     * <p>
     * Запросы выполняются параллельно через {@link CompletableFuture}
     * с использованием {@code batchUploadExecutor}, что ускоряет
     * обработку большого количества номеров.
     * </p>
     */
    /**
     * Обрабатывает треки Европочты.
     * <p>
     * Запросы выполняются параллельно с использованием {@link CompletableFuture}
     * и {@code batchUploadExecutor}, что ускоряет обработку большого количества номеров.
     * </p>
     *
     * @param evroTracks список треков Европочты
     * @param userId     идентификатор пользователя
     * @return список результатов обработки
     */
    private List<TrackingResultAdd> processEvropostTracks(List<TrackMeta> evroTracks,
                                                          Long userId) {
        List<TrackingResultAdd> results = new ArrayList<>();
        if (evroTracks == null || evroTracks.isEmpty()) {
            return results;
        }

        List<CompletableFuture<TrackingResultAdd>> futures = evroTracks.stream()
                .map(meta -> CompletableFuture.supplyAsync(() -> {
                    TrackInfoListDTO info = trackFacade.processTrack(
                            meta.number(), meta.storeId(), userId, meta.canSave(), meta.phone());
                    String status = info.getList().isEmpty()
                            ? "Нет данных"
                            : info.getList().get(0).getInfoTrack();
                    return new TrackingResultAdd(meta.number(), status);
                }, batchUploadExecutor))
                .toList();

        futures.forEach(f -> results.add(f.join()));
        return results;
    }

    /**
     * Пакетная обработка треков Белпочты.
     * <p>
     * Для каждого номера выполняется сохранение данных при наличии пользователя
     * и разрешении {@link TrackMeta#canSave()}.
     * </p>
     *
     * @param belTracks список треков Белпочты
     * @param userId    идентификатор пользователя
     * @return список результатов обработки
     */
    private List<TrackingResultAdd> processBelpostTracks(List<TrackMeta> belTracks,
                                                         Long userId) {
        List<TrackingResultAdd> results = new ArrayList<>();
        if (belTracks == null || belTracks.isEmpty()) {
            return results;
        }

        Map<String, TrackInfoListDTO> infoMap = webBelPostBatchService.processBatch(
                belTracks.stream().map(TrackMeta::number).toList());
        for (TrackMeta meta : belTracks) {
            TrackInfoListDTO info = infoMap.getOrDefault(meta.number(), new TrackInfoListDTO());
            if (userId != null && meta.canSave()) {
                trackFacade.saveTrackInfo(meta.number(), info, meta.storeId(), userId, meta.phone());
            }
            String status = info.getList().isEmpty()
                    ? "Нет данных"
                    : info.getList().get(0).getInfoTrack();
            results.add(new TrackingResultAdd(meta.number(), status));
        }

        return results;
    }

}