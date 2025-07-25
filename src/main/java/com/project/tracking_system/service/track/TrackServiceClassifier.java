package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.utils.TrackNumberUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Определяет почтовую службу для каждого трек-номера и
 * группирует метаданные треков по типу почтовой службы.
 * <p>
 * Треки, отнесённые к {@link PostalServiceType#UNKNOWN},
 * не включаются в результирующее отображение, что исключает
 * дальнейшую обработку неподдерживаемых номеров.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class TrackServiceClassifier {

    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;

    /**
     * Классифицирует список треков по почтовым службам.
     *
     * @param tracks список валидированных трек-метаданных
     * @return карта «служба → список треков» без UNKNOWN
     */
    public Map<PostalServiceType, List<TrackMeta>> classify(List<TrackMeta> tracks) {
        Map<PostalServiceType, List<TrackMeta>> grouped = new EnumMap<>(PostalServiceType.class);
        for (TrackMeta meta : tracks) {
            PostalServiceType type = detect(meta.number());
            if (type == PostalServiceType.UNKNOWN) {
                // Номера с неопределённым форматом пропускаем
                continue;
            }
            grouped.computeIfAbsent(type, k -> new ArrayList<>()).add(meta);
        }
        return grouped;
    }

    /**
     * Detects postal service type for a single track number.
     *
     * @param number track number
     * @return detected postal service type
     */
    public PostalServiceType detect(String number) {
        String normalized = TrackNumberUtils.normalize(number);
        return typeDefinitionTrackPostService.detectPostalService(normalized);
    }
}
