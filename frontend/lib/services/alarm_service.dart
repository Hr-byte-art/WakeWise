import 'package:flutter/foundation.dart';
import 'api_service.dart';

class Alarm {
  final String id;
  final String userId;
  final String name;
  final String targetType;
  final String? baselineTime;
  final String? targetAt;
  final String? destinationLabel;
  final String? travelMode;
  final bool enabled;
  final String? nextTriggerAt;
  final int version;

  Alarm({
    required this.id,
    required this.userId,
    required this.name,
    required this.targetType,
    this.baselineTime,
    this.targetAt,
    this.destinationLabel,
    this.travelMode,
    required this.enabled,
    this.nextTriggerAt,
    required this.version,
  });

  factory Alarm.fromJson(Map<String, dynamic> json) => Alarm(
        id: json['id'],
        userId: json['userId'],
        name: json['name'],
        targetType: json['targetType'],
        baselineTime: json['baselineTime'],
        targetAt: json['targetAt'],
        destinationLabel: json['destinationLabel'],
        travelMode: json['travelMode'],
        enabled: json['enabled'],
        nextTriggerAt: json['nextTriggerAt'],
        version: json['version'],
      );

  String get targetTypeLabel {
    return switch (targetType) {
      'WAKE_AT' => '固定起床',
      'LEAVE_BY' => '最晚出门',
      'ARRIVE_BY' => '必须到达',
      'EVENT' => '日历事件',
      _ => targetType,
    };
  }
}

class AlarmDetail extends Alarm {
  final int prepDurationMin;
  final int maxAdvanceMin;
  final int maxDelayMin;
  final bool allowAutoDelay;
  final int automationLevel;
  final String? rrule;
  final int snoozeIntervalMin;
  final int maxSnoozeCount;
  final String wakeStrategyJson;

  AlarmDetail({
    required super.id,
    required super.userId,
    required super.name,
    required super.targetType,
    super.baselineTime,
    super.targetAt,
    super.destinationLabel,
    super.travelMode,
    required super.enabled,
    super.nextTriggerAt,
    required super.version,
    required this.prepDurationMin,
    required this.maxAdvanceMin,
    required this.maxDelayMin,
    required this.allowAutoDelay,
    required this.automationLevel,
    this.rrule,
    required this.snoozeIntervalMin,
    required this.maxSnoozeCount,
    required this.wakeStrategyJson,
  });

  factory AlarmDetail.fromJson(Map<String, dynamic> json) => AlarmDetail(
        id: json['id'],
        userId: json['userId'],
        name: json['name'],
        targetType: json['targetType'],
        baselineTime: json['baselineTime'],
        targetAt: json['targetAt'],
        destinationLabel: json['destinationLabel'],
        travelMode: json['travelMode'],
        enabled: json['enabled'],
        nextTriggerAt: json['nextTriggerAt'],
        version: json['version'],
        prepDurationMin: json['prepDurationMin'],
        maxAdvanceMin: json['maxAdvanceMin'],
        maxDelayMin: json['maxDelayMin'],
        allowAutoDelay: json['allowAutoDelay'],
        automationLevel: json['automationLevel'],
        rrule: json['rrule'],
        snoozeIntervalMin: json['snoozeIntervalMin'],
        maxSnoozeCount: json['maxSnoozeCount'],
        wakeStrategyJson: json['wakeStrategyJson'],
      );
}

class PlanPreview {
  final String baselineWakeAt;
  final String recommendedWakeAt;
  final int deltaMinutes;
  final double lateProbability;
  final double confidence;
  final String expiresAt;

  PlanPreview({
    required this.baselineWakeAt,
    required this.recommendedWakeAt,
    required this.deltaMinutes,
    required this.lateProbability,
    required this.confidence,
    required this.expiresAt,
  });

  factory PlanPreview.fromJson(Map<String, dynamic> json) => PlanPreview(
        baselineWakeAt: json['baselineWakeAt'],
        recommendedWakeAt: json['recommendedWakeAt'],
        deltaMinutes: json['deltaMinutes'],
        lateProbability: (json['lateProbability'] as num).toDouble(),
        confidence: (json['confidence'] as num).toDouble(),
        expiresAt: json['expiresAt'],
      );
}

class AlarmService extends ChangeNotifier {
  final ApiService _api;
  List<Alarm> _alarms = [];
  AlarmDetail? _selectedAlarm;

  AlarmService(this._api);

  List<Alarm> get alarms => _alarms;
  AlarmDetail? get selectedAlarm => _selectedAlarm;

  Future<void> loadAlarms(String userId) async {
    final response = await _api.get('/alarms', params: {'userId': userId});
    final data = response['data'] as List<dynamic>;
    _alarms = data.map((j) => Alarm.fromJson(j as Map<String, dynamic>)).toList();
    notifyListeners();
  }

  Future<AlarmDetail> loadAlarmDetail(String alarmId) async {
    final response = await _api.get('/alarms/$alarmId');
    _selectedAlarm = AlarmDetail.fromJson(response['data'] as Map<String, dynamic>);
    notifyListeners();
    return _selectedAlarm!;
  }

  Future<Alarm> createAlarm(Map<String, dynamic> body) async {
    final response = await _api.post('/alarms', body);
    final alarm = Alarm.fromJson(response['data'] as Map<String, dynamic>);
    _alarms.add(alarm);
    notifyListeners();
    return alarm;
  }

  Future<Alarm> updateAlarm(String alarmId, Map<String, dynamic> body) async {
    final response = await _api.patch('/alarms/$alarmId', body);
    final updated = Alarm.fromJson(response['data'] as Map<String, dynamic>);
    final idx = _alarms.indexWhere((a) => a.id == alarmId);
    if (idx >= 0) _alarms[idx] = updated;
    notifyListeners();
    return updated;
  }

  Future<void> deleteAlarm(String alarmId) async {
    await _api.delete('/alarms/$alarmId');
    _alarms.removeWhere((a) => a.id == alarmId);
    notifyListeners();
  }

  Future<PlanPreview> previewPlan(String alarmId, String geoHash) async {
    final response = await _api.post('/alarms/$alarmId/preview', {'geoHash': geoHash});
    return PlanPreview.fromJson(response['data'] as Map<String, dynamic>);
  }
}