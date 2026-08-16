import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/alarm_service.dart';
import '../services/auth_service.dart';

class CreateAlarmScreen extends StatefulWidget {
  const CreateAlarmScreen({super.key});

  @override
  State<CreateAlarmScreen> createState() => _CreateAlarmScreenState();
}

class _CreateAlarmScreenState extends State<CreateAlarmScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  String _targetType = 'ARRIVE_BY';
  TimeOfDay _baselineTime = const TimeOfDay(hour: 7, minute: 0);
  int _prepDurationMin = 30;
  int _maxAdvanceMin = 30;
  int _maxDelayMin = 10;
  int _automationLevel = 0;
  bool _isSubmitting = false;

  @override
  void dispose() {
    _nameController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('新建闹钟')),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            TextFormField(
              controller: _nameController,
              decoration: const InputDecoration(
                labelText: '闹钟名称',
                hintText: '例如：工作日通勤',
                border: OutlineInputBorder(),
              ),
              validator: (v) => (v == null || v.isEmpty) ? '请输入名称' : null,
            ),
            const SizedBox(height: 16),
            DropdownButtonFormField<String>(
              initialValue: _targetType,
              decoration: const InputDecoration(
                labelText: '目标类型',
                border: OutlineInputBorder(),
              ),
              items: const [
                DropdownMenuItem(value: 'WAKE_AT', child: Text('固定起床')),
                DropdownMenuItem(value: 'LEAVE_BY', child: Text('最晚出门')),
                DropdownMenuItem(value: 'ARRIVE_BY', child: Text('必须到达')),
                DropdownMenuItem(value: 'EVENT', child: Text('日历事件')),
              ],
              onChanged: (v) => setState(() => _targetType = v!),
            ),
            const SizedBox(height: 16),
            ListTile(
              title: const Text('基准时间'),
              subtitle: Text(_baselineTime.format(context)),
              trailing: const Icon(Icons.access_time),
              onTap: () async {
                final picked = await showTimePicker(
                  context: context,
                  initialTime: _baselineTime,
                );
                if (picked != null) setState(() => _baselineTime = picked);
              },
            ),
            const Divider(),
            Text('准备与调整', style: theme.textTheme.titleMedium),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: _buildNumberField('准备时长(分)', _prepDurationMin, (v) {
                    setState(() => _prepDurationMin = v);
                  }),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _buildNumberField('最大提前(分)', _maxAdvanceMin, (v) {
                    setState(() => _maxAdvanceMin = v);
                  }),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: _buildNumberField('最大延后(分)', _maxDelayMin, (v) {
                    setState(() => _maxDelayMin = v);
                  }),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _buildNumberField('自动化等级', _automationLevel, (v) {
                    setState(() => _automationLevel = v.clamp(0, 3));
                  }, max: 3),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              'L0=仅观察 L1=需确认 L2=边界内自动 L3=全流程',
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 24),
            FilledButton(
              onPressed: _isSubmitting ? null : _submit,
              child: _isSubmitting
                  ? const SizedBox(
                      height: 20,
                      width: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Text('创建闹钟'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildNumberField(String label, int value, ValueChanged<int> onChanged, {int max = 180}) {
    return TextFormField(
      initialValue: value.toString(),
      keyboardType: TextInputType.number,
      decoration: InputDecoration(
        labelText: label,
        border: const OutlineInputBorder(),
      ),
      onChanged: (v) {
        final parsed = int.tryParse(v);
        if (parsed != null) onChanged(parsed.clamp(0, max));
      },
    );
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isSubmitting = true);

    try {
      final auth = context.read<AuthService>();
      final alarmService = context.read<AlarmService>();

      await alarmService.createAlarm({
        'userId': auth.userId,
        'name': _nameController.text,
        'targetType': _targetType,
        'baselineTime':
            '${_baselineTime.hour.toString().padLeft(2, '0')}:${_baselineTime.minute.toString().padLeft(2, '0')}:00',
        'prepDurationMin': _prepDurationMin,
        'maxAdvanceMin': _maxAdvanceMin,
        'maxDelayMin': _maxDelayMin,
        'automationLevel': _automationLevel,
      });

      if (mounted) Navigator.pop(context);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('创建失败: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _isSubmitting = false);
    }
  }
}