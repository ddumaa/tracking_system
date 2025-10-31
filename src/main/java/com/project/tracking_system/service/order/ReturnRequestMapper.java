package com.project.tracking_system.service.order;

import com.project.tracking_system.dto.AvailableActionsDto;
import com.project.tracking_system.dto.RequestDto;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.ReturnRequestAction;
import com.project.tracking_system.entity.ReturnRequestMode;
import com.project.tracking_system.entity.ReturnRequestStage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Маппер доменной заявки на возврат в DTO для API.
 * <p>
 * Компонент инкапсулирует расчёт доступных действий и передачу идентификаторов,
 * чтобы контроллер и сервисы отображения оставались тонкими и следовали SRP.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class ReturnRequestMapper {

    private final OrderReturnRequestService orderReturnRequestService;

    /**
     * Преобразует заявку в минималистичный DTO карточки возврата.
     * Метод возвращает только идентификаторы и текущие технические поля,
     * необходимые клиентам для дальнейших запросов (SRP: форматирование витринных
     * данных вынесено в другие компоненты).
     *
     * @param request исходная заявка
     * @param userZone временная зона пользователя (не используется, сохранена для обратной совместимости вызовов)
     * @return заполненный DTO или {@code null}, если заявка отсутствует
     */
    public RequestDto toDto(OrderReturnRequest request, ZoneId userZone) {
        if (request == null) {
            return null;
        }
        ReturnRequestMode mode = Optional.ofNullable(request.getMode()).orElse(ReturnRequestMode.RETURN);
        ReturnRequestStage stage = Optional.ofNullable(request.getStage()).orElse(ReturnRequestStage.NEW);

        Long storeId = Optional.ofNullable(request.getStore())
                .map(store -> store.getId())
                .orElse(null);
        Long orderId = Optional.ofNullable(request.getEpisode())
                .map(episode -> episode.getId())
                .orElse(null);
        Long parcelId = Optional.ofNullable(request.getParcel())
                .map(parcel -> parcel.getId())
                .orElse(null);
        Long userId = Optional.ofNullable(request.getCreatedBy())
                .map(user -> user.getId())
                .orElse(null);
        Long responsibleId = Optional.ofNullable(request.getResponsibleManager())
                .map(manager -> manager.getId())
                .orElse(null);

        return new RequestDto(
                request.getId(),
                mode.name(),
                stage.getCode(),
                storeId,
                orderId,
                parcelId,
                userId,
                responsibleId
        );
    }

    /**
     * Рассчитывает доступные действия по заявке и возвращает их DTO.
     *
     * @param request заявка на возврат/обмен
     * @return список доступных действий
     */
    public List<AvailableActionsDto> toAvailableActions(OrderReturnRequest request) {
        if (request == null) {
            return List.of();
        }
        EnumSet<ReturnRequestAction> actions = orderReturnRequestService.resolveAvailableActions(request);
        return buildAvailableActions(request, actions);
    }

    /**
     * Собирает DTO доступных действий с учётом ограничений.
     */
    private List<AvailableActionsDto> buildAvailableActions(OrderReturnRequest request,
                                                            EnumSet<ReturnRequestAction> active) {
        List<AvailableActionsDto> result = new ArrayList<>();
        String cancelReason = orderReturnRequestService
                .getExchangeCancellationBlockReason(request)
                .orElse(null);
        for (ReturnRequestAction action : ReturnRequestAction.values()) {
            boolean enabled = active.contains(action);
            String reason = null;
            if (!enabled && action == ReturnRequestAction.CANCEL_EXCHANGE) {
                reason = cancelReason;
            }
            result.add(new AvailableActionsDto(
                    action.getCode(),
                    action.getDisplayName(),
                    enabled,
                    reason
            ));
        }
        return result;
    }
}
