package com.wjh.backend.agent;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 夜间计划 Quartz Job
 * 每晚 22:00 触发，为所有活跃闹钟生成次日计划
 */
@Component
public class NightlyPlanJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(NightlyPlanJob.class);

    private final PlanOrchestrator orchestrator;

    public NightlyPlanJob(PlanOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void execute(JobExecutionContext context) {
        log.info("Quartz 触发: NightlyPlanJob");
        orchestrator.runNightlyPlan();
    }
}