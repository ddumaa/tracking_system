package com.project.tracking_system.service;

import com.project.tracking_system.entity.ScheduledTaskConfig;
import com.project.tracking_system.repository.ScheduledTaskConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZoneOffset;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Сервис динамического планирования задач.
 * <p>
 * Позволяет регистрировать задачи в рантайме и изменять их расписание
 * без перезапуска приложения.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicSchedulerService {

    private final ScheduledTaskConfigRepository repository;
    private final TaskScheduler taskScheduler;

    private final Map<Long, Runnable> tasks = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    /**
     * Регистрирует задачи, имеющиеся в базе данных, после старта приложения.
     */
    @PostConstruct
    public void init() {
        for (ScheduledTaskConfig cfg : repository.findAll()) {
            schedule(cfg);
        }
    }

    /**
     * Регистрирует новую задачу.
     *
     * @param id   идентификатор конфигурации
     * @param task исполняемый код
     */
    public void registerTask(Long id, Runnable task) {
        tasks.put(id, task);
        repository.findById(id).ifPresent(this::schedule);
    }

    /**
     * Получить список всех конфигураций задач.
     *
     * @return список конфигураций
     */
    public List<ScheduledTaskConfig> getAllConfigs() {
        return repository.findAll();
    }

    /**
     * Обновить cron-выражение и пересоздать задачу.
     *
     * @param id   идентификатор задачи
     * @param cron новое cron-выражение
     */
    @Transactional
    public void updateCron(Long id, String cron) {
        ScheduledTaskConfig cfg = repository.findById(id)
                .orElseThrow();
        cfg.setCron(cron);
        // При отсутствии таймзоны проставляем значение по умолчанию
        if (cfg.getZone() == null || cfg.getZone().isBlank()) {
            cfg.setZone("UTC");
        }
        repository.save(cfg);
        reschedule(cfg);
    }

    private void schedule(ScheduledTaskConfig cfg) {
        Runnable r = tasks.get(cfg.getId());
        if (r == null) {
            return;
        }

        // Определяем зону по умолчанию UTC при отсутствии или пустом значении
        ZoneId zoneId = (cfg.getZone() == null || cfg.getZone().isBlank())
                ? ZoneOffset.UTC
                : ZoneId.of(cfg.getZone());

        CronTrigger trigger = new CronTrigger(cfg.getCron(), zoneId);
        futures.put(cfg.getId(), taskScheduler.schedule(r, trigger));
        log.info("Запланирована задача {} c cron {} в таймзоне {}",
                cfg.getDescription(), cfg.getCron(), zoneId);
    }

    private void reschedule(ScheduledTaskConfig cfg) {
        ScheduledFuture<?> future = futures.get(cfg.getId());
        if (future != null) {
            future.cancel(false);
        }
        schedule(cfg);
    }
}
