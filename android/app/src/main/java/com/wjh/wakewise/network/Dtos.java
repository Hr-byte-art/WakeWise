package com.wjh.wakewise.network;

import java.util.List;

/** 与后端 /api/v1 契约对应的 DTO（public 字段 + Gson）。 */
public final class Dtos {

    private Dtos() {
    }

    public static class ApiEnvelope<T> {
        public boolean success;
        public T data;
        public ErrorBody error;
    }

    public static class ErrorBody {
        public String code;
        public String message;
    }

    // ---------------- 认证 ----------------

    public static class LoginRequest {
        public String email;
        public String password;

        public LoginRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }

    public static class RegisterRequest {
        public String email;
        public String password;
        public String displayName;
        public String city;

        public RegisterRequest(String email, String password, String displayName, String city) {
            this.email = email;
            this.password = password;
            this.displayName = displayName;
            this.city = city;
        }
    }

    public static class AuthData {
        public String token;
        public UserProfile user;
    }

    public static class UserProfile {
        public String id;
        public String email;
        public String displayName;
        public String timezone;
        public String city;
        public String homeAddress;
        public String riskPreference;
        public int automationLevel;
    }

    /** 更新常驻地址（家）：真实交通 ETA 的出发地；空串清除。 */
    public static class UpdateProfileRequest {
        public String homeAddress;

        public UpdateProfileRequest(String homeAddress) {
            this.homeAddress = homeAddress;
        }
    }

    // ---------------- 闹钟 ----------------

    public static class RuleInput {
        public int prepDurationMin;
        public int commuteMinutes;
        public int maxAdvanceMin;
        public int maxDelayMin;
        public boolean allowAutoDelay;
        public int automationLevel;

        public RuleInput(int prepDurationMin, int commuteMinutes, int maxAdvanceMin,
                         int maxDelayMin, boolean allowAutoDelay, int automationLevel) {
            this.prepDurationMin = prepDurationMin;
            this.commuteMinutes = commuteMinutes;
            this.maxAdvanceMin = maxAdvanceMin;
            this.maxDelayMin = maxDelayMin;
            this.allowAutoDelay = allowAutoDelay;
            this.automationLevel = automationLevel;
        }
    }

    public static class CreateAlarmRequest {
        public String name;
        public String targetType;
        public String targetLocalTime;
        public String recurrence;
        public String destinationLabel;
        public String travelMode;
        public RuleInput rule;

        public CreateAlarmRequest(String name, String targetType, String targetLocalTime,
                                  String recurrence, String destinationLabel, String travelMode,
                                  RuleInput rule) {
            this.name = name;
            this.targetType = targetType;
            this.targetLocalTime = targetLocalTime;
            this.recurrence = recurrence;
            this.destinationLabel = destinationLabel;
            this.travelMode = travelMode;
            this.rule = rule;
        }
    }

    public static class ToggleAlarmRequest {
        public long version;
        public Boolean enabled;

        public ToggleAlarmRequest(long version, Boolean enabled) {
            this.version = version;
            this.enabled = enabled;
        }
    }

    public static class RuleDto {
        public int prepDurationMin;
        public int commuteMinutes;
        public int maxAdvanceMin;
        public int maxDelayMin;
        public boolean allowAutoDelay;
        public int automationLevel;
        public int snoozeIntervalMin;
        public int maxSnoozeCount;
    }

    public static class AlarmDto {
        public String id;
        public String name;
        public String targetType;
        public boolean enabled;
        public String baselineTime;
        public String targetLocalTime;
        public String recurrence;
        public String destinationLabel;
        public String travelMode;
        public String nextTriggerAt;
        public long version;
        public RuleDto rule;

        public String displayTime() {
            if ("ARRIVE_BY".equals(targetType)) {
                return "到达 " + hhmm(targetLocalTime);
            }
            return "起床 " + hhmm(baselineTime);
        }

        private static String hhmm(String time) {
            return time == null ? "--:--" : time.length() >= 5 ? time.substring(0, 5) : time;
        }

        public String typeLabel() {
            return "ARRIVE_BY".equals(targetType) ? "目标到达" : "固定起床";
        }
    }

    // ---------------- 计划 ----------------

    public static class PendingPlanDto {
        public String planId;
        public String alarmId;
        public String wakeAt;
        public String targetAt;
        public int deltaMinutes;
        public String explanation;
        public long version;
    }

    public static class PlanViewDto {
        public String planId;
        public String alarmId;
        public String status;
        public String baselineWakeAt;
        public String recommendedWakeAt;
        public int deltaMinutes;
        public String explanation;
        public String reasonCode;
        public boolean requiresConfirmation;
    }

    public static class ConfirmRequest {
        public String choice;

        public ConfirmRequest(String choice) {
            this.choice = choice;
        }
    }

    public static class RevertRequest {
        public String reason;

        public RevertRequest(String reason) {
            this.reason = reason;
        }
    }

    public static class DeviceEventRequest {
        public String eventId;
        public String planId;
        public String type;
        public String occurredAt;

        public DeviceEventRequest(String eventId, String planId, String type, String occurredAt) {
            this.eventId = eventId;
            this.planId = planId;
            this.type = type;
            this.occurredAt = occurredAt;
        }
    }

    // ---------------- 晨间简报 ----------------

    public static class MorningContextDto {
        public String date;
        public WakePlanDto wakePlan;
        public WeatherDto weather;
        public TrafficDto traffic;
        public EventDto firstEvent;
        public String updatedAt;
        public CalibrationDto calibration;
    }

    /** 行为学习校准信息（后端样本不足时为 null）。 */
    public static class CalibrationDto {
        public int sampleCount;
        public double estimateMinutes;
        public String algorithmVersion;
    }

    public static class WakePlanDto {
        public String wakeAt;
        public String leaveBy;
        public String arriveBy;
        public String planId;
        public Integer deltaMinutes;
        public String explanation;
        public Boolean requiresConfirmation;
    }

    public static class WeatherDto {
        public String summary;
        public String level;
        public String alert;
        public List<String> suggestions;
    }

    public static class TrafficDto {
        public String mode;
        public int etaP50Minutes;
        public int etaP90Minutes;
        public String etaSource;
        public String status;
    }

    public static class EventDto {
        public String title;
        public String startAt;
        public String locationLabel;
    }

    // ---------------- 统计 ----------------

    public static class WeeklyStatsDto {
        public double onTimeRate;
        public int trackedMornings;
        public double averageAdjustmentMinutes;
        public double recommendationAcceptanceRate;
        public List<String> topRiskFactors;
    }

    // ---------------- 设置（BYOK 外部服务凭据） ----------------

    /** 服务状态回显：api_key 不回显；sourceLabel 中文（你的配置生效中/服务端默认/已禁用/未启用）。 */
    public static class ExternalServiceStatusDto {
        public String service;
        public String source;
        public String sourceLabel;
        public String baseUrl;
        public String model;
    }

    /** PUT /me/external-services/{service}：凭据字段与 disabled 互斥；LLM 三项须同填；全空 = 清除恢复继承。 */
    public static class UpdateCredentialRequest {
        public String apiKey;
        public String baseUrl;
        public String model;
        public Boolean disabled;

        public UpdateCredentialRequest(String apiKey, String baseUrl, String model, Boolean disabled) {
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.model = model;
            this.disabled = disabled;
        }
    }
}
