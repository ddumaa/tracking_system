package com.project.tracking_system.model.evropost.jsonRequestModel;

/**
 * Перечисление, представляющее имена методов для взаимодействия с системой EuroPost.
 * <p>
 * Это перечисление используется для указания имени метода в запросах к системе EuroPost.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
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