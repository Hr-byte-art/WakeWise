package com.wjh.backend.agent;

import com.wjh.backend.domain.alarm.Alarm;
import com.wjh.backend.domain.alarm.AlarmRule;
import com.wjh.backend.domain.user.UserAccount;

import java.time.Instant;

/**
 * 晨间上下文 — 聚合天气、闹钟、用户偏好为统一决策输入
 */
public record MorningContext(
        String userId,
        String alarmId,
        Alarm alarm,
        AlarmRule rule,
        UserAccount user,
        String geoHash,
        Instant targetDate,
        Instant weatherObservedAt,
        double weatherQualityScore
) {
    public static MorningContext of(Alarm alarm, AlarmRule rule, UserAccount user,
                                     String geoHash, Instant targetDate) {
        return new MorningContext(
                user.getId(), alarm.getId(), alarm, rule, user,
                geoHash, targetDate, null, 0
        );
    }
}