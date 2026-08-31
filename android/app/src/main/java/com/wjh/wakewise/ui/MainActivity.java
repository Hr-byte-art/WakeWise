package com.wjh.wakewise.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.wjh.wakewise.R;
import com.wjh.wakewise.data.SessionManager;
import com.wjh.wakewise.sync.PlanSyncWorker;
import com.wjh.wakewise.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private SessionManager session;
    private ActivityMainBinding binding;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        requestNotificationPermissionIfNeeded();
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                show(new HomeFragment());
                return true;
            } else if (id == R.id.nav_alarms) {
                show(new AlarmsFragment());
                return true;
            } else if (id == R.id.nav_stats) {
                show(new StatsFragment());
                return true;
            }
            return false;
        });

        if (session.hasToken()) {
            showMain();
        } else {
            showLogin();
        }
    }

    public void showMain() {
        binding.bottomNav.setVisibility(android.view.View.VISIBLE);
        PlanSyncWorker.enqueue(this);
        show(new HomeFragment());
    }

    public void showLogin() {
        binding.bottomNav.setVisibility(android.view.View.GONE);
        show(new LoginFragment());
    }

    public void openEditAlarm() {
        show(new AlarmEditFragment());
    }

    private void show(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .setReorderingAllowed(true)
                .commit();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }
}
