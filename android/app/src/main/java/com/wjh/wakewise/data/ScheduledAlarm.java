package com.wjh.wakewise.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/** 本地已调度的闹钟缓存（Room）。用于开机重排与离线兜底——云端不可达时本地闹钟仍然有效。 */
@Entity(tableName = "scheduled_alarms")
public class ScheduledAlarm {

    public static final int KIND_PLAN = 0;
    public static final int KIND_BASELINE = 1;

    @PrimaryKey
    @NonNull
    public String planId;

    public String alarmId;

    public String label;

    public long wakeAtMillis;

    public long targetAtMillis;

    public int kind;

    public boolean exact;

    public ScheduledAlarm() {
    }

    @Ignore
    public ScheduledAlarm(String planId, String alarmId, String label,
                          long wakeAtMillis, long targetAtMillis, int kind, boolean exact) {
        this.planId = planId;
        this.alarmId = alarmId;
        this.label = label;
        this.wakeAtMillis = wakeAtMillis;
        this.targetAtMillis = targetAtMillis;
        this.kind = kind;
        this.exact = exact;
    }
}
