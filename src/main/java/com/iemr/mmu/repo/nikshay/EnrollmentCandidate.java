package com.iemr.mmu.repo.nikshay;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Projection returned by NikshayBeneficiaryRepository.fetchEnrollmentCandidates().
 * Column aliases in the native query must match these getter names exactly.
 */
public interface EnrollmentCandidate {
    Long getBenRegId();

    String getFirstName();

    String getMiddleName();

    String getLastName();

    String getGender();

    LocalDateTime getDob();

    String getMaritalStatus();

    String getOccupation();

    String getCommunity();

    String getHivStatus();

    String getAddress();

    String getExtraFields();

    String getPhone();

    Long getHouseholdId();

    Integer getVillageId();

    String getTracingStatus();

    Boolean getIsIndexPatient();

    Boolean getIsContact();

    String getNikshayId();

    Integer getCurrentEpisodeNo();

    Integer getAttemptCount();

    String getSyncStatus();
}
