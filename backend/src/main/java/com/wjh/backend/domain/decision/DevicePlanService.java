package com.wjh.backend.domain.decision;

import com.wjh.backend.infrastructure.BusinessException;
import com.wjh.backend.infrastructure.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 设备计划服务
 * 管理移动端计划同步、ACK 确认、旧计划保留
 */
@Service
public class DevicePlanService {

    private static final Logger log = LoggerFactory.getLogger(DevicePlanService.class);

    private final DecisionHistoryRepository decisionRepo;

    public DevicePlanService(DecisionHistoryRepository decisionRepo) {
        this.decisionRepo = decisionRepo;
    }

    /**
     * 获取待同步到设备的计划
     * 返回状态为 PROPOSED 或 PENDING 的计划（已确认但未 ACK 的）
     */
    public List<DecisionHistory> getPendingSync(String userId, long afterVersion) {
        return decisionRepo.findByUserIdAndStatusInOrderByCreatedAtDesc(userId,
                List.of(DecisionHistory.DecisionStatus.PROPOSED,
                        DecisionHistory.DecisionStatus.PENDING,
                        DecisionHistory.DecisionStatus.ARMED));
    }

    /**
     * 设备确认本地调度成功
     * 只有 ACK 后计划才真正生效，旧计划同时被标记为 SUPERSEDED
     */
    @Transactional
    public DecisionHistory ack(String planId, String deviceId, long planVersion,
                                String localAlarmId, Instant scheduledWakeAt) {
        DecisionHistory dh = decisionRepo.findByPlanId(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "计划不存在"));

        if (dh.getVersion() != planVersion) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT, "计划版本不匹配");
        }

        // 标记当前计划为已装备
        dh.setStatus(DecisionHistory.DecisionStatus.ARMED);
        dh.setEffectiveAt(scheduledWakeAt);
        DecisionHistory saved = decisionRepo.save(dh);

        // 将同一闹钟的旧 ARMED 计划标记为被取代
        List<DecisionHistory> oldArmed = decisionRepo
                .findByAlarmIdAndStatusOrderByCreatedAtDesc(dh.getAlarmId(), DecisionHistory.DecisionStatus.ARMED);
        for (DecisionHistory old : oldArmed) {
            if (!old.getPlanId().equals(planId)) {
                old.setStatus(DecisionHistory.DecisionStatus.REVERTED);
                decisionRepo.save(old);
                log.info("旧计划已取代: planId={}, alarmId={}", old.getPlanId(), old.getAlarmId());
            }
        }

        log.info("设备 ACK 成功: planId={}, deviceId={}, localAlarmId={}", planId, deviceId, localAlarmId);
        return saved;
    }
}