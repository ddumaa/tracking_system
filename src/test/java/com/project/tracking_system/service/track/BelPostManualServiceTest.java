package com.project.tracking_system.service.track;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.TrackProcessingProgressDTO;
import com.project.tracking_system.service.belpost.BelPostTrackQueueService;
import com.project.tracking_system.service.belpost.QueuedTrack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Набор тестов для {@link BelPostManualService},
 * проверяющий регистрацию партий и начальное уведомление о прогрессе.
 */
@ExtendWith(MockitoExtension.class)
class BelPostManualServiceTest {

    @Mock
    private BelPostTrackQueueService belPostTrackQueueService;
    @Mock
    private TrackUpdateEligibilityService trackUpdateEligibilityService;
    @Mock
    private WebSocketController webSocketController;
    @Mock
    private BatchIdGenerator batchIdGenerator;

    private ProgressAggregatorService progressAggregatorService;
    private BelPostManualService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        progressAggregatorService = spy(new ProgressAggregatorService(webSocketController, clock, 0L));
        service = new BelPostManualService(
                belPostTrackQueueService,
                trackUpdateEligibilityService,
                webSocketController,
                batchIdGenerator,
                progressAggregatorService
        );
    }

    /**
     * Убеждаемся, что ручное добавление трека создаёт новую партию,
     * отправляет стартовый прогресс и ставит задачу в очередь.
     */
    @Test
    void enqueueIfAllowed_RegistersBatchAndSendsProgress() {
        long batchId = 42L;
        Long storeId = 11L;
        Long userId = 7L;
        String number = "RA123456789BY";

        when(trackUpdateEligibilityService.canUpdate(number, userId)).thenReturn(true);
        when(batchIdGenerator.nextId()).thenReturn(batchId);

        boolean result = service.enqueueIfAllowed(number, storeId, userId, null);

        assertTrue(result, "Трек должен быть поставлен в очередь");

        InOrder inOrder = inOrder(progressAggregatorService, belPostTrackQueueService);
        inOrder.verify(progressAggregatorService).registerBatch(batchId, 1, userId);
        ArgumentCaptor<QueuedTrack> queuedTrackCaptor = ArgumentCaptor.forClass(QueuedTrack.class);
        inOrder.verify(belPostTrackQueueService).enqueue(queuedTrackCaptor.capture());

        QueuedTrack queuedTrack = queuedTrackCaptor.getValue();
        assertEquals(number, queuedTrack.trackNumber(), "Номер трека должен нормализоваться без изменений");
        assertEquals(userId, queuedTrack.userId(), "В очередь должен передаваться корректный пользователь");
        assertEquals(storeId, queuedTrack.storeId(), "В очередь должен попадать магазин, из которого запрошен трек");
        assertEquals(batchId, queuedTrack.batchId(), "Очередь должна знать идентификатор новой партии");
        assertEquals(TrackSource.MANUAL, queuedTrack.source(), "Источник трека должен отражать ручное добавление");

        ArgumentCaptor<TrackProcessingProgressDTO> progressCaptor = ArgumentCaptor.forClass(TrackProcessingProgressDTO.class);
        verify(webSocketController).sendProgress(eq(userId), progressCaptor.capture());
        TrackProcessingProgressDTO progressDto = progressCaptor.getValue();
        assertEquals(batchId, progressDto.batchId(), "Прогресс отправляется по верному batchId");
        assertEquals(0, progressDto.processed(), "На старте обработанных треков быть не должно");
        assertEquals(1, progressDto.total(), "Сервис сообщает, что в партии один трек");

        TrackProcessingProgressDTO storedProgress = progressAggregatorService.getProgress(batchId);
        assertEquals(1, storedProgress.total(), "Партия зарегистрирована с ожидаемым объёмом");
        assertEquals(0, storedProgress.processed(), "Новые партии начинают с нулевого прогресса");
    }
}
