package com.project.tracking_system.service;

import com.project.tracking_system.entity.ScheduledTaskConfig;
import com.project.tracking_system.repository.ScheduledTaskConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.mockito.Mockito;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link DynamicSchedulerService}.
 * Проверяем корректную работу планировщика при инициализации и изменении cron.
 */
@ExtendWith(MockitoExtension.class)
class DynamicSchedulerServiceTest {

    @Mock
    private ScheduledTaskConfigRepository repository;
    @Mock
    private TaskScheduler taskScheduler;

    private DynamicSchedulerService service;

    @BeforeEach
    void setUp() {
        service = new DynamicSchedulerService(repository, taskScheduler);
    }

    /**
     * Проверяем, что при наличии конфигураций и зарегистрированных задач
     * метод {@link DynamicSchedulerService#init()} запланирует их с учётом таймзоны.
     */
    @Test
    void init_SchedulesRegisteredTasksWithZones() {
        ScheduledTaskConfig cfg1 = new ScheduledTaskConfig();
        cfg1.setId(1L);
        cfg1.setCron("0 0 * * * *");
        cfg1.setZone("Europe/Moscow");

        ScheduledTaskConfig cfg2 = new ScheduledTaskConfig();
        cfg2.setId(2L);
        cfg2.setCron("0 5 * * * *");
        cfg2.setZone(""); // должна примениться зона UTC

        when(repository.findById(anyLong())).thenReturn(Optional.empty());
        Runnable task1 = mock(Runnable.class);
        Runnable task2 = mock(Runnable.class);
        service.registerTask(1L, task1);
        service.registerTask(2L, task2);

        when(repository.findAll()).thenReturn(List.of(cfg1, cfg2));
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        Mockito.<ScheduledFuture<?>>when(taskScheduler.schedule(any(Runnable.class), any(CronTrigger.class))).thenReturn(future);

        service.init();

        ArgumentCaptor<CronTrigger> triggerCaptor = ArgumentCaptor.forClass(CronTrigger.class);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), triggerCaptor.capture());

        List<CronTrigger> triggers = triggerCaptor.getAllValues();
        // Сравниваем с ожидаемыми триггерами: equals учитывает и cron, и используемый ZoneId
        assertTrue(triggers.contains(new CronTrigger("0 0 * * * *", ZoneId.of("Europe/Moscow"))));
        // Для пустой зоны конфигурации используется UTC, это также проверяем через equals
        assertTrue(triggers.contains(new CronTrigger("0 5 * * * *", ZoneOffset.UTC)));
    }

    /**
     * Проверяем, что при обновлении cron выражение сохраняется и задача пересоздаётся.
     */
    @Test
    void updateCron_PersistsAndReschedulesTask() {
        ScheduledTaskConfig cfg = new ScheduledTaskConfig();
        cfg.setId(1L);
        cfg.setCron("0 0 * * * *");
        cfg.setZone("UTC");

        // при регистрации конфигурация отсутствует
        when(repository.findById(1L)).thenReturn(Optional.empty(), Optional.of(cfg));
        Runnable task = mock(Runnable.class);
        service.registerTask(1L, task);

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        Mockito.<ScheduledFuture<?>>when(taskScheduler.schedule(eq(task), any(CronTrigger.class))).thenReturn(future);

        service.updateCron(1L, "0 10 * * * *");

        assertEquals("0 10 * * * *", cfg.getCron());
        verify(repository).save(cfg);

        ArgumentCaptor<CronTrigger> triggerCaptor = ArgumentCaptor.forClass(CronTrigger.class);
        verify(taskScheduler).schedule(eq(task), triggerCaptor.capture());
        assertEquals("0 10 * * * *", triggerCaptor.getValue().getExpression());
    }

    /**
     * Проверяем, что обновление конфигурации без зарегистрированной задачи не приводит к планированию.
     */
    @Test
    void updateCron_NoRegisteredTask_DoesNotSchedule() {
        ScheduledTaskConfig cfg = new ScheduledTaskConfig();
        cfg.setId(1L);
        cfg.setCron("0 0 * * * *");

        when(repository.findById(1L)).thenReturn(Optional.of(cfg));

        service.updateCron(1L, "0 5 * * * *");

        verify(taskScheduler, never()).schedule(any(), any(CronTrigger.class));
        verify(repository).save(cfg);
        assertEquals("0 5 * * * *", cfg.getCron());
    }

    /**
     * Проверяем обработку случая, когда конфигурация отсутствует в хранилище.
     */
    @Test
    void updateCron_ConfigMissing_Throws() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.updateCron(99L, "* * * * * *"));
        verifyNoInteractions(taskScheduler);
    }
}
