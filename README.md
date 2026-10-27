# 知晨 WakeWise — AI Morning Assistant

> **让每一次起床，都恰到好处。**
> Wake up at the right time, not just on time.

WakeWise 不是一个"根据天气提前几分钟"的闹钟，而是一套晨间决策系统：用户设定的是"几点前必须到达"，系统在夜间综合天气、通勤、日程与个人历史行为，算出最佳可行唤醒时间，并且每次调整都带原因、置信度和一键撤销。

产品设计、API 契约与数据库设计见 [`相关文档/`](./相关文档/)。

## 技术栈（当前重写版）

| 层 | 技术 | 说明 |
|---|---|---|
| 客户端 | **原生 Android（Java 17 + XML + ViewBinding）** | 精确闹钟、全屏响铃、开机重排、离线兜底 |
| 后端 | **Spring Boot 3.3 + Java 21**（模块化单体） | 决策引擎、可靠性链路、Quartz 夜间任务 |
| 存储 | MySQL 8（Flyway 迁移） | 事务与审计数据 |
| 外部数据 | Open-Meteo（天气，免 Key）；LLM 文案（OpenAI 兼容端点，可选）；高德路径规划（可选） | 失败自动降级 |
| 认证 | JWT（Spring Security） | MVP 简化实现 |

> 注：这是**推倒重写**后的版本。旧版（Flutter + Spring Boot）代码仍在 git 历史 `5a510ed` 中可查。

## 目录结构

```
WakeWise/
├── 相关文档/            # PRD、API 设计、数据库设计、路演材料
├── backend/             # Spring Boot 后端
│   └── src/main/java/com/wjh/wakewise/
│       ├── common/          # 统一响应、错误码、异常处理
│       ├── config/          # Security / JWT
│       ├── agent/           # Quartz 夜间计划任务（默认 22:00）
│       ├── ai/              # 解释生成端口（OpenAI 兼容 LLM 改写 + 确定性模板兜底）
│       └── domain/
│           ├── user/        # 注册/登录
│           ├── alarm/       # 闹钟 CRUD + 规则
│           ├── behavior/    # 行为学习（起床延迟 SHRINK_V1 校准）
│           ├── weather/     # Open-Meteo 客户端 + 缓存降级
│           ├── travel/      # 高德地理编码/路径规划 + ETA 缓存降级
│           ├── decision/    # 分位数决策引擎 + 计划状态机 + 设备同步
│           ├── morning/     # 晨间简报
│           ├── statistics/  # 周度统计
│           ├── history/     # 晨间执行历史
│           ├── notification/# 站内通知
│           └── feedback/    # 反馈
└── android/             # 原生 Android 客户端
    └── app/src/main/java/com/wjh/wakewise/
        ├── alarm/           # AlarmManager 精确调度、响铃前台服务、开机重排
        ├── calendar/         # CalendarProvider 明日首个事件读取（导入闹钟）
        ├── sync/            # 计划同步 Worker（pending → 调度 → ACK）
        ├── data/            # Room 本地缓存 + outbox 事件
        ├── network/         # Retrofit 接口
        └── ui/              # 登录 / 首页 / 闹钟 / 统计 / 全屏响铃页
```

## 快速启动

### 后端

要求：JDK 21、Maven 3.9+、本地 MySQL 8。

```bash
cd backend
# Windows (PowerShell/Git Bash)
MYSQL_USER=root MYSQL_PASSWORD=你的密码 mvn spring-boot:run
# Linux/macOS
MYSQL_USER=root MYSQL_PASSWORD=你的密码 mvn spring-boot:run
```

- 数据库默认名 `wakewise`，首次启动自动建库并执行 Flyway 迁移（`createDatabaseIfNotExist=true`）。
- ⚠️ 如果本机存在**旧版实现留下的 `wakewise` 库**，新旧 schema 冲突会导致启动失败。请先 `DROP DATABASE wakewise;`，或用 `MYSQL_DB=wakewise_v2` 指定新库名。
- JWT 密钥、端口、夜间任务 cron 见 `application.yaml`，均可通过环境变量覆盖（`JWT_SECRET`、`SERVER_PORT`、`WAKEWISE_NIGHTLY_PLAN_CRON`）。
- **LLM 解释文案（可选，默认关闭）**：三个环境变量 `LLM_BASE_URL` / `LLM_API_KEY` / `LLM_MODEL` 配置任意 OpenAI 兼容端点，未配置时使用确定性模板文案（行为与旧版一致）。常用 base-url（给到 `/v1` 为止）：

  | 服务商 | base-url | model 示例 |
  |---|---|---|
  | DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` |
  | OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` |
  | 通义千问 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` |
  | Kimi | `https://api.moonshot.cn/v1` | `moonshot-v1-8k` |

  红线：LLM 只做文案改写（提示词只含引擎算好的事实，禁止编造/推算时间），失败/超时/空白输出一律即时回落模板——AI 可以失败，闹钟不能。
- **真实交通 ETA（可选，默认关闭）**：`AMAP_KEY`（高德 Web 服务 key）+ App 统计页设置"常驻地址（家）"，闹钟目的地照旧填地址文本；三者齐备后通勤分钟来自高德路径规划（按出行方式选步行/骑行/公交/驾车），失败回落手填分钟。

### Android 客户端

1. 用 Android Studio（Ladybug+，AGP 8.5）打开 `android/` 目录，或直接运行 `./gradlew assembleDebug`——仓库已包含 Gradle Wrapper（8.7）。
2. 后端地址默认 `http://10.0.2.2:8080/api/v1/`（模拟器访问宿主机）；真机调试请在 `data/SessionManager.java` 中改成电脑局域网 IP。明文 http 仅 debug 构建允许（见 `networkSecurityConfig`），release 包需 https 后端地址。
3. 模拟器/真机运行：注册账号（城市填中文如"北京"即可触发真实天气）→ 新建"目标到达"闹钟 → 首页查看决策计划 → 确认/恢复。编辑页可点"从日历导入明日首个日程"自动填时间（需授权读取日历；模拟器日历需先建一条明日非全天事件）。

## 核心设计

### 决策引擎（`DecisionEngine`，确定性、可审计）

```
RequiredTime      = 起床延迟 + 准备时长 + 通勤时长 + 安全余量      （各段按 P50/P90 正态近似）
CandidateWakeTime = D − Quantile(RequiredTime, q)
```

- 分位数 `q` 由用户风险偏好决定：睡眠优先≈P60 / 平衡≈P75 / 保守≈P90。
- 起床延迟 P50/P90 支持按用户历史校准：从 `wake_history` 真实样本（dismissedAt − triggeredAt）算经验分位数并与全局先验收缩混合（SHRINK_V1）；样本不足 5 条时保持固定先验，冷启动行为与旧版一致，校准参与决策时 modelVersion 标注 `QUANTILE_V1+CAL(n=N)`。
- 硬约束：调整必须落在 `[基准−最大提前, 基准+最大延后]` 内，且不晚于目标前 15 分钟；延后需用户显式授权。
- 平静期规则：无外部风险信号且提前量不足 10 分钟时不折腾，保持原计划（计划变化成本）。
- 迟到概率用正态 CDF 闭式计算；输出带因素归因（通勤波动余量 / 降水增量 / 安全余量）。解释文案默认确定性模板，配置 LLM 后由模型改写——但提示词只含引擎算好的事实且明令禁止编造时间，LLM 不参与时间计算，失败即时回落模板。

### 可靠性链路（AI 可以失败，闹钟不能）

```
夜间任务/手动评估 → WakePlan(PROPOSED/DISPATCHING)
  → 用户确认（L2 且高置信度可自动）→ DISPATCHING
  → 客户端 GET /device-plans/pending → 端侧精确调度 → 先建新后删旧 → POST ack → ARMED
  → 响铃/关闭/贪睡 → POST /device-events（失败落 outbox 补报）
  → 关闭后可"出门打卡"（USER_LEFT_HOME）→ 为准备时长校准积累样本（一期只积累不消费）
```

- 新计划未 ACK 前，旧计划永不下线；同闹钟 ACK 成功后旧计划才标记 SUPERSEDED。
- 网络中断：客户端开机从 Room 缓存重排闹钟（不依赖云端）；事件进 outbox 补报。
- 天气/外部 API 失败：置信度降级、继续用基准计划，不阻塞响铃。

### API 一览（`/api/v1`，统一 `{success,data,traceId,timestamp}` 响应）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/auth/register` `/auth/login` | 注册/登录（返回 JWT） |
| GET/PATCH | `/auth/me` | 资料 / 更新常驻地址（家，ETA 出发地） |
| GET/POST/PATCH/DELETE | `/alarms[/{id}]` | 闹钟 CRUD（乐观锁版本） |
| POST | `/alarms/{id}/preview` | 即时预览计划 |
| POST | `/decisions/evaluate` | 生成正式计划 |
| GET | `/decisions/{planId}` | 计划详情/解释 |
| POST | `/decisions/{planId}/confirm` `/revert` | 确认 / 撤销恢复 |
| GET | `/device-plans/pending` | 端侧拉取待同步计划 |
| POST | `/device-plans/{planId}/ack` | 本地调度成功回执 → ARMED |
| POST | `/device-events` | 响铃/关闭/贪睡事件（幂等） |
| GET | `/morning/context` | 晨间简报（唤醒/出门/天气/交通/首事件） |
| GET | `/notifications` | 站内通知 |
| POST | `/feedback` | 反馈 |
| GET | `/statistics/weekly` | 周度统计 |

完整契约见《相关文档/WakeWise-API设计.md》（MVP 有少量精简：评估为同步返回、认证为 JWT、未引入 RabbitMQ/Redis——按 PRD 路线图它们属于 V1）。后端启动后也可访问在线文档 `http://localhost:8080/doc.html`（knife4j），点 Authorize 填入登录返回的 JWT 即可直接调试接口。

## 已知限制（诚实清单）

- iOS：客户端为原生 Android，暂无 iOS 版本（旧 Flutter 端可从 git 历史恢复参考）。
- 交通 ETA（一期已实装）：高德 Web 服务 API——常驻地址（家）与闹钟目的地做地理编码缓存坐标，按出行方式路径规划取真实耗时替换手填通勤分钟（2 小时缓存，`AMAP_KEY` 环境变量配置，未配置/失败回落手填）；modelVersion 标注 `+ETA`，晨间简报 traffic 带 `etaSource`。多方式组合策略与实时路况为规划。
- 行为学习（第一期已实装）：起床延迟先验按 `wake_history` 真实数据校准（SHRINK_V1 收缩估计，≥5 个有效样本生效，首页显示"已根据最近 N 次起床记录校准"）；准备/通勤时长仍用用户设置值；出门事件（USER_LEFT_HOME）仅积累数据、暂不参与决策；`occurredAt` 信任客户端时钟，偏差治理留待后续。
- LLM 解释文案：已实装 OpenAI 兼容可配置客户端（`LLM_BASE_URL`/`LLM_API_KEY`/`LLM_MODEL` 三项留空即纯模板、零感知）；自研轻量 RestClient 客户端而非 Spring AI（Spring AI 1.0 官方仅支持 Boot 3.4+，本项目钉在 Boot 3.3，接口留有替换位）。
- 外部服务凭据（BYOK，已实装）：App 设置页可自带高德 key 与 LLM 三项（V5 `user_credential` 表，api_key AES-256-GCM 加密落库、接口只写不回显明文），解析链「用户显式禁用 ＞ 用户级 ＞ 服务端默认（环境变量）＞ 关闭」，且凭据解析永不抛、失败回落服务端默认，不影响闹钟主链路；`traffic_cache` 路径快照跨用户共享（ETA 是路线属性，省配额，有意决策）；`BYOK_ENC_KEY` 生产必须环境变量注入强随机值，换钥后旧密文自动失效回落、需用户重存。凭据连通性校验、多凭据轮换为规划。
- 日历接入（一期已实装）：闹钟编辑页可"从日历导入"明日首个非全天日历事件，自动填目标到达时间（名称/地点为空时一并带上；运行时申请 READ_CALENDAR，拒绝或无日程只提示、不影响手动建闹钟）；多日历账户选择、后台自动读取为规划。
- 推送（FCM）为下一阶段工作。
- `application.yaml` 的 JWT 密钥是开发默认值，生产必须通过环境变量注入强随机密钥。

## 历史版本

- `5a510ed`：初版实现（Spring Boot 规则引擎 + Flutter 客户端），已于工作区删除、仅存于 git 历史，供对照。
