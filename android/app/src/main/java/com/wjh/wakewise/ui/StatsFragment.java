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
import com.wjh.wakewise.data.SessionManager;
import com.wjh.wakewise.databinding.FragmentStatsBinding;
import com.wjh.wakewise.network.ApiClient;
import com.wjh.wakewise.network.Dtos.ApiEnvelope;
import com.wjh.wakewise.network.Dtos.WeeklyStatsDto;

import java.util.Locale;

import retrofit2.Response;

public class StatsFragment extends Fragment {

    private FragmentStatsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStatsBinding.inflate(inflater, container, false);
        binding.btnLogout.setOnClickListener(v -> {
            new SessionManager(requireContext()).logout();
            if (getActivity() instanceof MainActivity main) {
                main.showLogin();
            }
        });
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        Async.run(() -> {
            try {
                Response<ApiEnvelope<WeeklyStatsDto>> response =
                        ApiClient.api(requireContext()).weeklyStats().execute();
                ApiEnvelope<WeeklyStatsDto> body = response.body();
                if (!response.isSuccessful() || body == null || !body.success || body.data == null) {
                    throw new IllegalStateException("stats");
                }
                Async.main(() -> {
                    if (binding == null) {
                        return;
                    }
                    WeeklyStatsDto stats = body.data;
                    binding.txtOnTime.setText(String.format(Locale.US, "%.0f%%", stats.onTimeRate * 100));
                    binding.txtTracked.setText(String.format(Locale.US, "%d", stats.trackedMornings));
                    binding.txtAdjust.setText(String.format(Locale.US, "%.1f 分钟", stats.averageAdjustmentMinutes));
                    binding.txtAcceptance.setText(String.format(Locale.US, "%.0f%%",
                            stats.recommendationAcceptanceRate * 100));
                    binding.txtRiskFactors.setText(stats.topRiskFactors == null || stats.topRiskFactors.isEmpty()
                            ? getString(R.string.stats_no_risk)
                            : String.join("、", stats.topRiskFactors));
                });
            } catch (Exception e) {
                Async.main(() -> Toast.makeText(requireContext(),
                        R.string.error_network, Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
