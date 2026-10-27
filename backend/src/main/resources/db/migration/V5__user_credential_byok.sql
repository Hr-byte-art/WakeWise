-- V5：外部服务凭据用户级配置（BYOK，App 设置页）
-- 每用户每服务一行：无行 = 继承服务端默认（环境变量 AMAP_KEY / LLM_*）；
-- disabled 为用户显式禁用，压过服务端默认（解析链：禁用 > 用户级 > 服务端默认 > 关闭）。
-- secret_enc 是 api_key 密文（AES-256-GCM，base64(iv||ct||tag)，密钥见 wakewise.byok.enc-key），
-- 任何接口不回显明文；llm_base_url/llm_model 非机密，明文存储供状态回显。
CREATE TABLE user_credential (
    id           VARCHAR(26)  NOT NULL PRIMARY KEY,
    user_id      VARCHAR(26)  NOT NULL,
    service      VARCHAR(8)   NOT NULL COMMENT 'AMAP / LLM',
    disabled     BOOLEAN  NOT NULL DEFAULT FALSE COMMENT '用户显式禁用，压过服务端默认',
    llm_base_url VARCHAR(255) NULL COMMENT '仅 LLM：base-url（非机密，可回显）',
    llm_model    VARCHAR(100) NULL COMMENT '仅 LLM：model（非机密，可回显）',
    secret_enc   VARCHAR(512) NULL COMMENT 'api_key 密文 base64(iv||ct||tag)',
    created_at   DATETIME(3)  NOT NULL,
    updated_at   DATETIME(3)  NOT NULL,
    UNIQUE KEY uk_user_credential (user_id, service)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
