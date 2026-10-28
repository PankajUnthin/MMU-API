package com.iemr.mmu.data.nikshay.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AddDiagnosticsTestData {
    @JsonProperty("RequestId")
    private Long requestId;
    @JsonProperty("TestType")
    private String testType;
    @JsonProperty("ResultId")
    private Long resultId;
}
