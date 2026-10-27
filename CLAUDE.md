# WakeWise（知晨）

AI Morning Assistant：用户设定"几点前必须到达"，系统夜间综合天气与个人行为算出最佳唤醒时间。Spring Boot 3.3（Java 21）模块化单体后端 + 原生 Android（Java 17 + ViewBinding）客户端 + MySQL 8（Flyway，ddl-auto=validate）。

## 协作入口（先读这个）

- **待办与进度的唯一看板是根目录 `TODO.md`**：开工前先读、领任务把 `[ ]` 改 `[~]`、完成后改 `[x]` 并补提交号。任何"下一步做什么"的问题以它为准。
- 设计与现状的差异以 **README「已知限制（诚实清单）」** 和 **`相关文档/WakeWise-简历与项目路演.md` 开头的「实现状态速览」表**为准；实现变更时同步这两处，不许宣称没实装的东西。

## 构建与验证

- 后端（在 `backend/`）：Maven 需私有仓库配置（路径见用户全局备注），测试 `mvn -s <settings> test`；本地跑需 MySQL 8（`MYSQL_PASSWORD` 环境变量，首次启动自动建库跑 Flyway）。
- Android（在 `android/`）：`./gradlew assembleDebug`；Room 为破坏性迁移，升级 version 会清本地库（已知且接受）。
- 在线 API 文档：后端启动后 `http://localhost:8080/doc.html`（knife4j，Authorize 填登录返回的 JWT）。

## 代码约定

- 决策引擎是纯函数、确定性：同输入必同输出，改动需保测试可回放；LLM/校准等增强链路失败绝不影响闹钟主链路（"AI 可以失败，闹钟不能"）。
- 注释与 UI 文案用中文；后端 Controller 惯例 `ApiResponse.ok(...)` + `AuthContext.currentUserId()` + 归属校验抛 `RESOURCE_NOT_FOUND`。
- 测试风格：纯 JUnit5 + AssertJ 行为断言，不引入 Mockito/Spring 测试上下文。
