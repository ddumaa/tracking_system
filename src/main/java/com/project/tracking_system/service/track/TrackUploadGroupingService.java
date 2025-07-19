package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.PostalServiceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Группировка валидированных треков по типу почтовой службы.
 */
@Service
@RequiredArgsConstructor
public class TrackUploadGroupingService {

    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;

    /**
     * Раскладывает треки по типам почтовых служб.
     *
     * @param tracks валидированные трек-метаданные
     * @return отображение "служба → список треков"
     */
    public Map<PostalServiceType, List<TrackMeta>> group(List<TrackMeta> tracks) {
        Map<PostalServiceType, List<TrackMeta>> grouped = new EnumMap<>(PostalServiceType.class);
        for (TrackMeta meta : tracks) {
            PostalServiceType type = typeDefinitionTrackPostService.detectPostalService(meta.number());
            grouped.computeIfAbsent(type, k -> new ArrayList<>()).add(meta);
        }
        return grouped;
    }
}
