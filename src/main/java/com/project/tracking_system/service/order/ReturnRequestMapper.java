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
import java.time.ZonedDateTime;
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
     * Преобразует заявку в DTO карточки возврата согласно JSON Schema.
     * Метод дополняет идентификаторы сведениями о причинах, комментариях и треках,
     * сохраняя ответственность маппера за конвертацию доменной модели в формат API
     * (принцип единой ответственности).
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

        String publicId = resolvePublicId(request);
        boolean manualInboundPick = resolveManualInboundPickFlag(request, stage);
        ZonedDateTime createdAt = request.getCreatedAt();
        ZonedDateTime updatedAt = resolveUpdatedAt(request, createdAt);

        return new RequestDto(
                publicId,
                mode.name(),
                stage.getCode(),
                storeId,
                orderId,
                parcelId,
                userId,
                responsibleId,
                request.getReason(),
                request.getRequestedAt(),
                request.getComment(),
                request.getReverseTrackNumber(),
                request.getExchangeTrackNumber(),
                manualInboundPick,
                createdAt,
                updatedAt
        );
    }

    /**
     * Возвращает публичный идентификатор заявки.
     * <p>
     * Если идемпотентный ключ отсутствует (старые записи), используется
     * числовой идентификатор, чтобы не терять совместимость.
     * </p>
     */
    private String resolvePublicId(OrderReturnRequest request) {
        return Optional.ofNullable(request.getIdempotencyKey())
                .filter(key -> !key.isBlank())
                .orElseGet(() -> Optional.ofNullable(request.getId())
                        .map(String::valueOf)
                        .orElse(null));
    }

    /**
     * Определяет флаг ручного подтверждения получения возврата.
     * <p>
     * Флаг активен только для стадии INBOUND_PICKED_UP и когда переход выполнен вручную,
     * что позволяет фронтенду отличить автоматические обновления от действий оператора.
     * </p>
     */
    private boolean resolveManualInboundPickFlag(OrderReturnRequest request, ReturnRequestStage stage) {
        return request.isManualStageOverride() && stage == ReturnRequestStage.INBOUND_PICKED_UP;
    }

    /**
     * Рассчитывает момент последнего изменения заявки с безопасным запасом.
     */
    private ZonedDateTime resolveUpdatedAt(OrderReturnRequest request, ZonedDateTime createdAt) {
        return Optional.ofNullable(request.getStageUpdatedAt())
                .orElseGet(() -> Optional.ofNullable(request.getStageStartedAt())
                        .orElse(createdAt));
    }

    /**
     * Рассчитывает доступные действия по заявке и возвращает их коды в DTO.
     *
     * @param request заявка на возврат/обмен
     * @return DTO со списком кодов доступных действий
     */
    public AvailableActionsDto toAvailableActions(OrderReturnRequest request) {
        if (request == null) {
            return new AvailableActionsDto(List.of());
        }
        EnumSet<ReturnRequestAction> actions = orderReturnRequestService.resolveAvailableActions(request);
        List<String> actionCodes = actions.stream()
                .map(ReturnRequestAction::getCode)
                .toList();
        return new AvailableActionsDto(actionCodes);
    }
}
