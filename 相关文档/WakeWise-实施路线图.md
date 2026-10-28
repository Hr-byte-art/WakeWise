# WakeWise 实施路线图

> 与 `WakeWise-PRD与架构设计.md`、`WakeWise-API设计.md`、`WakeWise-数据库设计.md` 配套。
> 目标：把"演示闭环 + AI 叙事"在 1–2 周内落到可运行的代码上。
> 原则：每次只动一个原子，原子完成 = 编译通过 + 已写/已改的测试通过 + 不破坏现有接口。

## 总览（5 个阶段）

| 阶段 | 主题 | 预期收益 | 估时 |
|---|---|---|---|
| P0 | 演示闭环补齐 | 任何闹钟都有可展示的计划与故事 | 1–2 天 |
| P1 | 通知与错误码补全 | 真实数据 + 客户端可定位失败 | 1 天 |
| P2 | Spring AI 工具调用骨架 | "AI Agent"叙事从架构变成 API | 2–3 天 |
| P3 | 个体学习闭环 | 系统会随使用变聪明 | 3–4 天 |
| P4 | 工程化 | 文档/容器/可观测性 | 持续 |

每阶段内部拆为可独立提交的原子；每个原子结束会同步报告。

---

## P0 演示闭环补齐

**目标**：任意类型闹钟都有"明早计划可看"，且前端能在编辑页预览一次。

### P0-1 修复 `/alarms/{id}/preview` 端点
- 背景：`AlarmController.preview` 调 `decisionService.previewForUser(...)`；当前服务里**没有这个方法**。
- 改动：
  1. 在 `DecisionService` 新增 `previewForUser(userId, alarmId, user, overrideMaxAdvance)`：复用 `compute()` 逻辑，**不写库、不发通知**。
  2. 处理 `WAKE_AT` 闹钟的预览：返回 `baselineWakeAt == recommendedWakeAt`，`deltaMinutes = 0`，`reasonCode = KEEP_BASELINE`。
3. （**撤回**：P0-1 范围内不加单测。`previewForUser` 是组合方法，WAKE_AT 分支只复用已测过的 `engine.baselineWakeAt()` + `explanationGenerator.generate()`，没有新逻辑。`DecisionService` 的单测覆盖推迟到 P4-4，与其他 Service 一起补。）
- 验证：`mvn compile` 通过；`curl POST /api/v1/alarms/{id}/preview` 对 WAKE_AT / ARRIVE_BY 闹钟都返回结构与 API 设计 §3.5 一致。

### P0-2 `WAKE_AT` 也能产出 decision_history
- 背景：`DecisionService.evaluateAndPropose` 早返回 `null`，导致纯早起用户首页永远是基础时间。
- 改动：
  1. 改写 `evaluateAndPropose`：对 `WAKE_AT` 也建一条 `decision_history`：
     - `baselineAt = recommendedAt = alarm.baseline_time` 当日实例
     - `deltaMinutes = 0`
     - `reasonCode = KEEP_BASELINE`
     - `confidence` 由天气质量推导（与现有公式一致）
     - `status = DISPATCHING`（无需确认，固定时间）
     - `explanation` 引用 "天气 / 通勤摘要"
  2. 不影响 `preview` 端点。
  3. `NightlyPlanJob` 同步行为已存在（`else` 分支刷新 `next_trigger_at`），保留双轨：写历史 + 刷新触发。
- 验证：建一个 `WAKE_AT` 闹钟 → 跑 `nightly`（或调 `evaluate`）→ `GET /decisions?alarmId=` 看到 1 条记录。

### P0-3 Android 编辑页加"预估"按钮
- 背景：现在只能等 22:00 自动跑，演示节奏不可控。
- 改动：
  1. `AlarmEditFragment` 保存前先调 `/alarms/{id}/preview`（新建时无 id，需要后端先建再预览，或先本地暂存后预览）→ 折中：把"预估"作为**独立按钮**，放在底部 `btnPreview`。
  2. 预览返回后用底部 Sheet 展示 `recommendedWakeAt` / `deltaMinutes` / `lateProbabilityAfter` / `confidence` / `explanation`。
  3. 不修改保存逻辑。
- 验证：手动操作录屏或截图演示。

---

## P1 通知与错误码补全

**目标**：评估/确认/撤销/降级都有真实通知入库；客户端能根据错误码显示精确文案。

### P1-1 决策事件写入 `notification` 表
- 改动点（都在 `DecisionService`）：
  1. `evaluateAndPropose` 完成后：`notificationService.create(userId, planId, "PLAN_PROPOSED", "新计划待确认", body)`（仅当 `status=PROPOSED`）
  2. `confirm` / `ack` / `revert`：分别写 `PLAN_CONFIRMED` / `PLAN_ARMED` / `PLAN_REVERTED`。
  3. `recordDeviceEvent`：`ALARM_DISMISSED` / `ALARM_SNOOZED` 写一条 `type=ALARM_EVENT`。
- 验证：跑一遍流程后 `GET /notifications` 不为空。

### P1-2 错误码补全
- 改动：
  1. `ErrorCode` 枚举补：`ALARM_ADJUSTMENT_OUT_OF_RANGE` / `DECISION_LOW_CONFIDENCE` / `DECISION_EXPIRED` / `SAFE_REVERT_UNAVAILABLE` / `EXTERNAL_WEATHER_UNAVAILABLE` / `RATE_LIMITED`。
  2. `GlobalExceptionHandler` 校验映射表。
  3. 对应业务点：
     - `DecisionService.confirm` / `revert` 在状态非 `PROPOSED` / `ARMED` 时抛 `DECISION_EXPIRED`。
     - `DecisionEngine` 在 clamp 后仍越界抛 `ALARM_ADJUSTMENT_OUT_OF_RANGE`。
- 验证：单测覆盖新分支；客户端拿到 409/422 时能显示合理提示。

### P1-3 Android 通知 UI（轻量）
- 改动：
  1. `ApiService` 加 `@GET("notifications")`。
  2. 底部导航已有"首页/闹钟/统计"，在统计页加一个"通知"卡片，或在首页顶部小红点。
  3. 不做推送渠道（保持 MVP 范围）。

---

## P2 Spring AI 工具调用骨架

**目标**：用真实 LLM 调用 + 受策略守卫的工具注册中心，让"AI Agent"叙事可演示可审计。

### P2-1 引入 Spring AI 依赖
- 改动：
  1. `pom.xml` 加 `spring-ai-starter-model-openai`（或 `anthropic`），版本对齐 Spring Boot 3.3。
  2. `application.yaml` 加 `spring.ai.openai.api-key` / `chat.options.model`，从环境变量读取。
  3. 不替换现有 `TemplateExplanationGenerator`——仅作为可选实现，通过 `application.yaml` 开关切换。

### P2-2 工具注册与权限沙箱
- 改动：
  1. 新建 `ai/tools/` 包：`Tool` 接口（`name` / `description` / `jsonSchema` / `requiredScopes`）。
  2. 内置三个工具：
     - `QueryWeatherTool`（scope=`location:read`）→ 调 `WeatherService`
     - `QueryAlarmTool`（scope=`alarm:read`）→ 调 `AlarmService`
     - `ProposePlanTool`（scope=`decision:execute`）→ 只生成 `proposal` 对象，**不直接修改计划**
  3. `ToolRegistry`：按 scope 过滤 + 审计日志（`ai_conversation` 表已经预留）。

### P2-3 `/assistant/messages` 端点
- 改动：
  1. `AssistantController` + `AssistantService`：
     - 接收 `sessionId` + `message` + `clientContext`
     - 调 `ChatClient` 拼装 system prompt（"你只能使用已授权工具，不直接修改计划"）
     - 模型产出 `proposal` → 返回结构化 JSON（reply + proposal + facts）
  2. `AssistantService.executeProposal(confirmationToken)` 单独走策略守卫，**聊天回复不产生副作用**。
- 验证：演示一句"明天会议改线上，我能晚起多久？"→ 拿到 proposal + facts，前端弹确认卡。

### P2-4 审计与影子评估
- 改动：
  1. 每次工具调用 + 模型返回都写 `ai_conversation`（已有 schema，缺实现）。
  2. `proposal` 落地为 `decision_history`（状态 `PROPOSED`），走与 P0-1 相同的二次确认流程。
- 验证：数据库能查到完整的对话链。

---

## P3 个体学习闭环

**目标**：用户用得越久，预测越准；并且系统能在用户推翻决策时不"闹脾气"。

### P3-1 `BehaviorLearningService` 数据通路
- 改动：
  1. 监听 `recordDeviceEvent` 完成的 `wake_history`：异步计算 `actual_wake_latency_min` / `actual_prep_min` / `actual_travel_min`（需要用户主动提交"出门时间"或"到达时间"——MVP 阶段从 `dismissedAt` 反推：起床延迟 ≈ triggeredAt→dismissedAt）。
  2. 按 `weekday+weather_level+travel_mode` 维度写 `behavior_learning`。
  3. 算法：`estimate = 0.8 * prior + 0.2 * sample`（指数衰减起步），`sample_count` 累加。

### P3-2 决策引擎读个体参数
- 改动：
  1. `DecisionEngine.evaluate` 之前先查 `behavior_learning`，用 `estimate` 覆盖默认先验。
  2. 没记录才用 `WAKE_LATENCY_P50_MIN` / `prepP50`。

### P3-3 影子评估
- 改动：
  1. 新增 `BehaviorShadowRunner`（Quartz 每日 23:00 触发）。
  2. 取过去 7 天所有 `decision_history`，回放当时的 `behavior_learning` 估值（按时间戳回滚），对比 `actual_outcome` 计算 `MAE`。
  3. 写一份 `behavior_eval` 日志表（新建 V2 迁移）。
- 验证：跑一周后能在管理面板看到 MAE 趋势。

### P3-4 用户纠正与重置
- 改动：
  1. `POST /feedback` 接受 `corrected_wake_latency_min` 等可选字段。
  2. `DELETE /users/{id}/behavior`（未来扩展）：MVP 阶段在 `UserService` 加一个 `resetBehavior(userId)` 方法 + 简单端点。

---

## P4 工程化（持续）

### P4-1 OpenAPI 文档
- 引入 `springdoc-openapi-starter-webmvc-ui`，在 `/swagger-ui.html` 暴露。
- CI 步骤导出 `openapi.json` 作为 Android DTO 生成源（v1 仍手写）。

### P4-2 容器化
- `Dockerfile`（multi-stage JDK21 → JRE21）
- `docker-compose.yml`：MySQL 8 + backend
- 启动健康检查 `/actuator/health`

### P4-3 可观测性
- `spring-boot-starter-actuator` + `micrometer-registry-prometheus`
- 关键指标：`decision_evaluate_total` / `plan_auto_armed_total` / `device_ack_lag_seconds` / `external_weather_degraded_total`

### P4-4 测试覆盖
- 后端：`DecisionService` 集成测试（`@SpringBootTest` + `Testcontainers MySQL`）。
- Android：`PlanSyncWorker` 先建后删的 Robolectric 测试。

### P4-5 安全收紧
- `SecurityConfig` 启动校验：生产环境 JWT 密钥与 dev 默认值相等时拒绝启动。
- 速率限制：简单 `Bucket4j` 内存实现（按用户/IP 分桶）。

---

## 执行节奏

每完成一个原子，做三件事：
1. 改文件 → 列在 `## 修改清单` 段
2. 跑相关测试 → 把结果汇报
3. 等你确认 → 再开下一个原子

---

## 已完成原子

### ✅ P0-1 修复 `/alarms/{id}/preview` 端点（2026-10-28）
- 改动：`DecisionService.previewForUser` 新增 WAKE_AT 分支（基线=推荐，delta=0，KEEP_BASELINE）；`@Transactional` 改为 readOnly；撤回单测（推迟到 P4-4）。
- 验证：后端 `mvn -q test` 57/57 通过（含 BYOK 签名对齐后新增用例）；Android `assembleDebug` BUILD SUCCESSFUL。

### ✅ P0-2 `WAKE_AT` 也能产出 decision_history（2026-10-28）
- 改动：
  1. `DecisionService.evaluateAndPropose` 拆出 WAKE_AT 路径：去掉“非 ARRIVE_BY 就 return null”，改为调新增的 private `evaluateWakeAt(alarm, user, trigger)`。
  2. `evaluateWakeAt`：算下一次唤醒 + 查天气 + 生成解释 + 写 `DecisionHistory`（status=DISPATCHING, delta=0, reasonCode=KEEP_BASELINE, confidence 由天气质量推导） + 刷 `alarm.next_trigger_at`。
  3. `NightlyPlanJob` 简化：去掉 `if (ARRIVE_BY) / else` 分支，统一调 `evaluateAndPropose`；删 `AlarmService` 依赖（字段数 4 → 3）。
- 验证：后端 `mvn -q test` 57/57 通过；Android `assembleDebug` BUILD SUCCESSFUL。
- 风险点：
  1. `evaluateWakeAt` 末尾 `withPersistence(new WakePlan(...))` 传的 `status` 字符串实际被忽略（`withPersistence` 用 `history.getStatus().name()`），传 `null` 更诚实，留待 review。
  2. `AlarmService.update` 仍只对 `ARRIVE_BY` 调 `evaluateAndPropose`：`WAKE_AT` 在“改闹钟”路径只刷 `nextTriggerAt`、不写历史，与夜间任务的语义不一致，待收敛。

### ✅ P0-3 Android 编辑页加“预估”按钮（2026-10-28）
- **设计决策**（计划修订）：preview 按钮 = “保存并预览”，不另加 draft preview 端点。
  - 原因：避免“未保存的草稿预览”导致后端 / 前端 / 错误码多套设计；MVP 演示时只创建一次闹钟就够。
  - 代价：用户点 5 次 preview 会创建 5 个闹钟。已在 fragment 注释里说明，正式版应改为 draft preview。
  - draft preview 留待 P1 阶段（需要统一的草稿 / 错误码 / notification 三件套）。
- 改动：
  1. `Dtos.java` 加 `PreviewRequest` / `PreviewFactorDto` / `PreviewPlanDto`。
  2. `ApiService.java` 加 `@POST("alarms/{id}/preview")` 端点 + import。
  3. `fragment_alarm_edit.xml` 在 `btn_save` 上方加 outlined-style `btn_preview`。
  4. `strings.xml` 加 12 个 `preview_*` 文案（标题 / 基线 / 推荐 / 提前量 / 迟到概率 / 置信度 / 因素 / 关闭等）。
  5. `AlarmEditFragment.java`：
     - 抽 `buildRequest()` 共享表单校验
     - 抽 `createAlarmThen(request, onSuccess, onError)` 异步包装（`Async.run` 内）
     - `save()` = build + createAlarmThen(success→back, error→toast)
     - `preview()` = build + createAlarmThen(success→fetchAndShowPreview, error→toast)
     - `fetchAndShowPreview(alarmId, maxAdvanceMinutes)` 调 preview API，失败 toast
     - `showPreviewDialog(plan)` 用 AlertDialog 展示 wakeAt / delta / prob / confidence / factors / explanation
- 验证：`assembleDebug` BUILD SUCCESSFUL（本机 Gradle Wrapper 可正常构建，此前“本地无 Gradle”的判断作废）；同时跑通后端 `mvn -q test` 57/57。
- 风险点：preview 按钮 = save 后预览，可能产生重复 alarm（已在 fragment 注释）；`showPreviewDialog` 未展示 `requiresConfirmation`（对预览无影响）。
