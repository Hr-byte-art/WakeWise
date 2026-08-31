package com.wjh.wakewise.ui;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.wjh.wakewise.R;
import com.wjh.wakewise.common.Async;
import com.wjh.wakewise.databinding.FragmentAlarmEditBinding;
import com.wjh.wakewise.network.ApiClient;
import com.wjh.wakewise.network.ApiErrors;
import com.wjh.wakewise.network.Dtos.AlarmDto;
import com.wjh.wakewise.network.Dtos.ApiEnvelope;
import com.wjh.wakewise.network.Dtos.CreateAlarmRequest;
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

        binding.btnSave.setOnClickListener(v -> save());
        return binding.getRoot();
    }

    private void save() {
        String name = binding.inputName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), R.string.error_fill_all, Toast.LENGTH_SHORT).show();
            return;
        }
        boolean arriveBy = binding.spinnerType.getSelectedItemPosition() == 0;
        String recurrence = collectRecurrence();
        if (recurrence.isEmpty()) {
            Toast.makeText(requireContext(), R.string.error_pick_day, Toast.LENGTH_SHORT).show();
            return;
        }

        RuleInput rule = new RuleInput(
                parseInt(binding.inputPrep, 30),
                parseInt(binding.inputCommute, 40),
                parseInt(binding.inputMaxAdvance, 30),
                10,
                false,
                AUTOMATION_LEVELS[binding.spinnerAutomation.getSelectedItemPosition()]);

        CreateAlarmRequest request = new CreateAlarmRequest(
                name,
                arriveBy ? "ARRIVE_BY" : "WAKE_AT",
                arriveBy ? selectedTime : null,
                recurrence,
                textOrNull(binding.inputDestination),
                TRAVEL_MODE_CODES[binding.spinnerMode.getSelectedItemPosition()],
                rule);

        binding.btnSave.setEnabled(false);
        Async.run(() -> {
            try {
                Response<ApiEnvelope<AlarmDto>> response =
                        ApiClient.api(requireContext()).createAlarm(request).execute();
                ApiEnvelope<?> body = response.body();
                if (!response.isSuccessful() || body == null || !body.success) {
                    String message = body != null && body.error != null && body.error.message != null
                            ? body.error.message
                            : ApiErrors.describe(response, getString(R.string.error_network));
                    throw new IllegalStateException(message);
                }
                PlanSyncWorker.enqueue(requireContext());
                Async.main(() -> {
                    if (getActivity() != null) {
                        getActivity().getOnBackPressedDispatcher().onBackPressed();
                    }
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? getString(R.string.error_network) : e.getMessage();
                Async.main(() -> {
                    if (binding != null) {
                        binding.btnSave.setEnabled(true);
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
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
