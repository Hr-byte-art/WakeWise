package com.wjh.wakewise.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.wjh.wakewise.R;
import com.wjh.wakewise.common.Async;
import com.wjh.wakewise.databinding.FragmentHomeBinding;
import com.wjh.wakewise.network.ApiClient;
import com.wjh.wakewise.network.ApiErrors;
import com.wjh.wakewise.network.Dtos.ApiEnvelope;
import com.wjh.wakewise.network.Dtos.ConfirmRequest;
import com.wjh.wakewise.network.Dtos.MorningContextDto;
import com.wjh.wakewise.network.Dtos.PlanViewDto;
import com.wjh.wakewise.network.Dtos.RevertRequest;
import com.wjh.wakewise.network.Dtos.WakePlanDto;
import com.wjh.wakewise.sync.PlanSyncWorker;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import retrofit2.Response;

/** 首页：明早几点起、为什么、是否可靠——一次回答三个问题。 */
public class HomeFragment extends Fragment {

    private static final DateTimeFormatter HHMM =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private FragmentHomeBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        binding.btnConfirm.setOnClickListener(v -> act(true));
        binding.btnRevert.setOnClickListener(v -> act(false));
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        binding.txtOffline.setVisibility(View.GONE);
        Async.run(() -> {
            try {
                Response<ApiEnvelope<MorningContextDto>> response =
                        ApiClient.api(requireContext()).morningContext().execute();
                ApiEnvelope<MorningContextDto> body = response.body();
                if (!response.isSuccessful() || body == null || !body.success) {
                    throw new IllegalStateException("context");
                }
                Async.main(() -> render(body.data));
            } catch (Exception e) {
                Async.main(() -> {
                    if (binding != null) {
                        binding.txtOffline.setVisibility(View.VISIBLE);
                        binding.txtOffline.setText(R.string.offline_hint);
                    }
                });
            }
        });
    }

    private void render(MorningContextDto context) {
        if (binding == null) {
            return;
        }
        WakePlanDto plan = context.wakePlan;
        currentPlan = plan;
        if (plan == null) {
            binding.txtWakeTime.setText("--:--");
            binding.txtEvent.setText(R.string.home_no_plan);
            binding.txtLeaveBy.setVisibility(View.GONE);
            binding.cardPlan.setVisibility(View.GONE);
        } else {
            binding.txtWakeTime.setText(hhmm(plan.wakeAt));
            if (context.firstEvent != null) {
                String line = getString(R.string.home_for_event,
                        context.firstEvent.title,
                        context.firstEvent.locationLabel == null ? "" : context.firstEvent.locationLabel);
                binding.txtEvent.setText(line.trim());
            } else {
                binding.txtEvent.setText("");
            }
            if (plan.leaveBy != null) {
                binding.txtLeaveBy.setVisibility(View.VISIBLE);
                binding.txtLeaveBy.setText(getString(R.string.home_leave_by, hhmm(plan.leaveBy)));
            } else {
                binding.txtLeaveBy.setVisibility(View.GONE);
            }

            boolean awaiting = plan.planId != null
                    && plan.requiresConfirmation != null && plan.requiresConfirmation;
            boolean adjustable = plan.planId != null
                    && plan.deltaMinutes != null && plan.deltaMinutes != 0;
            if (plan.planId != null && (awaiting || adjustable)) {
                binding.cardPlan.setVisibility(View.VISIBLE);
                binding.txtExplanation.setText(plan.explanation == null ? "" : plan.explanation);
                binding.txtDelta.setText(plan.deltaMinutes == null ? ""
                        : (plan.deltaMinutes < 0
                        ? getString(R.string.home_delta_early, -plan.deltaMinutes)
                        : getString(R.string.home_delta_late, plan.deltaMinutes)));
                binding.btnConfirm.setVisibility(awaiting ? View.VISIBLE : View.GONE);
                binding.btnRevert.setVisibility(!awaiting && adjustable ? View.VISIBLE : View.GONE);
            } else {
                binding.cardPlan.setVisibility(View.GONE);
            }
        }

        if (context.weather != null) {
            binding.txtWeather.setText(context.weather.summary);
            String suggestion = context.weather.suggestions == null || context.weather.suggestions.isEmpty()
                    ? "" : " · " + String.join("、", context.weather.suggestions);
            binding.txtSuggestion.setText(suggestion);
        }
        if (context.traffic != null && context.traffic.etaP50Minutes > 0) {
            String line = getString(R.string.home_traffic, context.traffic.etaP50Minutes,
                    context.traffic.etaP90Minutes);
            binding.txtTraffic.setVisibility(View.VISIBLE);
            binding.txtTraffic.setText(line);
        } else {
            binding.txtTraffic.setVisibility(View.GONE);
        }
    }

    /** awaiting=true 时为确认计划，否则为撤销至原计划。 */
    private void act(boolean confirm) {
        WakePlanDto plan = currentPlan;
        if (plan == null || plan.planId == null) {
            return;
        }
        String planId = plan.planId;
        binding.btnConfirm.setEnabled(false);
        binding.btnRevert.setEnabled(false);
        String fallback = getString(R.string.error_network);
        Async.run(() -> {
            String failure = null;
            try {
                Response<ApiEnvelope<PlanViewDto>> response = confirm
                        ? ApiClient.api(requireContext()).confirmPlan(planId, new ConfirmRequest("RECOMMENDED")).execute()
                        : ApiClient.api(requireContext()).revertPlan(planId, new RevertRequest("USER_PREFERRED_BASELINE")).execute();
                if (!response.isSuccessful()) {
                    failure = ApiErrors.describe(response, "HTTP " + response.code());
                }
            } catch (Exception e) {
                failure = fallback;
            }
            String message = failure;
            PlanSyncWorker.enqueue(requireContext());
            Async.main(() -> {
                if (binding != null) {
                    binding.btnConfirm.setEnabled(true);
                    binding.btnRevert.setEnabled(true);
                    if (message != null) {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                }
                refresh();
            });
        });
    }

    private WakePlanDto currentPlan;

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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
