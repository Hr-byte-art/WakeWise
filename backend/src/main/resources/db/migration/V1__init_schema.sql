-- WakeWise MVP Schema v1
-- 字符集 utf8mb4，时间使用 UTC

CREATE DATABASE IF NOT EXISTS wakewise
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE wakewise;

CREATE TABLE user_account (
    id              CHAR(26)     NOT NULL,
    device_id       VARCHAR(128) NOT NULL,
    display_name    VARCHAR(80)  NOT NULL DEFAULT '',
    timezone        VARCHAR(64)  NOT NULL DEFAULT 'Asia/Shanghai',
    locale          VARCHAR(16)  NOT NULL DEFAULT 'zh-CN',
    risk_preference ENUM('BALANCED','CONSERVATIVE','SLEEP_FIRST') NOT NULL DEFAULT 'BALANCED',
    automation_level TINYINT     NOT NULL DEFAULT 0,
    status          ENUM('ACTIVE','LOCKED','DELETING','DELETED') NOT NULL DEFAULT 'ACTIVE',
    last_login_at   DATETIME(3)  NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)),
    updated_at      DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE UTC_TIMESTAMP(3),
    version         BIGINT       NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE alarm (
    id                    CHAR(26)     NOT NULL,
    user_id               CHAR(26)     NOT NULL,
    name                  VARCHAR(100) NOT NULL,
    target_type           ENUM('WAKE_AT','LEAVE_BY','ARRIVE_BY','EVENT') NOT NULL,
    baseline_time         TIME         NULL,
    target_at             DATETIME(3)  NULL,
    calendar_event_ref    VARCHAR(160) NULL,
    destination_label     VARCHAR(200) NULL,
    travel_mode           ENUM('WALK','BIKE','TRANSIT','DRIVE','MIXED','NONE') NULL,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    next_trigger_at       DATETIME(3)  NULL,
    created_at            DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)),
    updated_at            DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE UTC_TIMESTAMP(3),
    version               BIGINT       NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    INDEX idx_alarm_user_enabled (user_id, enabled),
    INDEX idx_alarm_next_trigger (next_trigger_at, enabled),
    CONSTRAINT fk_alarm_user FOREIGN KEY (user_id) REFERENCES user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE alarm_rule (
    id                  CHAR(26)     NOT NULL,
    alarm_id            CHAR(26)     NOT NULL,
    rrule               VARCHAR(512) NULL,
    prep_duration_min   SMALLINT     NOT NULL DEFAULT 30,
    max_advance_min     SMALLINT     NOT NULL DEFAULT 30,
    max_delay_min       SMALLINT     NOT NULL DEFAULT 10,
    allow_auto_delay    BOOLEAN      NOT NULL DEFAULT FALSE,
    automation_level    TINYINT      NOT NULL DEFAULT 0,
    snooze_interval_min SMALLINT     NOT NULL DEFAULT 5,
    max_snooze_count    TINYINT      NOT NULL DEFAULT 2,
    wake_strategy_json  JSON         NOT NULL,
    effective_from      DATE         NULL,
    effective_until     DATE         NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)),
    updated_at          DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE UTC_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_alarm (alarm_id),
    CONSTRAINT fk_rule_alarm FOREIGN KEY (alarm_id) REFERENCES alarm(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE weather_cache (
    id                CHAR(26)     NOT NULL,
    provider          VARCHAR(32)  NOT NULL,
    geo_hash          VARCHAR(12)  NOT NULL,
    forecast_time     DATETIME(3)  NOT NULL,
    observed_at       DATETIME(3)  NOT NULL,
    temperature_c     DECIMAL(5,2) NULL,
    feels_like_c      DECIMAL(5,2) NULL,
    precip_probability DECIMAL(5,4) NULL,
    precip_mm         DECIMAL(8,2) NULL,
    visibility_m      INT          NULL,
    wind_speed_mps    DECIMAL(6,2) NULL,
    aqi               SMALLINT     NULL,
    alert_level       VARCHAR(24)  NULL,
    quality_score     DECIMAL(5,4) NOT NULL DEFAULT 1.0,
    payload_json      JSON         NOT NULL,
    expires_at        DATETIME(3)  NOT NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)),
    PRIMARY KEY (id),
    UNIQUE KEY uk_weather (provider, geo_hash, forecast_time, observed_at),
    INDEX idx_weather_expire (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE decision_history (
    id                      CHAR(26)     NOT NULL,
    plan_id                 CHAR(26)     NOT NULL,
    user_id                 CHAR(26)     NOT NULL,
    alarm_id                CHAR(26)     NOT NULL,
    baseline_at             DATETIME(3)  NOT NULL,
    recommended_at          DATETIME(3)  NOT NULL,
    effective_at            DATETIME(3)  NULL,
    status                  ENUM('PROPOSED','PENDING','ARMED','REVERTED','FAILED','EXECUTED') NOT NULL DEFAULT 'PROPOSED',
    risk_score              DECIMAL(7,4) NOT NULL DEFAULT 0,
    late_probability        DECIMAL(6,5) NOT NULL DEFAULT 0,
    confidence              DECIMAL(5,4) NOT NULL DEFAULT 1.0,
    factor_contribution_json JSON        NOT NULL,
    context_snapshot_json   JSON         NOT NULL,
    reason_code             VARCHAR(64)  NOT NULL,
    explanation             VARCHAR(1000) NOT NULL DEFAULT '',
    policy_version          VARCHAR(40)  NOT NULL DEFAULT 'v1.0.0',
    model_version           VARCHAR(40)  NOT NULL DEFAULT 'v1.0.0',
    created_at              DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)),
    updated_at              DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE UTC_TIMESTAMP(3),
    version                 BIGINT       NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_decision_plan (plan_id),
    INDEX idx_decision_alarm_time (alarm_id, created_at),
    INDEX idx_decision_user_status (user_id, status, created_at),
    CONSTRAINT fk_decision_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT fk_decision_alarm FOREIGN KEY (alarm_id) REFERENCES alarm(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE wake_history (
    id              CHAR(26)     NOT NULL,
    user_id         CHAR(26)     NOT NULL,
    alarm_id        CHAR(26)     NOT NULL,
    plan_id         CHAR(26)     NULL,
    scheduled_at    DATETIME(3)  NOT NULL,
    triggered_at    DATETIME(3)  NULL,
    dismissed_at    DATETIME(3)  NULL,
    woke_at         DATETIME(3)  NULL,
    departed_at     DATETIME(3)  NULL,
    arrived_at      DATETIME(3)  NULL,
    snooze_count    TINYINT      NOT NULL DEFAULT 0,
    outcome         ENUM('SUCCESS','LATE','MISSED','UNKNOWN') NOT NULL DEFAULT 'UNKNOWN',
    source_flags    SET('USER','DEVICE','LOCATION','CALENDAR') NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)),
    PRIMARY KEY (id),
    INDEX idx_wake_user_time (user_id, scheduled_at),
    INDEX idx_wake_alarm_time (alarm_id, scheduled_at),
    CONSTRAINT fk_wake_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT fk_wake_alarm FOREIGN KEY (alarm_id) REFERENCES alarm(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification (
    id                  CHAR(26)     NOT NULL,
    user_id             CHAR(26)     NOT NULL,
    decision_id         CHAR(26)     NULL,
    type                VARCHAR(40)  NOT NULL,
    channel             ENUM('IN_APP','PUSH','EMAIL','WEARABLE') NOT NULL DEFAULT 'IN_APP',
    title               VARCHAR(160) NOT NULL,
    body                VARCHAR(1000) NOT NULL,
    status              ENUM('PENDING','SENT','DELIVERED','OPENED','FAILED') NOT NULL DEFAULT 'PENDING',
    provider_message_id VARCHAR(160) NULL,
    idempotency_key     VARCHAR(100) NULL,
    sent_at             DATETIME(3)  NULL,
    delivered_at        DATETIME(3)  NULL,
    opened_at           DATETIME(3)  NULL,
    failure_code        VARCHAR(64)  NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_idempotency (idempotency_key),
    INDEX idx_notification_user_time (user_id, created_at),
    INDEX idx_notification_status (status, created_at),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE feedback (
    id              CHAR(26)     NOT NULL,
    user_id         CHAR(26)     NOT NULL,
    decision_id     CHAR(26)     NULL,
    wake_history_id CHAR(26)     NULL,
    rating          TINYINT      NOT NULL,
    reason_code     VARCHAR(64)  NOT NULL,
    comment         VARCHAR(1000) NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)),
    PRIMARY KEY (id),
    INDEX idx_feedback_decision (decision_id),
    INDEX idx_feedback_user_time (user_id, created_at),
    CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;