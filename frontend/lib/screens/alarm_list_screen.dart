import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/alarm_service.dart';
import 'create_alarm_screen.dart';

class AlarmListScreen extends StatelessWidget {
  const AlarmListScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final alarmService = context.watch<AlarmService>();
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('闹钟管理')),
      body: alarmService.alarms.isEmpty
          ? Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.alarm_off, size: 64, color: theme.colorScheme.onSurfaceVariant),
                  const SizedBox(height: 16),
                  Text('还没有闹钟', style: theme.textTheme.titleMedium),
                  const SizedBox(height: 8),
                  Text('点击右下角按钮创建', style: theme.textTheme.bodyMedium),
                ],
              ),
            )
          : ListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: alarmService.alarms.length,
              itemBuilder: (context, index) {
                final alarm = alarmService.alarms[index];
                return Card(
                  margin: const EdgeInsets.only(bottom: 12),
                  child: ListTile(
                    leading: Icon(
                      alarm.enabled ? Icons.alarm : Icons.alarm_off,
                      color: alarm.enabled
                          ? theme.colorScheme.primary
                          : theme.colorScheme.onSurfaceVariant,
                    ),
                    title: Text(alarm.name),
                    subtitle: Text('${alarm.targetTypeLabel} · ${alarm.baselineTime ?? "单次"}'),
                    trailing: Switch(
                      value: alarm.enabled,
                      onChanged: null,
                    ),
                    onTap: () async {
                      await alarmService.loadAlarmDetail(alarm.id);
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(content: Text('已加载 ${alarm.name} 的详情')),
                        );
                      }
                    },
                  ),
                );
              },
            ),
      floatingActionButton: FloatingActionButton(
        onPressed: () {
          Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => const CreateAlarmScreen()),
          );
        },
        child: const Icon(Icons.add),
      ),
    );
  }
}