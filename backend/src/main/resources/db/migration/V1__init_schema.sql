-- WakeWise V1 初始 schema（依据《WakeWise-数据库设计》v1.0.0，MVP 精简版）
-- 时间统一 UTC；ID 为 ULID VARCHAR(26)

CREATE TABLE user_account (
    id               VARCHAR(26)     NOT NULL PRIMARY KEY,
    email            VARCHAR(254) NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    display_name     VARCHAR(80)  NOT NULL,
    timezone         VARCHAR(64)  NOT NULL DEFAULT 'Asia/Shanghai',
    locale           VARCHAR(16)  NOT NULL DEFAULT 'zh-CN',
    city             VARCHAR(80)  NULL,
    risk_preference  VARCHAR(32)  NOT NULL DEFAULT 'BALANCED',
    automation_level INT      NOT NULL DEFAULT 1,
    status           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at    DATETIME(3)  NULL,
    created_at       DATETIME(3)  NOT NULL,
    updated_at       DATETIME(3)  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    UNIQUE KEY uk_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE alarm (
    id                VARCHAR(26)     NOT NULL PRIMARY KEY,
    user_id           VARCHAR(26)     NOT NULL,
    name              VARCHAR(100) NOT NULL,
    target_type       VARCHAR(16)  NOT NULL COMMENT 'WAKE_AT / ARRIVE_BY',
    baseline_time     TIME         NULL COMMENT 'WAKE_AT 基准起床时刻',
    target_local_time TIME         NULL COMMENT 'ARRIVE_BY 目标到达时刻',
    recurrence        VARCHAR(64)  NULL COMMENT '周几 CSV，如 MO,TU,WE,TH,FR；NULL=每天',
    destination_label VARCHAR(120) NULL,
    travel_mode       VARCHAR(16)  NULL COMMENT 'WALK/BIKE/TRANSIT/DRIVE/MIXED/NONE',
    enabled           BOOLEAN      NOT NULL DEFAULT TRUE,
    next_trigger_at   DATETIME(3)  NULL,
    created_at        DATETIME(3)  NOT NULL,
    updated_at        DATETIME(3)  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    KEY idx_alarm_user_enabled (user_id, enabled),
    KEY idx_alarm_next_trigger (next_trigger_at, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE alarm_rule (
    id                  VARCHAR(26)    NOT NULL PRIMARY KEY,
    alarm_id            VARCHAR(26)    NOT NULL,
    prep_duration_min   INT    NOT NULL,
    commute_minutes     INT    NOT NULL DEFAULT 0,
    max_advance_min     INT    NOT NULL,
    max_delay_min       INT    NOT NULL,
    allow_auto_delay    BOOLEAN     NOT NULL DEFAULT FALSE,
    automation_level    INT     NOT NULL DEFAULT 1,
    snooze_interval_min INT    NOT NULL DEFAULT 5,
    max_snooze_count    INT     NOT NULL DEFAULT 2,
    created_at          DATETIME(3) NOT NULL,
    updated_at          DATETIME(3) NOT NULL,
    UNIQUE KEY uk_rule_alarm (alarm_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE weather_cache (
    id                VARCHAR(26)     NOT NULL PRIMARY KEY,
    provider          VARCHAR(32)  NOT NULL,
    city              VARCHAR(80)  NOT NULL,
    forecast_time     DATETIME(3)  NOT NULL COMMENT '预报目标时段起点',
    observed_at       DATETIME(3)  NOT NULL,
    temperature_c     DECIMAL(5,2) NULL,
    precip_probability DECIMAL(5,4) NULL,
    precip_mm         DECIMAL(8,2) NULL,
    weather_code      INT          NULL,
    alert_level       VARCHAR(24)  NULL,
    quality_score     DECIMAL(5,4) NOT NULL,
    payload_json      TEXT         NULL,
    expires_at        DATETIME(3)  NOT NULL,
    created_at        DATETIME(3)  NOT NULL,
    KEY idx_weather_lookup (city, forecast_time, observed_at),
    KEY idx_weather_expire (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE decision_history (
    id                      VARCHAR(26)      NOT NULL PRIMARY KEY,
    plan_id                 VARCHAR(26)      NOT NULL,
    user_id                 VARCHAR(26)      NOT NULL,
    alarm_id                VARCHAR(26)      NOT NULL,
    baseline_at             DATETIME(3)   NOT NULL,
    recommended_at          DATETIME(3)   NOT NULL,
    target_at               DATETIME(3)   NOT NULL,
    effective_at            DATETIME(3)   NULL,
    status                  VARCHAR(24)   NOT NULL COMMENT 'PROPOSED/DISPATCHING/ARMED/REVERTED/EXECUTED/SUPERSEDED/FAILED',
    delta_minutes           INT           NOT NULL,
    late_probability_before DECIMAL(6,5)  NOT NULL,
    late_probability_after  DECIMAL(6,5)  NOT NULL,
    risk_score              DECIMAL(7,4)  NOT NULL,
    confidence              DECIMAL(5,4)  NOT NULL,
    factor_contribution_json TEXT      NOT NULL,
    reason_code             VARCHAR(64)   NOT NULL,
    explanation             VARCHAR(1000) NOT NULL,
    model_version           VARCHAR(40)   NOT NULL,
    trigger_type            VARCHAR(32)   NOT NULL DEFAULT 'MANUAL',
    created_at              DATETIME(3)   NOT NULL,
    updated_at              DATETIME(3)   NOT NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,
    UNIQUE KEY uk_plan (plan_id),
    KEY idx_decision_alarm (alarm_id, created_at),
    KEY idx_decision_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE wake_history (
    id           VARCHAR(26)    NOT NULL PRIMARY KEY,
    user_id      VARCHAR(26)    NOT NULL,
    alarm_id     VARCHAR(26)    NOT NULL,
    plan_id      VARCHAR(26)    NULL,
    scheduled_at DATETIME(3) NOT NULL,
    triggered_at DATETIME(3) NULL,
    dismissed_at DATETIME(3) NULL,
    snooze_count INT     NOT NULL DEFAULT 0,
    outcome      VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT 'SUCCESS/LATE/MISSED/UNKNOWN',
    created_at   DATETIME(3) NOT NULL,
    KEY idx_wake_user_time (user_id, scheduled_at),
    KEY idx_wake_alarm_time (alarm_id, scheduled_at),
    KEY idx_wake_plan (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notification (
    id          VARCHAR(26)      NOT NULL PRIMARY KEY,
    user_id     VARCHAR(26)      NOT NULL,
    decision_id VARCHAR(26)      NULL,
    type        VARCHAR(40)   NOT NULL,
    title       VARCHAR(160)  NOT NULL,
    body        VARCHAR(1000) NOT NULL,
    status      VARCHAR(16)   NOT NULL DEFAULT 'SENT',
    read_at     DATETIME(3)   NULL,
    created_at  DATETIME(3)   NOT NULL,
    KEY idx_notification_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE feedback (
    id          VARCHAR(26)      NOT NULL PRIMARY KEY,
    user_id     VARCHAR(26)      NOT NULL,
    decision_id VARCHAR(26)      NULL,
    rating      INT       NOT NULL,
    reason_code VARCHAR(64)   NOT NULL,
    comment     VARCHAR(1000) NULL,
    created_at  DATETIME(3)   NOT NULL,
    KEY idx_feedback_user (user_id, created_at),
    KEY idx_feedback_decision (decision_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE behavior_learning (
    id                VARCHAR(26)       NOT NULL PRIMARY KEY,
    user_id           VARCHAR(26)       NOT NULL,
    segment_key       VARCHAR(100)   NOT NULL,
    feature_key       VARCHAR(64)    NOT NULL COMMENT 'WAKE_LATENCY/PREP_DURATION/TRAVEL_DURATION',
    estimate          DECIMAL(12,4)  NOT NULL,
    variance          DECIMAL(14,6)  NULL,
    sample_count      INT            NOT NULL DEFAULT 0,
    last_sample_at    DATETIME(3)    NULL,
    algorithm_version VARCHAR(40)    NOT NULL DEFAULT 'PRIOR_V1',
    created_at        DATETIME(3)    NOT NULL,
    updated_at        DATETIME(3)    NOT NULL,
    UNIQUE KEY uk_behavior (user_id, segment_key, feature_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
