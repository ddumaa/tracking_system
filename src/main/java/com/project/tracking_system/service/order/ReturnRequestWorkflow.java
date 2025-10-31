package com.project.tracking_system.service.order;

import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.OrderReturnRequestStatus;
import com.project.tracking_system.entity.ReturnRequestAction;
import com.project.tracking_system.entity.ReturnRequestMode;
import com.project.tracking_system.entity.ReturnRequestStage;
import com.project.tracking_system.entity.User;

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

    /**
     * Создаёт workflow с таблицей допустимых переходов.
     */
    public ReturnRequestWorkflow() {
        this.transitionTable = buildTransitionTable();
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
     * Вычисляет базовый набор действий, доступных по текущему состоянию заявки.
     *
     * @param request заявка на возврат/обмен
     * @return множество потенциально доступных действий
     */
    public EnumSet<ReturnRequestAction> resolveBaseActions(OrderReturnRequest request) {
        if (request == null) {
            return EnumSet.noneOf(ReturnRequestAction.class);
        }
        OrderReturnRequestStatus status = request.getStatus();
        ReturnRequestStage stage = safeStage(request.getStage());
        EnumSet<ReturnRequestAction> actions = EnumSet.noneOf(ReturnRequestAction.class);
        if (status == OrderReturnRequestStatus.REGISTERED) {
            actions.add(ReturnRequestAction.CANCEL_RETURN);
        }
        if (status == OrderReturnRequestStatus.EXCHANGE_APPROVED
                && stage != ReturnRequestStage.EXCHANGE_DELIVERED) {
            actions.add(ReturnRequestAction.CANCEL_EXCHANGE);
            actions.add(ReturnRequestAction.CONVERT_TO_RETURN);
        }
        return actions;
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
