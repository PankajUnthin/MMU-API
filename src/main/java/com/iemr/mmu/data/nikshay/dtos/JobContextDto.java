package com.iemr.mmu.data.nikshay.dtos;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class JobContextDto {
    private Long jobRunId;
    private String step;                 // ENROLL
    private String triggeredBy;          // JOB | MANUAL
    private Integer triggeredUserId;
    private int batchSize;
    private int maxAttempts;
    private int retryBackoffMinutes;
    private int loginRetryCount;
    private int callDelayMs;
    private List<Long> onlyBenRegIds;    // non-null for manual push
}
