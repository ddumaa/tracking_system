package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.service.belpost.WebBelPostBatchService;
import com.project.tracking_system.service.track.TrackConstants;
import com.project.tracking_system.service.track.TrackProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Процессор обновления треков для службы Белпочты.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BelpostTrackUpdateProcessor implements TrackUpdateProcessor {

    /**
     * Сервис низкого уровня, отвечающий за обработку и сохранение треков.
     */
    private final TrackProcessingService trackProcessingService;

    /**
     * Клиент для групповой загрузки данных с сайта Белпочты.
     * <p>
     * Реализует парсинг веб-страницы через Selenium и ChromeDriver,
     * а не обращается к какому-либо официальному API.
     * </p>
     */
    private final WebBelPostBatchService webBelPostBatchService;

    /**
     * Возвращает тип почтового сервиса, который поддерживает данный процессор.
     */
    @Override
    public PostalServiceType supportedType() {
        return PostalServiceType.BELPOST;
    }

    /**
     * Обновляет список треков пользователя одной пачкой.
     *
     * @param tracks список трек-метаданных
     * @param userId идентификатор пользователя, которому принадлежат треки
     * @return список результатов обновления
     */
    @Override
    public List<TrackingResultAdd> process(List<TrackMeta> tracks, Long userId) {
        List<TrackingResultAdd> results = new ArrayList<>();
        if (tracks == null || tracks.isEmpty()) {
            return results;
        }
        Map<String, TrackInfoListDTO> infoMap = webBelPostBatchService.processBatch(
                tracks.stream().map(TrackMeta::number).toList());
        for (TrackMeta meta : tracks) {
            TrackInfoListDTO info = infoMap.getOrDefault(meta.number(), new TrackInfoListDTO());
            boolean hasStatus = !info.getList().isEmpty();
            if (userId != null && meta.canSave()) {
                trackProcessingService.save(meta.number(), info, meta.storeId(), userId, meta.phone());
            }
            // Информируем о результате обработки без персональных данных
            log.debug(hasStatus ? "Статусы получены" : "Статусы отсутствуют");
            String status = hasStatus
                    ? info.getList().get(0).getInfoTrack()
                    : TrackConstants.NO_DATA_STATUS;
            results.add(new TrackingResultAdd(meta.number(), status));
        }
        return results;
    }

    /**
     * Загружает и сохраняет информацию по одному треку.
     *
     * @param meta   метаданные трек-номера
     * @param userId идентификатор пользователя, от имени которого выполняется обновление
     * @return результат обработки
     */
    @Override
    public TrackingResultAdd process(TrackMeta meta, Long userId) {
        if (meta == null) {
            return new TrackingResultAdd(null, TrackConstants.NO_DATA_STATUS, new TrackInfoListDTO());
        }
        Map<String, TrackInfoListDTO> infoMap = webBelPostBatchService.processBatch(List.of(meta.number()));
        TrackInfoListDTO info = infoMap.getOrDefault(meta.number(), new TrackInfoListDTO());
        boolean hasStatus = !info.getList().isEmpty();
        if (meta.canSave()) {
            trackProcessingService.save(meta.number(), info, meta.storeId(), userId, meta.phone());
        }
        // Информируем о результате обработки без персональных данных
        log.debug(hasStatus ? "Статусы получены" : "Статусы отсутствуют");
        String status = hasStatus
                ? info.getList().get(0).getInfoTrack()
                : TrackConstants.NO_DATA_STATUS;
        return new TrackingResultAdd(meta.number(), status, info);
    }
}
