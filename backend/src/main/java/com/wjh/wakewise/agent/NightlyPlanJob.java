package com.wjh.wakewise.agent;

import com.wjh.wakewise.domain.alarm.Alarm;
import com.wjh.wakewise.domain.alarm.AlarmRepository;
import com.wjh.wakewise.domain.decision.DecisionService;
import com.wjh.wakewise.domain.user.UserAccount;
import com.wjh.wakewise.domain.user.UserAccountRepository;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 夜间计划任务（默认 22:00）：
 * 对所有启用闹钟统一调 evaluateAndPropose：
 *   - ARRIVE_BY 走分位数引擎 + 自动 ARM/待确认策略
 *   - WAKE_AT   写 KEEP_BASELINE 历史 + 刷新 next_trigger_at
 * 业务幂等由 planId 唯一约束与状态机保证。
 */
public class NightlyPlanJob implements Job {

    public static final String JOB_NAME = "nightlyPlanJob";
    public static final String TRIGGER_NAME = "nightlyPlanTrigger";

    private static final Logger log = LoggerFactory.getLogger(NightlyPlanJob.class);

    @Autowired
    private AlarmRepository alarmRepository;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private DecisionService decisionService;

    @Override
    public void execute(JobExecutionContext context) {
        log.info("Nightly plan job triggered");
        int evaluated = 0;
        int failed = 0;
        for (Alarm alarm : alarmRepository.findByEnabledTrue()) {
            UserAccount user = userRepository.findById(alarm.getUserId()).orElse(null);
            if (user == null) {
                continue;
            }
            try {
                decisionService.evaluateAndPropose(alarm, user, "NIGHTLY_PLAN");
                evaluated++;
            } catch (Exception e) {
                failed++;
                log.error("Nightly plan failed for alarm {}: {}", alarm.getId(), e.getMessage(), e);
            }
        }
        log.info("Nightly plan job finished: evaluated={}, failed={}", evaluated, failed);
    }
}
