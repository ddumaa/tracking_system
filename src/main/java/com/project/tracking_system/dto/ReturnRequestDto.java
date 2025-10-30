package com.project.tracking_system.dto;

/**
 * DTO заявки на возврат/обмен для отображения в карточке посылки.
 *
 * @param id                 идентификатор заявки
 * @param status             человеко-читаемый статус
 * @param reason             причина оформления возврата
 * @param comment            дополнительный комментарий пользователя
 * @param reverseTrackNumber трек обратной отправки, если указан
 * @param requiresAction     признак, что заявка ожидает действий
 * @param state              агрегированное состояние заявки (режим, этап, флаги)
 * @param availableActions   доступные действия для пользователя
 * @param timestamps         набор временных меток жизненного цикла
 * @param storeId            идентификатор магазина, обработавшего заявку
 * @param responsibleId      идентификатор ответственного менеджера
 */
public record ReturnRequestDto(Long id,
                               String status,
                               String reason,
                               String comment,
                               String reverseTrackNumber,
                               boolean requiresAction,
                               ReturnRequestStateDto state,
                               ReturnRequestAvailableActionsDto availableActions,
                               ReturnRequestTimestampsDto timestamps,
                               Long storeId,
                               Long responsibleId) {

    /**
     * Совместимый с фронтендом аксессор, чтобы не ломать проверку {@code isExchangeRequest}.
     *
     * @return {@code true}, если заявка оформлена как обмен
     */
    public boolean isExchangeRequest() {
        return state != null && state.exchangeRequested();
    }
}
