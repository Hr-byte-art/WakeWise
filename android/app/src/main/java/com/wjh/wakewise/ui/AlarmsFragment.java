package com.wjh.wakewise.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wjh.wakewise.R;
import com.wjh.wakewise.common.Async;
import com.wjh.wakewise.databinding.FragmentAlarmsBinding;
import com.wjh.wakewise.databinding.ItemAlarmBinding;
import com.wjh.wakewise.network.ApiClient;
import com.wjh.wakewise.network.Dtos.AlarmDto;
import com.wjh.wakewise.network.Dtos.ApiEnvelope;
import com.wjh.wakewise.network.Dtos.ToggleAlarmRequest;
import com.wjh.wakewise.sync.PlanSyncWorker;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Response;

public class AlarmsFragment extends Fragment {

    private static final DateTimeFormatter NEXT_TIME =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private FragmentAlarmsBinding binding;
    private final AlarmAdapter adapter = new AlarmAdapter();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAlarmsBinding.inflate(inflater, container, false);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);
        binding.swipeRefresh.setOnRefreshListener(this::load);
        binding.fabAdd.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity main) {
                main.openEditAlarm();
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
                Response<ApiEnvelope<List<AlarmDto>>> response =
                        ApiClient.api(requireContext()).listAlarms().execute();
                ApiEnvelope<List<AlarmDto>> body = response.body();
                List<AlarmDto> alarms = body != null && body.success && body.data != null
                        ? body.data : new ArrayList<>();
                Async.main(() -> {
                    if (binding == null) {
                        return;
                    }
                    binding.swipeRefresh.setRefreshing(false);
                    binding.txtEmpty.setVisibility(alarms.isEmpty() ? View.VISIBLE : View.GONE);
                    adapter.submit(alarms);
                });
            } catch (Exception e) {
                Async.main(() -> {
                    if (binding != null) {
                        binding.swipeRefresh.setRefreshing(false);
                        Toast.makeText(requireContext(), R.string.error_network, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private class AlarmAdapter extends RecyclerView.Adapter<AlarmViewHolder> {

        private final List<AlarmDto> items = new ArrayList<>();

        void submit(List<AlarmDto> alarms) {
            items.clear();
            items.addAll(alarms);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public AlarmViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemAlarmBinding itemBinding = ItemAlarmBinding.inflate(
                    getLayoutInflater(), parent, false);
            return new AlarmViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull AlarmViewHolder holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private class AlarmViewHolder extends RecyclerView.ViewHolder {

        private final ItemAlarmBinding item;

        AlarmViewHolder(ItemAlarmBinding itemBinding) {
            super(itemBinding.getRoot());
            item = itemBinding;
        }

        void bind(AlarmDto alarm) {
            item.txtName.setText(alarm.name);
            item.txtDetail.setText(alarm.typeLabel() + " · " + alarm.displayTime()
                    + (alarm.destinationLabel == null ? "" : " · " + alarm.destinationLabel));
            item.txtNext.setText(alarm.nextTriggerAt == null ? getString(R.string.alarm_paused)
                    : getString(R.string.alarm_next, NEXT_TIME.format(Instant.parse(alarm.nextTriggerAt))));
            item.switchEnabled.setOnCheckedChangeListener(null);
            item.switchEnabled.setChecked(alarm.enabled);
            item.switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) ->
                    toggle(alarm, isChecked));
            item.getRoot().setOnLongClickListener(v -> {
                confirmDelete(alarm);
                return true;
            });
        }

        private void toggle(AlarmDto alarm, boolean enabled) {
            Async.run(() -> {
                try {
                    ApiClient.api(requireContext())
                            .updateAlarm(alarm.id, new ToggleAlarmRequest(alarm.version, enabled))
                            .execute();
                    PlanSyncWorker.enqueue(requireContext());
                } catch (Exception ignored) {
                }
                Async.main(this::loadReload);
            });
        }

        private void loadReload() {
            if (binding != null) {
                load();
            }
        }

        private void confirmDelete(AlarmDto alarm) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.delete_alarm_title)
                    .setMessage(getString(R.string.delete_alarm_message, alarm.name))
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        Async.run(() -> {
                            try {
                                // 逻辑删除：停用并清空下一次触发
                                ApiClient.api(requireContext())
                                        .updateAlarm(alarm.id, new ToggleAlarmRequest(alarm.version, false))
                                        .execute();
                            } catch (Exception ignored) {
                            }
                            Async.main(this::loadReload);
                        });
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }
}
