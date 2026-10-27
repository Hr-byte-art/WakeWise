package com.wjh.wakewise.domain.history;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** 设备事件状态迁移：字段级幂等行为断言（对应 DecisionService 事件入口）。 */
class WakeHistoryTest {

    private static final Instant T0 = Instant.parse("2026-10-27T22:45:00Z");
    private static final Instant TARGET = Instant.parse("2026-10-28T00:45:00Z");

    @Test
    void alarmFiredKeepsFirstTriggerAcrossSnoozeRefires() {
        // 贪睡后同 planId 会再响再报 FIRED：triggeredAt 必须保首次，
        // 否则校准样本 latency = dismissedAt − 末次响铃，贪睡时间被整段挤掉
        WakeHistory h = new WakeHistory();
        h.applyDeviceEvent("ALARM_FIRED", T0, TARGET);
        h.applyDeviceEvent("ALARM_SNOOZED", T0.plusSeconds(300), TARGET);
        h.applyDeviceEvent("ALARM_FIRED", T0.plusSeconds(300), TARGET);
        h.applyDeviceEvent("ALARM_DISMISSED", T0.plusSeconds(720), TARGET);

        assertThat(h.getTriggeredAt()).isEqualTo(T0);
        assertThat(h.getDismissedAt()).isEqualTo(T0.plusSeconds(720));
        // 校准样本语义：从首次响铃到真正起床，贪睡 5 分钟计入延迟
        assertThat(h.getDismissedAt().toEpochMilli() - h.getTriggeredAt().toEpochMilli())
                .isEqualTo(12 * 60_000L);
        assertThat(h.getSnoozeCount()).isEqualTo(1);
    }

    @Test
    void dismissedFillsTriggerWhenMissing() {
        // 客户端漏报 FIRED 时 DISMISSED 兜底补 triggeredAt，样本仍可用
        WakeHistory h = new WakeHistory();
        Instant dismissed = T0.plusSeconds(300);
        h.applyDeviceEvent("ALARM_DISMISSED", dismissed, TARGET);
        assertThat(h.getTriggeredAt()).isEqualTo(dismissed);
        assertThat(h.getOutcome()).isEqualTo("SUCCESS");
    }

    @Test
    void outcomeMarksLateOnlyAfterTarget() {
        WakeHistory onTime = new WakeHistory();
        onTime.applyDeviceEvent("ALARM_DISMISSED", TARGET, TARGET);
        WakeHistory late = new WakeHistory();
        late.applyDeviceEvent("ALARM_DISMISSED", TARGET.plusSeconds(1), TARGET);
        assertThat(onTime.getOutcome()).isEqualTo("SUCCESS");
        assertThat(late.getOutcome()).isEqualTo("LATE");
    }

    @Test
    void leftHomeKeepsFirstOccurrence() {
        // outbox 重放/重复上报只记第一次出门时刻
        WakeHistory h = new WakeHistory();
        h.applyDeviceEvent("USER_LEFT_HOME", T0.plusSeconds(1800), TARGET);
        h.applyDeviceEvent("USER_LEFT_HOME", T0.plusSeconds(3600), TARGET);
        assertThat(h.getLeftHomeAt()).isEqualTo(T0.plusSeconds(1800));
    }

    @Test
    void unknownEventLeavesStateUntouched() {
        WakeHistory h = new WakeHistory();
        h.applyDeviceEvent("SOMETHING_ELSE", T0, TARGET);
        assertThat(h.getTriggeredAt()).isNull();
        assertThat(h.getDismissedAt()).isNull();
        assertThat(h.getLeftHomeAt()).isNull();
        assertThat(h.getSnoozeCount()).isZero();
        assertThat(h.getOutcome()).isEqualTo("UNKNOWN");
    }
}
