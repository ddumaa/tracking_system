package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.TrackStatusEvent;
import com.project.tracking_system.repository.TrackStatusEventRepository;
import com.project.tracking_system.utils.DateParserUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис управления событиями истории статусов.
 * <p>
 * Выделен отдельно от {@link TrackProcessingService}, чтобы изолировать
 * ответственность за работу с сущностью {@link TrackStatusEvent} и
 * упростить повторное использование логики чтения/записи.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackStatusEventService {

    private final TrackStatusEventRepository trackStatusEventRepository;

    /**
     * Полностью заменяет список событий для посылки.
     * <p>
     * Метод сначала удаляет предыдущие записи, затем сохраняет новые в порядке,
     * возвращённом почтовой службой. Ошибочные даты пропускаются, чтобы не
     * прерывать обновление остальных событий.
     * </p>
     *
     * @param parcel    посылка, к которой относятся события
     * @param events    свежий список событий из API
     * @param userZone  часовой пояс пользователя для корректного парсинга дат
     */
    @Transactional
    public void replaceEvents(TrackParcel parcel, List<TrackInfoDTO> events, ZoneId userZone) {
        trackStatusEventRepository.deleteByTrackParcelId(parcel.getId());
        if (events == null || events.isEmpty()) {
            return;
        }

        List<TrackStatusEvent> toSave = new ArrayList<>(events.size());
        for (TrackInfoDTO event : events) {
            if (event == null) {
                continue;
            }
            String rawDate = event.getTimex();
            String description = event.getInfoTrack();
            if (rawDate == null || description == null) {
                continue;
            }
            try {
                ZonedDateTime moment = DateParserUtils.parse(rawDate, userZone);
                TrackStatusEvent statusEvent = new TrackStatusEvent();
                statusEvent.setTrackParcel(parcel);
                statusEvent.setEventTime(moment);
                statusEvent.setDescription(description);
                toSave.add(statusEvent);
            } catch (DateTimeParseException ex) {
                log.warn("Не удалось распарсить дату события '{}' для трека {}", rawDate, parcel.getNumber(), ex);
            }
        }

        if (!toSave.isEmpty()) {
            trackStatusEventRepository.saveAll(toSave);
        }
    }

    /**
     * Возвращает события по идентификатору посылки.
     *
     * @param trackId идентификатор посылки
     * @return события в обратном хронологическом порядке
     */
    @Transactional(readOnly = true)
    public List<TrackStatusEvent> findEvents(Long trackId) {
        return trackStatusEventRepository.findByTrackParcelIdOrderByEventTimeDesc(trackId);
    }
}
