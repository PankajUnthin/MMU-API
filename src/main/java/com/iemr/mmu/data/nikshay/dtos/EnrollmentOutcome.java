package com.iemr.mmu.data.nikshay.dtos;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EnrollmentOutcome {
    public enum Outcome {ENROLLED, LINKED, NEW_EPISODE_CREATED, LINKED_NO_ACTION, HELD, FAILED}

    private Long benRegId;
    private Outcome outcome;
    private String linkStatus;            // NEW | LINKED | NEW_EPISODE | LINKED_NO_ACTION
    private String failureReason;         // SyncFailureReason value
    private String failureDetail;
//    private boolean retryable;             // → sets nikshayNextRetryAt
    private String nikshayParentId;             // parent
    private String nikshayEpisodeId;      // this episode's PatientId
    private String stageFound;
    private Boolean outcomeAssigned;
    private boolean createdByWrapper;
    private String possibleDuplicateOf;
    private Integer deviceConfigId;
    private String dupCheckResponseJson;
    private String addRequestJson;
    private String addResponseJson;
    private Integer nikshayFacilityId;
    private List<AutoFixDto> autoFixes;
    private Integer httpStatus;
    private boolean countsAsAttempt;
    private long durationMs;
}
