package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.TrackNumberAudit;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.repository.TrackNumberAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Сервис записи аудита изменений трек-номеров.
 * <p>
 * Выделен в отдельный компонент, чтобы {@link TrackParcelService}
 * не зависел напрямую от деталей хранения аудита (принцип SRP/DIP).
 * </p>
 */
@Service
@RequiredArgsConstructor
public class TrackNumberAuditService {

    private final TrackNumberAuditRepository trackNumberAuditRepository;

    /**
     * Сохраняет запись об изменении трек-номера.
     *
     * @param parcel    посылка, для которой выполнено изменение
     * @param oldNumber прежнее значение трек-номера (может быть {@code null})
     * @param newNumber новое значение трек-номера
     * @param userId    идентификатор пользователя, выполнившего действие
     */
    @Transactional
    public void recordChange(TrackParcel parcel, String oldNumber, String newNumber, Long userId) {
        TrackNumberAudit audit = new TrackNumberAudit();
        audit.setTrackParcel(parcel);
        audit.setOldNumber(oldNumber);
        audit.setNewNumber(newNumber);
        audit.setChangedBy(userId);
        audit.setChangedAt(ZonedDateTime.now(ZoneOffset.UTC));
        trackNumberAuditRepository.save(audit);
    }
}
