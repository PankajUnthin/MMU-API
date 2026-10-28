package com.iemr.mmu.data.nikshay.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SsoClientData {
    private String authToken;
}
