package com.wjh.wakewise.calendar;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 明日首个日历事件读取（CalendarProvider 一期接入，纯端侧）。
 *
 * 查询窗口为设备时区的明日 00:00–24:00，按开始时间升序取首个：
 * 跳过全天事件（节假日/生日日历几乎全是全天，没有可用的到达时刻），
 * 跳过昨晚开始跨入明天的长事件。任何异常（未授权/无日历账户/查询失败）
 * 一律返回 null，由调用方 Toast 提示——导入是增强链路，绝不影响建闹钟主流程。
 */
public final class TomorrowEventFinder {

    /** 明日首个非全天事件；location 可能为 null。 */
    public record NextEvent(String title, LocalTime start, String location) {
    }

    private TomorrowEventFinder() {
    }

    public static NextEvent firstEventTomorrow(Context context) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate tomorrow = LocalDate.now(zone).plusDays(1);
        long windowStart = tomorrow.atStartOfDay(zone).toInstant().toEpochMilli();
        long windowEnd = tomorrow.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();

        // Instances 按 [start, end) 查询：CONTENT_URI 依次 append 起止时间戳（官方文档写法）
        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, windowStart);
        ContentUris.appendId(builder, windowEnd);
        Uri uri = builder.build();
        String[] projection = {
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION};
        try (Cursor cursor = context.getContentResolver().query(
                uri, projection, null, null,
                CalendarContract.Instances.BEGIN + " ASC")) {
            while (cursor != null && cursor.moveToNext()) {
                if (cursor.getInt(2) == 1) {
                    continue;
                }
                long begin = cursor.getLong(1);
                if (begin < windowStart) {
                    continue;
                }
                String title = cursor.getString(0);
                if (title == null || title.isBlank()) {
                    continue;
                }
                String location = cursor.getString(3);
                LocalTime start = LocalTime.ofInstant(Instant.ofEpochMilli(begin), zone);
                return new NextEvent(title, start,
                        location == null || location.isBlank() ? null : location);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
