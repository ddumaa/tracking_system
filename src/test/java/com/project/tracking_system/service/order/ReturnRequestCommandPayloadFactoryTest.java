package com.project.tracking_system.service.order;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.project.tracking_system.controller.ReturnRequestCommandType;
import com.project.tracking_system.service.order.payload.ReturnRequestCommandPayload;
import com.project.tracking_system.service.order.payload.ReturnRequestCommandPayloadFactory;
import com.project.tracking_system.service.order.payload.ExchangeShipmentPayload;
import com.project.tracking_system.service.order.payload.UpdateReverseTrackPayload;
import com.project.tracking_system.service.order.payload.StageMarkPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты фабрики валидации полезной нагрузки команд.
 */
class ReturnRequestCommandPayloadFactoryTest {

    private ReturnRequestCommandPayloadFactory factory;

    @BeforeEach
    void setUp() {
        factory = new ReturnRequestCommandPayloadFactory();
    }

    @Test
    void create_whenCommandWithoutPayload_acceptsNullNode() {
        ReturnRequestCommandPayload payload = factory.create(ReturnRequestCommandType.SET_MODE_EXCHANGE, null);

        assertThat(payload).isNotNull();
    }

    @Test
    void create_whenUpdateReverseTrackHasValues_returnsNormalizedPayload() {
        var node = JsonNodeFactory.instance.objectNode()
                .put("reverseTrack", "  ab123 ")
                .put("comment", "  Test comment  ");

        UpdateReverseTrackPayload payload = (UpdateReverseTrackPayload) factory.create(ReturnRequestCommandType.UPDATE_REVERSE_TRACK, node);

        assertThat(payload.reverseTrack()).isEqualTo("AB123");
        assertThat(payload.comment()).isEqualTo("Test comment");
    }

    @Test
    void create_whenUpdateReverseTrackHasLongTrack_throwsException() {
        String longTrack = "A".repeat(70);
        var node = JsonNodeFactory.instance.objectNode()
                .put("reverseTrack", longTrack);

        assertThatThrownBy(() -> factory.create(ReturnRequestCommandType.UPDATE_REVERSE_TRACK, node))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не должен превышать 64");
    }

    @Test
    void create_whenUpdateReverseTrackWithoutTrack_throwsException() {
        var node = JsonNodeFactory.instance.objectNode()
                .putNull("reverseTrack")
                .put("comment", "Комментарий");

        assertThatThrownBy(() -> factory.create(ReturnRequestCommandType.UPDATE_REVERSE_TRACK, node))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reverseTrack");
    }

    @Test
    void create_whenUpdateReverseTrackWithoutComment_returnsPayloadWithNullComment() {
        var node = JsonNodeFactory.instance.objectNode()
                .put("reverseTrack", "rr123");

        UpdateReverseTrackPayload payload = (UpdateReverseTrackPayload) factory
                .create(ReturnRequestCommandType.UPDATE_REVERSE_TRACK, node);

        assertThat(payload.reverseTrack()).isEqualTo("RR123");
        assertThat(payload.comment()).isNull();
    }

    @Test
    void create_whenExchangeCommandWithoutTrack_throwsException() {
        var node = JsonNodeFactory.instance.objectNode();

        assertThatThrownBy(() -> factory.create(ReturnRequestCommandType.REGISTER_EXCHANGE_PARCEL, node))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exchangeTrack");
    }

    @Test
    void create_whenExchangeCommandHasTrack_returnsNormalizedPayload() {
        var node = JsonNodeFactory.instance.objectNode()
                .put("exchangeTrack", " xx777 ");

        ExchangeShipmentPayload payload = (ExchangeShipmentPayload) factory
                .create(ReturnRequestCommandType.REGISTER_EXCHANGE_PARCEL, node);

        assertThat(payload.exchangeTrack()).isEqualTo("XX777");
        assertThat(payload.stageMoment()).isNull();
    }

    @Test
    void create_whenStagePayloadProvided_convertsMomentToUtc() {
        var node = JsonNodeFactory.instance.objectNode()
                .put("stageMoment", "2024-02-01T12:00:00+03:00");

        StageMarkPayload payload = (StageMarkPayload) factory
                .create(ReturnRequestCommandType.MARK_OUTBOUND_SENT, node);

        assertThat(payload.stageMoment()).isEqualTo(java.time.ZonedDateTime.parse("2024-02-01T09:00:00Z"));
    }

    @Test
    void create_whenExchangePayloadContainsMoment_normalizesTrackAndTimestamp() {
        var node = JsonNodeFactory.instance.objectNode()
                .put("exchangeTrack", " ex001 ")
                .put("stageMoment", "2024-05-10T18:30:00+02:00");

        ExchangeShipmentPayload payload = (ExchangeShipmentPayload) factory
                .create(ReturnRequestCommandType.MARK_EXCHANGE_SENT, node);

        assertThat(payload.exchangeTrack()).isEqualTo("EX001");
        assertThat(payload.stageMoment()).isEqualTo(java.time.ZonedDateTime.parse("2024-05-10T16:30:00Z"));
    }
}
