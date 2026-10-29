package com.wjh.wakewise.domain.history;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** occurredAt 消毒：纯函数，直接对夹取边界做行为断言。 */
class OccurredAtSanitizerTest {

    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");
    private static final Instant PLAN_CREATED = NOW.minus(Duration.ofHours(9));
    private static final Duration FUTURE_SKEW = Duration.ofMinutes(2);
    private static final Duration PAST_MARGIN = Duration.ofMinutes(1);

    private OccurredAtSanitizer.Result sanitize(String text, Instant planCreatedAt) {
        return OccurredAtSanitizer.sanitize(text, planCreatedAt, NOW, FUTURE_SKEW, PAST_MARGIN);
    }

    @Test
    void nullFallsBackToNow() {
        OccurredAtSanitizer.Result r = sanitize(null, PLAN_CREATED);
        assertThat(r.sanitized()).isEqualTo(NOW);
        assertThat(r.fellBackToNow()).isTrue();
        assertThat(r.clampedToFloor()).isFalse();
        assertThat(r.clampedToCeiling()).isFalse();
    }

    @Test
    void blankFallsBackToNow() {
        OccurredAtSanitizer.Result r = sanitize("   ", PLAN_CREATED);
        assertThat(r.sanitized()).isEqualTo(NOW);
        assertThat(r.fellBackToNow()).isTrue();
    }

    @Test
    void unparseableFallsBackToNow() {
        // 非法格式绝不拒收：outbox 才能排空（Retrofit 非 2xx 会让事件无限重试）
        OccurredAtSanitizer.Result r = sanitize("not-a-timestamp", PLAN_CREATED);
        assertThat(r.sanitized()).isEqualTo(NOW);
        assertThat(r.fellBackToNow()).isTrue();
    }

    @Test
    void validTimestampPassesThroughUntouched() {
        Instant t = NOW.minus(Duration.ofMinutes(5));
        OccurredAtSanitizer.Result r = sanitize(t.toString(), PLAN_CREATED);
        assertThat(r.sanitized()).isEqualTo(t);
        assertThat(r.fellBackToNow()).isFalse();
        assertThat(r.clampedToFloor()).isFalse();
        assertThat(r.clampedToCeiling()).isFalse();
    }

    @Test
    void beforePlanCreationClampedToFloor() {
        // 早于计划创建 10 分钟 → 夹到 创建时刻 - 1 分钟余量
        Instant tooEarly = PLAN_CREATED.minus(Duration.ofMinutes(10));
        OccurredAtSanitizer.Result r = sanitize(tooEarly.toString(), PLAN_CREATED);
        assertThat(r.sanitized()).isEqualTo(PLAN_CREATED.minus(PAST_MARGIN));
        assertThat(r.clampedToFloor()).isTrue();
    }

    @Test
    void beyondFutureSkewClampedToCeiling() {
        // 未来 30 分钟（设备时钟快）→ 夹到 now + 2 分钟
        Instant future = NOW.plus(Duration.ofMinutes(30));
        OccurredAtSanitizer.Result r = sanitize(future.toString(), PLAN_CREATED);
        assertThat(r.sanitized()).isEqualTo(NOW.plus(FUTURE_SKEW));
        assertThat(r.clampedToCeiling()).isTrue();
    }

    @Test
    void boundaryValuesAreNotClamped() {
        // 恰等于 floor / ceiling 属于窗口内（严格不等号语义）
        OccurredAtSanitizer.Result atFloor = sanitize(
                PLAN_CREATED.minus(PAST_MARGIN).toString(), PLAN_CREATED);
        assertThat(atFloor.sanitized()).isEqualTo(PLAN_CREATED.minus(PAST_MARGIN));
        assertThat(atFloor.clampedToFloor()).isFalse();

        OccurredAtSanitizer.Result atCeiling = sanitize(NOW.plus(FUTURE_SKEW).toString(), PLAN_CREATED);
        assertThat(atCeiling.sanitized()).isEqualTo(NOW.plus(FUTURE_SKEW));
        assertThat(atCeiling.clampedToCeiling()).isFalse();
    }

    @Test
    void nullPlanCreatedAtSkipsLowerBound() {
        // 无计划锚点时不设下界：早时刻原样通过
        Instant old = NOW.minus(Duration.ofDays(2));
        OccurredAtSanitizer.Result r = sanitize(old.toString(), null);
        assertThat(r.sanitized()).isEqualTo(old);
        assertThat(r.clampedToFloor()).isFalse();
    }

    @Test
    void ceilingWinsWhenBoundsConflict() {
        // 病态数据（计划创建在未来）+ 早时刻：先夹到 floor，再被上界夹回，两个标志同真
        Instant pathologicalPlan = NOW.plus(Duration.ofHours(1));
        OccurredAtSanitizer.Result r = sanitize(
                NOW.minus(Duration.ofHours(2)).toString(), pathologicalPlan);
        assertThat(r.sanitized()).isEqualTo(NOW.plus(FUTURE_SKEW));
        assertThat(r.clampedToFloor()).isTrue();
        assertThat(r.clampedToCeiling()).isTrue();
    }
}
