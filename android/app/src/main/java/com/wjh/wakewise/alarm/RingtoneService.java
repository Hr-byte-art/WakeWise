package com.wjh.wakewise.alarm;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.wjh.wakewise.R;
import com.wjh.wakewise.WakeWiseApp;
import com.wjh.wakewise.common.Async;
import com.wjh.wakewise.data.LocalAlarmStore;
import com.wjh.wakewise.data.ScheduledAlarm;
import com.wjh.wakewise.ui.AlarmRingActivity;

/** 前台响铃服务：循环铃声 + 振动 + 全屏意图通知；支持关闭与贪睡。 */
public class RingtoneService extends Service {

    public static final String ACTION_STOP = "com.wjh.wakewise.action.STOP";
    public static final String ACTION_SNOOZE = "com.wjh.wakewise.action.SNOOZE";
    private static final long SNOOZE_MILLIS = 5 * 60_000L;
    private static final int NOTIFICATION_ID = 1001;

    private MediaPlayer player;
    private Vibrator vibrator;
    private String planId;
    private String label;

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        planId = intent == null ? null : intent.getStringExtra(AlarmReceiver.EXTRA_PLAN_ID);
        label = intent == null ? "" : intent.getStringExtra(AlarmReceiver.EXTRA_LABEL);

        if (ACTION_STOP.equals(action)) {
            EventReporter.report(this, planId, "ALARM_DISMISSED");
            stopRinging();
            return START_NOT_STICKY;
        }
        if (ACTION_SNOOZE.equals(action)) {
            EventReporter.report(this, planId, "ALARM_SNOOZED");
            snooze();
            return START_NOT_STICKY;
        }
        startRinging(intent);
        return START_STICKY;
    }

    private void startRinging(Intent intent) {
        Intent ring = new Intent(this, AlarmRingActivity.class);
        ring.putExtras(intent == null ? new Intent() : intent);
        PendingIntent fullScreenPi = PendingIntent.getActivity(this, 2001, ring,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, WakeWiseApp.CHANNEL_ALARM)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle(label == null || label.isEmpty()
                        ? getString(R.string.ring_default_title) : label)
                .setContentText(getString(R.string.ring_default_text))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setFullScreenIntent(fullScreenPi, true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        startSound();
        startVibration();
    }

    private void startSound() {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) {
                uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }
            player = new MediaPlayer();
            player.setDataSource(this, uri);
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            if (player != null) {
                player.release();
                player = null;
            }
        }
    }

    private void startVibration() {
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null) {
            return;
        }
        long[] pattern = {0, 600, 400, 600, 2000};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
        } else {
            vibrator.vibrate(pattern, 0);
        }
    }

    private void snooze() {
        final String id = planId;
        Async.run(() -> {
            if (id == null) {
                return;
            }
            LocalAlarmStore store = LocalAlarmStore.get(RingtoneService.this);
            ScheduledAlarm alarm = store.scheduledAlarmDao().findByPlanId(id);
            if (alarm != null) {
                alarm.wakeAtMillis = System.currentTimeMillis() + SNOOZE_MILLIS;
                store.scheduledAlarmDao().upsert(alarm);
                AlarmScheduler.schedule(RingtoneService.this, alarm);
            }
        });
        stopRinging();
    }

    private void stopRinging() {
        if (player != null) {
            try {
                if (player.isPlaying()) {
                    player.stop();
                }
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
