package com.wjh.wakewise.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** 闹钟触发入口：上报响铃事件并启动前台响铃服务（铃声 + 振动 + 全屏通知）。 */
public class AlarmReceiver extends BroadcastReceiver {

    public static final String ACTION_FIRE = "com.wjh.wakewise.action.FIRE";
    public static final String EXTRA_PLAN_ID = "planId";
    public static final String EXTRA_ALARM_ID = "alarmId";
    public static final String EXTRA_LABEL = "label";
    public static final String EXTRA_TARGET_AT = "targetAt";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_FIRE.equals(intent.getAction())) {
            return;
        }
        String planId = intent.getStringExtra(EXTRA_PLAN_ID);
        EventReporter.report(context, planId, "ALARM_FIRED");

        Intent service = new Intent(context, RingtoneService.class);
        service.putExtras(intent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(service);
        } else {
            context.startService(service);
        }
    }
}
