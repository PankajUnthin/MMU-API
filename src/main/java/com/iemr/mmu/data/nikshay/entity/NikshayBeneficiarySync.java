package com.iemr.mmu.data.nikshay.entity;

import com.google.gson.annotations.Expose;
import com.iemr.mmu.utils.mapper.OutputMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Entity
@Table(name = "t_nikshay_beneficiary_sync")
public class NikshayBeneficiarySync {

//    public enum Outcome {ENROLLED, LINKED, NEW_EPISODE_CREATED, LINKED_NO_ACTION, HELD, FAILED}

//    public enum TriggeredBy {JOB, MANUAL}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Expose
    @Column(name = "sync_id")
    private Long syncId;

    @Expose
    @Column(name = "ben_reg_id", nullable = false)
    private Long benRegId;

    // Episode produced / linked by this attempt; null on failure.
    @Expose
    @Column(name = "episode_id")
    private Long episodeId;

    @Expose
    @Column(name = "job_run_id", nullable = false)
    private Long jobRunId;

    @Expose
    @Column(name = "device_config_id")
    private Integer deviceConfigId;

    @Expose
    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Expose
//    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", nullable = false)
    private String triggeredBy;

    @Expose
    @Column(name = "triggered_user_id")
    private Integer triggeredUserId;

    @Expose
    @Column(name = "sync_date", insertable = false, updatable = false)
    private Timestamp syncDate;

    @Expose
//    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private String outcome;

    // Same values as i_beneficiarydetails.nikshaySyncFailureReason.
    @Expose
    @Column(name = "failure_reason")
    private String failureReason;

    @Expose
    @Column(name = "failure_detail", length = 1000)
    private String failureDetail;

    @Expose
    @Column(name = "nikshay_id")
    private String nikshayId;

    @Expose
    @Column(name = "nikshay_episode_id")
    private String nikshayEpisodeId;

    @Expose
    @Column(name = "stage_found")
    private String stageFound;

    // PatientIds returned by GetPossibleDuplicates, space-joined.
    @Expose
    @Column(name = "possible_duplicate_of")
    private String possibleDuplicateOf;

    @Column(name = "dup_check_response", columnDefinition = "json")
    private String dupCheckResponse;

    @Column(name = "add_request_summary", columnDefinition = "json")
    private String addRequestSummary;

    @Column(name = "add_response", columnDefinition = "json")
    private String addResponse;

    @Column(name = "auto_fixes", columnDefinition = "json")
    private String autoFixes;

    @Expose
    @Column(name = "http_status")
    private Integer httpStatus;

    @Expose
    @Column(name = "duration_ms")
    private Integer durationMs;

    @Transient
    private OutputMapper outputMapper = new OutputMapper();

    @Override
    public String toString() {
        return outputMapper.gson().toJson(this);
    }
}