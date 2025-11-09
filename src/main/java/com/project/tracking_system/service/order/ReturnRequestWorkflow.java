package com.project.tracking_system.service.order;

import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.OrderReturnRequestStatus;
import com.project.tracking_system.entity.ReturnRequestAction;
import com.project.tracking_system.entity.ReturnRequestMode;
import com.project.tracking_system.entity.ReturnRequestStage;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.order.context.ReturnRequestActionContext;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Управляет допустимыми переходами стадий и вычислением действий для заявок возврата/обмена.
 * <p>
 * Класс концентрирует правила, чтобы сервисы могли переиспользовать единую таблицу переходов
 * и соблюсти принцип единой ответственности.
 * </p>
 */
@Component
public class ReturnRequestWorkflow {

    private final Map<ReturnRequestMode, Map<ReturnRequestStage, Set<ReturnRequestStage>>> transitionTable;
    private final Map<ReturnRequestMode, Map<ReturnRequestStage, EnumSet<ReturnRequestAction>>> actionMatrix;
    private final Map<OrderReturnRequestStatus, EnumSet<ReturnRequestAction>> statusActionMatrix;

    /**
     * Создаёт workflow с таблицей допустимых переходов.
     */
    public ReturnRequestWorkflow() {
        this.transitionTable = buildTransitionTable();
        this.actionMatrix = buildActionMatrix();
        this.statusActionMatrix = buildStatusActionMatrix();
    }

    /**
     * Переводит заявку на новую стадию, проверяя допустимость перехода.
     *
     * @param request          заявка, чья стадия изменяется
     * @param targetStage      стадия назначения
     * @param manualTransition признак ручного действия менеджера
     * @param actor            пользователь, выполнивший действие
     * @param moment           момент фиксации события (UTC)
     */
    public void transitionToStage(OrderReturnRequest request,
                                  ReturnRequestStage targetStage,
                                  boolean manualTransition,
                                  User actor,
                                  ZonedDateTime moment) {
        if (request == null || targetStage == null) {
            return;
        }
        ZonedDateTime effectiveMoment = moment != null ? moment : ZonedDateTime.now(ZoneOffset.UTC);
        ReturnRequestMode mode = safeMode(request.getMode());
        ReturnRequestStage currentStage = safeStage(request.getStage());
        ReturnRequestStage effectiveCurrent = adjustStageForMode(mode, currentStage);
        if (!canTransition(mode, effectiveCurrent, targetStage)) {
            throw new IllegalStateException(String.format(
                    "Недопустимый переход стадии %s→%s для режима %s",
                    effectiveCurrent, targetStage, mode
            ));
        }
        boolean stageChanged = !Objects.equals(request.getStage(), targetStage);
        if (stageChanged) {
            request.setStage(targetStage);
            request.setStageStartedAt(effectiveMoment);
        }
        request.setStageUpdatedAt(effectiveMoment);
        if (manualTransition) {
            request.setManualStageOverride(true);
        }
        request.snapshotHistory(manualTransition, actor, effectiveMoment);
    }

    /**
     * Определяет, допустим ли переход между стадиями в заданном режиме.
     *
     * @param mode        режим обработки заявки
     * @param fromStage   исходная стадия
     * @param targetStage целевая стадия
     * @return {@code true}, если переход разрешён
     */
    public boolean canTransition(ReturnRequestMode mode,
                                 ReturnRequestStage fromStage,
                                 ReturnRequestStage targetStage) {
        if (mode == null || fromStage == null || targetStage == null) {
            return false;
        }
        if (Objects.equals(fromStage, targetStage)) {
            return true;
        }
        Map<ReturnRequestStage, Set<ReturnRequestStage>> modeTransitions = transitionTable.get(mode);
        if (modeTransitions == null) {
            return false;
        }
        Set<ReturnRequestStage> allowedTargets = modeTransitions.get(fromStage);
        if (allowedTargets == null || allowedTargets.isEmpty()) {
            return allowedStages(mode).contains(targetStage);
        }
        return allowedTargets.contains(targetStage);
    }

    /**
     * Возвращает начальную стадию для режима.
     */
    public ReturnRequestStage initialStage(ReturnRequestMode mode) {
        return ReturnRequestStage.NEW;
    }

    /**
     * Нормализует стадию под целевой режим, чтобы переходы были валидны.
     *
     * @param mode   целевой режим заявки
     * @param stage  текущая стадия
     * @return стадия, допустимая для режима
     */
    public ReturnRequestStage adjustStageForMode(ReturnRequestMode mode, ReturnRequestStage stage) {
        ReturnRequestStage safeStage = stage != null ? stage : initialStage(mode);
        if (safeStage.supportsMode(mode)) {
            return safeStage;
        }
        if (mode == ReturnRequestMode.RETURN) {
            return ReturnRequestStage.INBOUND_PICKED_UP;
        }
        return ReturnRequestStage.EXCHANGE_REGISTERED;
    }

    /**
     * Вычисляет итоговый набор действий, доступных для заявки с учётом матрицы и контекста.
     * <p>
     * Метод не ограничивается стадией: он объединяет базовые переходы из {@link #actionMatrix}
     * и статусные команды из {@link #statusActionMatrix}, фильтруя их через политику доступности
     * {@link ReturnRequestActionContext}. Благодаря этому сервисы получают уже согласованный
     * перечень кнопок без повторной фильтрации (SRP).
     * </p>
     *
     * @param context контекст заявки и предрасчитанные ограничения
     * @return итоговое множество доступных действий
     */
    public EnumSet<ReturnRequestAction> resolveBaseActions(ReturnRequestActionContext context) {
        if (context == null) {
            return EnumSet.noneOf(ReturnRequestAction.class);
        }
        ReturnRequestMode mode = safeMode(context.mode());
        ReturnRequestStage stage = adjustStageForMode(mode, safeStage(context.stage()));
        OrderReturnRequestStatus status = context.status();

        EnumSet<ReturnRequestAction> resolved = EnumSet.noneOf(ReturnRequestAction.class);

        Map<ReturnRequestStage, EnumSet<ReturnRequestAction>> modeActions = actionMatrix.get(mode);
        if (modeActions != null) {
            EnumSet<ReturnRequestAction> stageActions = modeActions.get(stage);
            if (stageActions != null) {
                stageActions.stream()
                        .filter(context::allows)
                        .forEach(resolved::add);
            }
        }

        EnumSet<ReturnRequestAction> statusActions = statusActionMatrix.get(status);
        if (statusActions != null) {
            statusActions.stream()
                    .filter(context::allows)
                    .forEach(resolved::add);
        }

        return resolved;
    }

    private Map<ReturnRequestMode, Map<ReturnRequestStage, Set<ReturnRequestStage>>> buildTransitionTable() {
        Map<ReturnRequestMode, Map<ReturnRequestStage, Set<ReturnRequestStage>>> table = new EnumMap<>(ReturnRequestMode.class);

        Map<ReturnRequestStage, Set<ReturnRequestStage>> returnTransitions = new EnumMap<>(ReturnRequestStage.class);
        returnTransitions.put(ReturnRequestStage.NEW, EnumSet.of(
                ReturnRequestStage.OUTBOUND_SENT,
                ReturnRequestStage.INBOUND_ARRIVED,
                ReturnRequestStage.INBOUND_PICKED_UP
        ));
        returnTransitions.put(ReturnRequestStage.OUTBOUND_SENT, EnumSet.of(
                ReturnRequestStage.INBOUND_ARRIVED,
                ReturnRequestStage.INBOUND_PICKED_UP
        ));
        returnTransitions.put(ReturnRequestStage.INBOUND_ARRIVED, EnumSet.of(ReturnRequestStage.INBOUND_PICKED_UP));
        returnTransitions.put(ReturnRequestStage.INBOUND_PICKED_UP, EnumSet.of(ReturnRequestStage.INBOUND_PICKED_UP));
        table.put(ReturnRequestMode.RETURN, returnTransitions);

        Map<ReturnRequestStage, Set<ReturnRequestStage>> exchangeTransitions = new EnumMap<>(ReturnRequestStage.class);
        exchangeTransitions.put(ReturnRequestStage.NEW, EnumSet.of(
                ReturnRequestStage.OUTBOUND_SENT,
                ReturnRequestStage.INBOUND_ARRIVED,
                ReturnRequestStage.INBOUND_PICKED_UP,
                ReturnRequestStage.EXCHANGE_REGISTERED
        ));
        exchangeTransitions.put(ReturnRequestStage.OUTBOUND_SENT, EnumSet.of(
                ReturnRequestStage.INBOUND_ARRIVED,
                ReturnRequestStage.INBOUND_PICKED_UP,
                ReturnRequestStage.EXCHANGE_REGISTERED
        ));
        exchangeTransitions.put(ReturnRequestStage.INBOUND_ARRIVED, EnumSet.of(
                ReturnRequestStage.INBOUND_PICKED_UP,
                ReturnRequestStage.EXCHANGE_REGISTERED
        ));
        exchangeTransitions.put(ReturnRequestStage.INBOUND_PICKED_UP, EnumSet.of(ReturnRequestStage.EXCHANGE_REGISTERED));
        exchangeTransitions.put(ReturnRequestStage.EXCHANGE_REGISTERED, EnumSet.of(
                ReturnRequestStage.EXCHANGE_SENT,
                ReturnRequestStage.EXCHANGE_DELIVERED
        ));
        exchangeTransitions.put(ReturnRequestStage.EXCHANGE_SENT, EnumSet.of(ReturnRequestStage.EXCHANGE_DELIVERED));
        exchangeTransitions.put(ReturnRequestStage.EXCHANGE_DELIVERED, EnumSet.of(ReturnRequestStage.EXCHANGE_DELIVERED));
        table.put(ReturnRequestMode.EXCHANGE, exchangeTransitions);

        return table;
    }

    private Map<ReturnRequestMode, Map<ReturnRequestStage, EnumSet<ReturnRequestAction>>> buildActionMatrix() {
        Map<ReturnRequestMode, Map<ReturnRequestStage, EnumSet<ReturnRequestAction>>> matrix = new EnumMap<>(ReturnRequestMode.class);
        for (ReturnRequestMode mode : ReturnRequestMode.values()) {
            Map<ReturnRequestStage, EnumSet<ReturnRequestAction>> stageActions = new EnumMap<>(ReturnRequestStage.class);
            Map<ReturnRequestStage, Set<ReturnRequestStage>> modeTransitions = transitionTable.getOrDefault(mode, Collections.emptyMap());
            for (ReturnRequestStage stage : ReturnRequestStage.values()) {
                if (!stage.supportsMode(mode)) {
                    continue;
                }
                Set<ReturnRequestStage> targets = modeTransitions.getOrDefault(stage, Collections.emptySet());
                EnumSet<ReturnRequestAction> actions = EnumSet.noneOf(ReturnRequestAction.class);
                for (ReturnRequestStage target : targets) {
                    if (stage == target) {
                        continue;
                    }
                    EnumSet<ReturnRequestAction> mapped = mapTransitionToActions(mode, stage, target);
                    if (!mapped.isEmpty()) {
                        actions.addAll(mapped);
                    }
                }
                if (!actions.isEmpty()) {
                    stageActions.put(stage, actions);
                }
            }
            matrix.put(mode, stageActions);
        }
        return matrix;
    }

    private Map<OrderReturnRequestStatus, EnumSet<ReturnRequestAction>> buildStatusActionMatrix() {
        EnumMap<OrderReturnRequestStatus, EnumSet<ReturnRequestAction>> matrix = new EnumMap<>(OrderReturnRequestStatus.class);
        matrix.put(OrderReturnRequestStatus.REGISTERED, EnumSet.of(
                ReturnRequestAction.SET_MODE_EXCHANGE,
                ReturnRequestAction.UPDATE_REVERSE_TRACK,
                ReturnRequestAction.CLOSE_REQUEST
        ));
        matrix.put(OrderReturnRequestStatus.EXCHANGE_APPROVED, EnumSet.of(
                ReturnRequestAction.SET_MODE_RETURN,
                ReturnRequestAction.UPDATE_REVERSE_TRACK,
                ReturnRequestAction.REGISTER_EXCHANGE_PARCEL
        ));
        return matrix;
    }

    /**
     * Подбирает действия, приводящие к целевой стадии, исходя из текущего контекста.
     *
     * @param mode   режим заявки, задающий доступные команды
     * @param from   исходная стадия
     * @param target целевая стадия перехода
     * @return набор команд, инициирующих переход, может быть пустым
     */
    private EnumSet<ReturnRequestAction> mapTransitionToActions(ReturnRequestMode mode,
                                                                ReturnRequestStage from,
                                                                ReturnRequestStage target) {
        if (target == null) {
            return EnumSet.noneOf(ReturnRequestAction.class);
        }
        return switch (target) {
            case OUTBOUND_SENT -> EnumSet.of(ReturnRequestAction.MARK_OUTBOUND_SENT);
            case INBOUND_ARRIVED -> EnumSet.of(ReturnRequestAction.MARK_INBOUND_ARRIVED);
            case INBOUND_PICKED_UP -> EnumSet.of(ReturnRequestAction.MARK_INBOUND_PICKED_UP);
            case EXCHANGE_SENT -> mode == ReturnRequestMode.EXCHANGE
                    ? EnumSet.of(ReturnRequestAction.MARK_EXCHANGE_SENT)
                    : EnumSet.noneOf(ReturnRequestAction.class);
            case EXCHANGE_DELIVERED -> mode == ReturnRequestMode.EXCHANGE
                    ? EnumSet.of(ReturnRequestAction.MARK_EXCHANGE_DELIVERED)
                    : EnumSet.noneOf(ReturnRequestAction.class);
            default -> EnumSet.noneOf(ReturnRequestAction.class);
        };
    }

    private Set<ReturnRequestStage> allowedStages(ReturnRequestMode mode) {
        EnumSet<ReturnRequestStage> allowed = EnumSet.noneOf(ReturnRequestStage.class);
        for (ReturnRequestStage stage : ReturnRequestStage.values()) {
            if (stage.supportsMode(mode)) {
                allowed.add(stage);
            }
        }
        return Collections.unmodifiableSet(allowed);
    }

    private ReturnRequestMode safeMode(ReturnRequestMode mode) {
        return mode != null ? mode : ReturnRequestMode.RETURN;
    }

    private ReturnRequestStage safeStage(ReturnRequestStage stage) {
        return stage != null ? stage : ReturnRequestStage.NEW;
    }
}
