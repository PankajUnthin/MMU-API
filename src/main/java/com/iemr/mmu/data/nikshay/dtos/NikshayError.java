package com.iemr.mmu.data.nikshay.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NikshayError {
    @JsonProperty("Message")
    private String message;
    @JsonProperty("Code")
    private String code;
}
