package com.wjh.wakewise.alarm;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** "我出门了"打卡：上报 USER_LEFT_HOME 事件（失败自动落 outbox 补报）并撤下提醒通知。 */
public class LeftHomeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String planId = intent.getStringExtra(AlarmReceiver.EXTRA_PLAN_ID);
        if (planId != null && !planId.isEmpty()) {
            EventReporter.report(context, planId, "USER_LEFT_HOME");
        }
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.cancel(RingtoneService.NOTIFICATION_ID_LEAVE);
        }
    }
}
