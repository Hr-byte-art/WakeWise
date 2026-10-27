package com.wjh.wakewise;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

public class WakeWiseApp extends Application {

    public static final String CHANNEL_ALARM = "alarm_ring";
    public static final String CHANNEL_REMINDER = "leave_home_reminder";

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
    }

    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        NotificationChannel alarm = new NotificationChannel(CHANNEL_ALARM,
                getString(R.string.channel_alarm_name), NotificationManager.IMPORTANCE_HIGH);
        alarm.setDescription(getString(R.string.channel_alarm_desc));
        alarm.enableVibration(true);
        alarm.setBypassDnd(true);
        nm.createNotificationChannel(alarm);

        NotificationChannel reminder = new NotificationChannel(CHANNEL_REMINDER,
                getString(R.string.channel_reminder_name), NotificationManager.IMPORTANCE_DEFAULT);
        reminder.setDescription(getString(R.string.channel_reminder_desc));
        nm.createNotificationChannel(reminder);
    }
}
