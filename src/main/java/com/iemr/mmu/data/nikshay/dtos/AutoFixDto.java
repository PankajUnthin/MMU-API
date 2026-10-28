package com.iemr.mmu.data.nikshay.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AutoFixDto {
    private String rule;      // "V7"
    private String field;     // "phone"
    private String oldValue;
    private String newValue;
}