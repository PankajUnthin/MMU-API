package com.iemr.mmu.data.nikshay.dtos;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class DuplicateCheckResult {

    private List<DuplicateCandidateDto> allHits;
    private String rawBody;
    private String rawResponse;
    private int httpStatus;

    public boolean hasAnyHit() {
        return allHits != null && !allHits.isEmpty();
    }

    public String possibleDuplicateOf() {
        return hasAnyHit() ? allHits.stream().map(h -> String.valueOf(h.getPatientId()))
                .collect(Collectors.joining(" ")) : null;
    }
}