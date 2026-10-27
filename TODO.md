# TODO — 待办与进度看板

> **协作约定**：这是项目唯一的待办清单，人和 AI 共用。
> 标记：`[ ]` 待做 · `[~]` 进行中（开工时改）· `[x]` 已完成（完成时补日期和提交号）。
> 任何时候看这一个文件就知道项目状态；开工前先领任务，完成后回来勾掉。

## 一、待做：行为学习一期 · 端到端验证（优先）

代码已推送（见"已完成"），但当时不在常用设备上，以下验证**全部未跑**。

### 后端（需要本地 MySQL 8 + JDK 21）

- [ ] **启动迁移验证**：先 `DROP DATABASE wakewise;`（或用 `MYSQL_DB=wakewise_v2`），再启动后端（记得带 `-s ~/Applications/apache-maven-3.9.16/conf/settings-shucheng.xml`），确认 Flyway 跑完 V1+V2 无报错
- [ ] **造历史触发校准**：开 doc.html（`http://localhost:8080/doc.html`，Authorize 填登录 JWT）→ 注册（城市"北京"）→ 建 ARRIVE_BY 闹钟（08:45 / prep 30 / commute 40 / maxAdvance 30）→ 循环 6 轮：`POST /decisions/evaluate` 拿新 planId → 对每个 planId 发两条 device-events：ALARM_FIRED（如 `22:45:00Z`）+ ALARM_DISMISSED（依次延迟 3/5/6/7/9/12 分钟，occurredAt 全 UTC）
- [ ] **校准断言**：`SELECT * FROM behavior_learning` 出一行（sample_count=6、SHRINK_V1、estimate≈5.36）；`GET /morning/context` 返回 `calibration` 字段；再 evaluate 一次，新计划详情 modelVersion = `QUANTILE_V1+CAL(n=6)`；后端日志出现 `Wake latency calibrated`
- [ ] **出门事件幂等**：同一 planId 连发两次 USER_LEFT_HOME（不同 eventId、同 occurredAt）→ `SELECT left_home_at FROM wake_history WHERE plan_id=...` 只记第一次

### Android（需要模拟器/真机）

- [ ] ⚠️ **装包前须知**：Room v1→2 破坏性迁移会清本地库，已布防闹钟会丢——装新 APK 后重新 evaluate + 打开 app 让 PlanSyncWorker 同步一次
- [ ] **响铃页简报三级兜底**：`adb shell am broadcast -a com.wjh.wakewise.action.FIRE --es planId <真实planId> --es label 测试 --el targetAt 1790000000000` → 先显示"简报同步中…"，网络通则 ≤3 行简报；关后端再触发 → 读 Room 缓存；清数据冷启动 → "简报暂不可达 · 闹钟不受影响"
- [ ] **时钟修复**：响铃页停留跨过整分钟，时钟应跳分钟
- [ ] **出门打卡闭环**：点"关闭"→ 通知栏出"出门打卡"→ 点【我出门了】→ 通知消失 + 后端 `left_home_at` 有值（planId 需真实，否则归属校验不过）
- [ ] **首页校准提示**：校准生效后首页显示"已根据最近 6 次起床记录校准"

## 二、待做：后续功能（按优先级）

- [ ] **LLM 解释生成**：接 Spring AI 到现有 `ExplanationGenerator` 接口，模板兜底。红线：LLM 只写文案不算时间
- [ ] **真实交通 ETA**：高德 Web 服务 API（免费额度够 demo 用），决策引擎已预留口子
- [ ] **日历接入**：Android `CalendarProvider` 读次日首个事件，自动填"目标到达"时间（纯端侧，无后端改动，三条里最便宜）
- [ ] **准备时长校准**：消费已积累的 `left_home_at` 样本（攒够 ≥5 条后实施），替换固定 PREP_P90_FACTOR；一期只埋点未消费
- [ ] **eventId 服务端全局去重**：当前只有字段级幂等（ALARM_SNOOZED 重复上报会多计贪睡次数），详见 API 设计 5.3
- [ ] **客户端时钟偏差治理**：occurredAt 信任设备时钟，README 诚实清单已提
- [ ] ~~不做（明确排除）~~：iOS、FCM 推送（国内不可靠且轮询+ACK 已够用）、多设备协同——避免误起炉灶

## 三、已完成

- [x] **行为学习一期**：起床延迟 SHRINK_V1 校准 + 响铃页晨间简报 + 出门打卡（2026-10-27，`87d3858`→`502f1a3` 共 5 提交，已推 origin/main）
  - 备注 1：后端单测 13/13 绿（WakeLatencyEstimatorTest 6 + DecisionEngineTest 7），Android assembleDebug 通过；端到端验证未跑 → 见上方第一节
  - 备注 2：校准触发点放在 DevicePlanController（事件事务提交**之后**）而非 DecisionService 的事务内——事务内调用 @Transactional 代理再捕获异常会标记 rollback-only，反而丢掉 DISMISSED 事件本身；详见 `87d3858` 提交说明
- [x] knife4j 在线 API 文档（`1a8feb3`）
- [x] 闹钟模块重构 + Quartz 夜间计划任务（`4e4fb32`）
- [x] 项目初始化（`5a510ed`，旧 Flutter 版仅在 git 历史可查）
