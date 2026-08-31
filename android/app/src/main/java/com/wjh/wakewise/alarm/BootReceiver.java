package com.wjh.wakewise.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.wjh.wakewise.common.Async;
import com.wjh.wakewise.sync.PlanSyncWorker;

/** 开机广播：先从本地缓存重排闹钟（离线兜底），再异步与云端同步一次。 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) {
            return;
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            PendingResult result = goAsync();
            Context appContext = context.getApplicationContext();
            Async.run(() -> {
                try {
                    AlarmScheduler.rescheduleAllFromCache(appContext);
                    PlanSyncWorker.enqueue(appContext);
                } finally {
                    result.finish();
                }
            });
        }
    }
}
