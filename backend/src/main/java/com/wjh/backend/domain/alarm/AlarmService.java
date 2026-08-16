package com.wjh.backend.domain.alarm;

import com.wjh.backend.infrastructure.BusinessException;
import com.wjh.backend.infrastructure.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AlarmService {

    private final AlarmRepository alarmRepo;
    private final AlarmRuleRepository ruleRepo;

    public AlarmService(AlarmRepository alarmRepo, AlarmRuleRepository ruleRepo) {
        this.alarmRepo = alarmRepo;
        this.ruleRepo = ruleRepo;
    }

    @Transactional
    public Alarm create(Alarm alarm, AlarmRule rule) {
        Alarm saved = alarmRepo.save(alarm);
        rule.setAlarmId(saved.getId());
        ruleRepo.save(rule);
        return saved;
    }

    public Alarm getById(String alarmId) {
        return alarmRepo.findById(alarmId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "闹钟不存在"));
    }

    public AlarmRule getRule(String alarmId) {
        return ruleRepo.findByAlarmId(alarmId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "闹钟规则不存在"));
    }

    public List<Alarm> listByUser(String userId) {
        return alarmRepo.findByUserId(userId);
    }

    public List<Alarm> listEnabledByUser(String userId) {
        return alarmRepo.findByUserIdAndEnabledTrue(userId);
    }

    @Transactional
    public Alarm update(String alarmId, long version, Alarm patch) {
        Alarm existing = getById(alarmId);
        if (existing.getVersion() != version) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT, "闹钟已被修改，请刷新后重试");
        }
        if (patch.getName() != null) existing.setName(patch.getName());
        if (patch.getTargetType() != null) existing.setTargetType(patch.getTargetType());
        if (patch.getBaselineTime() != null) existing.setBaselineTime(patch.getBaselineTime());
        if (patch.getTargetAt() != null) existing.setTargetAt(patch.getTargetAt());
        if (patch.getDestinationLabel() != null) existing.setDestinationLabel(patch.getDestinationLabel());
        if (patch.getTravelMode() != null) existing.setTravelMode(patch.getTravelMode());
        // enabled 通过专门的 toggle 方法修改
        return alarmRepo.save(existing);
    }

    @Transactional
    public AlarmRule updateRule(String alarmId, AlarmRule patch) {
        AlarmRule existing = getRule(alarmId);
        if (patch.getPrepDurationMin() > 0) existing.setPrepDurationMin(patch.getPrepDurationMin());
        if (patch.getMaxAdvanceMin() > 0) existing.setMaxAdvanceMin(patch.getMaxAdvanceMin());
        if (patch.getMaxDelayMin() >= 0) existing.setMaxDelayMin(patch.getMaxDelayMin());
        existing.setAllowAutoDelay(patch.isAllowAutoDelay());
        if (patch.getAutomationLevel() >= 0) existing.setAutomationLevel(patch.getAutomationLevel());
        if (patch.getRrule() != null) existing.setRrule(patch.getRrule());
        if (patch.getWakeStrategyJson() != null) existing.setWakeStrategyJson(patch.getWakeStrategyJson());
        return ruleRepo.save(existing);
    }

    @Transactional
    public void delete(String alarmId) {
        Alarm alarm = getById(alarmId);
        alarm.setEnabled(false);
        alarmRepo.save(alarm);
    }

    @Transactional
    public void toggleEnabled(String alarmId, boolean enabled) {
        Alarm alarm = getById(alarmId);
        alarm.setEnabled(enabled);
        alarmRepo.save(alarm);
    }
}