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
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Entity
@Table(name = "t_nikshay_episode",
        uniqueConstraints = @UniqueConstraint(name = "uq_episode", columnNames = {"ben_reg_id", "episode_no"}))
public class NikshayEpisode {

    public enum LinkStatus {NEW, LINKED, NEW_EPISODE, LINKED_NO_ACTION}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Expose
    @Column(name = "episode_id")
    private Long episodeId;

    @Expose
    @Column(name = "ben_reg_id", nullable = false)
    private Long benRegId;

    @Expose
    @Column(name = "episode_no", nullable = false)
    private Integer episodeNo;

    // i_beneficiarydetails.nikshayId
    @Expose
    @Column(name = "nikshay_parent_id", nullable = false)
    private String nikshayParentId;

    // PatientId used by tests / transfer for THIS episode (= parent for episode 1).
    @Expose
    @Column(name = "nikshay_episode_id", nullable = false)
    private String nikshayEpisodeId;

    @Expose
//    @Enumerated(EnumType.STRING)
    @Column(name = "link_status", nullable = false)
    private String linkStatus;

    // Raw Ni-kshay Stage at duplicate check, e.g. "Presumptive (Open)".
    @Expose
    @Column(name = "stage_found")
    private String stageFound;

    @Expose
    @Column(name = "outcome_assigned")
    private Boolean outcomeAssigned;

    // true when AddIndiaTB was called by us → transfer eligible.
    @Expose
    @Column(name = "created_by_wrapper")
    private Boolean createdByWrapper = false;

    // Exactly one true per beneficiary.
    @Expose
    @Column(name = "is_current")
    private Boolean isCurrent = true;

    // Camp / visit id that triggered this episode
    @Expose
    @Column(name = "screening_ref")
    private Long screeningRef;

    @Expose
    @Column(name = "device_config_id")
    private Integer deviceConfigId;

    @Expose
    @Column(name = "nikshay_facility_id")
    private Integer nikshayFacilityId;

    @Expose
    @Column(name = "enrolled_at", insertable = false, updatable = false)
    private Timestamp enrolledAt;

    @Expose
    @Column(name = "created_by")
    private String createdBy = "SYSTEM";

    @Expose
    @Column(name = "more_data", columnDefinition = "json")
    private String moreData;

    @Transient
    private OutputMapper outputMapper = new OutputMapper();

    // Tests must not be pushed for this episode.

    @Override
    public String toString() {
        return outputMapper.gson().toJson(this);
    }
}