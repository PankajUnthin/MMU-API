package com.iemr.mmu.service.nikshay.scheduler;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import com.iemr.mmu.data.nikshay.dtos.EnrollmentOutcome.Outcome;
import com.iemr.mmu.data.nikshay.entity.NikshayJobRun;
import com.iemr.mmu.repo.nikshay.NikshayJobRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns t_nikshay_job_run: opens/closes a run, keeps the counters, answers "what happened".
 * Generic over `step` so the NAAT/X-ray push schedulers reuse it unchanged.
 * Every update is REQUIRES_NEW so counters survive even if the caller's transaction rolls back.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NikshayJobManager {

    private final NikshayJobRunRepository repo;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NikshayJobRun open(String step, String triggeredBy, Integer userId) {
        NikshayJobRun r = new NikshayJobRun();
        r.setStep(step);
        r.setTriggeredBy(triggeredBy);
        r.setTriggeredUserId(userId);
        r.setRunStatus(NikshayJobRun.RunStatus.RUNNING);
        r.setStartedAt(Timestamp.from(Instant.now()));
        r = repo.save(r);
        log.info("Ni-kshay {} run {} started ({})", step, r.getJobRunId(), triggeredBy);
        return r;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void close(Long runId, String status, String error) {
        repo.findById(runId).ifPresent(r -> {
            r.setRunStatus(NikshayJobRun.RunStatus.valueOf(status));
            r.setEndedAt(Timestamp.from(Instant.now()));
            if (error != null) r.setErrorSummary(trunc(r.getErrorSummary() == null ? error : r.getErrorSummary() + " | " + error));
            repo.save(r);
            log.info("Ni-kshay run {} {}: fetched={} enrolled={} linked={} episode={} noAction={} held={} failed={} devices={} loginFailed={}",
                    runId, status, r.getCandidatesFetched(), r.getCntEnrolled(), r.getCntLinked(), r.getCntNewEpisode(),
                    r.getCntLinkedNoAction(), r.getCntHeld(), r.getCntFailed(), r.getDevicesProcessed(), r.getDevicesLoginFailed());
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void candidatesFetched(Long runId, int n) { repo.findById(runId).ifPresent(r -> { r.setCandidatesFetched(n); repo.save(r); }); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deviceProcessed(Long runId) { repo.findById(runId).ifPresent(r -> { r.setDevicesProcessed(r.getDevicesProcessed() + 1); repo.save(r); }); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deviceLoginFailed(Long runId) { repo.findById(runId).ifPresent(r -> { r.setDevicesLoginFailed(r.getDevicesLoginFailed() + 1); repo.save(r); }); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void count(Long runId, Outcome outcome) {
        repo.findById(runId).ifPresent(r -> {
            switch (outcome) {
                case ENROLLED -> r.setCntEnrolled(r.getCntEnrolled() + 1);
                case LINKED -> r.setCntLinked(r.getCntLinked() + 1);
                case NEW_EPISODE_CREATED -> r.setCntNewEpisode(r.getCntNewEpisode() + 1);
                case LINKED_NO_ACTION -> r.setCntLinkedNoAction(r.getCntLinkedNoAction() + 1);
                case HELD -> r.setCntHeld(r.getCntHeld() + 1);
                case FAILED -> r.setCntFailed(r.getCntFailed() + 1);
            }
            repo.save(r);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void countFailed(Long runId, int n) { repo.findById(runId).ifPresent(r -> { r.setCntFailed(r.getCntFailed() + n); repo.save(r); }); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void errorSummary(Long runId, String msg) {
        repo.findById(runId).ifPresent(r -> { r.setErrorSummary(trunc(r.getErrorSummary() == null ? msg : r.getErrorSummary() + " | " + msg)); repo.save(r); });
    }

    /** Guard against two overlapping runs (second Quartz node, or manual push while job is running). */
    public boolean isRunning(String step) { return repo.existsByStepAndRunStatus(step, "RUNNING"); }

    public Optional<NikshayJobRun> lastRun(String step) { return repo.findFirstByStepOrderByStartedAtDesc(step); }

    private static String trunc(String s) { return s == null || s.length() <= 1000 ? s : s.substring(0, 1000); }
}
