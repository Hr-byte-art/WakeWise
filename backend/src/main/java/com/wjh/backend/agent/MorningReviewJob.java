package com.wjh.backend.agent;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MorningReviewJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(MorningReviewJob.class);

    private final PlanOrchestrator orchestrator;

    public MorningReviewJob(PlanOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute(JobExecutionContext context) {
        log.info("Quartz 触发: MorningReviewJob");
        orchestrator.runMorningReview();
    }
}