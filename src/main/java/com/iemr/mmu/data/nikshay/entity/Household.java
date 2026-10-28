package com.iemr.mmu.data.nikshay.entity;

import com.google.gson.annotations.Expose;
import com.iemr.mmu.utils.mapper.OutputMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Entity
@Table(name = "t_household")
public class Household {

    public static final String TRACING_PENDING = "PENDING";
    public static final String TRACING_COMPLETED = "COMPLETED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Expose
    @Column(name = "household_id")
    private Long householdId;

    @Expose
    @Column(name = "household_code")
    private String householdCode;

    /**
     * village id — joins m_nikshay_device_village_mapping.village_id.
     */
    @Expose
    @Column(name = "village_id", nullable = false)
    private Integer villageId;

    @Expose
    @Column(name = "head_ben_reg_id")
    private Long headBenRegId;

    @Expose
    @Column(name = "address")
    private String address;

    @Expose
    @Column(name = "member_count")
    private Integer memberCount = 0;

    /**
     * PENDING | COMPLETED — gate for creating a fresh Ni-kshay ID.
     */
    @Expose
    @Column(name = "tracing_status", nullable = false)
    private String tracingStatus = TRACING_PENDING;

    @Expose
    @Column(name = "tracing_completed_on")
    private Timestamp tracingCompletedOn;

    @Expose
    @Column(name = "tracing_marked_by")
    private String tracingMarkedBy;

    @Expose
    @Column(name = "enumerated_on")
    private Timestamp enumeratedOn;

    @Expose
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Expose
    @Column(name = "created_by")
    private String createdBy;

    @Expose
    @Column(name = "created_at", insertable = false, updatable = false)
    private Timestamp createdAt;

    @Expose
    @Column(name = "updated_by")
    private String updatedBy;

    @Expose
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Timestamp updatedAt;

    @Expose
    @Column(name = "more_data", columnDefinition = "json")
    private String moreData;

    @Transient
    private OutputMapper outputMapper = new OutputMapper();

    public boolean isTracingCompleted() {
        return TRACING_COMPLETED.equals(tracingStatus);
    }

    @Override
    public String toString() {
        return outputMapper.gson().toJson(this);
    }
}