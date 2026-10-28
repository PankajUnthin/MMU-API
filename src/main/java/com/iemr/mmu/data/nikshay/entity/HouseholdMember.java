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
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Entity
@Table(name = "t_household_member",
        uniqueConstraints = @UniqueConstraint(name = "uq_member", columnNames = "ben_reg_id"))
public class HouseholdMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Expose
    @Column(name = "member_id")
    private Long memberId;

    @Expose
    @Column(name = "household_id", nullable = false)
    private Long householdId;

    /**
     * i_beneficiarydetails.BeneficiaryRegID
     */
    @Expose
    @Column(name = "ben_reg_id", nullable = false)
    private Long benRegId;

    @Expose
    @Column(name = "relation_to_head")
    private String relationToHead;

    /**
     * Confirmed TB. Several members of one household may be index patients.
     */
    @Expose
    @Column(name = "is_index_patient")
    private Boolean isIndexPatient = false;

    /**
     * Key population = contact of known TB patient.
     */
    @Expose
    @Column(name = "is_contact")
    private Boolean isContact = false;

    @Expose
    @Column(name = "is_tested")
    private Boolean isTested = false;

    @Expose
    @Column(name = "tested_on")
    private Timestamp testedOn;

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
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Timestamp updatedAt;

    @Transient
    private OutputMapper outputMapper = new OutputMapper();

    @Override
    public String toString() {
        return outputMapper.gson().toJson(this);
    }
}