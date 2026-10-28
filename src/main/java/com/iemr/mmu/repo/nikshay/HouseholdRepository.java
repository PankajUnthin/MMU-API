package com.iemr.mmu.repo.nikshay;

import com.iemr.mmu.data.nikshay.entity.Household;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface HouseholdRepository extends JpaRepository<Household, Long> {

    Optional<Household> findByHouseholdCode(String householdCode);

    List<Household> findByVillageIdAndIsActiveTrue(Integer villageId);

    @Query("SELECT h FROM Household h JOIN HouseholdMember m ON m.householdId = h.householdId WHERE m.benRegId = :benRegId")
    Optional<Household> findByMemberBenRegId(@Param("benRegId") Long benRegId);

    @Modifying
    @Transactional
    @Query("""
            UPDATE Household h
            SET h.tracingStatus = 'COMPLETED', h.tracingCompletedOn = CURRENT_TIMESTAMP, h.tracingMarkedBy = :markedBy
            WHERE h.householdId = :householdId AND h.tracingStatus = 'PENDING'
            """)
    int markTracingCompleted(@Param("householdId") Long householdId, @Param("markedBy") String markedBy);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE t_household h
            SET    h.tracing_status = 'COMPLETED', h.tracing_completed_on = NOW(), h.tracing_marked_by = 'SYSTEM'
            WHERE  h.tracing_status = 'PENDING' AND h.is_active = 1
              AND  NOT EXISTS (SELECT 1 FROM t_household_member m
                                WHERE m.household_id = h.household_id AND m.is_active = 1 AND m.is_tested = 0)
              AND  EXISTS     (SELECT 1 FROM t_household_member m WHERE m.household_id = h.household_id AND m.is_active = 1)
            """, nativeQuery = true)
    int autoCompleteTracing();

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE t_household h
            SET    h.member_count = (SELECT COUNT(*) FROM t_household_member m WHERE m.household_id = h.household_id AND m.is_active = 1)
            WHERE  h.household_id = :householdId
            """, nativeQuery = true)
    int refreshMemberCount(@Param("householdId") Long householdId);
}
