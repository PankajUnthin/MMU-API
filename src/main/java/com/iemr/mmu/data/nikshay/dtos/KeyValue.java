package com.iemr.mmu.data.nikshay.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KeyValue {
    @JsonProperty("Key")
    private Integer key;
    @JsonProperty("Value")
    private String value;
}
