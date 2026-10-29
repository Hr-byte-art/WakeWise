package com.wjh.wakewise.alarm;

import android.content.Context;

import com.wjh.wakewise.common.Async;
import com.wjh.wakewise.data.LocalAlarmStore;
import com.wjh.wakewise.data.OutboxEvent;
import com.wjh.wakewise.network.ApiClient;
import com.wjh.wakewise.network.Dtos.ApiEnvelope;
import com.wjh.wakewise.network.Dtos.DeviceEventRequest;
import com.wjh.wakewise.network.ServerClock;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import retrofit2.Response;

/** 设备事件上报：即时上报失败时落入 outbox，由同步任务补报（eventId 全局幂等）。 */
public final class EventReporter {

    private EventReporter() {
    }

    public static void report(Context context, String planId, String type) {
        String eventId = "evt-" + UUID.randomUUID();
        // 用服务端时钟校正后的时刻上报（未学习过偏移时即设备本地时间），降低墙钟漂移污染
        String occurredAt = ServerClock.now(context).toString();
        Async.run(() -> {
            try {
                Response<ApiEnvelope<Void>> response = ApiClient.api(context)
                        .reportEvent(new DeviceEventRequest(eventId, planId, type, occurredAt))
                        .execute();
                if (!response.isSuccessful()) {
                    throw new IOException("HTTP " + response.code());
                }
            } catch (Exception e) {
                OutboxEvent event = new OutboxEvent();
                event.eventId = eventId;
                event.planId = planId;
                event.type = type;
                event.occurredAt = occurredAt;
                LocalAlarmStore.get(context.getApplicationContext()).outboxEventDao().upsert(event);
            }
        });
    }

    /** 补报 outbox 中的事件（同步任务内调用，已在后台线程）。 */
    public static void flushOutbox(Context context) {
        LocalAlarmStore store = LocalAlarmStore.get(context.getApplicationContext());
        List<OutboxEvent> events = store.outboxEventDao().getAll();
        for (OutboxEvent event : events) {
            try {
                Response<ApiEnvelope<Void>> response = ApiClient.api(context)
                        .reportEvent(new DeviceEventRequest(event.eventId, event.planId,
                                event.type, event.occurredAt))
                        .execute();
                if (response.isSuccessful()) {
                    store.outboxEventDao().deleteByEventId(event.eventId);
                }
            } catch (IOException ignored) {
                break;
            }
        }
    }
}
