package com.iemr.mmu.data.nikshay.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NikshayApiResponse<T> {
    @JsonProperty("Success") private Boolean success;
    @JsonProperty("Data") private JsonNode data;
    @JsonProperty("Error") private JsonNode error;
    @JsonProperty("ErrorMessage") private String errorMessage;
    @JsonProperty("PatientId") private Long patientId;

    public boolean isSuccess() { return Boolean.TRUE.equals(success); }

    public String errorText() {
        if (errorMessage != null && !errorMessage.isBlank()) return errorMessage;
        if (error == null || error.isNull()) return null;
        if (error.isTextual()) return error.asText();
        JsonNode m = error.get("Message");
        return m != null ? m.asText() : error.toString();
    }
}
