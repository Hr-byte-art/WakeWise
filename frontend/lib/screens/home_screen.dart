import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/auth_service.dart';
import '../services/alarm_service.dart';
import 'alarm_list_screen.dart';
import 'create_alarm_screen.dart';
import 'settings_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _initAuth();
    });
  }

  Future<void> _initAuth() async {
    final auth = context.read<AuthService>();
    await auth.init();
    if (!auth.isLoggedIn) {
      _showRegisterDialog();
    } else {
      context.read<AlarmService>().loadAlarms(auth.userId!);
    }
  }

  void _showRegisterDialog() {
    final deviceIdController = TextEditingController(text: 'flutter_device_001');
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        title: const Text('欢迎使用知晨'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('请输入设备标识以开始使用'),
            const SizedBox(height: 12),
            TextField(
              controller: deviceIdController,
              decoration: const InputDecoration(
                labelText: '设备 ID',
                border: OutlineInputBorder(),
              ),
            ),
          ],
        ),
        actions: [
          FilledButton(
            onPressed: () async {
              await context.read<AuthService>().register(deviceIdController.text);
              if (ctx.mounted) Navigator.of(ctx).pop();
              if (mounted) {
                final auth = context.read<AuthService>();
                context.read<AlarmService>().loadAlarms(auth.userId!);
              }
            },
            child: const Text('开始使用'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthService>();
    final alarmService = context.watch<AlarmService>();
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('知晨 WakeWise'),
        centerTitle: true,
        actions: [
          IconButton(
            icon: const Icon(Icons.settings_outlined),
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const SettingsScreen()),
            ),
          ),
        ],
      ),
      body: !auth.isLoggedIn
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: () => alarmService.loadAlarms(auth.userId!),
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  // 问候卡片
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(20),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '早安，${auth.displayName ?? "用户"}',
                            style: theme.textTheme.headlineSmall,
                          ),
                          const SizedBox(height: 8),
                          Text(
                            alarmService.alarms.isEmpty
                                ? '创建你的第一个智能闹钟'
                                : '你有 ${alarmService.alarms.length} 个闹钟',
                            style: theme.textTheme.bodyLarge?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),

                  // 计划卡片（如果有活跃计划）
                  if (alarmService.alarms.isNotEmpty) ...[
                    _buildPlanCard(context, alarmService.alarms.first),
                    const SizedBox(height: 16),
                  ],

                  // 快捷操作
                  Text('快捷操作', style: theme.textTheme.titleMedium),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Expanded(
                        child: _ActionCard(
                          icon: Icons.alarm_add,
                          label: '新建闹钟',
                          onTap: () => _navigateToCreateAlarm(context),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: _ActionCard(
                          icon: Icons.list_alt,
                          label: '管理闹钟',
                          onTap: () => Navigator.push(
                            context,
                            MaterialPageRoute(builder: (_) => const AlarmListScreen()),
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
    );
  }

  Widget _buildPlanCard(BuildContext context, Alarm alarm) {
    final theme = Theme.of(context);
    return Card(
      color: theme.colorScheme.primaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.wb_sunny_outlined, color: theme.colorScheme.onPrimaryContainer),
                const SizedBox(width: 8),
                Text('下一次唤醒', style: theme.textTheme.titleMedium?.copyWith(
                  color: theme.colorScheme.onPrimaryContainer,
                )),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              alarm.baselineTime ?? '07:00',
              style: theme.textTheme.displaySmall?.copyWith(
                fontWeight: FontWeight.bold,
                color: theme.colorScheme.onPrimaryContainer,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              '${alarm.targetTypeLabel} · ${alarm.name}',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onPrimaryContainer.withValues(alpha: 0.8),
              ),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                FilledButton.tonal(
                  onPressed: () {},
                  child: const Text('查看分析'),
                ),
                const SizedBox(width: 8),
                OutlinedButton(
                  onPressed: () {},
                  style: OutlinedButton.styleFrom(
                    foregroundColor: theme.colorScheme.onPrimaryContainer,
                  ),
                  child: const Text('恢复原时间'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  void _navigateToCreateAlarm(BuildContext context) {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const CreateAlarmScreen()),
    );
  }
}

class _ActionCard extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  const _ActionCard({required this.icon, required this.label, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 24, horizontal: 16),
          child: Column(
            children: [
              Icon(icon, size: 32, color: Theme.of(context).colorScheme.primary),
              const SizedBox(height: 8),
              Text(label, style: Theme.of(context).textTheme.bodyMedium),
            ],
          ),
        ),
      ),
    );
  }
}