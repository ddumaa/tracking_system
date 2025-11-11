package com.project.tracking_system.service.order;

import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.OrderReturnRequestStatus;
import com.project.tracking_system.entity.ReturnRequestAction;
import com.project.tracking_system.entity.ReturnRequestMode;
import com.project.tracking_system.entity.ReturnRequestStage;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.order.context.ReturnRequestActionContext;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты таблицы переходов стадий заявок на возврат/обмен.
 */
class ReturnRequestWorkflowTest {

    private final ReturnRequestWorkflow workflow = new ReturnRequestWorkflow();

    @Test
    void transitionToStage_AllowsExchangeRegistrationFromInboundStage() {
        OrderReturnRequest request = new OrderReturnRequest();
        request.setMode(ReturnRequestMode.EXCHANGE);
        request.setStage(ReturnRequestStage.INBOUND_PICKED_UP);
        User manager = new User();
        manager.setId(42L);
        ZonedDateTime moment = ZonedDateTime.now(ZoneOffset.UTC);

        workflow.transitionToStage(request, ReturnRequestStage.EXCHANGE_REGISTERED, true, manager, moment);

        assertThat(request.getStage()).isEqualTo(ReturnRequestStage.EXCHANGE_REGISTERED);
        assertThat(request.getStageStartedAt()).isEqualTo(moment);
        assertThat(request.getStageUpdatedAt()).isEqualTo(moment);
        assertThat(request.isManualStageOverride()).isTrue();
        assertThat(request.getHistoryEntries()).hasSize(1);
    }

    @Test
    void transitionToStage_RejectsDirectExchangeLaunchFromNewStage() {
        OrderReturnRequest request = new OrderReturnRequest();
        request.setMode(ReturnRequestMode.EXCHANGE);
        request.setStage(ReturnRequestStage.NEW);

        assertThatThrownBy(() -> workflow.transitionToStage(
                request,
                ReturnRequestStage.EXCHANGE_REGISTERED,
                true,
                null,
                ZonedDateTime.now(ZoneOffset.UTC)
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void transitionToStage_ThrowsWhenTargetNotAllowedForMode() {
        OrderReturnRequest request = new OrderReturnRequest();
        request.setMode(ReturnRequestMode.RETURN);
        request.setStage(ReturnRequestStage.OUTBOUND_SENT);

        assertThatThrownBy(() -> workflow.transitionToStage(
                request,
                ReturnRequestStage.EXCHANGE_DELIVERED,
                true,
                null,
                ZonedDateTime.now(ZoneOffset.UTC)
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resolveBaseActions_ReturnRegisteredIncludesManagementActions() {
        OrderReturnRequest request = new OrderReturnRequest();
        request.setMode(ReturnRequestMode.RETURN);
        request.setStatus(OrderReturnRequestStatus.REGISTERED);
        request.setStage(ReturnRequestStage.NEW);

        ReturnRequestActionContext context = ReturnRequestActionContext.builder(request)
                .withMode(ReturnRequestMode.RETURN)
                .withStage(ReturnRequestStage.NEW)
                .withStatus(OrderReturnRequestStatus.REGISTERED)
                .allow(ReturnRequestAction.SET_MODE_EXCHANGE, true)
                .allow(ReturnRequestAction.UPDATE_REVERSE_TRACK, true)
                .allow(ReturnRequestAction.CLOSE_REQUEST, true)
                .allow(ReturnRequestAction.MARK_OUTBOUND_SENT, true)
                .allow(ReturnRequestAction.MARK_INBOUND_ARRIVED, true)
                .allow(ReturnRequestAction.MARK_INBOUND_PICKED_UP, true)
                .build();

        EnumSet<ReturnRequestAction> actions = workflow.resolveBaseActions(context);

        assertThat(actions)
                .containsExactlyInAnyOrder(
                        ReturnRequestAction.SET_MODE_EXCHANGE,
                        ReturnRequestAction.UPDATE_REVERSE_TRACK,
                        ReturnRequestAction.CLOSE_REQUEST,
                        ReturnRequestAction.MARK_OUTBOUND_SENT
                );
    }

    @Test
    void resolveBaseActions_ExchangeApprovedIncludesParcelActions() {
        OrderReturnRequest request = new OrderReturnRequest();
        request.setMode(ReturnRequestMode.EXCHANGE);
        request.setStatus(OrderReturnRequestStatus.EXCHANGE_APPROVED);
        request.setStage(ReturnRequestStage.EXCHANGE_REGISTERED);

        ReturnRequestActionContext context = ReturnRequestActionContext.builder(request)
                .withMode(ReturnRequestMode.EXCHANGE)
                .withStage(ReturnRequestStage.EXCHANGE_REGISTERED)
                .withStatus(OrderReturnRequestStatus.EXCHANGE_APPROVED)
                .allow(ReturnRequestAction.SET_MODE_RETURN, true)
                .allow(ReturnRequestAction.UPDATE_REVERSE_TRACK, true)
                .allow(ReturnRequestAction.REGISTER_EXCHANGE_PARCEL, true)
                .allow(ReturnRequestAction.MARK_EXCHANGE_SENT, true)
                .build();

        EnumSet<ReturnRequestAction> actions = workflow.resolveBaseActions(context);

        assertThat(actions)
                .containsExactlyInAnyOrder(
                        ReturnRequestAction.REGISTER_EXCHANGE_PARCEL,
                ReturnRequestAction.MARK_EXCHANGE_SENT,
                        ReturnRequestAction.SET_MODE_RETURN,
                        ReturnRequestAction.UPDATE_REVERSE_TRACK
                );
    }

    @Test
    void transitionSequentially_AdvancesReturnThroughAllStages() {
        OrderReturnRequest request = new OrderReturnRequest();
        request.setMode(ReturnRequestMode.RETURN);
        request.setStage(ReturnRequestStage.NEW);
        User manager = new User();
        manager.setId(77L);
        ZonedDateTime moment = ZonedDateTime.now(ZoneOffset.UTC);

        workflow.transitionSequentially(request, ReturnRequestStage.INBOUND_PICKED_UP, true, manager, moment);

        assertThat(request.getStage()).isEqualTo(ReturnRequestStage.INBOUND_PICKED_UP);
        assertThat(request.getHistoryEntries()).hasSizeGreaterThanOrEqualTo(3);
    }
}
