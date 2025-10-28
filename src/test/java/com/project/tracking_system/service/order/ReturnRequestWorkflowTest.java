package com.project.tracking_system.service.order;

import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.ReturnRequestMode;
import com.project.tracking_system.entity.ReturnRequestStage;
import com.project.tracking_system.entity.User;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты таблицы переходов стадий заявок на возврат/обмен.
 */
class ReturnRequestWorkflowTest {

    private final ReturnRequestWorkflow workflow = new ReturnRequestWorkflow();

    @Test
    void transitionToStage_AllowsExchangeShipmentFromMerchantStage() {
        OrderReturnRequest request = new OrderReturnRequest();
        request.setMode(ReturnRequestMode.EXCHANGE);
        request.setStage(ReturnRequestStage.MERCHANT_ACCEPT_RETURN);
        User manager = new User();
        manager.setId(42L);
        ZonedDateTime moment = ZonedDateTime.now(ZoneOffset.UTC);

        workflow.transitionToStage(request, ReturnRequestStage.EXCHANGE_SHIPMENT, true, manager, moment);

        assertThat(request.getStage()).isEqualTo(ReturnRequestStage.EXCHANGE_SHIPMENT);
        assertThat(request.getStageStartedAt()).isEqualTo(moment);
        assertThat(request.getStageUpdatedAt()).isEqualTo(moment);
        assertThat(request.isManualStageOverride()).isTrue();
        assertThat(request.getHistoryEntries()).hasSize(1);
    }

    @Test
    void transitionToStage_AllowsDirectExchangeLaunchFromCustomerStage() {
        OrderReturnRequest request = new OrderReturnRequest();
        request.setMode(ReturnRequestMode.EXCHANGE);
        request.setStage(ReturnRequestStage.CUSTOMER_RETURN);
        ZonedDateTime moment = ZonedDateTime.now(ZoneOffset.UTC);

        workflow.transitionToStage(request, ReturnRequestStage.EXCHANGE_SHIPMENT, true, null, moment);

        assertThat(request.getStage()).isEqualTo(ReturnRequestStage.EXCHANGE_SHIPMENT);
        assertThat(request.getHistoryEntries()).hasSize(1);
    }

    @Test
    void transitionToStage_ThrowsWhenTargetNotAllowedForMode() {
        OrderReturnRequest request = new OrderReturnRequest();
        request.setMode(ReturnRequestMode.RETURN);
        request.setStage(ReturnRequestStage.CUSTOMER_RETURN);

        assertThatThrownBy(() -> workflow.transitionToStage(
                request,
                ReturnRequestStage.EXCHANGE_DELIVERY,
                true,
                null,
                ZonedDateTime.now(ZoneOffset.UTC)
        )).isInstanceOf(IllegalStateException.class);
    }
}
