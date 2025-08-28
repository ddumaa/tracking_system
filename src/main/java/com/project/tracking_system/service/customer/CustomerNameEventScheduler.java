package com.project.tracking_system.service.customer;

import com.project.tracking_system.entity.CustomerNameEvent;
import com.project.tracking_system.entity.CustomerNameEventStatus;
import com.project.tracking_system.repository.CustomerNameEventRepository;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Планировщик, переводящий просроченные события в статус EXPIRED.
 */
@Service
@RequiredArgsConstructor
public class CustomerNameEventScheduler {

    private final CustomerNameEventRepository repository;

    /** Сколько часов ожидать решения покупателя. */
    @Value("${customer.name-event.expire-hours:24}")
    private long expireHours;

    /**
     * Найти и обновить просроченные события, по которым нет решения покупателя.
     */
    @Scheduled(fixedDelayString = "${customer.name-event.check-delay-ms:60000}")
    @Transactional
    public void expirePending() {
        ZonedDateTime threshold = ZonedDateTime.now(ZoneOffset.UTC).minusHours(expireHours);
        List<CustomerNameEvent> events = repository
                .findByStatusAndCreatedAtBefore(CustomerNameEventStatus.PENDING, threshold);
        for (CustomerNameEvent event : events) {
            event.setStatus(CustomerNameEventStatus.EXPIRED);
        }
        repository.saveAll(events);
    }
}
