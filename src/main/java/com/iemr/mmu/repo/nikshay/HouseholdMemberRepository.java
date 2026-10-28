package com.iemr.mmu.repo.nikshay;

import com.iemr.mmu.data.nikshay.entity.HouseholdMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, Long> {

    Optional<HouseholdMember> findByBenRegId(Long benRegId);

    List<HouseholdMember> findByHouseholdIdAndIsActiveTrue(Long householdId);

    List<HouseholdMember> findByHouseholdIdAndIsIndexPatientTrueAndIsActiveTrue(Long householdId);

    @Query("SELECT m.benRegId FROM HouseholdMember m WHERE m.householdId = :householdId AND m.isActive = true AND m.isTested = false")
    List<Long> findUntestedBenRegIds(@Param("householdId") Long householdId);

    @Modifying
    @Transactional
    @Query("UPDATE HouseholdMember m SET m.isTested = true, m.testedOn = CURRENT_TIMESTAMP WHERE m.benRegId = :benRegId AND m.isTested = false")
    int markTested(@Param("benRegId") Long benRegId);

    @Modifying
    @Transactional
    @Query("UPDATE HouseholdMember m SET m.isIndexPatient = true WHERE m.benRegId = :benRegId")
    int markIndexPatient(@Param("benRegId") Long benRegId);
}
