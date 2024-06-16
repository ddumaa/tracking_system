package com.project.tracking_system.model.jsonModel;


public enum MethodName {
    POSTAL_TRACKING("Postal.Tracking"),
    GET_JWT("GetJWT");

    private final String methodName;

    MethodName(String methodName) {
        this.methodName = methodName;
    }

    @Override
    public String toString() {
        return methodName;
    }
}