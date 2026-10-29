# TODO — 待办与进度看板

> **协作约定**：这是项目唯一的待办清单，人和 AI 共用。
> 标记：`[ ]` 待做 · `[~]` 进行中（开工时改）· `[x]` 已完成（完成时补日期和提交号）。
> 任何时候看这一个文件就知道项目状态；开工前先领任务，完成后回来勾掉。

## 一、待做：行为学习一期 · 端到端验证（优先）

代码已推送（见"已完成"），但当时不在常用设备上，以下验证**全部未跑**。

### 后端（需要本地 MySQL 8 + JDK 21）

- [ ] **启动迁移验证**：先 `DROP DATABASE wakewise;`（或用 `MYSQL_DB=wakewise_v2`），再启动后端（记得带 `-s ~/Applications/apache-maven-3.9.16/conf/settings-shucheng.xml`），确认 Flyway 跑完 V1~V5 无报错
- [ ] **真实 ETA 冒烟**（`e9c2aed` 一期实装）：`AMAP_KEY=<key>` 起后端 → PATCH /auth/me 设家址 → 建 ARRIVE_BY 闹钟（真实目的地）→ evaluate 后 modelVersion 含 `+ETA`、morning/context traffic 的 etaSource=AMAP 且分钟为真实值；不配 key 重启 → 回落手填分钟、traffic.etaSource 为 null、无报错
- [ ] **eventId 全局去重**（`57ea72c` V3 实装）：同一 eventId 连发两次 ALARM_SNOOZED → `SELECT snooze_count FROM wake_history WHERE plan_id=...` 只计 1 次，两次请求均返回成功
- [ ] **造历史触发校准**：开 doc.html（`http://localhost:8080/doc.html`，Authorize 填登录 JWT）→ 注册（城市"北京"）→ 建 ARRIVE_BY 闹钟（08:45 / prep 30 / commute 40 / maxAdvance 30）→ 循环 6 轮：`POST /decisions/evaluate` 拿新 planId → 对每个 planId 发两条 device-events：ALARM_FIRED（如 `22:45:00Z`）+ ALARM_DISMISSED（依次延迟 3/5/6/7/9/12 分钟，occurredAt 全 UTC 且必须是过去时刻——消毒器会把未来时刻夹到 now+2min）
- [ ] **校准断言**：`SELECT * FROM behavior_learning` 出一行（sample_count=6、SHRINK_V1、estimate≈5.36）；`GET /morning/context` 返回 `calibration` 字段；再 evaluate 一次，新计划详情 modelVersion = `QUANTILE_V1+CAL(n=6)`；后端日志出现 `Wake latency calibrated`
- [ ] **出门事件幂等**：同一 planId 连发两次 USER_LEFT_HOME（不同 eventId、同 occurredAt）→ `SELECT left_home_at FROM wake_history WHERE plan_id=...` 只记第一次
- [ ] **贪睡语义回归**（`ecea2e8` 修复项）：同一 planId 依次发 ALARM_FIRED(t0) → ALARM_SNOOZED → ALARM_FIRED(t0+5min) → ALARM_DISMISSED(t0+12min) → `triggered_at` 应为 t0（首次响铃），校准样本延迟含贪睡 5 分钟
- [ ] **BYOK 运行时冒烟**（`af862e4` 后端 + `e9411cb` Android 实装）：起后端 → knife4j `GET /me/external-services` 看默认状态（AMAP/LLM 按 `AMAP_KEY`/`LLM_*` 是否配置显示 SERVER 或 OFF）→ PUT 用户级高德 key（真 key）→ evaluate 的 modelVersion 含 `+ETA`、traffic.etaSource=AMAP → PUT `{disabled:true}` → evaluate 回落手填分钟且无报错 → GET 确认任何响应都无 api_key 明文；LLM 三项同理（配真端点看 LLM 文案、只填两项看 400 提示）；App 端统计页入口 → 设置页四态流转（继承→你的 key→禁用→恢复默认）
- [ ] **准备时长校准端到端**（`c0d7b63` 后端 + `44a2529` Android 实装）：doc.html 造 ≥5 条同时含 ALARM_DISMISSED + USER_LEFT_HOME 的历史（出门距关闭 36/42/48/54/60 分钟，occurredAt 用过去时刻）→ `SELECT * FROM behavior_learning WHERE feature_key='PREP_DURATION'` 出一行（sample_count≥5、PREP_SHRINK_V1、estimate≈实际/设定比率）→ 再 evaluate 新计划 modelVersion 含 `+PREP(n=5)` → `GET /morning/context` 的 calibration 带 prepSampleCount/prepRatioP50 → App 首页出现"准备时长按最近 N 次出门校准（约设定的 X 倍）"
- [ ] **occurredAt 消毒**（`6593cc8` 后端 + `c9ae1fd` Android 实装）：向 device-events 发 occurredAt=未来 1 小时 → 请求成功、`SELECT occurred_at FROM device_event WHERE event_id=...` 夹到 now+2min 且日志出 `Device event time sanitized`；发非法字符串 → 同样成功不 500、occurred_at≈接收时刻；App 端改系统时间 +10 分钟后成功发一次请求（学到 offset）→ 后续事件上报时刻应已被校正

### Android（需要模拟器/真机）

- [ ] ⚠️ **装包前须知**：Room v1→2 破坏性迁移会清本地库，已布防闹钟会丢——装新 APK 后重新 evaluate + 打开 app 让 PlanSyncWorker 同步一次
- [ ] **响铃页简报三级兜底**：`adb shell am broadcast -a com.wjh.wakewise.action.FIRE --es planId <真实planId> --es label 测试 --el targetAt 1790000000000` → 先显示"简报同步中…"，网络通则 ≤3 行简报；关后端再触发 → 读 Room 缓存；清数据冷启动 → "简报暂不可达 · 闹钟不受影响"
- [ ] **时钟修复**：响铃页停留跨过整分钟，时钟应跳分钟
- [ ] **出门打卡闭环**：点"关闭"→ 通知栏出"出门打卡"→ 点【我出门了】→ 通知消失 + 后端 `left_home_at` 有值（planId 需真实，否则归属校验不过）
- [ ] **首页校准提示**：校准生效后首页显示"已根据最近 6 次起床记录校准"
- [ ] **日历导入**（`8af9d61` 一期实装）：系统日历建明日 09:30 非全天事件（带地点）→ 新建闹钟点"从日历导入"→ 时间/名称/地点自动填 + Toast；明日无日程、拒绝 READ_CALENDAR 权限、日历里全是全天节假日事件三条降级路径均只提示不阻塞

## 二、待做：后续功能（按优先级）

- [x] **LLM 解释生成**：OpenAI 兼容轻量客户端接入现有 `ExplanationGenerator` 接口（base-url/api-key/model 用户可配，留空纯模板），模板兜底。红线：LLM 只写文案不算时间（2026-10-27，`5283be5`，测试 28/28 绿；真 key 冒烟——配 `LLM_BASE_URL`/`LLM_API_KEY`/`LLM_MODEL` 起 evaluate 看 LLM 文案、错 key 看回落——待有 key 时顺手跑）
- [x] **真实交通 ETA**：高德 Web 服务 API 一期——地理编码缓存坐标 + 按方式路径规划 + 2h 快照，modelVersion 标 `+ETA`，失败逐字节回落手填分钟（2026-10-27，`e9c2aed`，后端 40/40 绿 + assembleDebug 通过；真 key 冒烟见第一节）
- [x] **日历接入**：Android `CalendarProvider` 读次日首个事件，自动填"目标到达"时间（纯端侧，无后端改动，三条里最便宜）（2026-10-27，`8af9d61`，assembleDebug 通过；运行时验证见第一节"日历导入"）
- [x] **准备时长校准**：消费 `left_home_at` 出门样本，比率收缩（PREP_SHRINK_V1：实际准备 ÷ 用户设定，先验 1.0 信任设置，≥5 样本生效，modelVersion 标 `+PREP(n=N)`），替换固定 PREP_P90_FACTOR；复用 behavior_learning 已预留的 `PREP_DURATION` featureKey，零迁移；`USER_LEFT_HOME` 提交后触发重算（2026-10-29，后端 `c0d7b63` + Android `44a2529`，后端 78/78 绿 + assembleDebug 通过；运行时验证见第一节）
- [x] **eventId 服务端全局去重**：V3 `device_event` 流水表 event_id 唯一键，入口存在性跳过 + 并发约束兜底（2026-10-27，`57ea72c`，28/28 测试绿；运行时验证见第一节）
- [x] **外部服务凭据用户级配置（App 设置页 BYOK）**：统一设置页支持用户自带高德 key 与 LLM 三项（V5 `user_credential` 表存 per-user 凭据，api_key AES-256-GCM 加密落库、接口只写不回显明文、每服务支持显式禁用并回显生效来源），解析链"用户显式禁用 > 用户级 > 服务端默认(环境变量) > 关闭"，凭据解析永不抛、失败回落服务端默认；`AmapClient`/`LlmChatClient` 改按调用传凭据，`ExplanationGenerator.generate` 增参传的是解析后的 `LlmEndpoint` 而非原写的 userId（保持 ai/ 层纯函数可测，按实际实现修正表述）（2026-10-27，后端 `af862e4` + Android `e9411cb`，后端 57/57 绿 + assembleDebug 通过；运行时冒烟见第一节）
- [x] **客户端时钟偏差治理**：端侧从 OkHttp 响应 Date 头学习服务器偏移（EWMA 平滑、|偏差|>5min 丢弃、SharedPreferences 持久化、异常全静默）校正上报时刻，outbox 重放保留原始时刻；服务端纯函数消毒——空/非法兜底接收时刻（绝不拒收，保证 outbox 能排空）、夹取到 [计划创建−1min, now+2min] 并 log.warn 留痕（2026-10-29，后端 `6593cc8` + Android `c9ae1fd`，后端 78/78 绿 + assembleDebug 通过；运行时验证见第一节）
- [ ] ~~不做（明确排除）~~：iOS、FCM 推送（国内不可靠且轮询+ACK 已够用）、多设备协同——避免误起炉灶

## 三、已完成

- [x] **贪睡时间从校准样本丢失的修复**（2026-10-27，`ecea2e8`）：代码走查发现贪睡后同 planId 重报 ALARM_FIRED 会覆盖 `triggered_at`（last-write），校准样本只算末次响铃到关闭、贪睡时长被挤掉，与"贪睡计入起床延迟"的建模语义相反。改为 first-write-wins，事件状态迁移收进 `WakeHistory.applyDeviceEvent` 纯方法并补 5 例回归测试（后端 18/18 绿）；运行时回归用例见上方第一节"贪睡语义回归"
- [x] **路演文档对齐实现**（2026-10-27）：修正 Flutter→原生 Android、技术栈如实化（Spring AI/Redis/RabbitMQ 标规划）、四类目标→两类、四级→三级自动化、Quartz 单实例如实表述、Q5/Q6 问答对齐当前实现、演示故事线全部改为当前可演示步骤；文档头新增"实现状态速览"表，面试前先看那张表
- [x] **行为学习一期**：起床延迟 SHRINK_V1 校准 + 响铃页晨间简报 + 出门打卡（2026-10-27，`87d3858`→`502f1a3` 共 5 提交，已推 origin/main）
  - 备注 1：后端单测 13/13 绿（WakeLatencyEstimatorTest 6 + DecisionEngineTest 7），Android assembleDebug 通过；端到端验证未跑 → 见上方第一节
  - 备注 2：校准触发点放在 DevicePlanController（事件事务提交**之后**）而非 DecisionService 的事务内——事务内调用 @Transactional 代理再捕获异常会标记 rollback-only，反而丢掉 DISMISSED 事件本身；详见 `87d3858` 提交说明
- [x] knife4j 在线 API 文档（`1a8feb3`）
- [x] 闹钟模块重构 + Quartz 夜间计划任务（`4e4fb32`）
- [x] 项目初始化（`5a510ed`，旧 Flutter 版仅在 git 历史可查）
