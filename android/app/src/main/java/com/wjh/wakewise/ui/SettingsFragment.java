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
import com.wjh.wakewise.databinding.FragmentSettingsBinding;
import com.wjh.wakewise.network.ApiClient;
import com.wjh.wakewise.network.ApiErrors;
import com.wjh.wakewise.network.Dtos.ApiEnvelope;
import com.wjh.wakewise.network.Dtos.ExternalServiceStatusDto;
import com.wjh.wakewise.network.Dtos.UpdateCredentialRequest;

import java.util.List;

import retrofit2.Response;

/**
 * 设置页：外部服务凭据（BYOK）。凭据只存服务端（加密落库），本页不落地任何 key，
 * 输入框永远留空（保存成功即清空）；状态行显示生效来源（你的配置/服务端默认/已禁用/未启用）。
 */
public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        binding.btnAmapSave.setOnClickListener(v -> saveAmap());
        binding.btnLlmSave.setOnClickListener(v -> saveLlm());
        binding.btnAmapClear.setOnClickListener(v -> putService("AMAP",
                new UpdateCredentialRequest(null, null, null, null), R.string.settings_cleared));
        binding.btnLlmClear.setOnClickListener(v -> putService("LLM",
                new UpdateCredentialRequest(null, null, null, null), R.string.settings_cleared));
        binding.btnAmapDisable.setOnClickListener(v -> putService("AMAP",
                new UpdateCredentialRequest(null, null, null, true), R.string.settings_saved));
        binding.btnLlmDisable.setOnClickListener(v -> putService("LLM",
                new UpdateCredentialRequest(null, null, null, true), R.string.settings_saved));
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadStatus();
    }

    private void loadStatus() {
        Async.run(() -> {
            try {
                Response<ApiEnvelope<List<ExternalServiceStatusDto>>> response = ApiClient
                        .api(requireContext()).externalServices().execute();
                ApiEnvelope<List<ExternalServiceStatusDto>> body = response.body();
                if (!response.isSuccessful() || body == null || !body.success || body.data == null) {
                    throw new IllegalStateException("status");
                }
                Async.main(() -> {
                    if (binding == null) {
                        return;
                    }
                    renderStatus(body.data);
                });
            } catch (Exception e) {
                Async.main(() -> {
                    if (binding != null) {
                        Toast.makeText(requireContext(), R.string.error_network, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void renderStatus(List<ExternalServiceStatusDto> services) {
        for (ExternalServiceStatusDto s : services) {
            boolean disabled = "DISABLED".equals(s.source);
            if ("AMAP".equals(s.service)) {
                binding.txtAmapStatus.setText(getString(R.string.settings_source_label,
                        s.sourceLabel == null ? s.source : s.sourceLabel));
                binding.btnAmapDisable.setText(disabled
                        ? R.string.settings_disabled_now : R.string.settings_disable);
                binding.btnAmapDisable.setEnabled(!disabled);
            } else if ("LLM".equals(s.service)) {
                binding.txtLlmStatus.setText(getString(R.string.settings_source_label,
                        s.sourceLabel == null ? s.source : s.sourceLabel));
                binding.btnLlmDisable.setText(disabled
                        ? R.string.settings_disabled_now : R.string.settings_disable);
                binding.btnLlmDisable.setEnabled(!disabled);
                // 用户级生效时附带非机密的 baseUrl/model；api_key 永不回显
                boolean hasDetail = s.baseUrl != null || s.model != null;
                binding.txtLlmDetail.setVisibility(hasDetail ? View.VISIBLE : View.GONE);
                if (hasDetail) {
                    binding.txtLlmDetail.setText(
                            (s.baseUrl == null ? "" : s.baseUrl) + " · " + (s.model == null ? "" : s.model));
                }
            }
        }
    }

    private void saveAmap() {
        String key = text(binding.inputAmapKey);
        if (key.isEmpty()) {
            Toast.makeText(requireContext(), R.string.error_fill_all, Toast.LENGTH_SHORT).show();
            return;
        }
        putService("AMAP", new UpdateCredentialRequest(key, null, null, null), R.string.settings_saved);
    }

    private void saveLlm() {
        String baseUrl = text(binding.inputLlmBaseUrl);
        String apiKey = text(binding.inputLlmApiKey);
        String model = text(binding.inputLlmModel);
        if (baseUrl.isEmpty() || apiKey.isEmpty() || model.isEmpty()) {
            Toast.makeText(requireContext(), R.string.settings_llm_partial, Toast.LENGTH_SHORT).show();
            return;
        }
        putService("LLM", new UpdateCredentialRequest(apiKey, baseUrl, model, null), R.string.settings_saved);
    }

    private void putService(String service, UpdateCredentialRequest request, int successMsg) {
        Async.run(() -> {
            try {
                Response<ApiEnvelope<ExternalServiceStatusDto>> response = ApiClient.api(requireContext())
                        .updateExternalService(service, request).execute();
                ApiEnvelope<ExternalServiceStatusDto> body = response.body();
                if (!response.isSuccessful() || body == null || !body.success) {
                    String message = ApiErrors.describe(response, getString(R.string.error_network));
                    Async.main(() -> {
                        if (binding != null) {
                            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                        }
                    });
                    return;
                }
                Async.main(() -> {
                    if (binding == null) {
                        return;
                    }
                    Toast.makeText(requireContext(), successMsg, Toast.LENGTH_SHORT).show();
                    // key 不回显：保存成功即清空输入，避免误以为值还留在表单里
                    if ("AMAP".equals(service)) {
                        binding.inputAmapKey.setText("");
                    } else {
                        binding.inputLlmBaseUrl.setText("");
                        binding.inputLlmApiKey.setText("");
                        binding.inputLlmModel.setText("");
                    }
                    loadStatus();
                });
            } catch (Exception e) {
                Async.main(() -> {
                    if (binding != null) {
                        Toast.makeText(requireContext(), R.string.error_network, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private static String text(com.google.android.material.textfield.TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
