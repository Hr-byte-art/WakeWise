package com.wjh.wakewise.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.wjh.wakewise.R;
import com.wjh.wakewise.alarm.AlarmReceiver;
import com.wjh.wakewise.alarm.RingtoneService;
import com.wjh.wakewise.databinding.ActivityAlarmRingBinding;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 晨间弱认知场景：大目标、少选项、高对比。 */
public class AlarmRingActivity extends AppCompatActivity {

    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("HH:mm").withLocale(Locale.getDefault());

    private ActivityAlarmRingBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        binding = ActivityAlarmRingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String label = getIntent().getStringExtra(AlarmReceiver.EXTRA_LABEL);
        long targetAt = getIntent().getLongExtra(AlarmReceiver.EXTRA_TARGET_AT, 0);
        binding.txtLabel.setText(label == null || label.isEmpty()
                ? getString(R.string.ring_default_title) : label);
        if (targetAt > 0) {
            binding.txtGoal.setText(getString(R.string.ring_goal,
                    CLOCK.format(java.time.Instant.ofEpochMilli(targetAt).atZone(java.time.ZoneId.systemDefault()))));
        }

        updateClock();
        binding.btnSnooze.setOnClickListener(v -> sendToService(RingtoneService.ACTION_SNOOZE));
        binding.btnDismiss.setOnClickListener(v -> sendToService(RingtoneService.ACTION_STOP));
    }

    private void updateClock() {
        TextView clock = binding.txtClock;
        Runnable tick = () -> clock.setText(CLOCK.format(LocalTime.now()));
        tick.run();
    }

    private void sendToService(String action) {
        Intent intent = new Intent(this, RingtoneService.class);
        intent.setAction(action);
        intent.putExtras(getIntent());
        startService(intent);
        finish();
    }
}
