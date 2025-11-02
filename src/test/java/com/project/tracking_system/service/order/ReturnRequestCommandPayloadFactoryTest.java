package com.project.tracking_system.service.order;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.project.tracking_system.controller.ReturnRequestCommandType;
import com.project.tracking_system.service.order.payload.ReturnRequestCommandPayload;
import com.project.tracking_system.service.order.payload.ReturnRequestCommandPayloadFactory;
import com.project.tracking_system.service.order.payload.UpdateDetailsPayload;
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
    void create_whenUpdateDetailsWithoutFields_throwsException() {
        assertThatThrownBy(() -> factory.create(ReturnRequestCommandType.UPDATE_REVERSE_TRACK,
                JsonNodeFactory.instance.objectNode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reverseTrack");
    }

    @Test
    void create_whenUpdateDetailsHasValues_returnsNormalizedPayload() {
        var node = JsonNodeFactory.instance.objectNode()
                .put("reverseTrack", "  ab123 ")
                .put("comment", "  Test comment  ");

        UpdateDetailsPayload payload = (UpdateDetailsPayload) factory.create(ReturnRequestCommandType.UPDATE_REVERSE_TRACK, node);

        assertThat(payload.reverseTrack()).isEqualTo("AB123");
        assertThat(payload.comment()).isEqualTo("Test comment");
    }

    @Test
    void create_whenUpdateDetailsHasLongTrack_throwsException() {
        String longTrack = "A".repeat(70);
        var node = JsonNodeFactory.instance.objectNode()
                .put("reverseTrack", longTrack);

        assertThatThrownBy(() -> factory.create(ReturnRequestCommandType.UPDATE_REVERSE_TRACK, node))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не должен превышать 64");
    }

    @Test
    void create_whenUpdateDetailsClearsValues_isAccepted() {
        var node = JsonNodeFactory.instance.objectNode()
                .putNull("reverseTrack")
                .put("comment", " ");

        UpdateDetailsPayload payload = (UpdateDetailsPayload) factory.create(ReturnRequestCommandType.UPDATE_REVERSE_TRACK, node);

        assertThat(payload.reverseTrack()).isNull();
        assertThat(payload.comment()).isNull();
    }
}
