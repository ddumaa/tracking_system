package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.PostalServiceType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * Распределяет сгруппированные треки между специализированными процессорами.
 */
@Service
public class TrackUpdateDispatcherService {

    /**
     * Карта соответствия типа почтовой службы его процессору.
     */
    private final Map<PostalServiceType, TrackUpdateProcessor> processorMap;

    /**
     * Сервис определения почтовой службы по номеру трека.
     */
    private final TrackServiceClassifier classifier;

    /**
     * Создает сервис и инициализирует карту процессоров.
     *
     * @param processors список доступных процессоров
     */
    public TrackUpdateDispatcherService(List<TrackUpdateProcessor> processors,
                                        TrackServiceClassifier classifier) {
        this.processorMap = processors.stream()
                .collect(Collectors.toUnmodifiableMap(TrackUpdateProcessor::supportedType,
                        Function.identity()));
        this.classifier = classifier;
    }

    /**
     * Передает каждую группу треков своему процессору.
     *
     * @param grouped карта «служба → список треков»
     * @param userId  идентификатор пользователя
     * @return список собранных результатов обработки
     */
    public List<TrackingResultAdd> dispatch(Map<PostalServiceType, List<TrackMeta>> grouped, Long userId) {
        List<TrackingResultAdd> results = new ArrayList<>();
        if (grouped == null || grouped.isEmpty()) {
            return results;
        }
        for (Map.Entry<PostalServiceType, List<TrackMeta>> entry : grouped.entrySet()) {
            TrackUpdateProcessor processor = processorMap.get(entry.getKey());
            if (processor != null) {
                results.addAll(processor.process(entry.getValue(), userId));
            }
        }
        return results;
    }

    /**
     * Обрабатывает одиночный трек-номер.
     * Если тип почтовой службы не указан, определяется автоматически.
     *
     * @param meta метаданные трека
     * @return результат обработки
     */
    public TrackingResultAdd dispatch(TrackMeta meta) {
        if (meta == null) {
            return new TrackingResultAdd(null, TrackConstants.NO_DATA_STATUS, new TrackInfoListDTO());
        }
        PostalServiceType type = meta.postalServiceType();
        if (type == null) {
            type = classifier.detect(meta.number());
        }
        TrackUpdateProcessor processor = processorMap.get(type);
        if (processor == null) {
            return new TrackingResultAdd(meta.number(), TrackConstants.NO_DATA_STATUS, new TrackInfoListDTO(), "Unsupported service");
        }
        return processor.process(meta);
    }

}