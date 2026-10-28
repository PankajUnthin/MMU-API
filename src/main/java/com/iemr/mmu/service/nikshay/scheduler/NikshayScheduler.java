package com.iemr.mmu.service.nikshay.scheduler;


import java.util.Collection;
import java.util.List;

import com.iemr.mmu.config.NikshayProperties;
import com.iemr.mmu.data.nikshay.entity.NikshayJobRun;
import com.iemr.mmu.service.nikshay.NikshayEnrollmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

/**
 * Entry points for every Ni-kshay scheduler. Thin: open run → delegate → close run.
 * The Quartz job classes are nested so one file lists all schedulers; each one is a few lines.
 *
 * Manual push (UI "Push now" / "Reprocess") calls {@link #runEnrollManually(Collection, Integer)}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NikshayScheduler {

    private final NikshayProperties props;
    private final NikshayJobManager jobManager;
    private final NikshayEnrollmentService enrollmentService;

    // ------------------------------------------------------------------ ID generation

    /** Quartz-invoked. */
    public void runEnroll() {
        if (!props.getJob().isEnabled()) { log.info("Ni-kshay enroll job disabled by property"); return; }
        if (jobManager.isRunning("ENROLL")) { log.warn("Ni-kshay enroll job already RUNNING – skipping this fire"); return; }
        NikshayJobRun run = jobManager.open("ENROLL", "JOB", null);
        execute(run, ctx -> enrollmentService.run(ctx));
    }

    /** UI-invoked for specific beneficiaries. Returns the run id so the UI can poll its summary. */
    public Long runEnrollManually(List<Long> benRegIds, Integer userId) {
        NikshayJobRun run = jobManager.open("ENROLL", "MANUAL", userId);
        execute(run, ctx -> enrollmentService.runForBeneficiaries(ctx, benRegIds));
        return run.getJobRunId();
    }

    private void execute(NikshayJobRun run, java.util.function.Consumer<NikshayEnrollmentService.RunContext> body) {
        NikshayEnrollmentService.RunContext ctx = new NikshayEnrollmentService.RunContext(run.getJobRunId(), run.getTriggeredBy(), run.getTriggeredUserId());
        try {
            body.accept(ctx);
            jobManager.close(run.getJobRunId(), "COMPLETED", null);
        } catch (Throwable t) {
            log.error("Ni-kshay run {} aborted: {}", run.getJobRunId(), t.getMessage(), t);
            jobManager.close(run.getJobRunId(), "ABORTED", t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------ Quartz job classes

    /** Registered by NikshayQuartzConfig. @DisallowConcurrentExecution + clustered JDBC store = single run. */
    @DisallowConcurrentExecution
    public static class EnrollJob extends QuartzJobBean {
        @Autowired private NikshayScheduler scheduler;            // injected by Spring's SpringBeanJobFactory
        @Override protected void executeInternal(JobExecutionContext context) { scheduler.runEnroll(); }
    }

    // Next release: NaatTestPushJob, XrayTestPushJob – same shape, different service.
}
