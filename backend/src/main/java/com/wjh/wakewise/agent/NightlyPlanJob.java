package com.wjh.wakewise.agent;

import com.wjh.wakewise.domain.alarm.Alarm;
import com.wjh.wakewise.domain.alarm.AlarmRepository;
import com.wjh.wakewise.domain.alarm.AlarmService;
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
 * 对目标到达型闹钟评估并生成/更新晨间计划；固定闹钟仅刷新下一次触发时间。
 * 单实例 MVP 使用内存 JobStore；业务幂等由 planId 唯一约束与状态机保证。
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

    @Autowired
    private AlarmService alarmService;

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
                if (alarm.getTargetType() == Alarm.AlarmType.ARRIVE_BY) {
                    decisionService.evaluateAndPropose(alarm, user, "NIGHTLY_PLAN");
                    evaluated++;
                } else {
                    alarm.setNextTriggerAt(alarmService.computeNextTrigger(alarm, user));
                }
            } catch (Exception e) {
                failed++;
                log.error("Nightly plan failed for alarm {}: {}", alarm.getId(), e.getMessage(), e);
            }
        }
        log.info("Nightly plan job finished: evaluated={}, failed={}", evaluated, failed);
    }
}
