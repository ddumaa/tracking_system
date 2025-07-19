package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.service.belpost.WebBelPostBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Процессор обновления треков для службы Белпочты.
 */
@Service
@RequiredArgsConstructor
public class BelpostTrackUpdateProcessor implements TrackUpdateProcessor {

    private final TrackFacade trackFacade;
    private final WebBelPostBatchService webBelPostBatchService;

    @Override
    public PostalServiceType supportedType() {
        return PostalServiceType.BELPOST;
    }

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
            if (userId != null && meta.canSave()) {
                trackFacade.saveTrackInfo(meta.number(), info, meta.storeId(), userId, meta.phone());
            }
            String status = info.getList().isEmpty()
                    ? TrackConstants.NO_DATA_STATUS
                    : info.getList().get(0).getInfoTrack();
            results.add(new TrackingResultAdd(meta.number(), status));
        }
        return results;
    }
}
