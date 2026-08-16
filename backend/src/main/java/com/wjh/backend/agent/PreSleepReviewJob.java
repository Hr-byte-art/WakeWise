package com.wjh.backend.agent;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PreSleepReviewJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(PreSleepReviewJob.class);

    private final PlanOrchestrator orchestrator;

    public PreSleepReviewJob(PlanOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute(JobExecutionContext context) {
        log.info("Quartz 触发: PreSleepReviewJob");
        orchestrator.runPreSleepReview();
    }
}