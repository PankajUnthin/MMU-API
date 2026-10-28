package com.iemr.mmu.data.nikshay.dtos;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
//@Builder
@NoArgsConstructor
public class ValidationResultDto {

    private boolean valid;
    private String failedRule;          // "V8"
    private String failedField;         // "phone"
    private String message;
//    @Builder.Default
    private List<AutoFixDto> autoFixes = new ArrayList<>();
    private BeneficiaryCandidateDto cleaned;

    public ValidationResultDto fail(String rule, String field, String msg) {
        this.valid = false; this.failedRule = rule; this.failedField = field; this.message = msg;
        return this;
    }
    public String failureDetail() { return failedRule + ": " + failedField + " – " + message; }
}
