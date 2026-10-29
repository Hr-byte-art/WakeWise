package com.wjh.wakewise.network;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 服务端时钟偏差学习：从 HTTP 响应 Date 头学习"服务器 - 本地"的偏移量，
 * 事件上报时用校正后的时间，降低设备墙钟漂移对 LATE/SUCCESS 判定与行为校准的污染。
 *
 * 增强链路红线：学习全静默（任何异常都不影响请求），从未成功学习时不做校正。
 * 注意：outbox 重放必须保留事件原始时刻，不要在校验补齐路径上"顺手"套偏移。
 */
public final class ServerClock {

    private static final String PREFS = "wakewise_clock";
    private static final String KEY_OFFSET_MS = "offset_ms";
    /** EWMA 平滑传输延迟对 Date 头的瞬时污染。 */
    private static final double ALPHA = 0.25;
    /** 超过 5 分钟的偏差视为脏样本，丢弃不学（服务器迁移、错误页上的假 Date）。 */
    private static final long MAX_ABS_OFFSET_MS = 5 * 60_000L;

    private static volatile Long cachedOffset;

    private ServerClock() {
    }

    /** 从未学习过时为 false（与 offset=0 区分）。 */
    public static boolean learned(Context context) {
        return preferences(context).contains(KEY_OFFSET_MS);
    }

    public static long offsetMillis(Context context) {
        Long cached = cachedOffset;
        if (cached != null) {
            return cached;
        }
        long offset = preferences(context).getLong(KEY_OFFSET_MS, 0L);
        cachedOffset = offset;
        return offset;
    }

    /** 从响应 Date 头学习偏移；没有该头或解析失败时静默跳过。 */
    public static void learn(Context context, String dateHeader) {
        try {
            if (dateHeader == null || dateHeader.isEmpty()) {
                return;
            }
            long serverMillis = ZonedDateTime.parse(dateHeader, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli();
            long observed = serverMillis - System.currentTimeMillis();
            if (Math.abs(observed) > MAX_ABS_OFFSET_MS) {
                return;
            }
            long next = learned(context)
                    ? Math.round(ALPHA * observed + (1 - ALPHA) * offsetMillis(context))
                    : observed;
            cachedOffset = next;
            preferences(context).edit().putLong(KEY_OFFSET_MS, next).apply();
        } catch (Exception ignored) {
            // 学习失败绝不影响请求
        }
    }

    /** 校正后的当前时刻；未学习过偏移时不校正。 */
    public static Instant now(Context context) {
        return learned(context)
                ? Instant.now().plusMillis(offsetMillis(context))
                : Instant.now();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
