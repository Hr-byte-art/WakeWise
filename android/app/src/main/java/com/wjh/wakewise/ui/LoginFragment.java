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
import com.wjh.wakewise.databinding.FragmentLoginBinding;
import com.wjh.wakewise.network.ApiClient;
import com.wjh.wakewise.network.ApiErrors;
import com.wjh.wakewise.network.Dtos.ApiEnvelope;
import com.wjh.wakewise.network.Dtos.AuthData;
import com.wjh.wakewise.network.Dtos.LoginRequest;
import com.wjh.wakewise.network.Dtos.RegisterRequest;

import retrofit2.Response;

public class LoginFragment extends Fragment {

    private FragmentLoginBinding binding;
    private SessionManager session;
    private boolean registerMode = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        session = new SessionManager(requireContext());

        binding.btnPrimary.setOnClickListener(v -> submit());
        binding.btnSwitchMode.setOnClickListener(v -> {
            registerMode = !registerMode;
            binding.btnPrimary.setText(registerMode ? R.string.register : R.string.login);
            binding.btnSwitchMode.setText(registerMode ? R.string.switch_to_login : R.string.switch_to_register);
            binding.inputDisplayName.setVisibility(registerMode ? View.VISIBLE : View.GONE);
            binding.inputCity.setVisibility(registerMode ? View.VISIBLE : View.GONE);
        });
        return binding.getRoot();
    }

    private void submit() {
        String email = binding.inputEmail.getText().toString().trim();
        String password = binding.inputPassword.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            toast(R.string.error_fill_all);
            return;
        }
        setBusy(true);
        Async.run(() -> {
            try {
                Response<ApiEnvelope<AuthData>> response = registerMode
                        ? ApiClient.api(requireContext())
                        .register(new RegisterRequest(email, password,
                                text(binding.inputDisplayName), text(binding.inputCity)))
                        .execute()
                        : ApiClient.api(requireContext())
                        .login(new LoginRequest(email, password))
                        .execute();
                ApiEnvelope<AuthData> body = response.body();
                if (!response.isSuccessful() || body == null || !body.success || body.data == null) {
                    String message = body != null && body.error != null && body.error.message != null
                            ? body.error.message
                            : ApiErrors.describe(response, getString(R.string.error_network));
                    throw new IllegalStateException(message);
                }
                session.saveAuth(body.data.token, body.data.user.id, body.data.user.email);
                Async.main(this::goMain);
            } catch (Exception e) {
                String message = e.getMessage() == null ? getString(R.string.error_network) : e.getMessage();
                Async.main(() -> {
                    setBusy(false);
                    toast(message);
                });
            }
        });
    }

    private void goMain() {
        if (getActivity() instanceof MainActivity main) {
            main.showMain();
        }
    }

    private void setBusy(boolean busy) {
        binding.btnPrimary.setEnabled(!busy);
        binding.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private String text(com.google.android.material.textfield.TextInputEditText edit) {
        String value = edit.getText().toString().trim();
        return value.isEmpty() ? null : value;
    }

    private void toast(CharSequence message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    private void toast(int resId) {
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
