package com.iemr.mmu.repo.nikshay;

import com.iemr.mmu.data.nikshay.entity.NikshayBeneficiarySync;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NikshayBeneficiarySyncRepository extends JpaRepository<NikshayBeneficiarySync, Long> {

    List<NikshayBeneficiarySync> findByBenRegIdOrderBySyncDateDesc(Long benRegId);

    NikshayBeneficiarySync findFirstByBenRegIdOrderBySyncDateDesc(Long benRegId);

    @Query(value = """
            SELECT s.outcome, s.failure_reason, COUNT(*)
            FROM   t_nikshay_beneficiary_sync s
            WHERE  s.job_run_id = :jobRunId
            GROUP BY s.outcome, s.failure_reason
            """, nativeQuery = true)
    List<Object[]> summarizeRun(@Param("jobRunId") Long jobRunId);

    List<NikshayBeneficiarySync> findByJobRunIdOrderBySyncDate(Long jobRunId);
}