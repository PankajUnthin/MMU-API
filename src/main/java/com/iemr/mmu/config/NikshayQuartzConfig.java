package com.iemr.mmu.config;

import com.iemr.mmu.service.nikshay.scheduler.NikshayScheduler;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the Quartz job + cron trigger for Ni-kshay ID generation.
 * Needs spring-boot-starter-quartz and the stock QRTZ_* tables
 * (spring.quartz.job-store-type=jdbc, org.quartz.jobStore.isClustered=true) so that
 * only one MMU-API instance runs the job at a time.
 */
@Configuration
@ConditionalOnProperty(prefix = "nikshay.job", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NikshayQuartzConfig {

    public static final String ENROLL_JOB = "nikshayEnrollJob";
    public static final String GROUP = "nikshay";

    @Bean
    public JobDetail nikshayEnrollJobDetail() {
        return JobBuilder.newJob(NikshayScheduler.EnrollJob.class)
                .withIdentity(ENROLL_JOB, GROUP)
                .withDescription("Generate Ni-kshay IDs / episodes for eligible TBMJA beneficiaries")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger nikshayEnrollTrigger(JobDetail nikshayEnrollJobDetail, NikshayProperties props) {
        return TriggerBuilder.newTrigger()
                .forJob(nikshayEnrollJobDetail)
                .withIdentity(ENROLL_JOB + "Trigger", GROUP)
                .withSchedule(CronScheduleBuilder.cronSchedule(props.getJob().getEnrollCron())
                        .withMisfireHandlingInstructionDoNothing())
                .build();
    }
}
