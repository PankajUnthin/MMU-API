package com.iemr.mmu.data.nikshay.entity;

import com.google.gson.annotations.Expose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Entity
@Table(name = "t_nikshay_job_run")
public class NikshayJobRun {

    public enum RunStatus {RUNNING, COMPLETED, ABORTED, FAILED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Expose
    @Column(name = "job_run_id")
    private Long jobRunId;

    @Expose
    @Column(name = "step", nullable = false)
    private String step = "ENROLL";

    @Expose
//    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", nullable = false)
    private String triggeredBy;

    @Expose
    @Column(name = "triggered_user_id")
    private Integer triggeredUserId;

    @Expose
    @Enumerated(EnumType.STRING)
    @Column(name = "run_status", nullable = false)
    private RunStatus runStatus = RunStatus.RUNNING;

    @Expose
    @Column(name = "started_at", insertable = false, updatable = false)
    private Timestamp startedAt;

    @Expose
    @Column(name = "ended_at")
    private Timestamp endedAt;

    @Expose
    @Column(name = "candidates_fetched")
    private Integer candidatesFetched = 0;
    @Expose
    @Column(name = "devices_processed")
    private Integer devicesProcessed = 0;
    @Expose
    @Column(name = "devices_login_failed")
    private Integer devicesLoginFailed = 0;
    @Expose
    @Column(name = "cnt_enrolled")
    private Integer cntEnrolled = 0;
    @Expose
    @Column(name = "cnt_linked")
    private Integer cntLinked = 0;
    @Expose
    @Column(name = "cnt_new_episode")
    private Integer cntNewEpisode = 0;
    @Expose
    @Column(name = "cnt_linked_no_action")
    private Integer cntLinkedNoAction = 0;
    @Expose
    @Column(name = "cnt_held")
    private Integer cntHeld = 0;
    @Expose
    @Column(name = "cnt_failed")
    private Integer cntFailed = 0;

    @Expose
    @Column(name = "error_summary", length = 1000)
    private String errorSummary;

//    @Transient
//    private OutputMapper outputMapper = new OutputMapper();

//    @Override
//    public String toString() {
//        return outputMapper.gson().toJson(this);
//    }
}