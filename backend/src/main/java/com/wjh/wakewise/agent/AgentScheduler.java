package com.wjh.wakewise.agent;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 注册 Quartz 夜间计划任务；幂等：已存在则替换触发器。 */
@Component
public class AgentScheduler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentScheduler.class);

    private final Scheduler scheduler;
    private final String cron;

    public AgentScheduler(Scheduler scheduler,
                          @Value("${wakewise.nightly-plan-cron}") String cron) {
        this.scheduler = scheduler;
        this.cron = cron;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        JobDetail job = JobBuilder.newJob(NightlyPlanJob.class)
                .withIdentity(NightlyPlanJob.JOB_NAME, "wakewise")
                .storeDurably()
                .build();
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(NightlyPlanJob.TRIGGER_NAME, "wakewise")
                .forJob(JobKey.jobKey(NightlyPlanJob.JOB_NAME, "wakewise"))
                .withSchedule(CronScheduleBuilder.cronSchedule(cron))
                .build();
        if (scheduler.checkExists(job.getKey())) {
            scheduler.rescheduleJob(trigger.getKey(), trigger);
        } else {
            scheduler.scheduleJob(job, trigger);
        }
        log.info("Nightly plan job scheduled, cron={}", cron);
    }
}
