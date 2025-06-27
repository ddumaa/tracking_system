package com.project.tracking_system.service.jsonEvropostService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.tracking_system.model.evropost.jsonRequestModel.JsonData;
import com.project.tracking_system.model.evropost.jsonRequestModel.JsonPacket;
import com.project.tracking_system.model.evropost.jsonRequestModel.JsonRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link GetJwtTokenService}.
 */
@ExtendWith(MockitoExtension.class)
class GetJwtTokenServiceTest {

    @Mock
    private JsonHandlerService jsonHandlerService;

    private JsonData jsonData;
    private JsonRequest jsonRequest;
    private JsonPacket jsonPacket;

    private GetJwtTokenService service;

    @BeforeEach
    void setUp() {
        jsonData = new JsonData();
        jsonRequest = new JsonRequest();
        jsonPacket = new JsonPacket();
        service = new GetJwtTokenService(jsonHandlerService, jsonData, jsonRequest, jsonPacket);
    }

    @Test
    void getSystemTokenFromApi_ReturnsToken() throws Exception {
        jsonData.setLoginName("log");
        jsonData.setPassword("pass");
        jsonData.setLoginNameTypeId("type");
        jsonRequest.setCrc("crc");
        jsonPacket.setServiceNumber("srv");

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode response = mapper.createObjectNode();
        ObjectNode tableItem = mapper.createObjectNode();
        tableItem.put("JWT", "jwt");
        response.putArray("Table").add(tableItem);
        when(jsonHandlerService.jsonRequest(any())).thenReturn(response);

        String token = service.getSystemTokenFromApi();

        assertEquals("jwt", token);

        ArgumentCaptor<JsonRequest> captor = ArgumentCaptor.forClass(JsonRequest.class);
        verify(jsonHandlerService).jsonRequest(captor.capture());
        JsonPacket packet = captor.getValue().getPacket();
        assertEquals("GET_JWT", packet.getMethodName());
        assertEquals("srv", packet.getServiceNumber());
    }

    @Test
    void getSystemTokenFromApi_NoToken_ThrowsException() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode response = mapper.createObjectNode();
        response.putArray("Table").add(mapper.createObjectNode());
        when(jsonHandlerService.jsonRequest(any())).thenReturn(response);

        assertThrows(RuntimeException.class, () -> service.getSystemTokenFromApi());
    }

    @Test
    void getUserTokenFromApi_ReturnsToken() throws Exception {
        jsonData.setLoginNameTypeId("type");
        jsonRequest.setCrc("crc");

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode response = mapper.createObjectNode();
        ObjectNode tableItem = mapper.createObjectNode();
        tableItem.put("JWT", "jwt2");
        response.putArray("Table").add(tableItem);
        when(jsonHandlerService.jsonRequest(any())).thenReturn(response);

        String token = service.getUserTokenFromApi("user", "pwd", "srv2");

        assertEquals("jwt2", token);

        ArgumentCaptor<JsonRequest> captor = ArgumentCaptor.forClass(JsonRequest.class);
        verify(jsonHandlerService).jsonRequest(captor.capture());
        JsonPacket packet = captor.getValue().getPacket();
        assertEquals("GET_JWT", packet.getMethodName());
        assertEquals("srv2", packet.getServiceNumber());
    }
}
