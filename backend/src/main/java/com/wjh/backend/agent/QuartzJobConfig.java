package com.wjh.backend.agent;

import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzJobConfig {

    @Bean
    public JobDetail nightlyPlanJobDetail() {
        return JobBuilder.newJob(NightlyPlanJob.class)
                .withIdentity("nightlyPlanJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger nightlyPlanTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(nightlyPlanJobDetail())
                .withIdentity("nightlyPlanTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 22 * * ?"))
                .build();
    }

    @Bean
    public JobDetail preSleepReviewJobDetail() {
        return JobBuilder.newJob(PreSleepReviewJob.class)
                .withIdentity("preSleepReviewJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger preSleepReviewTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(preSleepReviewJobDetail())
                .withIdentity("preSleepReviewTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 23 * * ?"))
                .build();
    }

    @Bean
    public JobDetail morningReviewJobDetail() {
        return JobBuilder.newJob(MorningReviewJob.class)
                .withIdentity("morningReviewJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger morningReviewTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(morningReviewJobDetail())
                .withIdentity("morningReviewTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 5 * * ?"))
                .build();
    }
}