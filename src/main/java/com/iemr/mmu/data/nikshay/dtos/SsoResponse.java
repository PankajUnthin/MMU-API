package com.iemr.mmu.data.nikshay.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SsoResponse<T> {
    private boolean success;
    private T data;
    private String message;
}
