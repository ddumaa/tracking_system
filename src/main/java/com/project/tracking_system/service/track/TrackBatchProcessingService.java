package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.service.belpost.WebBelPostBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class TrackBatchProcessingService {

    private final TrackFacade trackFacade;
    private final WebBelPostBatchService webBelPostBatchService;

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

        Map<PostalServiceType, List<TrackMeta>> evroMap = Map.of(
                PostalServiceType.EVROPOST,
                tracksByService.getOrDefault(PostalServiceType.EVROPOST, List.of()));
        Map<PostalServiceType, List<TrackMeta>> belMap = Map.of(
                PostalServiceType.BELPOST,
                tracksByService.getOrDefault(PostalServiceType.BELPOST, List.of()));

        results.addAll(processEvropost(evroMap, userId));
        results.addAll(processBelpost(belMap, userId));
        return results;
    }

    /**
     * Обработка треков Европочты.
     * <p>
     * Использует {@link CompletableFuture} для параллельных запросов,
     * что повышает скорость при большом количестве номеров.
     * </p>
     */
    private List<TrackingResultAdd> processEvropost(Map<PostalServiceType, List<TrackMeta>> tracksByService,
                                                    Long userId) {
        List<TrackingResultAdd> results = new ArrayList<>();
        List<TrackMeta> evroTracks = tracksByService.getOrDefault(PostalServiceType.EVROPOST, List.of());
        if (evroTracks.isEmpty()) {
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
                }))
                .toList();

        futures.forEach(f -> results.add(f.join()));
        return results;
    }

    /**
     * Обработка треков Белпочты пакетным запросом.
     * <p>
     * Для каждого номера выполняется сохранение данных при наличии пользователя
     * и разрешении {@link TrackMeta#canSave()}.
     * </p>
     */
    private List<TrackingResultAdd> processBelpost(Map<PostalServiceType, List<TrackMeta>> tracksByService,
                                                   Long userId) {
        List<TrackingResultAdd> results = new ArrayList<>();
        List<TrackMeta> belTracks = tracksByService.getOrDefault(PostalServiceType.BELPOST, List.of());
        if (belTracks.isEmpty()) {
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
