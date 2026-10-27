-- V4：真实交通 ETA（高德 Web 服务 API）
-- 1) 用户常驻地址（家）及地理编码坐标缓存
-- 2) 闹钟目的地坐标缓存（destination_label 仍是文本主输入，坐标仅缓存）
-- 3) 路径规划耗时快照 traffic_cache（镜像 weather_cache 的 2 小时 TTL 模式；
--    设计文档 3.5 中的 departure_bucket/incident_count/payload_json 属规划，未建列）
ALTER TABLE user_account
    ADD COLUMN home_address VARCHAR(160) NULL AFTER city,
    ADD COLUMN home_lng DECIMAL(10,6) NULL AFTER home_address,
    ADD COLUMN home_lat DECIMAL(9,6) NULL AFTER home_lng;

ALTER TABLE alarm
    ADD COLUMN dest_lng DECIMAL(10,6) NULL AFTER destination_label,
    ADD COLUMN dest_lat DECIMAL(9,6) NULL AFTER dest_lng;

CREATE TABLE traffic_cache (
    id            VARCHAR(26)   NOT NULL PRIMARY KEY,
    provider      VARCHAR(32)   NOT NULL,
    route_key     VARCHAR(64)   NOT NULL COMMENT '起点|终点|方式（坐标 4dp 压缩）',
    travel_mode   VARCHAR(16)   NOT NULL,
    eta_p50_sec   INT           NOT NULL,
    eta_p90_sec   INT           NULL COMMENT 'p50 × 方式尾部系数',
    distance_m    INT           NULL,
    quality_score DECIMAL(5,4)  NOT NULL,
    observed_at   DATETIME(3)   NOT NULL,
    expires_at    DATETIME(3)   NOT NULL,
    created_at    DATETIME(3)   NOT NULL,
    KEY idx_traffic_lookup (route_key, observed_at),
    KEY idx_traffic_expire (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
