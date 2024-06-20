package com.project.tracking_system.model.jsonRequestModel;


import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

@Data
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = JsonTrackingData.class),
        @JsonSubTypes.Type(value = JsonGetJWTData.class)
})
public abstract class JsonDataAbstract {


}
