package com.project.tracking_system.service.jsonEvropostService;

import com.project.tracking_system.model.evropost.jsonRequestModel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RequestFactory {

    private final JsonData jsonData;
    private final JsonPacket jsonPacket;
    private final JsonRequest jsonRequest;

    @Autowired
    public RequestFactory(JsonData jsonData, JsonPacket jsonPacket, JsonRequest jsonRequest) {
        this.jsonData = jsonData;
        this.jsonPacket = jsonPacket;
        this.jsonRequest = jsonRequest;
    }

    public JsonRequest createTrackingRequest(String number) {
        JsonDataAbstract data = new JsonTrackingData(number);
        JsonPacket packet = new JsonPacket(jsonPacket.getJwt(), JsonMethodName.POSTAL_TRACKING.toString(), jsonPacket.getServiceNumber(), data);
        return new JsonRequest(jsonRequest.getCrc(), packet);
    }

    public JsonRequest createGetJWTRequest() {
        JsonDataAbstract data = new JsonGetJWTData(jsonData.getLoginName(), jsonData.getPassword(), jsonData.getLoginNameTypeId());
        JsonPacket packet = new JsonPacket(jsonPacket.getJwt(), JsonMethodName.GET_JWT.toString(), jsonPacket.getServiceNumber(), data);
        return new JsonRequest(jsonRequest.getCrc(), packet);
    }

}
