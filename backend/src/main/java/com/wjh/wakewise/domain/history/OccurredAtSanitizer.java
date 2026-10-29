package com.wjh.wakewise.domain.history;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * 设备事件时间消毒（纯函数）：客户端时钟不可信，occurredAt 只作参考，夹进服务端可信窗口。
 *
 * 为什么不拒收：Android 端 Retrofit 对非 2xx 抛 IOException，事件会永久留在 outbox 无限重试；
 * 因此空/非法时间戳一律兜底为服务端接收时刻，保证 outbox 能排空、事件不丢。
 *
 * 窗口语义：
 * - 下界 = 计划创建时刻 - pastMargin：事件不可能早于计划存在（留 margin 容忍"确认计划后立即关闹钟"
 *   与客户端时钟略快）；outbox 离线补报的旧时间戳通常远在窗口内，不误伤。
 * - 上界 = now + maxFutureSkew：快时钟上报未来时刻会被夹回；上界以服务端事实为准，冲突时上界赢。
 *
 * 纯函数不记日志：是否被夹取通过 Result 标志返回，由调用方决定日志策略。
 */
public final class OccurredAtSanitizer {

    /** sanitized=最终入库时刻；三个标志互斥于"原样通过"，供调用方记日志。 */
    public record Result(Instant sanitized, boolean fellBackToNow,
                         boolean clampedToFloor, boolean clampedToCeiling) {
    }

    /**
     * @param occurredAtText 客户端上报的 ISO-8601 字符串（可空、可非法）
     * @param planCreatedAt  计划创建时刻（下界锚点，null 表示不设下界）
     * @param now            服务端当前时刻（上界锚点）
     * @param maxFutureSkew  上界余量（默认 2 分钟：容忍传输延迟 + 秒级时钟误差）
     * @param pastMargin     下界余量（默认 1 分钟：容忍计划创建后立即发生的事件）
     */
    public static Result sanitize(String occurredAtText, Instant planCreatedAt, Instant now,
                                  Duration maxFutureSkew, Duration pastMargin) {
        if (occurredAtText == null || occurredAtText.isBlank()) {
            return new Result(now, true, false, false);
        }
        Instant t;
        try {
            t = Instant.parse(occurredAtText.trim());
        } catch (DateTimeParseException e) {
            return new Result(now, true, false, false);
        }
        // 下界：事件不能早于计划创建（严格 isBefore，恰等 floor 不夹）
        Instant floor = planCreatedAt == null ? null : planCreatedAt.minus(pastMargin);
        boolean toFloor = false;
        if (floor != null && t.isBefore(floor)) {
            t = floor;
            toFloor = true;
        }
        // 上界：不能晚于 now + maxFutureSkew；上界是服务端事实，与下界冲突时最终生效
        Instant ceiling = now.plus(maxFutureSkew);
        boolean toCeiling = false;
        if (t.isAfter(ceiling)) {
            t = ceiling;
            toCeiling = true;
        }
        return new Result(t, false, toFloor, toCeiling);
    }

    private OccurredAtSanitizer() {
    }
}
