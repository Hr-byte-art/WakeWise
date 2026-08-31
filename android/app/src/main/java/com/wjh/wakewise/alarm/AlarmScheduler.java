package com.wjh.wakewise.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.wjh.wakewise.data.LocalAlarmStore;
import com.wjh.wakewise.data.ScheduledAlarm;

import java.util.List;

/** 精确闹钟调度：setExactAndAllowWhileIdle，无精确权限时降级为 10 分钟窗口。 */
public final class AlarmScheduler {

    private AlarmScheduler() {
    }

    public static void schedule(Context context, ScheduledAlarm alarm) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        PendingIntent pi = firePendingIntent(context, alarm);
        boolean canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms();
        alarm.exact = canExact;
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.wakeAtMillis, pi);
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, alarm.wakeAtMillis, 10 * 60_000L, pi);
        }
    }

    public static void cancel(Context context, ScheduledAlarm alarm) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(firePendingIntent(context, alarm));
        }
    }

    /** 开机/重启后从 Room 缓存恢复全部未触发的闹钟（离线兜底：不依赖网络）。 */
    public static void rescheduleAllFromCache(Context context) {
        List<ScheduledAlarm> all = LocalAlarmStore.get(context).scheduledAlarmDao().getAll();
        long now = System.currentTimeMillis();
        for (ScheduledAlarm alarm : all) {
            if (alarm.wakeAtMillis > now) {
                schedule(context, alarm);
            }
        }
    }

    private static PendingIntent firePendingIntent(Context context, ScheduledAlarm alarm) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(AlarmReceiver.ACTION_FIRE);
        intent.putExtra(AlarmReceiver.EXTRA_PLAN_ID, alarm.planId);
        intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.alarmId);
        intent.putExtra(AlarmReceiver.EXTRA_LABEL, alarm.label == null ? "" : alarm.label);
        intent.putExtra(AlarmReceiver.EXTRA_TARGET_AT, alarm.targetAtMillis);
        return PendingIntent.getBroadcast(context, alarm.planId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
