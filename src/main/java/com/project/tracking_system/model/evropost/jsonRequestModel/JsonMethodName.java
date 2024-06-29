package com.project.tracking_system.model.evropost.jsonRequestModel;


public enum JsonMethodName {
    POSTAL_TRACKING("Postal.Tracking"),
    GET_JWT("GetJWT");

    private final String methodName;

    JsonMethodName(String methodName) {
        this.methodName = methodName;
    }

    @Override
    public String toString() {
        return methodName;
    }
}