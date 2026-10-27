package com.wjh.wakewise.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.wjh.wakewise.R;
import com.wjh.wakewise.common.Async;
import com.wjh.wakewise.data.SessionManager;
import com.wjh.wakewise.databinding.FragmentStatsBinding;
import com.wjh.wakewise.network.ApiClient;
import com.wjh.wakewise.network.Dtos.ApiEnvelope;
import com.wjh.wakewise.network.Dtos.UpdateProfileRequest;
import com.wjh.wakewise.network.Dtos.UserProfile;
import com.wjh.wakewise.network.Dtos.WeeklyStatsDto;

import java.util.Locale;

import retrofit2.Response;

public class StatsFragment extends Fragment {

    private FragmentStatsBinding binding;
    private String currentHomeAddress;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStatsBinding.inflate(inflater, container, false);
        binding.btnHomeAddress.setOnClickListener(v -> promptHomeAddress());
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
            // 常驻地址展示是 best-effort，失败不影响统计
            try {
                Response<ApiEnvelope<UserProfile>> me =
                        ApiClient.api(requireContext()).me().execute();
                ApiEnvelope<UserProfile> meBody = me.body();
                if (me.isSuccessful() && meBody != null && meBody.success && meBody.data != null) {
                    currentHomeAddress = meBody.data.homeAddress;
                }
            } catch (Exception ignored) {
            }
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
                    renderHomeAddress();
                });
            } catch (Exception e) {
                Async.main(() -> Toast.makeText(requireContext(),
                        R.string.error_network, Toast.LENGTH_SHORT).show());
            }
        });
    }

    /** 常驻地址（家）：真实交通 ETA 的出发地，弹窗输入保存（留空清除）。 */
    private void promptHomeAddress() {
        EditText input = new EditText(requireContext());
        input.setText(currentHomeAddress == null ? "" : currentHomeAddress);
        input.setHint(R.string.stats_home_hint);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.stats_home_address)
                .setView(input)
                .setPositiveButton(R.string.save, (d, w) ->
                        saveHomeAddress(input.getText().toString().trim()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void saveHomeAddress(String address) {
        Async.run(() -> {
            try {
                Response<ApiEnvelope<UserProfile>> response = ApiClient.api(requireContext())
                        .updateProfile(new UpdateProfileRequest(address)).execute();
                ApiEnvelope<UserProfile> body = response.body();
                if (!response.isSuccessful() || body == null || !body.success || body.data == null) {
                    throw new IllegalStateException("profile");
                }
                String saved = body.data.homeAddress;
                Async.main(() -> {
                    if (binding == null) {
                        return;
                    }
                    currentHomeAddress = saved;
                    renderHomeAddress();
                    Toast.makeText(requireContext(), R.string.stats_home_saved, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Async.main(() -> Toast.makeText(requireContext(),
                        R.string.error_network, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void renderHomeAddress() {
        binding.btnHomeAddress.setText(currentHomeAddress == null || currentHomeAddress.isEmpty()
                ? getString(R.string.stats_home_unset)
                : getString(R.string.stats_home_value, currentHomeAddress));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
