package com.wjh.wakewise.ui;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.wjh.wakewise.R;
import com.wjh.wakewise.calendar.TomorrowEventFinder;
import com.wjh.wakewise.common.Async;
import com.wjh.wakewise.databinding.FragmentAlarmEditBinding;
import com.wjh.wakewise.network.ApiClient;
import com.wjh.wakewise.network.ApiErrors;
import com.wjh.wakewise.network.Dtos.AlarmDto;
import com.wjh.wakewise.network.Dtos.ApiEnvelope;
import com.wjh.wakewise.network.Dtos.CreateAlarmRequest;
import com.wjh.wakewise.network.Dtos.PreviewPlanDto;
import com.wjh.wakewise.network.Dtos.PreviewRequest;
import com.wjh.wakewise.network.Dtos.RuleInput;
import com.wjh.wakewise.sync.PlanSyncWorker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Response;

public class AlarmEditFragment extends Fragment {

    private static final String[] TRAVEL_MODE_CODES = {"WALK", "BIKE", "TRANSIT", "DRIVE", "MIXED", "NONE"};
    private static final int[] AUTOMATION_LEVELS = {0, 1, 2};

    private FragmentAlarmEditBinding binding;
    private String selectedTime = "08:45";

    /** 日历读取授权（点"从日历导入"时按需申请，拒绝只提示不阻塞）。 */
    private final ActivityResultLauncher<String> calendarPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    importFromCalendar();
                } else {
                    Toast.makeText(requireContext(), R.string.toast_calendar_denied, Toast.LENGTH_SHORT).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAlarmEditBinding.inflate(inflater, container, false);

        ArrayAdapter<CharSequence> typeAdapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.alarm_types, android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerType.setAdapter(typeAdapter);

        ArrayAdapter<CharSequence> modeAdapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.travel_modes, android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerMode.setAdapter(modeAdapter);

        ArrayAdapter<CharSequence> automationAdapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.automation_levels, android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerAutomation.setAdapter(automationAdapter);

        binding.inputTime.setText(selectedTime);
        binding.inputTime.setOnClickListener(v -> {
            String[] parts = selectedTime.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            new TimePickerDialog(requireContext(), (picker, h, m) -> {
                selectedTime = String.format(Locale.US, "%02d:%02d", h, m);
                binding.inputTime.setText(selectedTime);
            }, hour, minute, true).show();
        });

        binding.btnImportCalendar.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALENDAR)
                    == PackageManager.PERMISSION_GRANTED) {
                importFromCalendar();
            } else {
                calendarPermission.launch(Manifest.permission.READ_CALENDAR);
            }
        });

        binding.btnSave.setOnClickListener(v -> save());
        binding.btnPreview.setOnClickListener(v -> preview());
        return binding.getRoot();
    }

    /**
     * 从系统日历读取明日首个非全天事件，自动填目标到达时间（名称/目的地为空时一并带上）。
     * 增强链路：无日程/查询失败只 Toast 提示，不影响手动建闹钟。
     */
    private void importFromCalendar() {
        Context context = requireContext().getApplicationContext();
        Async.run(() -> {
            TomorrowEventFinder.NextEvent event = TomorrowEventFinder.firstEventTomorrow(context);
            Async.main(() -> {
                if (binding == null) {
                    return;
                }
                if (event == null) {
                    Toast.makeText(context, R.string.toast_no_event_tomorrow, Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedTime = String.format(Locale.US, "%02d:%02d",
                        event.start().getHour(), event.start().getMinute());
                binding.inputTime.setText(selectedTime);
                if (binding.inputName.getText().toString().trim().isEmpty()) {
                    binding.inputName.setText(event.title());
                }
                if (event.location() != null
                        && binding.inputDestination.getText().toString().trim().isEmpty()) {
                    binding.inputDestination.setText(event.location());
                }
                Toast.makeText(context,
                        getString(R.string.toast_event_filled, selectedTime, event.title()),
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private void save() {
        CreateAlarmRequest request = buildRequest();
        if (request == null) {
            return; // 校验失败，Toast 已展示
        }
        binding.btnSave.setEnabled(false);
        Async.run(() -> createAlarmThen(request, this::onSaveSuccess, this::onSaveError));
    }

    /** 预览：先 createAlarm 拿 alarmId，再调 /preview，把结果用 AlertDialog 展示，留在本页。 */
    private void preview() {
        CreateAlarmRequest request = buildRequest();
        if (request == null) {
            return;
        }
        binding.btnPreview.setEnabled(false);
        Async.run(() -> createAlarmThen(request,
                alarmId -> fetchAndShowPreview(alarmId, parseInt(binding.inputMaxAdvance, 30)),
                error -> Async.main(() -> {
                    if (binding != null) {
                        binding.btnPreview.setEnabled(true);
                        Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                    }
                })));
    }

    /** 从表单构造请求；返回 null 表示校验失败。 */
    private CreateAlarmRequest buildRequest() {
        String name = binding.inputName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), R.string.error_fill_all, Toast.LENGTH_SHORT).show();
            return null;
        }
        boolean arriveBy = binding.spinnerType.getSelectedItemPosition() == 0;
        String recurrence = collectRecurrence();
        if (recurrence.isEmpty()) {
            Toast.makeText(requireContext(), R.string.error_pick_day, Toast.LENGTH_SHORT).show();
            return null;
        }
        RuleInput rule = new RuleInput(
                parseInt(binding.inputPrep, 30),
                parseInt(binding.inputCommute, 40),
                parseInt(binding.inputMaxAdvance, 30),
                10,
                false,
                AUTOMATION_LEVELS[binding.spinnerAutomation.getSelectedItemPosition()]);
        return new CreateAlarmRequest(
                name,
                arriveBy ? "ARRIVE_BY" : "WAKE_AT",
                arriveBy ? selectedTime : null,
                recurrence,
                textOrNull(binding.inputDestination),
                TRAVEL_MODE_CODES[binding.spinnerMode.getSelectedItemPosition()],
                rule);
    }

    private interface AlarmIdCallback {
        void onSuccess(String alarmId);
    }

    private interface ErrorCallback {
        void onError(String message);
    }

    /** createAlarm 异步包装：成功后回调 onSuccess(alarmId)，失败回调 onError(message)。已在后台线程。 */
    private void createAlarmThen(CreateAlarmRequest request, AlarmIdCallback onSuccess, ErrorCallback onError) {
        try {
            Response<ApiEnvelope<AlarmDto>> response =
                    ApiClient.api(requireContext()).createAlarm(request).execute();
            ApiEnvelope<AlarmDto> body = response.body();
            if (!response.isSuccessful() || body == null || !body.success || body.data == null) {
                String message = body != null && body.error != null && body.error.message != null
                        ? body.error.message
                        : ApiErrors.describe(response, getString(R.string.error_network));
                onError.onError(message);
                return;
            }
            onSuccess.onSuccess(body.data.id);
        } catch (Exception e) {
            onError.onError(e.getMessage() == null ? getString(R.string.error_network) : e.getMessage());
        }
    }

    private void onSaveSuccess(String alarmId) {
        PlanSyncWorker.enqueue(requireContext());
        Async.main(() -> {
            if (getActivity() != null) {
                getActivity().getOnBackPressedDispatcher().onBackPressed();
            }
        });
    }

    private void onSaveError(String message) {
        Async.main(() -> {
            if (binding != null) {
                binding.btnSave.setEnabled(true);
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void fetchAndShowPreview(String alarmId, Integer maxAdvanceMinutes) {
        try {
            Response<ApiEnvelope<PreviewPlanDto>> response =
                    ApiClient.api(requireContext()).previewAlarm(alarmId, new PreviewRequest(maxAdvanceMinutes)).execute();
            ApiEnvelope<PreviewPlanDto> body = response.body();
            if (!response.isSuccessful() || body == null || !body.success || body.data == null) {
                String message = body != null && body.error != null && body.error.message != null
                        ? body.error.message
                        : ApiErrors.describe(response, getString(R.string.error_network));
                throw new IllegalStateException(message);
            }
            PreviewPlanDto plan = body.data;
            Async.main(() -> showPreviewDialog(plan));
        } catch (Exception e) {
            String message = e.getMessage() == null ? getString(R.string.error_network) : e.getMessage();
            Async.main(() -> {
                if (binding != null) {
                    binding.btnPreview.setEnabled(true);
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void showPreviewDialog(PreviewPlanDto plan) {
        if (binding == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.preview_baseline, hhmm(plan.baselineWakeAt))).append("\n");
        sb.append(getString(R.string.preview_recommended, hhmm(plan.recommendedWakeAt))).append("\n\n");
        int delta = plan.deltaMinutes;
        String deltaText;
        if (delta < 0) {
            deltaText = getString(R.string.preview_delta_early, -delta);
        } else if (delta > 0) {
            deltaText = getString(R.string.preview_delta_late, delta);
        } else {
            deltaText = getString(R.string.preview_delta_zero);
        }
        sb.append(deltaText).append("\n\n");
        sb.append(getString(R.string.preview_prob_before,
                Math.round(plan.lateProbabilityBefore * 100))).append("\n");
        sb.append(getString(R.string.preview_prob_after,
                Math.round(plan.lateProbabilityAfter * 100))).append("\n");
        sb.append(getString(R.string.preview_confidence,
                Math.round(plan.confidence * 100))).append("\n");
        if (plan.topFactors != null && !plan.topFactors.isEmpty()) {
            StringBuilder factors = new StringBuilder();
            for (int i = 0; i < plan.topFactors.size(); i++) {
                if (i > 0) factors.append("、");
                factors.append(plan.topFactors.get(i).label);
            }
            sb.append("\n").append(getString(R.string.preview_factors, factors.toString()));
        }
        if (plan.explanation != null && !plan.explanation.isEmpty()) {
            sb.append("\n\n").append(plan.explanation);
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.preview_title)
                .setMessage(sb.toString())
                .setPositiveButton(R.string.preview_close, null)
                .setOnDismissListener(d -> {
                    if (binding != null) {
                        binding.btnPreview.setEnabled(true);
                    }
                })
                .show();
    }

    private static String hhmm(String iso) {
        if (iso == null || iso.isEmpty()) {
            return "--:--";
        }
        try {
            java.time.Instant instant = java.time.Instant.parse(iso);
            return java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                    .withZone(java.time.ZoneId.systemDefault()).format(instant);
        } catch (Exception e) {
            return iso;
        }
    }

    private String collectRecurrence() {
        List<String> days = new ArrayList<>();
        if (binding.chkMo.isChecked()) days.add("MO");
        if (binding.chkTu.isChecked()) days.add("TU");
        if (binding.chkWe.isChecked()) days.add("WE");
        if (binding.chkTh.isChecked()) days.add("TH");
        if (binding.chkFr.isChecked()) days.add("FR");
        if (binding.chkSa.isChecked()) days.add("SA");
        if (binding.chkSu.isChecked()) days.add("SU");
        return String.join(",", days);
    }

    private int parseInt(com.google.android.material.textfield.TextInputEditText edit, int fallback) {
        try {
            return Integer.parseInt(edit.getText().toString().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private String textOrNull(com.google.android.material.textfield.TextInputEditText edit) {
        String value = edit.getText().toString().trim();
        return value.isEmpty() ? null : value;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
