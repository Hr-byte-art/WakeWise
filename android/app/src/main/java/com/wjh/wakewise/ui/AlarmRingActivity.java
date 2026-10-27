package com.wjh.wakewise.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.wjh.wakewise.R;
import com.wjh.wakewise.alarm.AlarmReceiver;
import com.wjh.wakewise.alarm.RingtoneService;
import com.wjh.wakewise.common.Async;
import com.wjh.wakewise.data.MorningBriefStore;
import com.wjh.wakewise.databinding.ActivityAlarmRingBinding;
import com.wjh.wakewise.network.ApiClient;
import com.wjh.wakewise.network.Dtos.ApiEnvelope;
import com.wjh.wakewise.network.Dtos.MorningContextDto;
import com.wjh.wakewise.network.Dtos.WakePlanDto;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import retrofit2.Response;

/** 晨间弱认知场景：大目标、少选项、高对比；简报只读不可点，三级兜底（网络 / 缓存 / 中性文案）。 */
public class AlarmRingActivity extends AppCompatActivity {

    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("HH:mm").withLocale(Locale.getDefault());
    private static final DateTimeFormatter HHMM =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private ActivityAlarmRingBinding binding;
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            if (isDestroyed() || binding == null) {
                return;
            }
            binding.txtClock.setText(CLOCK.format(LocalTime.now()));
            // 对齐下一个分钟边界（+16ms 余量），唤醒时刻的时钟必须可信
            clockHandler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000 + 16);
        }
    };

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
                    CLOCK.format(Instant.ofEpochMilli(targetAt).atZone(ZoneId.systemDefault()))));
        }

        clockTick.run();
        binding.txtBrief.setText(R.string.ring_brief_loading);
        loadBrief();
        binding.btnSnooze.setOnClickListener(v -> sendToService(RingtoneService.ACTION_SNOOZE));
        binding.btnDismiss.setOnClickListener(v -> sendToService(RingtoneService.ACTION_STOP));
    }

    @Override
    protected void onDestroy() {
        clockHandler.removeCallbacks(clockTick);
        super.onDestroy();
    }

    /** 拉取晨间简报：网络成功先落缓存再渲染；失败读缓存；无缓存显示中性文案（闹钟不受影响）。 */
    private void loadBrief() {
        Async.run(() -> {
            MorningContextDto brief = null;
            try {
                Response<ApiEnvelope<MorningContextDto>> response =
                        ApiClient.api(this).morningContext().execute();
                ApiEnvelope<MorningContextDto> body = response.body();
                if (response.isSuccessful() && body != null && body.success) {
                    brief = body.data;
                    MorningBriefStore.write(this, brief);
                }
            } catch (Exception ignored) {
                // 走缓存兜底
            }
            if (brief == null) {
                brief = MorningBriefStore.read(this);
            }
            MorningContextDto rendered = brief;
            Async.main(() -> {
                if (isDestroyed() || isFinishing() || binding == null) {
                    return;
                }
                if (rendered != null) {
                    renderBrief(rendered);
                } else {
                    binding.txtBrief.setText(R.string.ring_brief_offline);
                }
            });
        });
    }

    /** 简报最多三行：决策原因 + 天气 + 出门时间/首事件。 */
    private void renderBrief(MorningContextDto brief) {
        StringBuilder text = new StringBuilder();
        WakePlanDto plan = brief.wakePlan;
        if (plan != null) {
            String reason;
            if (plan.deltaMinutes == null || plan.deltaMinutes == 0) {
                reason = getString(R.string.ring_brief_keep);
            } else if (plan.deltaMinutes < 0) {
                reason = getString(R.string.ring_brief_early, -plan.deltaMinutes);
            } else {
                reason = getString(R.string.ring_brief_late, plan.deltaMinutes);
            }
            String why = plan.explanation == null ? "" : trimTo(plan.explanation, 20);
            text.append(why.isEmpty() ? reason : reason + " · " + why);
        }
        if (brief.weather != null && brief.weather.summary != null) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(brief.weather.summary);
            if (brief.weather.alert != null && !brief.weather.alert.isEmpty()) {
                text.append(getString(R.string.ring_brief_alert_suffix));
            }
        }
        String lastLine = null;
        if (plan != null && plan.leaveBy != null) {
            lastLine = getString(R.string.ring_brief_leave_by, hhmm(plan.leaveBy));
        } else if (brief.firstEvent != null && brief.firstEvent.title != null) {
            lastLine = brief.firstEvent.title;
        }
        if (lastLine != null) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(lastLine);
        }
        binding.txtBrief.setText(text.length() == 0
                ? getString(R.string.ring_brief_keep) : text.toString());
    }

    private static String trimTo(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String hhmm(String iso) {
        if (iso == null || iso.isEmpty()) {
            return "--:--";
        }
        try {
            return HHMM.format(Instant.parse(iso));
        } catch (Exception e) {
            return iso;
        }
    }

    private void sendToService(String action) {
        Intent intent = new Intent(this, RingtoneService.class);
        intent.setAction(action);
        intent.putExtras(getIntent());
        startService(intent);
        finish();
    }
}
