package com.wjh.wakewise.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** 待上报的设备事件（离线兜底：网络不可用时先落库，由同步任务补报）。 */
@Entity(tableName = "outbox_events")
public class OutboxEvent {

    @PrimaryKey
    @NonNull
    public String eventId;

    public String planId;

    public String type;

    public String occurredAt;
}
