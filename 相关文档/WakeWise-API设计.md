# WakeWise REST API 设计

| 属性 | 内容 |
|---|---|
| 版本 | v1 |
| 基础路径 | `/api/v1` |
| 内容类型 | `application/json; charset=utf-8` |
| 时间格式 | ISO 8601 UTC，例如 `2026-08-01T22:00:00Z` |
| 认证 | OAuth 2.1 / OIDC Bearer Token |

## 1. 通用约定

### 1.1 请求头

| Header | 必需 | 说明 |
|---|---:|---|
| `Authorization: Bearer <token>` | 是 | 访问令牌；公开健康检查除外 |
| `X-Device-Id` | 移动端是 | 注册设备 ID |
| `Idempotency-Key` | 写请求建议/部分必需 | 24 小时内防重复提交 |
| `Accept-Language` | 否 | 响应文案语言 |
| `X-Request-Id` | 否 | 客户端请求追踪 ID |

### 1.2 成功响应

```json
{
  "success": true,
  "data": {},
  "traceId": "01K1W9Q4J8E4H8VJ8G9K7A2M1B",
  "timestamp": "2026-07-31T14:00:00Z"
}
```

### 1.3 错误响应

```json
{
  "success": false,
  "error": {
    "code": "ALARM_ADJUSTMENT_OUT_OF_RANGE",
    "message": "建议时间超出允许调整范围",
    "details": {
      "maxAdvanceMinutes": 30,
      "requestedAdvanceMinutes": 42
    }
  },
  "traceId": "01K1W9Q4J8E4H8VJ8G9K7A2M1B",
  "timestamp": "2026-07-31T14:00:00Z"
}
```

### 1.4 状态码

| HTTP | 场景 |
|---:|---|
| 200 | 查询、修改、业务动作成功 |
| 201 | 资源创建成功 |
| 202 | 异步任务已接收 |
| 204 | 删除/停用成功且无响应体 |
| 400 | 参数格式或业务约束错误 |
| 401 | 未认证或令牌失效 |
| 403 | 权限/授权范围不足 |
| 404 | 资源不存在或不可见 |
| 409 | 版本冲突、重复操作、计划已失效 |
| 422 | 参数合法但无法形成有效计划 |
| 429 | 请求频率或供应商预算超限 |
| 503 | 依赖不可用且无可接受降级结果 |

## 2. 认证与授权

### 2.1 Scope

| Scope | 能力 |
|---|---|
| `profile:read` | 读取用户基础偏好 |
| `alarm:read` / `alarm:write` | 读取/修改闹钟 |
| `calendar:read` | 读取授权日历摘要 |
| `location:read` | 读取晨间计划需要的位置 |
| `decision:read` / `decision:execute` | 查看/执行智能计划 |
| `history:read` | 查看历史和统计 |
| `feedback:write` | 提交反馈 |

访问令牌建议 15 分钟有效，刷新令牌轮换并绑定设备。修改授权、删除账户、导出敏感数据需要近期认证或二次验证。

## 3. 闹钟 API

### 3.1 创建闹钟

`POST /api/v1/alarms`

```json
{
  "name": "工作日通勤",
  "targetType": "ARRIVE_BY",
  "targetLocalTime": "08:45:00",
  "timezone": "Asia/Shanghai",
  "recurrence": "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR",
  "destination": {
    "placeId": "provider:place:abc123",
    "label": "公司"
  },
  "travelMode": "TRANSIT",
  "rule": {
    "preparationMinutes": 30,
    "maxAdvanceMinutes": 30,
    "maxDelayMinutes": 10,
    "allowAutoDelay": false,
    "automationLevel": 1,
    "snoozeIntervalMinutes": 5,
    "maxSnoozeCount": 2
  }
}
```

响应 `201 Created`：

```json
{
  "success": true,
  "data": {
    "id": "01K1WAK9V5H8TQ9P5W2FDY3G7A",
    "name": "工作日通勤",
    "enabled": true,
    "nextBaselineAt": "2026-08-03T00:45:00Z",
    "nextEvaluationAt": "2026-08-02T14:00:00Z",
    "version": 1
  },
  "traceId": "01K1...",
  "timestamp": "2026-07-31T14:00:00Z"
}
```

### 3.2 查询闹钟列表

`GET /api/v1/alarms?enabled=true&cursor=<cursor>&limit=20`

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "01K1WAK9V5H8TQ9P5W2FDY3G7A",
        "name": "工作日通勤",
        "targetType": "ARRIVE_BY",
        "enabled": true,
        "nextWakeAt": "2026-08-03T22:45:00Z",
        "status": "PLANNED",
        "version": 3
      }
    ],
    "nextCursor": null
  },
  "traceId": "01K1...",
  "timestamp": "2026-07-31T14:00:00Z"
}
```

### 3.3 修改闹钟

`PATCH /api/v1/alarms/{alarmId}`

```json
{
  "version": 3,
  "enabled": true,
  "rule": {
    "maxAdvanceMinutes": 40,
    "automationLevel": 2
  }
}
```

版本不匹配返回 `409 RESOURCE_VERSION_CONFLICT`，客户端应重新获取资源后提示用户合并。

### 3.4 删除闹钟

`DELETE /api/v1/alarms/{alarmId}`

逻辑删除或停用配置，历史决策仍保留；响应 `204 No Content`。

### 3.5 预览下一次计划

`POST /api/v1/alarms/{alarmId}/preview`

用于设置页面即时预览，不执行、不通知、不写入正式计划。

```json
{
  "override": {
    "maxAdvanceMinutes": 45,
    "riskPreference": "CONSERVATIVE"
  }
}
```

```json
{
  "success": true,
  "data": {
    "baselineWakeAt": "2026-08-03T23:00:00Z",
    "recommendedWakeAt": "2026-08-03T22:45:00Z",
    "deltaMinutes": -15,
    "lateProbabilityBefore": 0.21,
    "lateProbabilityAfter": 0.06,
    "confidence": 0.82,
    "expiresAt": "2026-07-31T14:05:00Z"
  },
  "traceId": "01K1...",
  "timestamp": "2026-07-31T14:00:00Z"
}
```

## 4. 决策 API

### 4.1 发起评估

`POST /api/v1/decisions/evaluate`

该接口供 Morning Agent 或受信客户端调用。普通客户端默认只对自己拥有的闹钟操作。

```json
{
  "alarmId": "01K1WAK9V5H8TQ9P5W2FDY3G7A",
  "targetDate": "2026-08-04",
  "trigger": "NIGHTLY_PLAN",
  "contextVersion": "ctx_01K1W..."
}
```

响应 `202 Accepted`：

```json
{
  "success": true,
  "data": {
    "jobId": "01K1WB3YJ0Z1F6D1NF4G7M9NQH",
    "status": "QUEUED"
  },
  "traceId": "01K1...",
  "timestamp": "2026-07-31T14:00:00Z"
}
```

### 4.2 查询计划

`GET /api/v1/decisions/{planId}`

```json
{
  "success": true,
  "data": {
    "planId": "01K1WBGXSAWSZV1H4T5X7CCSD2",
    "alarmId": "01K1WAK9V5H8TQ9P5W2FDY3G7A",
    "status": "AWAITING_CONFIRMATION",
    "baselineWakeAt": "2026-08-03T23:00:00Z",
    "recommendedWakeAt": "2026-08-03T22:45:00Z",
    "deltaMinutes": -15,
    "targetArrivalAt": "2026-08-04T00:45:00Z",
    "risk": {
      "score": 18.6,
      "lateProbabilityBefore": 0.21,
      "lateProbabilityAfter": 0.06,
      "confidence": 0.82
    },
    "topFactors": [
      { "code": "TRAFFIC_P90_INCREASE", "impactMinutes": 11 },
      { "code": "PREP_TIME_PERSONAL_OFFSET", "impactMinutes": 4 }
    ],
    "explanation": "预计降雨会增加公交行程波动，且你近期准备时间平均多 4 分钟。",
    "dataFreshness": {
      "weatherObservedAt": "2026-08-03T13:58:00Z",
      "trafficObservedAt": "2026-08-03T13:59:00Z"
    },
    "requiresConfirmation": true,
    "expiresAt": "2026-08-03T15:00:00Z",
    "version": 1
  },
  "traceId": "01K1...",
  "timestamp": "2026-08-03T14:00:00Z"
}
```

### 4.3 确认计划

`POST /api/v1/decisions/{planId}/confirm`

请求必须带 `Idempotency-Key`。

```json
{
  "version": 1,
  "choice": "RECOMMENDED"
}
```

响应表示服务端已批准下发，不代表移动端已经完成本地调度：

```json
{
  "success": true,
  "data": {
    "planId": "01K1WBGXSAWSZV1H4T5X7CCSD2",
    "status": "DISPATCHING",
    "scheduledWakeAt": "2026-08-03T22:45:00Z"
  },
  "traceId": "01K1...",
  "timestamp": "2026-08-03T14:02:00Z"
}
```

### 4.4 撤销计划

`POST /api/v1/decisions/{planId}/revert`

```json
{
  "version": 2,
  "reason": "USER_PREFERRED_BASELINE"
}
```

若计划已经触发，返回 `409 DECISION_ALREADY_EXECUTED`；如果旧计划无法安全恢复，返回 `422 SAFE_REVERT_UNAVAILABLE` 并给出可选方案。

## 5. 设备计划 API

### 5.1 获取待同步计划

`GET /api/v1/device-plans/pending?afterVersion=18`

客户端启动、网络恢复和推送到达后调用。响应包含签名、计划版本、闹钟内容和替代的旧计划 ID。

### 5.2 确认本地调度

`POST /api/v1/device-plans/{planId}/ack`

```json
{
  "deviceId": "01K1DEV...",
  "planVersion": 2,
  "localAlarmId": "android:alarm:48291",
  "scheduledWakeAt": "2026-08-03T22:45:00Z",
  "scheduledAt": "2026-08-03T14:02:03Z",
  "platform": "ANDROID",
  "appVersion": "1.0.0",
  "signature": "base64-device-signature"
}
```

只有 ACK 校验成功后，计划状态才变为 `ARMED`。相同计划与设备的重复 ACK 返回相同结果。

### 5.3 上报执行事件

`POST /api/v1/device-events`

```json
{
  "eventId": "01K1EVENT...",
  "planId": "01K1WBGXSAWSZV1H4T5X7CCSD2",
  "type": "ALARM_DISMISSED",
  "occurredAt": "2026-08-03T22:46:19Z",
  "attributes": {
    "snoozeCount": 0
  }
}
```

支持离线上报补发（客户端 outbox）。`type` 取值：`ALARM_FIRED` / `ALARM_SNOOZED` / `ALARM_DISMISSED` / `USER_LEFT_HOME`（出门打卡，一期只积累数据不参与决策）。

幂等性（当前实现，如实说明）：两层——① `eventId` 全局去重（V3 起 `device_event` 流水表唯一键，outbox 重放/客户端重试在入口即幂等跳过并返回成功，并发重复由唯一约束回滚兜底）；② 字段级幂等（每计划在 `wake_history` 聚合为一行：`ALARM_FIRED` 保首次响铃、`USER_LEFT_HOME` 只记第一次出门时刻、重复 `ALARM_DISMISSED` 覆盖同值无副作用）。`ALARM_DISMISSED` 事件提交后会触发起床延迟校准重算（SHRINK_V1），校准失败仅记日志、不影响事件链路。

## 6. 晨间上下文 API

### 6.1 获取晨间简报

`GET /api/v1/morning/context?date=2026-08-04`

```json
{
  "success": true,
  "data": {
    "wakePlan": {
      "wakeAt": "2026-08-03T22:45:00Z",
      "leaveBy": "2026-08-03T23:32:00Z",
      "arriveBy": "2026-08-04T00:45:00Z"
    },
    "weather": {
      "summary": "中雨，18～22℃",
      "alert": null,
      "suggestions": ["携带雨具"]
    },
    "traffic": {
      "mode": "TRANSIT",
      "etaP50Minutes": 38,
      "etaP90Minutes": 47,
      "etaSource": "AMAP",
      "status": "SLOWER_THAN_USUAL"
    },
    "firstEvent": {
      "title": "产品评审",
      "startAt": "2026-08-04T00:30:00Z",
      "locationLabel": "公司"
    },
    "updatedAt": "2026-08-03T14:00:00Z",
    "calibration": {
      "sampleCount": 6,
      "estimateMinutes": 5.4,
      "algorithmVersion": "SHRINK_V1"
    }
  },
  "traceId": "01K1...",
  "timestamp": "2026-08-03T14:00:00Z"
}
```

`calibration` 为可选字段（未校准/样本不足时为 `null`）：起床延迟先验已按该用户真实起床历史校准，`estimateMinutes` 为校准后的 P50。

`traffic.etaSource` 为可选字段（`"AMAP"` 或 `null`）：配置 `AMAP_KEY` 且常驻地址/目的地可解析时，`etaP50Minutes` 来自高德路径规划真实耗时，否则为用户手填通勤分钟。

> 当前实现补充（认证域）：`GET /api/v1/auth/me` 返回资料（含 `homeAddress`）；`PATCH /api/v1/auth/me` body `{"homeAddress": "..."}` 更新常驻地址（家）——真实交通 ETA 的出发地，传空串清除，地址变更时后端自动地理编码缓存坐标。

## 7. 反馈与统计 API

### 7.1 提交反馈

`POST /api/v1/feedback`

```json
{
  "decisionId": "01K1DECISION...",
  "wakeHistoryId": "01K1HISTORY...",
  "rating": 4,
  "reasonCode": "JUST_RIGHT",
  "comment": "时间合适，但解释可以更短"
}
```

### 7.2 周度统计

`GET /api/v1/statistics/weekly?week=2026-W32`

```json
{
  "success": true,
  "data": {
    "planSuccessRate": 0.92,
    "onTimeRate": 0.96,
    "recommendationAcceptanceRate": 0.78,
    "averageAdjustmentMinutes": -8.4,
    "sleepMinutesPreserved": 24,
    "preparationPredictionMaeMinutes": 5.2,
    "topRiskFactors": ["TRAFFIC_VARIANCE", "SNOOZE"]
  },
  "traceId": "01K1...",
  "timestamp": "2026-08-09T12:00:00Z"
}
```

## 8. AI 助手 API

### 8.1 发送消息

`POST /api/v1/assistant/messages`

```json
{
  "sessionId": "01K1SESSION...",
  "message": "明天的评审改成线上，我能晚起多久？",
  "clientContext": {
    "currentPlanId": "01K1WBGXSAWSZV1H4T5X7CCSD2"
  }
}
```

返回结构化提案而不是直接执行：

```json
{
  "success": true,
  "data": {
    "reply": "取消通勤后，建议闹钟从 06:45 调整到 07:30。",
    "proposal": {
      "type": "UPDATE_PLAN",
      "recommendedWakeAt": "2026-08-03T23:30:00Z",
      "requiresConfirmation": true,
      "confirmationToken": "short-lived-token"
    },
    "facts": [
      { "source": "USER_MESSAGE", "value": "会议改为线上" },
      { "source": "ALARM_RULE", "value": "准备时间 30 分钟" }
    ]
  },
  "traceId": "01K1...",
  "timestamp": "2026-08-03T14:10:00Z"
}
```

任何写操作都必须通过单独确认接口和策略守卫，聊天回复本身不产生副作用。

## 9. 错误码

| 错误码 | HTTP | 含义 | 客户端处理 |
|---|---:|---|---|
| `AUTH_TOKEN_EXPIRED` | 401 | 访问令牌过期 | 尝试安全刷新 |
| `AUTH_SCOPE_REQUIRED` | 403 | 缺少授权范围 | 展示分目的授权页 |
| `RESOURCE_NOT_FOUND` | 404 | 资源不存在或无权访问 | 返回列表/刷新 |
| `RESOURCE_VERSION_CONFLICT` | 409 | 乐观锁冲突 | 拉取最新版并提示合并 |
| `IDEMPOTENCY_CONFLICT` | 409 | 相同幂等键对应不同请求 | 生成新键或恢复原请求 |
| `ALARM_ADJUSTMENT_OUT_OF_RANGE` | 400 | 超出用户允许边界 | 展示边界并允许修改设置 |
| `DECISION_LOW_CONFIDENCE` | 422 | 数据不足，不能自动执行 | 请求确认或保留原计划 |
| `DECISION_EXPIRED` | 409 | 计划已过期 | 重新评估 |
| `DECISION_ALREADY_EXECUTED` | 409 | 已执行，不能撤销 | 展示历史结果 |
| `SAFE_REVERT_UNAVAILABLE` | 422 | 无法安全恢复旧计划 | 展示可行时间 |
| `DEVICE_ACK_TIMEOUT` | 503 | 客户端未确认本地调度 | 保留旧计划并提示自检 |
| `EXTERNAL_WEATHER_UNAVAILABLE` | 503 | 天气服务不可用且无缓存 | 标记降级/保持原计划 |
| `EXTERNAL_TRAFFIC_UNAVAILABLE` | 503 | 交通服务不可用且无缓存 | 使用历史估计或请求确认 |
| `RATE_LIMITED` | 429 | 请求超限 | 根据 `Retry-After` 重试 |

## 10. API 安全检查清单

- 对象级授权：所有 `{alarmId}/{planId}` 必须校验所属用户。
- 写接口幂等，关键状态变更同时校验资源版本。
- 目的地、日历内容和会话文本不写入访问日志。
- Webhook/设备 ACK 使用签名、时间戳和重放窗口。
- LLM 工具参数经过 Schema 校验，不接受模型生成的任意 URL、SQL 或系统命令。
- 速率限制按用户、设备、IP 和供应商预算分层。
- 数据导出使用短期签名链接，删除任务异步且可审计。

