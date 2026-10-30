# TODO — 待办与进度看板

> **协作约定**：这是项目唯一的待办清单，人和 AI 共用。
> 标记：`[ ]` 待做 · `[~]` 进行中（开工时改）· `[x]` 已完成（完成时补日期和提交号）。
> 任何时候看这一个文件就知道项目状态；开工前先领任务，完成后回来勾掉。

## 一、待做：行为学习一期 · 端到端验证（优先）

代码已推送（见"已完成"）。2026-10-30 后端部分已自动化验证通过（见下方勾选项）；剩余 ⚠️ 项需真 key / 真机。

### 后端（需要本地 MySQL 8 + JDK 21）

> 2026-10-30 用 `MYSQL_DB=wakewise_v2` 新库 + Python 脚本（`%TEMP%\ww_verify.py`）跑完一轮自动化验证，17/17 PASS + DB 断言全符合；样本时刻压在消毒窗口内（秒级延迟），数值与 SHRINK_V1/PREP_SHRINK_V1 公式逐位吻合。带 ⚠️ 的项仍需真 key / 真机补跑。

- [x] **启动迁移验证**：先 `DROP DATABASE wakewise;`（或用 `MYSQL_DB=wakewise_v2`），再启动后端（记得带 `-s ~/Applications/apache-maven-3.9.16/conf/settings-shucheng.xml`），确认 Flyway 跑完 V1~V5 无报错（2026-10-30，`wakewise_v2` 新库 V1~V5 全过、4.5s 启动、Quartz 排班正常）
- [x] **真实 ETA 冒烟**（`e9c2aed` 一期实装）：`AMAP_KEY=<key>` 起后端 → PATCH /auth/me 设家址 → 建 ARRIVE_BY 闹钟（真实目的地）→ evaluate 后 modelVersion 含 `+ETA`、morning/context traffic 的 etaSource=AMAP 且分钟为真实值；不配 key 重启 → 回落手填分钟、traffic.etaSource 为 null、无报错（2026-10-30 真key 走 BYOK 用户级路径：中关村→北京西站驾车 P50=22min/P90=35min 与直调高德吻合、traffic_cache 快照落库、禁用后回落无报错；**修复真 bug 一个**：高德真实响应 duration 是字符串数字，`positiveInt` 只认数值节点导致 ETA 静默失效——单测 fixture 误用数字类型，已修 + 补真实形状回归用例，78/78 绿。注意 morning/context 的 traffic 只统计"明天有闹钟"的场景，周五晚验证需 recurrence 含周六）
- [x] **eventId 全局去重**（`57ea72c` V3 实装）：同一 eventId 连发两次 ALARM_SNOOZED → `SELECT snooze_count FROM wake_history WHERE plan_id=...` 只计 1 次，两次请求均返回成功（2026-10-30，两次均 success、`device_event` 无重复 event_id 行）
- [x] **造历史触发校准**：开 doc.html（`http://localhost:8080/doc.html`，Authorize 填登录 JWT）→ 注册（城市"北京"）→ 建 ARRIVE_BY 闹钟（08:45 / prep 30 / commute 40 / maxAdvance 30）→ 循环 6 轮：`POST /decisions/evaluate` 拿新 planId → 对每个 planId 发两条 device-events：ALARM_FIRED（如 `22:45:00Z`）+ ALARM_DISMISSED（依次延迟 3/5/6/7/9/12 分钟，occurredAt 全 UTC 且必须是过去时刻——消毒器会把未来时刻夹到 now+2min）（2026-10-30 脚本化跑通；注意原文档里"过去几小时"的时刻会被消毒下界夹平——窗口是 [计划创建−1min, now+2min]，样本必须发生在对应计划创建之后）
- [x] **校准断言**：`SELECT * FROM behavior_learning` 出一行（sample_count=6、SHRINK_V1、estimate≈5.36）；`GET /morning/context` 返回 `calibration` 字段；再 evaluate 一次，新计划详情 modelVersion = `QUANTILE_V1+CAL(n=6)`；后端日志出现 `Wake latency calibrated`（2026-10-30，用秒级延迟 30~105s：estimate=2.4318 与公式 `(5×4+6×1.125)/11` 逐位吻合、modelVersion=`QUANTILE_V1+CAL(n=6)`、calibration 字段返回）
- [x] **出门事件幂等**：同一 planId 连发两次 USER_LEFT_HOME（不同 eventId、同 occurredAt）→ `SELECT left_home_at FROM wake_history WHERE plan_id=...` 只记第一次（2026-10-30，left_home_at 只记首次）
- [x] **贪睡语义回归**（`ecea2e8` 修复项）：同一 planId 依次发 ALARM_FIRED(t0) → ALARM_SNOOZED → ALARM_FIRED(t0+5min) → ALARM_DISMISSED(t0+12min) → `triggered_at` 应为 t0（首次响铃），校准样本延迟含贪睡 5 分钟（2026-10-30 秒级变体：triggered_at=首次响铃 t0、dismissed_at=t0+110s，first-write-wins 生效）
- [x] **BYOK 运行时冒烟**（`af862e4` 后端 + `e9411cb` Android 实装）：起后端 → knife4j `GET /me/external-services` 看默认状态（AMAP/LLM 按 `AMAP_KEY`/`LLM_*` 是否配置显示 SERVER 或 OFF）→ PUT 用户级高德 key（真 key）→ evaluate 的 modelVersion 含 `+ETA`、traffic.etaSource=AMAP → PUT `{disabled:true}` → evaluate 回落手填分钟且无报错 → GET 确认任何响应都无 api_key 明文；LLM 三项同理（配真端点看 LLM 文案、只填两项看 400 提示）；App 端统计页入口 → 设置页四态流转（继承→你的 key→禁用→恢复默认）（2026-10-30 后端全链路验证通过：真 key 写入→+ETA 生效→禁用回落→清除恢复、LLM 真端点（OpenAI 兼容 glm-5.3-flash）文案生成正常且失败回落模板、只填两项 400 提示正确、全程无 api_key 明文；**App 端设置页四态流转待真机**）
- [x] **准备时长校准端到端**（`c0d7b63` 后端 + `44a2529` Android 实装）：doc.html 造 ≥5 条同时含 ALARM_DISMISSED + USER_LEFT_HOME 的历史（出门距关闭 36/42/48/54/60 分钟，occurredAt 用过去时刻）→ `SELECT * FROM behavior_learning WHERE feature_key='PREP_DURATION'` 出一行（sample_count≥5、PREP_SHRINK_V1、estimate≈实际/设定比率）→ 再 evaluate 新计划 modelVersion 含 `+PREP(n=5)` → `GET /morning/context` 的 calibration 带 prepSampleCount/prepRatioP50 → App 首页出现"准备时长按最近 N 次出门校准（约设定的 X 倍）"（2026-10-30 后端部分：prepSampleCount=5、prepRatioP50=1.2333 与公式吻合、modelVersion 含 `+PREP(n=5)`；App 首页提示待真机）
- [x] **occurredAt 消毒**（`6593cc8` 后端 + `c9ae1fd` Android 实装）：向 device-events 发 occurredAt=未来 1 小时 → 请求成功、`SELECT occurred_at FROM device_event WHERE event_id=...` 夹到 now+2min 且日志出 `Device event time sanitized`；发非法字符串 → 同样成功不 500、occurred_at≈接收时刻；App 端改系统时间 +10 分钟后成功发一次请求（学到 offset）→ 后续事件上报时刻应已被校正（2026-10-30 后端部分：未来 1h 夹到 now+2min（clampedToCeiling=true）、非法串兜底接收时刻（fellBackToNow=true）、日志留痕；App 端 offset 学习待真机）

### Android（需要模拟器/真机）

> 2026-10-30 Pixel_8 模拟器端到端跑通（emulator + adb，输入通道损坏的部分用后端 API 补位）。**修复真 bug 两个**：①注册页昵称/城市字段永远不显示（`visibility=gone` 设在 TextInputLayout 父容器上，代码只改了子 EditText）→ 注册功能整体不可用；②首页"建议 X 出门"显示 UTC 时刻未转本地时区（应 08:07 显示 23:43）。已修①并重编译验证，②待修（见下"遗留"）。

- [x] **装包前须知**：Room v1→2 破坏性迁移会清本地库，已布防闹钟会丢——装新 APK 后重新 evaluate + 打开 app 让 PlanSyncWorker 同步一次（2026-10-30，`pm clear` + 装包后走登录 → PlanSyncWorker 正常补拉布防）
- [x] **响铃页简报三级兜底**：`adb shell am broadcast -a com.wjh.wakewise.action.FIRE --es planId <真实planId> --es label 测试 --el targetAt 1790000000000` → 先显示"简报同步中…"，网络通则 ≤3 行简报；关后端再触发 → 读 Room 缓存；清数据冷启动 → "简报暂不可达 · 闹钟不受影响"（2026-10-30 变通验证：`am broadcast` 到不了 `exported=false` 的 receiver（TODO 原方法不可行），改用真实链路——AlarmManager 布防 + `cmd alarm set-time` 拨时钟触发：一级兜底 ✅ 响铃页在线拉简报（LLM 解释+天气+出门建议全显示）、离线 ✅ 首页显示"云端暂不可达 · 已下发的本地闹钟仍会正常响铃"；铃声/前台服务通知/fullScreenIntent 均正常）
- [ ] **时钟修复**：响铃页停留跨过整分钟，时钟应跳分钟（未验证——响铃页停留时间不够长；代码有 TICKER 逻辑，低风险）
- [x] **出门打卡闭环**：点"关闭"→ 通知栏出"出门打卡"→ 点【我出门了】→ 通知消失 + 后端 `left_home_at` 有值（planId 需真实，否则归属校验不过）（2026-10-30，全链路 ✅：关闭→ALARM_DISMISSED 落库→通知弹出→点【我出门了】→通知消失+`USER_LEFT_HOME` 落库+`wake_history.left_home_at` 写入）
- [ ] **首页校准提示**：校准生效后首页显示"已根据最近 6 次起床记录校准"（部分验证：模拟器账号无校准样本故无提示，逻辑依赖 morning/context 的 calibration 字段——后端字段已验证返回，UI 渲染待有样本账号复验）
- [ ] **日历导入**（`8af9d61` 一期实装）：系统日历建明日 09:30 非全天事件（带地点）→ 新建闹钟点"从日历导入"→ 时间/名称/地点自动填 + Toast；明日无日程、拒绝 READ_CALENDAR 权限、日历里全是全天节假日事件三条降级路径均只提示不阻塞（未跑——模拟器 adb 文本输入通道损坏无法建日历事件/填表单，待真机或修好输入法后补）

**遗留（新发现待修）**：
- ~~首页"建议 23:43 出门"时区 bug~~ **已澄清为误报**：模拟器时区是 GMT，App 代码 `withZone(ZoneId.systemDefault())` 本来正确；时区切到 Asia/Shanghai 后显示正常（明早 07:31 / 建议 08:10 出门）。无需修复。
- 注册页字段 bug 已修（`fragment_login.xml` 的 TextInputLayout 加 id + `LoginFragment` 改对父容器 setVisibility），已提交 `b685023`。

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

- [x] **路线图 P0 三项：演示闭环补齐**（2026-10-28，后端 `938fef7` + Android `5989758` + 文档 `05678ad`）
  - **P0-1** `POST /alarms/{id}/preview` 补 `DecisionService.previewForUser`（此前 Controller 调的方法根本不存在，接口必 500）：ARRIVE_BY 走 `compute()` 全链路；WAKE_AT 返回 `baseline == recommended`、`delta=0`、`KEEP_BASELINE`，不写库不发通知，事务标 `readOnly`。
  - **P0-2** `WAKE_AT` 也能产出 `decision_history`：`evaluateAndPropose` 去掉"非 ARRIVE_BY 直接 return null"，改走新增 private `evaluateWakeAt`（status=DISPATCHING、delta=0、KEEP_BASELINE、confidence 由天气质量推导、解释引用天气与通勤摘要），同时刷 `alarm.next_trigger_at`。`NightlyPlanJob` 随之去掉 ARRIVE_BY 双轨与 `AlarmService` 依赖（字段 4 → 3），夜间任务对两类闹钟语义统一。
  - **P0-3** Android 编辑页加"预估明早计划"按钮：`POST alarms/{id}/preview` 走通链路，底部弹窗展示 recommendedWakeAt / deltaMinutes / 迟到概率 / confidence / 因素归因 / explanation；抽 `buildRequest()` + `createAlarmThen()` 复用表单校验与建闹钟流程，保存逻辑未改。
  - 验证：后端 `mvn -q test` 57/57 绿；Android `assembleDebug` BUILD SUCCESSFUL。**真实数据冒烟未跑**（需 MySQL 密码 + 真机/模拟器，见第一节）。
  - 顺手修掉 BYOK 改造（`af862e4`/`e9411cb`）遗留的 3 处编译错误：`DecisionService` 两处 `explanationGenerator.generate(...)` 仍传 `userId`（签名已改传 `LlmEndpoint`），`AlarmEditFragment` 一处括号错位 + `StringBuilder` 未声明。这两端当时是**编译不过**的状态，本次一并修好。
  - 遗留不一致（未处理，待收敛）：`AlarmService.update` 仍只对 ARRIVE_BY 调 `evaluateAndPropose`，WAKE_AT 走"改闹钟"路径时只刷 `nextTriggerAt`、不写历史，与夜间任务语义不一致；`evaluateWakeAt` 末尾 `new WakePlan(..., "DISPATCHING", ...)` 的 status 字符串被 `withPersistence` 忽略（实际取 `history.getStatus().name()`），传 `null` 更诚实。

- [x] **贪睡时间从校准样本丢失的修复**（2026-10-27，`ecea2e8`）：代码走查发现贪睡后同 planId 重报 ALARM_FIRED 会覆盖 `triggered_at`（last-write），校准样本只算末次响铃到关闭、贪睡时长被挤掉，与"贪睡计入起床延迟"的建模语义相反。改为 first-write-wins，事件状态迁移收进 `WakeHistory.applyDeviceEvent` 纯方法并补 5 例回归测试（后端 18/18 绿）；运行时回归用例见上方第一节"贪睡语义回归"
- [x] **路演文档对齐实现**（2026-10-27）：修正 Flutter→原生 Android、技术栈如实化（Spring AI/Redis/RabbitMQ 标规划）、四类目标→两类、四级→三级自动化、Quartz 单实例如实表述、Q5/Q6 问答对齐当前实现、演示故事线全部改为当前可演示步骤；文档头新增"实现状态速览"表，面试前先看那张表
- [x] **行为学习一期**：起床延迟 SHRINK_V1 校准 + 响铃页晨间简报 + 出门打卡（2026-10-27，`87d3858`→`502f1a3` 共 5 提交，已推 origin/main）
  - 备注 1：后端单测 13/13 绿（WakeLatencyEstimatorTest 6 + DecisionEngineTest 7），Android assembleDebug 通过；端到端验证未跑 → 见上方第一节
  - 备注 2：校准触发点放在 DevicePlanController（事件事务提交**之后**）而非 DecisionService 的事务内——事务内调用 @Transactional 代理再捕获异常会标记 rollback-only，反而丢掉 DISMISSED 事件本身；详见 `87d3858` 提交说明
- [x] knife4j 在线 API 文档（`1a8feb3`）
- [x] 闹钟模块重构 + Quartz 夜间计划任务（`4e4fb32`）
- [x] 项目初始化（`5a510ed`，旧 Flutter 版仅在 git 历史可查）
