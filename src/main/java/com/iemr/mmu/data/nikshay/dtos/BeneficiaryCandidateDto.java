package com.iemr.mmu.data.nikshay.dtos;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class BeneficiaryCandidateDto {
    private Long benRegId;
    private Long householdId;
    private Integer villageId;
    private Integer age;
    private boolean householdTracingComplete;   // t_household.tracing_status == COMPLETED
    private boolean indexPatient;               // t_household_member.is_index_patient
    private boolean contact;                    // t_household_member.is_contact
    private int attemptsSoFar;
    private String existingNikshayId;           // non-null → re-screening, expect NEW_EPISODE
    private int nextEpisodeNo;

    private String firstName, middleName, lastName;
    private String gender;
    private LocalDate dob;
    private String phone;
    private String address;
    private String maritalStatus, occupation, community, hivStatus;
    private String caseFindingType;             // ExtraFields.caseFindingType
    private String keyPopulation;
    private Long screeningRef;
}