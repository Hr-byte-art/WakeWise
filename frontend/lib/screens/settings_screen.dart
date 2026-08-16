import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/auth_service.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthService>();
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('设置')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('账户', style: theme.textTheme.titleMedium),
                  const SizedBox(height: 12),
                  _InfoRow(label: '用户 ID', value: auth.userId ?? '-'),
                  _InfoRow(label: '设备 ID', value: auth.deviceId ?? '-'),
                  _InfoRow(label: '昵称', value: auth.displayName?.isNotEmpty == true ? auth.displayName! : '未设置'),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('自动化边界', style: theme.textTheme.titleMedium),
                  const SizedBox(height: 12),
                  const ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(Icons.verified_user_outlined),
                    title: Text('默认自动化等级'),
                    subtitle: Text('MVP 默认 L0，仅观察或请求确认'),
                  ),
                  const ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(Icons.schedule_outlined),
                    title: Text('默认调整范围'),
                    subtitle: Text('最多提前 30 分钟，最多延后 10 分钟'),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('隐私说明', style: theme.textTheme.titleMedium),
                  const SizedBox(height: 8),
                  Text(
                    'MVP 只使用设备标识、闹钟规则和天气位置网格，不保存精确地址或日历正文。',
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),
          OutlinedButton(
            onPressed: () async {
              await context.read<AuthService>().logout();
              if (context.mounted) Navigator.pop(context);
            },
            child: const Text('退出本地账号'),
          ),
        ],
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  final String label;
  final String value;

  const _InfoRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 80,
            child: Text(label, style: theme.textTheme.bodyMedium),
          ),
          Expanded(
            child: Text(
              value,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
        ],
      ),
    );
  }
}