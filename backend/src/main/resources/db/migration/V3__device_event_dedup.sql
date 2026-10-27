-- V3：设备事件全局去重
-- eventId（客户端 UUID）唯一键：outbox 重放/客户端重试的重复事件在入口即幂等跳过，
-- 并发窗口内的重复由唯一约束回滚兜底（此前 ALARM_SNOOZED 重复上报会多计贪睡次数）。
CREATE TABLE device_event (
    id          VARCHAR(26)  NOT NULL PRIMARY KEY,
    event_id    VARCHAR(64)  NOT NULL,
    user_id     VARCHAR(26)  NOT NULL,
    plan_id     VARCHAR(26)  NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    occurred_at DATETIME(3)  NULL COMMENT '客户端上报时刻，缺省为服务端接收时刻',
    received_at DATETIME(3)  NOT NULL,
    UNIQUE KEY uk_device_event (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
