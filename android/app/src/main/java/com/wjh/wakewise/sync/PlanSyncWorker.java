package com.wjh.wakewise.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.wjh.wakewise.alarm.AlarmScheduler;
import com.wjh.wakewise.alarm.EventReporter;
import com.wjh.wakewise.data.LocalAlarmStore;
import com.wjh.wakewise.data.ScheduledAlarm;
import com.wjh.wakewise.data.SessionManager;
import com.wjh.wakewise.network.ApiClient;
import com.wjh.wakewise.network.Dtos.AlarmDto;
import com.wjh.wakewise.network.Dtos.ApiEnvelope;
import com.wjh.wakewise.network.Dtos.PendingPlanDto;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import retrofit2.Response;

/**
 * 设备计划同步（可靠性链路的移动端半边）：
 * 1. 拉取 pending 计划 -> 本地精确调度 -> 先建新后删旧 -> 回 ACK；
 * 2. 固定闹钟（WAKE_AT）在端侧直接调度，不依赖云端计划；
 * 3. 补报 outbox 事件。
 * 云端不可达时本地已调度的闹钟不受影响。
 */
public class PlanSyncWorker extends Worker {

    private static final String ONE_TIME_WORK = "wakewise-plan-sync";
    private static final String PERIODIC_WORK = "wakewise-plan-sync-periodic";

    public PlanSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void enqueue(Context context) {
        Constraints network = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(PlanSyncWorker.class)
                        .setConstraints(network)
                        .build());
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(PlanSyncWorker.class, 6, TimeUnit.HOURS)
                        .setConstraints(network)
                        .build());
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SessionManager session = new SessionManager(context);
        if (!session.hasToken()) {
            return Result.success();
        }
        try {
            EventReporter.flushOutbox(context);
            syncDecisionPlans(context);
            scheduleFixedAlarms(context);
            return Result.success();
        } catch (IOException e) {
            return Result.retry();
        } catch (Exception e) {
            return Result.failure();
        }
    }

    private void syncDecisionPlans(Context context) throws IOException {
        LocalAlarmStore store = LocalAlarmStore.get(context);
        Response<ApiEnvelope<List<PendingPlanDto>>> response =
                ApiClient.api(context).pendingPlans().execute();
        if (!response.isSuccessful() || response.body() == null || !response.body().success) {
            throw new IOException("pendingPlans failed");
        }
        for (PendingPlanDto plan : response.body().data) {
            long wakeAt = Instant.parse(plan.wakeAt).toEpochMilli();
            long targetAt = plan.targetAt == null ? 0 : Instant.parse(plan.targetAt).toEpochMilli();

            ScheduledAlarm existing = store.scheduledAlarmDao().findByPlanId(plan.planId);
            if (existing == null || existing.wakeAtMillis != wakeAt) {
                ScheduledAlarm alarm = new ScheduledAlarm(plan.planId, plan.alarmId,
                        "计划唤醒", wakeAt, targetAt, ScheduledAlarm.KIND_PLAN, false);
                store.scheduledAlarmDao().upsert(alarm);
                AlarmScheduler.schedule(context, alarm);
            }
            // 先建新后删旧：同闹钟的其它本地计划全部取消
            for (ScheduledAlarm old : store.scheduledAlarmDao().findByAlarmId(plan.alarmId)) {
                if (!old.planId.equals(plan.planId)) {
                    AlarmScheduler.cancel(context, old);
                    store.scheduledAlarmDao().delete(old);
                }
            }
            try {
                ApiClient.api(context).ackPlan(plan.planId).execute();
            } catch (IOException ignored) {
                // ACK 失败不影响本地已生效的闹钟；下次同步重试
            }
        }
    }

    private void scheduleFixedAlarms(Context context) throws IOException {
        Response<ApiEnvelope<List<AlarmDto>>> response = ApiClient.api(context).listAlarms().execute();
        if (!response.isSuccessful() || response.body() == null || !response.body().success) {
            throw new IOException("listAlarms failed");
        }
        LocalAlarmStore store = LocalAlarmStore.get(context);
        for (AlarmDto alarm : response.body().data) {
            if (!alarm.enabled || !"WAKE_AT".equals(alarm.targetType) || alarm.baselineTime == null) {
                continue;
            }
            long next = nextOccurrenceMillis(alarm.recurrence, alarm.baselineTime);
            String syntheticId = "baseline-" + alarm.id;
            ScheduledAlarm existing = store.scheduledAlarmDao().findByPlanId(syntheticId);
            if (existing != null && existing.wakeAtMillis == next) {
                continue;
            }
            if (existing != null) {
                AlarmScheduler.cancel(context, existing);
                store.scheduledAlarmDao().delete(existing);
            }
            ScheduledAlarm scheduled = new ScheduledAlarm(syntheticId, alarm.id, alarm.name,
                    next, 0, ScheduledAlarm.KIND_BASELINE, false);
            store.scheduledAlarmDao().upsert(scheduled);
            AlarmScheduler.schedule(context, scheduled);
        }
    }

    /** 在设备本地时区计算 WAKE_AT 的下一次响铃时刻。 */
    static long nextOccurrenceMillis(String recurrenceCsv, String baselineTime) {
        Set<DayOfWeek> days = EnumSet.allOf(DayOfWeek.class);
        if (recurrenceCsv != null && !recurrenceCsv.isBlank()) {
            days = EnumSet.noneOf(DayOfWeek.class);
            for (String token : recurrenceCsv.split(",")) {
                days.add(DayOfWeek.valueOf(named(token.trim())));
            }
        }
        LocalTime time = LocalTime.parse(baselineTime);
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        for (int i = 0; i <= 7; i++) {
            LocalDate date = today.plusDays(i);
            if (!days.contains(date.getDayOfWeek())) {
                continue;
            }
            long millis = date.atTime(time).atZone(zone).toInstant().toEpochMilli();
            if (millis > System.currentTimeMillis()) {
                return millis;
            }
        }
        return LocalDate.now(zone).plusDays(1).atTime(time).atZone(zone).toInstant().toEpochMilli();
    }

    private static String named(String code) {
        return switch (code.toUpperCase()) {
            case "MO" -> "MONDAY";
            case "TU" -> "TUESDAY";
            case "WE" -> "WEDNESDAY";
            case "TH" -> "THURSDAY";
            case "FR" -> "FRIDAY";
            case "SA" -> "SATURDAY";
            case "SU" -> "SUNDAY";
            default -> throw new IllegalArgumentException("bad day code: " + code);
        };
    }
}
