package com.iemr.mmu.data.nikshay.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

@Data
@Value
@Builder
public class AddIndiaTbResponse {
    Long patientId;
    String rawBody;
    int httpStatus;
}
