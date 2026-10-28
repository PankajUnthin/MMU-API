package com.iemr.mmu.repo.nikshay;

import com.iemr.mmu.data.nikshay.entity.NikshayJobRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface NikshayJobRunRepository extends JpaRepository<NikshayJobRun, Long> {

    Optional<NikshayJobRun> findFirstByStepOrderByStartedAtDesc(String step);

    List<NikshayJobRun> findTop50ByStepOrderByStartedAtDesc(String step);

    boolean existsByStepAndRunStatus(String step, String runStatus);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE t_nikshay_job_run
            SET    run_status = 'ABORTED', ended_at = NOW(),
                   error_summary = CONCAT('Auto-aborted: RUNNING for more than ', :maxMinutes, ' minutes')
            WHERE  step = :step AND run_status = 'RUNNING'
              AND  started_at < DATE_SUB(NOW(), INTERVAL :maxMinutes MINUTE)
            """, nativeQuery = true)
    int abortStaleRuns(@Param("step") String step, @Param("maxMinutes") int maxMinutes);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE t_nikshay_job_run
            SET    run_status = :status, ended_at = NOW(),
                   candidates_fetched = :fetched, devices_processed = :devices, devices_login_failed = :loginFailed,
                   cnt_enrolled = :enrolled, cnt_linked = :linked, cnt_new_episode = :newEpisode,
                   cnt_linked_no_action = :noAction, cnt_held = :held, cnt_failed = :failed,
                   error_summary = :errorSummary
            WHERE  job_run_id = :jobRunId
            """, nativeQuery = true)
    int closeRun(@Param("jobRunId") Long jobRunId, @Param("status") String status,
                 @Param("fetched") int fetched, @Param("devices") int devices, @Param("loginFailed") int loginFailed,
                 @Param("enrolled") int enrolled, @Param("linked") int linked, @Param("newEpisode") int newEpisode,
                 @Param("noAction") int noAction, @Param("held") int held, @Param("failed") int failed,
                 @Param("errorSummary") String errorSummary);
}
