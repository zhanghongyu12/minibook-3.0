# Changelog

本项目所有重要变更均会记录在此文件中。
格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added

- **F-15 云端存储改版：JSON 通用表 → 6 张真实业务表 + `sync_log`（DEC-037，2026-10-10）**：
  用户验收云端库时否决「业务字段塞进 `payload` JSON」的通用记录表（原话「不要这样，要在云端做明确的表，设计表结构」），
  改为按实体建 6 张类型化业务表（`ledgers`/`categories`/`transactions`/`debts`/`budgets`/`user_category_rules`，
  PK `(user_id,id)`，金额 `BIGINT` 存「分」、`date` VARCHAR(10)、`month` VARCHAR(7)，按查询形状建索引）
  + `sync_log` 变更日志（**拉取游标的唯一来源**）；墓碑 = 业务表**物理删行** + 日志 `deleted=1`（云端查账不被墓碑污染）；
  `created_at_raw`/`updated_at_raw` 保留客户端**原始 ISO 串**（逐字回传），`updated_at` 另存归一化 `DATETIME(6)` 供 LWW。
  **写协议零改动 ⇒ Android 一行未改**（`payload` 形状/键名与 DEC-036 语义不变），服务端用
  `entity_store.shred()/reassemble()` 做 `payload ⇄ 列` 双向搬运。
  迁移 `0002_typed_entities`：建表 → 拆列回填 → 删旧表（脏行只留日志 + 告警，**不中断迁移**），`downgrade` 可反向拼回 payload。
  **验证（全部真跑）**：双 DSN `pytest` 各 **61 例 / 0 失败**（新增 `test_typed_storage.py` 6 例 + `test_migration.py` 回填/降级往返）；
  真实开发库 `minibook` 上 `0001_init ⇄ 0002_typed_entities` 往返，5 行旧数据全部拆列、**0 行跳过**；
  云端 SQL 聚合直接可用（`SELECT date, SUM(amount) FROM transactions GROUP BY date` → 88.88 元）；
  JVM `SyncApiE2ETest` 与模拟器 `SyncDeviceE2ETest` 改版后复跑通过。
  修复踩坑：客户端 `explicitNulls=false` ⇒ 可空字段**整个缺键**，拆列必须 `.get()`（用下标会把真实历史行判成脏数据跳过）。
- **F-15 真 MySQL 8 落地验证（2026-10-10，TASK-069 收口，用户提供内网 MySQL 连接信息）**：
  在内网 MySQL **8.0.46**（docker `mysql:8.0`，`192.168.190.130:49779`，库 `minibook` / 测试库 `minibook_test`）上真连库跑通：
  `alembic upgrade head` 建 5 张业务表 + `alembic_version`（全 `InnoDB` + **`utf8mb4_unicode_ci`** + `DATETIME(6)` + `JSON`）；
  `downgrade base ⇄ upgrade head` 双向真跑通过；**同一套 `pytest` 52 例 / 0 失败，MySQL 与 SQLite 双 DSN 双绿**
  （52 = 原 49 + **TASK-064 新增 3 例「日志不得含 payload」隐私断言**：push / pull / 整批校验失败三条路径，
  断言日志里查不到备注与金额哨兵、以及 `"payload"` / `"description"` 字段名）；
  uvicorn 真进程端到端冒烟（注册 201 → 登录 → push `applied=1` → pull 取回 → status 计数）通过，
  且中文经 `HTTP API → MySQL → 拉回` **逐字一致**（23 字符 / 44 UTF-8 字节）。`server/.env` 落本机 DSN（已被 `.gitignore` 忽略）。
  - 本轮实测暴露并修复 5 个真缺陷：① `alembic/env.py` 把 URL 回写进 configparser → 口令 URL 编码含 `%` 时
    `ValueError: invalid interpolation syntax`，`alembic upgrade` 直接失败（改为只读取、不回写）；
    ② MySQL 8 对 `CHARSET=utf8mb4` 默认给 `utf8mb4_0900_ai_ci`，与部署口径不符（显式 `COLLATE utf8mb4_unicode_ci`）；
    ③ `downgrade()` 先 `DROP INDEX` 撞外键支撑索引（`errno 1553`）且中途失败留下半删库（改为只 `DROP TABLE`，先子表后父表）；
    ④ Android `hasTokens` 只看密文非空 → Keystore key 失效时误报「已登录」（改为必须真的解得开）；
    ⑤ `://x` / 越界端口等畸形服务器地址漏过校验（补主机名+端口白名单）。
    另修 health 测试把 `db == "sqlite"` 写死（改为与真实引擎比对）。
- **F-15 Android 云端网络栈（TASK-065 完成，2026-10-10）**：新增唯一三方依赖 **OkHttp 4.12.0**，
  `data/remote/{SyncDtos,SyncErrors,SyncUrlValidator,SyncApi,SyncRemote,SyncAuthStore,SyncTokenAuthenticator}.kt`
  + `data/local/security/SecretCipher.kt`（Keystore 别名 `minibook_sync_token`，**不动**开库路径的 `KeyManager`）
  + `di/SyncModule.kt`；debug 清单单独开 `usesCleartextTraffic`（release 仍禁明文）。
  401 → 刷新一次 → 重试；网络故障**不清**账号态、令牌撤销才清。**JVM 单测 30 例 / 0 失败**
  （DTO 口径 7 + 错误码 5 + 地址校验 6 + AuthStore 6 + 令牌编排 6；含「落盘字节里没有明文令牌」断言）。

- **F-15 Android 同步 UI（TASK-067 完成，2026-10-10）**：设置 → 数据 → **云同步**二级页落地
  （`ui/settings/{SettingsCloudScreen,SettingsCloudViewModel,SyncPresenter}.kt`，路由 `settings-cloud`，
  数据页新增入口行）。账号区（服务器地址 / 用户名 / 口令，登录 / **注册后自动登录** / 退出时先尽力撤销刷新令牌）、
  三模式按钮（双向同步 / 仅上传 / 仅下载）+ **预览确认卡** + 结果卡（成功给四计数，失败明说「游标未推进」）、
  状态行（上次同步 / 待处理冲突数，**只有计数与时间，不含账目明细**）、冲突留档查看（类型 / 短 id / 处置口径 /
  **两份副本原文**，可手工恢复），并明示上云数据边界（DEC-034）与同步前自动备份。
  业务判定与全部文案抽到 `SyncPresenter` 纯函数层，Composable 不含规则。**`SyncPresenterTest` 21 例 / 0 失败**；
  **全量 JVM 回归 481 例 / 0 失败**。
- **F-15 服务端日志隐私断言（TASK-064 收口，2026-10-10）**：新增 `server/tests/test_log_privacy.py` 3 例——
  推送（成功路径）、拉取（响应里有、日志里没有）、整批校验失败（被拒条目原始值不得进日志）；
  断言口径：**记录值**（备注 / 金额哨兵）与**字段名**（`"payload"` / `"description"`）都不得出现在日志文本。
  双 DSN 各 **52 例 / 0 失败 / 0 跳过**（SQLite 与 MySQL 8，两次 `exit=0`）。

- **F-15 真实服务端端到端（无设备部分，2026-10-10 新增）**：`app/src/test/.../SyncApiE2ETest.kt` ——
  用**生产代码路径**（`SyncApi` 真 OkHttp + 真 DTO + 真错误码映射）打真在跑的 API（真 MySQL），
  断言：注册→登录→`me`→推 3 类实体→拉回（**中文 + emoji 逐字一致、UTF-8 44 字节相等**）→
  重复推送 **no-op**（`applied=0` 且 `server_seq` 不前进）→ 墓碑下行 → `status` 计数 → `refresh` 轮换 →
  **旧 refresh token 被拒** → 坏令牌归 `TOKEN_INVALID`。**1 例 / 0 失败 / 3.05s**；
  另在 MySQL 侧独立核验落库结果（3 条记录 / 1 条墓碑 / `updated_at_raw` 逐字一致 / 墓碑 `payload` 为 JSON null），
  核验后测试数据已清理。默认由 `app/build/e2e-base-url.txt` 开关控制，未配置时如实 skipped（不冒充通过）。

- **F-15 Android 同步核心（TASK-066 完成，2026-10-10）**：本地同步引擎落地——
  `domain/usecase/sync/{SyncModels,SyncPlan,SyncUseCase}.kt`（三模式 双向/仅上传/仅下载；三路比较 base↔local↔remote + LWW + 输家留档）、
  `data/local/sync/SyncEntityAdapter.kt`（6 类实体适配器 + 注册表）、Room **v3 → v4**
  （只加 `sync_state` / `sync_conflicts`，**既有 7 表 CREATE 与索引逐字未变**，`app/schemas/com.minibook.data.local.db.MiniBookDatabase/4.json` 已生成）、
  `di/DatabaseModule.kt` 增 3 个 DAO 提供者。「本地不丢」五道保险全部接线：同步前快照 `files/sync_backup/`（留最近 3 份）、
  删除只走墓碑、冲突留档、失败不推进游标、dry-run 预览 == 执行。
  **JVM 单测 20 例 / 0 失败 / 0 跳过**（`SyncPlanTest` 19 + `SyncPayloadContractTest` 1）；**全量回归 460 例 / 0 失败 / 0 跳过**。
  - 两处设计修订（`07_decisions.md` 记 **DEC-035 / DEC-036**）：墓碑改为「`sync_state` 有、本地已无」的**差分补发**
    （原方案要改 5+ 处删除路径，漏一处即静默不同步；差分零侵入，且覆盖「删账本」批量清表与「导入替换」）；
    上行判据改为「与基线不一致」⇒ 时钟回拨不再漏推；payload 定为 Room 行**原样 JSON** + 跨语言键集断言
    （`SyncPayloadContractTest` **未跳过** ⇒ 真的解析了 `server/app/entity_spec.py` 并逐字一致）。
  - 未闭合：`MigrationV3To4Test` 已写**未跑**（instrumented 需设备；真机 `A1B2C3D4` 有用户真实数据，禁装测试 APK）；
    同步 UI（TASK-067）与真机 E2E（TASK-068）待做。

- **F-15 服务端 API 实现（2026-10-10，本轮落地，用户请求「开发 api 服务，让数据上云」）**：新增 `server/`
  （FastAPI + SQLAlchemy 2 + Alembic + PyMySQL，纯 Python）——用户系统（注册 / 登录 / 刷新轮换 / 登出 / `me`，
  argon2id 口令 + JWT + 登录限流）、同步接口（`POST /sync/push`、`GET /sync/pull`、`GET /sync/status`；
  幂等 no-op / 墓碑 / LWW 冲突回传 / **整批回滚**）、6 类实体字段白名单校验、Alembic `0001_init`
  （SQLite 实跑通过；MySQL 侧离线 DDL 核验 `DATETIME(6)` / JSON / BIGINT / InnoDB / utf8mb4 / 复合主键）、
  `server/README.md`（起服务 / 测试 / 双 DSN 说明）。
  **测试：`pytest` 47 例 / 0 失败（SQLite）**，`alembic upgrade head` 通过。
  仍未闭合：MySQL 上跑同套测试（**TASK-069 等用户给 DSN**）、日志不含 payload 的自动化断言、Android 客户端（TASK-065~067）。
  实现差分（游标原子自增、`updated_at_raw`、`server_seq_global`、JWT aware-UTC 踩坑等）记在 `14_cloud_sync.md §12`。

- **F-15 云端同步设计（2026-10-10，用户请求「开发 api 服务，让数据上云」；本轮仅设计，未动代码）**：新增 `14_cloud_sync.md`
  （完整设计：口径问答、目标/非目标、总体架构、服务端选型与目录、数据模型、API 契约、认证与隔离、同步协议三模式、
  冲突策略、墓碑、Android 落点、Room v3→v4 迁移、隐私边界、11 条可测验收断言、测试计划、上云路径、风险）；
  并回写 `01_prd.md §F-15`（需求 + 验收 + 异常处理）、`02_architecture.md §19`（与 §6「云端预留」的兑现关系 + 口径修正说明）、
  `03_database.md §12`（本地只加两表 + 云端 schema 指针）、`07_decisions.md` **DEC-031~034**、`06_tasks.md` **TASK-061~068**。
  四项已确认口径：**先本机跑通 / 数据库 = MySQL（用户明确「不用 pg 用 mysql」；代码 DSN 驱动，SQLite 走开发与单测）/
  用户名+口令 JWT 不做邮件验证 / 全量核心业务表**；MySQL 特有口径（utf8mb4、索引长度、JSON 列、**DATETIME(6)**、
  用户名大小写归一化、`FOR UPDATE` 游标）已写入 `14_cloud_sync.md §3.4` 与 DEC-031。
  实现待开始（TASK-061 起）；**MySQL 环境未就绪是当前唯一阻塞项**（TASK-069，需用户选定提供 DSN / 本机安装 / 容器测试库）。

## v1.0.0 收尾

P2 技术债清账 + 文档收口（本地 debug 交付，不涉及真机 / release / 云端）。

### Added

- 导入账单平台提示卡可点击直接选文件（4ccaca1）。
- 设置页主题颜色调色盘（d0759b3）。
- 主题色覆盖容器色与底部菜单栏（0f748f7）。

### Changed

- **P2-5 枚举命名**：`GRANULARITY` 改名 `Granularity`（类名大驼峰），同步报表 ViewModel/Screen/UseCase 与测试引用（b9c0e6f）。
- **F-12 P2-4 接口分层**：`BillParserFactory` 接口由 `data/local/bill` 归位 `data/datasource`（与 `TransactionRunner` 对称），实现 `DefaultBillParserFactory` 留 `data/local/bill`（7c42f4c）。
- **P2-2 超限文件拆分**：`SearchScreen.kt` / `BudgetsScreen.kt` / `SearchViewModel.kt` 拆至 ≤300 行——`OptionalDateField` 下沉 `ui/components`、搜索条件区归 `ui/search/components`、预算进度卡收敛为单一可复用组件、搜索过滤纯函数抽独立文件（f4fd92d）。
- **待审项 a 导航统一**：账本管理入口 `openLedgerManage()` 加入 `HomeNavigation`（CompositionLocal），Home / Settings 经 `LocalHomeNavigation` 读取，删除 `MainScreen.onNavigateToLedgerManage` 参数穿透（63eaa26）。
- **文档回写**：`docs/04_api.md §12.3` BudgetProgress 契约对齐实现（`month` 为 `String`(yyyy-MM)、总预算扁平 `totalBudgetFen`/`totalUsedFen` 0 哨兵、`category` 非空 + 孤儿预算跳过）。

## [1.1.0] - 2026-10-10

### Changed

- **短信导入两种扫描方式：增量 / 全量（2026-10-10 下午，用户第三次反馈；未提交；DEC-030 / TASK-060）**：用户要求「我本次导入已经看过这条短信，并且不用处理了，下次再重新导入时候就不要再标记出来了，做两种扫描方式，一种全量扫描，一种增量扫描，比如我 12:00 扫描过了，增量只扫描 12:00 以后的，只有全量的才全部扫描出来」。做法：① 新增 `SmsScanMode`（`INCREMENTAL` / `FULL`）与 `SmsBillReader.readAfter()`（**严格 `date > 水位`**，**刻意不复用** L3 补扫的 `readSince()`——后者为容错故意回退 `OVERLAP_MS`（3 天），拿来做手动增量必然把 3 天前看过的又翻出来）；② `SmsBillImportUseCase.scan(ledgerId, mode)`：增量**读完即推进水位**（读到即算扫描过 ⇒ 下次不再出现），全量**不动水位**（历史永远可由全量重见，含「此前已看过」折叠区），增量首次无水位只立基线并返回空扫描（与 L3 `catchUp` 首次口径一致）；③ 入口卡改两个按钮「增量扫描」（主）/「全量扫描」（次）+ 一行小字说明差别，预览页 stepper 下方新增扫描方式说明行（`全量扫描：列出全部历史短信` / `增量扫描：本次覆盖 MM-dd HH:mm 之后的新短信，起点已更新为 MM-dd HH:mm` / 首次增量 `起点已设为本次扫描时间 …`，**只有时间戳、无任何短信内容**，DEC-026 边界不变）；④ 「重新扫描」沿用当前模式（从 `preview.scanMode` 读回），切模式 = 顶栏 ← 回入口页；`SmsScanPreview` 新增 `scanMode` / `sinceMs` / `scannedAtMs`（均带默认值，既有构造点语义不变，`assertClosed()` 与「未识别 N」全量口径一字未动）。**同日节点纠正（13:4x）**：原把节点写成「这次读到的最新一条短信时间」，用户指出「时间节点……不应该是最后一条短信的时间，应该是当前扫描的时间」——该写法有两个实证毛病（空库时永远立不起节点；节点会显示成一条旧短信的日期），改为**节点 = 本次扫描的墙钟时间（在读之前取）**，`catchUp`（L3）也一并统一为「本次补扫的墙钟时间」（两条增量路径写同一个水位，口径必须一致；补扫是 3 天重叠回扫，故不会漏）。单测 +2 例（F-14 7 类 / **77** 例，全量 52 类 / **410** 例）。真机 `A1B2C3D4`：增量 → `扫描 0 条`（那条已看过的快递短信**完全不出现**）；全量 → `扫描 1 条 = 可导入 0 + 未识别 1 + 非账单 0 + 已存在/重复 0` + `▸ 此前已看过 1 条`；节点复验：进页 L3 写 `13:47` → 增量 `本次覆盖 10-10 13:47 …，起点已更新为 10-10 13:48`，再跑全量节点**不动**，随后增量 `本次覆盖 10-10 13:48 …，起点已更新为 10-10 13:49`。模拟器 `emulator-5554`：注入短信 B 后增量 `扫描 1 条` ⇒ **只读到新到达的 B、A 不重读**。
- **验收后 UI 信息架构改版（2026-10-10 下午，用户三诉求；未提交；DEC-029 / TASK-059）**：
  1. **导入页改「三个入口」**：删除常驻的「微信账单 / 支付宝账单」导出指引卡与顶部引导段（含可点击链接那句），改为三个入口卡（账单文件导入 / 邮箱账单自动导入 / 短信账单导入）——每卡 = 标题 + 一行简短说明 + 全宽按钮；导出路径折叠进「▸ 如何导出账单？」（文案一字未改）。返回语义改为逐层退：功能结果态 ← / 系统返回键 → 回三入口页（新增 `BackHandler`），入口页才退出本页；进行中退出 = 取消本次读取（VM 随返回栈销毁）。
  2. **设置页改「分组汇总 + 二级页」**：首页一张卡 5 行（账本 / 外观 / 安全 / 数据 / 短信账单），每行 = 分组名 + 一行当前状态汇总 + `›`；新增四个根图二级页 `settings-appearance` / `settings-security` / `settings-data` / `settings-sms`（顶栏「← + 标题」），原分区内容逐条搬运（交互语义不变）；账本仍走 `ledger-manage`；版本号降为页脚文本（原「关于」分区只有一行）。跨层导航新增 `LocalSettingsNavigation`（同 `LocalHomeNavigation` 口径）；`MainScreen` 去掉 `onNavigateToImport` / `onNavigateToMailAccount` 参数穿透（账单导入与邮箱凭据入口移入「数据」页，由该页在 NavHost 接线）；`TrailingValue` / `TrailingChevron` / `SettingsSubPageTopBar` 提取为公共组件。

### Fixed

- **设置 · 短信账单：白名单空态不再显示无意义的「清空白名单」按钮（2026-10-10 下午，用户提问「白名单在哪看，就只有清空」；未提交；DEC-030）**：白名单为空时该页只渲染一句「白名单为空…」+ **恒显的清空按钮**，用户看到的是一个清不掉任何东西的按钮，既看不出白名单在哪、也不知道怎么才会有内容（原「对某发送方手动确认入账 3 次后自动晋级」也没说清会自动出现在哪）。改为：空态**不显示清空按钮**，说明改写为「白名单为空：对某个发送方手动确认入账 3 次（期间无纠错）后自动加入；加入后会在这里逐条列出，可单独关闭它的自动记账。」；非空态先给一行「白名单（N 个发送方，打开 = 允许该发送方自动记账）：」，再逐条列出可单独开关的发送方，最后才是清空（danger 强确认语义不变）。真机空态截图 `.claude-runs/f14d-real-settings-wl.png`。
- **「扫描过的做标记」：未识别条目新增「已看过」折叠（2026-10-10 下午，用户诉求①；未提交；DEC-029）**：撤销角标后没有主动提示，噪音全落在清单里——真机上每次重扫都重复冒出同一条未识别快递短信。做法：`SmsSettingsStore` 新增 `unrecognized_ack_ms`（**只存一个时间戳**，DEC-026 隐私边界不变）→ 扫描时 `receivedAtMs <= 水位` 的条目 `SmsPendingItem.seen = true` → 清单上屏后 `acknowledgeUnrecognized()` 推进水位 → `SmsImportPresenter.lines()` 只出未看过条目，已看过进「▸ 此前已看过 N 条（点击展开）」折叠区。**口径不动**：`未识别 N` 仍按全量计（`SmsScanPreview.assertClosed()` 不变）、不删除不过滤条目、待确认清单页仍可逐条看 + 手工记一笔；单测 +2 例（F-14 7 类 / **75** 例，全量 52 类 / **408** 例）。（**同日二次反馈**：「待确认的 1 条还是永远消除不掉」→ `SmsImportPresenter.pendingCount` 改为 = **未看过的**未识别 + 疑似重复；全部已看过时数字归零，底部入口退化为不带数字的「查看未识别 / 重复记录」（记录仍可进入查看 + 手工记一笔）；全量 `未识别 N` 与闭合断言一字未动；在原 `待确认条数` 用例内扩展断言，**用例总数不变**。）
- **F-14 真机/模拟器验收发现并修复 5 个「JVM 全绿但真机静默失效」缺陷（2026-10-10，未提交）**：
  1. `SmsBillReader.selection()` 生成 `type >= 1..6.first` 非法 SQL → 真机首次扫描直接「读取失败」；改为拼 `SmsMessageType` 常量。
  2. `ImportScreen` 权限值取组合期快照 → 系统设置授权后返回仍读旧值、永久卡在引导页；改为**点击时 + `onResume` 当场复查**。
  3. `SmsReceivedReceiver` 只判 `!appLockEnabled`，漏判用户显式开启的「自动记账」总闸（未开启也可能写库）→ 统一口径 `SmsSettings.autoImportUsable`。
  4. 银行模板兜底渠道为 `OTHER`，而 `allowsAutoImport` 只认 `BANK_DIRECT` → **L2 自动记账在最常见场景永不触发**；新增 `SmsTemplate.defaultChannel`，`bank.generic` 兜底 `BANK_DIRECT`，并补回归单测。
  5. `SMS_RECEIVED` 唤起的新进程未走 UI 解锁流程、`DatabaseGate` 未 open，任何 DB 访问抛 `IllegalStateException` 且被接收器 `catch` 吞掉 → 后台链路整体静默失效；改为「自动记账可用时按 DEC-002 静默开库」+「DB 未就绪降级待确认、绝不抛」，并补隐私安全的结果/原因日志。
- **登记 ROM 级能力边界**：MIUI/HyperOS 对非默认短信应用暴露 `sms_restricted` 视图（过滤 `sp_type≠0` 的服务/通知类会话），实测 App 可见 1 条 / shell 可见 9 条 → oracle 对齐不再作为漏读判据（DEC-027）。
- **F-14 验收后口径定案：三层兜底（F1 修复保留；F2 角标方案撤销；2026-10-10，未提交；DEC-028 修订）**：
  1. **F1 补扫触发点名实不符**：设置项写「打开 App 时自动补扫」，实际 `catchUp` 全仓唯一调用点是打开**导入页**（`ImportViewModel.init`）→ 从不进导入页的用户拿不到兜底；改为首页 `HomeViewModel.init` 触发（导入页那次保留，幂等），并补可观测日志（只记计数与策略原因，DEC-026）。
  2. **F2/F3 角标方案撤销（用户决策）**：曾实现「水位不越过待确认 + `SmsPendingTracker` + 首页角标」（F2a/b/c）并验证通过，但真机实测角标**常驻去不掉**——未识别条目的 `msg:<短信_id>` 键没有任何销账出口（F3），每次开 App 补扫都重新计数。用户拍板「**角标不要了，不需要出现已读未读的，能接收到就记账，有 App 打开扫描兜底，还有最后手动导入兜底**」→ 删除 `SmsPendingTracker`（含 7 例单测）与全部销账/记账调用/UI 接线，水位回到「读到即推进」。
  3. **最终模型 = 三层兜底**：① 到达即记账（L1/L2，白名单四条件 + 应用锁门控）→ ② 打开 App 补扫（L3 增量，兜广播丢失）→ ③ 手动导入（L0 全量干跑 + 确认写入，最终兜底，与水位无关）。不再有任何「待确认/已读」状态；补扫的 `pending` 计数仅为日志口径。
- **补做 4 类边界短信复测（2026-10-10）**：通知类（106 端口）与「银行端口但正文无金额」均判**非账单**（零提醒、不猜金额）；未登记模板判**未识别**并在清单页留痕（发送方/时间/预览/原因 + 重新扫描/手工记一笔）；**跨源判重**：手工 ￥77.00 与同额同日、尚未入库的短信 → 复扫由「可导入」转「已存在/重复」，不会重复记账。

### Added

- **F-14 短信账单导入（设计定稿 + 领域/数据层实现，2026-10-09，未提交）**：第三条取件通道（F-12 文件 / F-13 邮件 / F-14 短信）。设计文档 `13_sms_bill_import.md`；PRD §F-14；DEC-025（技术路线）/ DEC-026（权限与隐私边界）；任务 TASK-051~057。
  - 核心能力：`SmsBillReader`（分页/水位/双卡/内容哈希，唯一短信 I/O 边界）、`SmsBillParser`（模板锚点抽取、**零正则**、三态输出、未识别留痕）、`SmsBillMatcher`（跨源按天匹配、双向共用、唯一候选自动判重）、`BalanceChainAuditor`（余额链缺口）、`SmsWhitelistPolicy/Store`（四条件自动记账、3 次确认晋级、纠错降级）、`SmsBillImportUseCase`（扫描→预览→确认入库→L1/L2 实时，计数闭合断言）。
  - **复用零分叉**：入库统一走 `ImportBillUseCase.importParsedRows`（`importPrepared` 同核）；`TransactionSource.SMS_IMPORT`；备份 JSON 无字段变更；**无 DB 迁移**。
  - **如实验证状态**：JVM 单测（解析/匹配/审计/白名单/计数闭合/编解码）**52 类 / 408 例 0 失败**；**真机 + 模拟器端到端验收已完成（2026-10-10，见 `13_sms_bill_import.md §14` 与 DEC-027）**，验收后 UI 改版（TASK-059 / DEC-029）亦已双端实测；仍**无真实银行账单样本**，应用锁开启态、通知逐条模式、L4 分享导入未覆盖（L3 开 App 补扫场景已于 2026-10-10 真机验证）。
- **首页分类联表**：首页最近交易列表显示真实分类名/图标/颜色（经 ObserveCategoriesUseCase 的 id→Category 映射，替代原 null 占位 ❔）。
- **可测性改造**：DatabaseGate 提炼 DatabaseProvider 接口，RoomTransactionRunner/RoomDataSourceImpl 改为依赖接口（加密语义不变），使事务原子性可在 androidTest 用内存库测试。
- **搜索（F-06）**：交易（备注模糊 / 金额区间 / 分类 / 日期区间 / 类型）与债务（姓名 / 方向）组合筛选，搜索词防抖 + min>max 校验，结果分组时间倒序 + 只读明细。
- **报表统计（F-07）**：月/年视图 + 收支趋势 / 分类占比 / 结余走势三图，Compose 原生 Canvas 自绘（折线/柱状/环形/图例/空态，DEC-011 零第三方图表库）。
- **预算管理（F-08）**：月度总预算 + 分类预算，已用/剩余/超支进度，超支页内标红 + 首页入口红点（DEC-012 仅 App 内告警、无系统通知栏）。
- **测试**：JVM 单测 56 → 95 例（预算超支口径 / 搜索组合 / 报表累计跑表 / min>max 校验 / 分类删除级联）。
- **账单导入（F-12，需求）**：微信/支付宝账单导入 PRD 落盘——支付宝 CSV(GBK) / 微信 XLSX 动态表头解析、`transactions` 4 新列（`externalId`/`counterparty`/`paymentMethod`/`source`）、幂等去重与自动分类映射（01_prd.md §F-12 + DEC-015，状态评审中待架构评审）。
- **账单导入（F-12，架构/DB/API 设计）**：`transactions` 加 4 列 + `UNIQUE(ledgerId, externalId)`、DB version 2（`MIGRATION_1_2`，DEC-018，SQLite 唯一索引 NULL 互异⇒手动记录不参与去重）；微信 XLSX 手动 zip+XML 解析（`ZipFile`+`XmlPullParser`，DEC-016 零第三方库）+ 支付宝 GBK CSV 动态表头解析；`BillParser` 抽象 + `ImportBillUseCase`（逐行过滤 + 单事务整批写入，DEC-015/019）；自动分类映射（`BillCategoryMapping`）+ 幂等计数；`import` 路由 + `ImportViewModel` 状态机；测试策略 + TASK-040~044 拆分落盘（02/03/04/06/07 文档）。
- **账单导入（F-12，UI 设计）**：P-15 导入账单页 + C-33~C-37 组件（DEC-020）——首页「导入账单」tile 主入口 + 设置「数据」区可选入口；`import` 根图全屏页四态（Idle 提示 / Busy 忙碌 / Result 结果 / Error 错误）；结果页态（非弹层）+ 解析失败折叠明细；`TransactionListItem`/`TransactionDetailSheet`/`TransactionSheet` 增 `counterparty`/`paymentMethod`/`source` 展示（金额语义色不动）；source 筛选可选增强（C-37）；精确中文文案表（05_ui.md §9 + 07_decisions.md DEC-020）。
- **账单导入（F-12，测试）**：Tester 补测——JVM 单测 111 → 132 例（新增/扩展 `BillParserFactoryTest` 7 / `BillTextTest` 7 / `BillHeaderMatcherTest` 4 / `EnumRoundTripTest` +2 / `MapperRoundTripTest` +1），覆盖 DEC-017 格式探测、金额/日期/备注转义边界、动态表头容错与列序重排、枚举与 mapper 新字段往返；新增 instrumented 迁移测试 `MigrationV1To2Test`（补齐 `androidTestImplementation(libs.androidx.room.testing)` + `androidTest` assets `srcDir("schemas")`），`:app:compileDebugAndroidTestKotlin` 绿；发现 androidTest 反引号含空格方法名无法 DEX（P2 测试基建缺陷）与 `BillColumn` 对方账号误捕（P3）等观察项，详见 `09_test_report.md §F-12`（真机 3 项因设备未连接转遗留手动清单）。
- **账单导入（F-12，评审）**：Reviewer 评审 F-12 全量交付（`be1ccdd..HEAD`）——PRD 十项验收全过、金额整数分、DB 1→2 迁移三方逐字一致、132 JVM 例 + instrumented 迁移测均已闭合；**结论 FAIL-with-issues**：1 项 P1（`TransactionDto`/`BackupJsonMapper` 未携带 F-12 四个新字段 → 备份/迁移还原后丢 source/对方/支付方式/externalId、重导同账单重复入账）+ 6 项 P2（工厂嗅探优先级 / §9.3.3 编辑态展示缺失 / 事务回滚单测口径失真 / 工厂接口层级 / CSV KDoc 多行 / XLSX 空行计数），无 P0，详见 `08_review.md`。

- **邮件账单自动导入（F-13，设计与决策）**：新增「自动取邮箱账单并导入」功能设计——QQ 邮箱 IMAP（授权码 + `ID` 命令）拉取 → **本地过滤**账单邮件（实测服务端 `SEARCH` 在本场景返回 0，禁用）→ 支付宝取邮件附件 / 微信取正文下载链接（HTTPS，链接 7 天有效）→ **自动穷举 6 位数字口令**（实测口令恒为 6 位纯数字且每期不同）→ ZipCrypto 解密 + 原生 `Inflater` 解压 → 交给 F-12 既有导入链路。新增 `12_mail_bill_import.md`（含全部实测证据与排除记录）+ `DEC-022`（技术路线）+ `DEC-023`（网络/凭据隐私边界修订）+ `01_prd.md §F-13` + `02_architecture.md §17` + TASK-045~050。
  - 已排除路线（留档避免重复尝试）：POP3、邮箱厂商 SDK/OAuth2、**已知明文攻击（bkcrack）**——实测两条目均为 Deflate 压缩，该攻击要求提供「压缩后字节流」而动态 Huffman 首块不可预测；以及「记住口令」方案（口令每期变化，已证伪）。
  - 依赖：新增 JavaMail（`android-mail`/`android-activation` 1.6.7，约 1 MB）+ `INTERNET` 权限；**不引入 zip4j**（实测为 ZipCrypto 非 AES，原生方案足够）。

- **邮件账单自动导入（F-13，实现）**：按 DEC-022 路线全量落地——QQ IMAP 授权码连接（`ID` 命令双保险、`READ_ONLY` 只读、30 天窗口拉头 + `MailBillMatcher` 本地过滤、**零服务端 SEARCH**）→ 支付宝取附件（`MailFilenameDecoder`：RFC2047 GB18030 Q/B + RFC2231 `name*N` 续行）/ 微信取正文链接（`WechatBillLinkExtractor` 精确 host+路径）→ `MailBillDownloader` HTTPS 下载（每跳强制 https、≤5 重定向、30s 超时、异常文案不含链接 token）→ `ZipArchiveInspector` 加密类型探测（method 99 / extra `0x9901` → 拒绝穷举）→ `ZipPasswordCracker` 6 位数字穷举（两段式：12 字节头快筛宽松双约定 + 解压后内容 CRC32 权威判定；前缀密钥状态预计算 + `Dispatchers.Default` 分片并发 + 4096 批次取消检查）→ `EncryptedZipExtractor` 解压（GBK/CP437 条目名）→ 交 F-12 链路（`ImportBillUseCase.importLocalFile` 与 `invoke` 共用 `importPrepared` 核心，口径零分叉）。凭据：`MailCredentialCipher`（Keystore AES-256/GCM 独立别名）+ `MailAccountStore`（落 `noBackupFilesDir`，解密失败防御性置空）+ 新增 P-16 邮箱账单设置页（`mail_account` 路由）；导入页新增 `MailBusy`/`MailResult`/`MailNotConfigured`/`MailError` 四态 + `MailImportPresenter`（9 分支失败文案 `when` 穷尽）。
  - 规模：新增主源码 17 文件 / 2344 行、测试 7 文件 / 951 行 / 70 例（`MailBillMatcherTest` 9 / `MailFilenameDecoderTest` 16 / `WechatBillLinkExtractorTest` 9 / `ContentDispositionParserTest` 6 / `ZipCryptoTest` 12 / `ZipPasswordCrackerTest` 6 / `MailImportPresenterTest` 12）；`assembleDebug` 绿（APK ≈35.8 MB）；全量单测 40 类 / 260 例 0 失败。
  - 真实账单验收（本地实跑，测试按约定删除）：三份真实账单 zip 判型均为 ZipCrypto、**全部命中 6 位数字口令**（单份 90~760 ms，多核亚秒级），解出字节与独立 Python oracle 完全一致。
  - 如实限制（详见 `04_api.md §14.9`）：手动输入口令 UI 未实现（穷举失败降级为结果页 `PasswordNotFound` 提示）；`ImapMailClient`/`MailBillDownloader`/`MailAccountStore` 无 JVM 单测（JavaMail 无假服务器、Context/Keystore 无替身，未引入新测试依赖）；`totalMailCount == billMailCount`（窗口内邮件总数不可得不伪造）；真机端到端、release 构建待设备/后续。
  - 文档回写：`04_api.md §14`、`05_ui.md §10`、`06_tasks.md` TASK-045~050、`07_decisions.md` DEC-022/023 实施状态、`12_mail_bill_import.md §11`。

- **邮件账单自动导入（F-13，分步骤进度可视化 + 多邮箱）**：一键收取升级为「看得见每一步」——5 步竖向 stepper（连接邮箱 / 拉取邮件 / 筛选账单邮件 / 处理账单邮件 / 完成汇总，每步附中文摘要：`已连接（IMAP/SSL）` / `窗口内共 N 封` / `命中 N 封（微信 x / 支付宝 y）` / `成功 N 封 / 失败 M 封` / `新导入 … / 已存在 …`）+ 第 4 步展开区（`第 i/N 封 · 文件名` + 子步骤如 `下载账单` / `解压账单` + 口令穷举百分比）+ 逐封结果行卡（`✅/❌ 平台 · 文件名 — 结果`）。设置页「邮箱账单」更名**「邮箱凭据」**并支持**多账号**（`MailAccountStore.accounts` 列表 + `upsert`/`delete`：0 个引导配置、1 个直接收取、≥2 个弹「选择邮箱」选择框 —— 选一个 / 全部收取 / 取消）。新增 `MailImportProgress` / `MailImportStep` / `MailImportStepState` / `MailImportSummary` 等模型（`domain/model/MailImportModels.kt`）。
- **邮件账单自动导入（F-13，进度页与结果页合并为单页）**：按用户要求**成功或失败都不再切换版式**——`MailBusy` / `MailResult` / `MailError` 三态统一渲染同一个 `MailImportContent`（单页单滚动容器：`verticalScroll` 内容区 + 钉底「重新选择 / 返回」按钮），成功后**原地追加**结果摘要与失败明细，失败时**原地追加**错误卡；删除旧的独立结果页组件（`ImportMailResultContent`），`ImportScreen` 的 F-12 SAF 文件导入链路不动。
- **测试**：F-13 JVM 单测大幅扩展——`MailHtmlTextTest` 19 例（含真机邮件 HTML 形态的实体转义 / 自闭合标签 / 引号内 `>` 边界）+ `WechatBillLinkExtractorTest` 21 例（含尾随标点、host 伪装、端口与路径边界）+ `MailImportStepTrackerTest` 7 例（进度快照累积回归，含真机 `○○○○✓` 场景复刻）；全量 **44 类 / 327 例 0 失败**。
- **真机验证（A1B2C3D4，2026-09-23）**：一键收取端到端通过——窗口内 13 封邮件命中 5 封账单邮件（微信 2 / 支付宝 3）**全部成功**（`成功 5 封，失败 0 封`；两封微信账单分别映射 211 / 231 条且幂等去重生效，二次收取 `新导入 0 条，已存在 896 条`）；子步骤文案与进度头部 `邮箱 k/N` 按预期渲染；多邮箱「选一个 / 全部收取 / 取消」实测可用；授权码缺失的账号在 stepper 上以 `✗ 连接邮箱 · 授权码不可用（解密失败），已跳过该邮箱` 显式呈现。
- **v1.0.0 首个签名 Release + README 按实际重写**：为 release 增加签名配置（`signingConfigs.release` 由仓库根 `keystore.properties` 驱动，四项齐备才创建；缺失时退回未签名产物、不阻断构建），生成 `keystore/minibook-release.jks`（**不入版本库**；`.gitignore` 补 `*.jks` 与 `keystore.properties`）→ 产出**已签名** `app-release.apk`（30.98 MB，`apksigner verify` 通过，证书 CN=Minibook / SHA-256 `e137b7e5…`）；发布 **GitHub Release `v1.0.0`**（tag → `b8375d0`），资产 `minibook-v1.0.0-release.apk`（SHA-256 `5EAF6B51…`，GitHub 侧 digest 与本地一致）。README 从「AI 协作模板」说明改写为 MiniBook 真实说明（功能 F-01~F-13、技术栈、构建与签名、测试与真机状态、权限与隐私、目录与文档地图、已知限制），原 README 原样归档至 `docs/archive/README-legacy-ai-work-model-2026-07-24.md`。**如实修正**：release 现为 `isMinifyEnabled = false` 的**非混淆非裁剪**构建（`proguard-rules.pro` keep 规则为备用配置），此前 §14.9 中「含 R8 混淆/裁剪」表述已改正；release 包**运行期未装机验证**（debug↔release 签名不同，换装需先导出备份，见 §14.9）。

### Changed

- **F-13 微信取链关键路径「零正则」重写（Android ICU 教训）**：`MailHtmlText.toPlainText` 改为手写引号感知 HTML 扫描器（`tagKindAt` / `findTagEnd` / `findUrlAttribute`，`>` 在引号内不算标签结束），`WechatBillLinkExtractor.extract` 改为手写 URL 扫描 + 精确匹配（host 必须全等 `tenpay.wechatpay.cn`、可选端口后必须紧跟 `/`，防仿冒域名），两文件**彻底不含 `Regex(` / `toRegex`**；`IcuSafeRegexTest` 绊线测试持续守护（扫描 F-13 源码中的危险正则字面量）。
- **F-13 邮件账单导入「每种账单只取最近一封」**：`MailBillImportUseCase` 在 FILTER 后经 `MailBillSelector`（`domain/usecase/mail/MailBillSelector.kt`，纯 Kotlin、JVM 可单测）按平台（支付宝/微信）各保留 `sentDateMillis` 最新一封，同平台旧邮件**不处理**——`itemLines` 留 `⏭ {平台} · {主题} — 已有更新的同类账单，跳过`，FILTER 摘要追加「，每种账单只取最近一封 → 待处理 K 封」；`billMailCount` 仍按命中数计。新增 `MailBillSelectorTest` **6 例**（空输入 / 每平台各一封全保留 / 同平台多封取最新 / 真机窗口形态 2 微信 + 3 支付宝 → 只留 2 封 / 同毫秒按主题字典序兜底且与输入顺序无关 / 单平台多封仅留一封并校验 `hitCount = kept + skipped`；`kept`/`skipped` 顺序保持由前两例断言）；全量 **45 类 / 333 例 0 失败**。**真机验证（A1B2C3D4，2026-10-09）**：窗口内 3 封命中 3 封（微信 1 / 支付宝 2）→ 摘要实测「每种账单只取最近一封 → 待处理 2 封」，旧支付宝账单以 `⏭ …已有更新的同类账单，跳过` 显式跳过，`成功 2 封 / 失败 0 封`、幂等 `新导入 0 条 / 已存在 240 条`；「选中的确为最新」由「保留的 ✅ 行为 21 条（≠ 旧账单 111 条）」判定。2026-09-23 那次的「5 封 → 2 封」已无法复现（旧账单不在收件箱了），差异与依据见 `04_api.md §14.9`。

### Deprecated

### Removed

### Fixed

- **事务原子性测试**：补全 TransactionRunnerAtomicityTest 三个空骨架用例为真实断言（deleteById 三表分发 / runInTransaction 异常回滚 / deleteByLedger 四表批删），内存库 + DatabaseProvider 替身，无新增依赖。
- **P1-1 孤儿预算静默丢失**：删除分类时于同一事务内级联清理该分类的预算行（`BudgetDao.deleteByCategoryId` + UseCase 事务闭合），防分类删除后其预算在 budgets 表成孤儿、从预算进度静默消失。
- **F-12 评审收口（1×P1 + 5×P2）**：备份 JSON 的 `TransactionDto` 补交易 4 新字段并双向映射（P1-1，`source` 旧 JSON 缺省兜底 `manual`，防备份/迁移还原后丢 `externalId` 致重导重复入账）；工厂按 zip 魔数前置嗅探（防 `.csv` 名 zip 内容失配）；编辑弹层加来源/对方/支付方式只读信息块；补 `insertBatch` 抛异常回滚单测；修正支付宝 CSV KDoc「换行」声明；微信 XLSX 空白行跳过（对齐 CSV 口径）——详见 08_review.md 与 cb679a7。
- **微信下载链接丢失（F-13 实现期发现并修复）**：`ImapMailClient.ExtractedContent.bodyText()` 原仅取 `text/plain` 部分、`stripHtml()` 原先移除标签导致 `href` 中的 URL 一并丢失 → 微信账单（正文链接仅在 HTML `<a href>`）取不到下载链接。修复：`bodyText()` 拼接 plain+html 两路、`stripHtml()` 先提取并保留 `href`/`src` URL 再去标签。
- **JavaMail 打包冲突（F-13 实现期发现并修复）**：`android-mail`/`android-activation` 1.6.7 的 `META-INF/NOTICE.md`（及其他 META-INF 元数据）与既有依赖打包冲突导致 `assembleDebug` 失败 → `packaging` 块 `excludes` 排除 5 个冲突元数据文件；另补 R8 `-dontwarn` 与 keep 规则（`javax.mail.**`/`com.sun.mail.**`/`javax.activation.**`）。
- **邮箱凭据保存即崩溃 + 设置页恒显示「未配置」（F-13，真机实测发现并修复）**：`MailAccountCodec` 的条目正则含 `\}`，Android ICU 在**类初始化**时抛 `PatternSyntaxException` → `ExceptionInInitializerError`（属 `Error`，`catch (Exception)` 兜不住）→ 点「保存」即闪退、设置页恒显示「未配置」、且迁移已把旧授权码文件改名而 json 从未写出（旧凭据险些丢失）。修复：codec 改为**零正则**手写字符扫描（含邮箱格式校验），`MailAccountStore.loadOrMigrate` 读路径改 `catch (Throwable)`、写入后**回读校验通过才删旧文件**、失败整体回滚并保留旧文件（迁移失败日志只记异常类名、不含凭据），`upsert` 回滚 catch 同步加固为 `Throwable`。
- **步进行长摘要把标签挤成竖排（F-13，真机发现并修复）**：`MailImportStepRow` 里摘要 `Text` **没有任何宽度约束**，而 Compose 的 `Row` 会**先量无权重子项** → FILTER 摘要加长后（「…，每种账单只取最近一封 → 待处理 K 封」）吃满整行，标签「筛选账单邮件」被挤到近乎 0 宽、**竖排成单字**（真机 2026-10-09 用户截图复现；同一缺陷对失败行长摘要「授权码不可用（解密失败），已跳过该邮箱」同样成立，属既有隐患被新文案放大）。修复：标签与摘要**各带权重**（`StepRowLabelWeight 1f : StepRowSummaryWeight 2.4f`）且摘要 `TextAlign.End`，超宽只在自己那一列折行；短摘要行的渲染与修复前逐像素一致。真机复验（A1B2C3D4，2026-10-09）：5 行标签统一 `x[133-426]`（293px）、摘要统一 `x[450-1152]`（702px），筛选行摘要折两行且标签正常，uiautomator dump 全页**无文字重叠、无越界、无单字竖排**；布局纪律已写入 `05_ui.md §10`。
- **微信账单「邮件里没有找到下载链接」（F-13）**：真机 2 封微信邮件全部失败 → 定位后用零正则重写取链路径（见 Changed）并在真机复测通过（`成功 5 封，失败 0 封`）。**如实记录**：真机诊断（旧正则匹配次数 / HTML 是否含链接 / 去标签后链接是否存活）证明**旧正则在真机 ICU 上匹配正常**，最初「ICU 语义差异导致匹配失败」的推断**不成立**；旧实现的确切根因已不可回溯（旧 APK 与旧文件均被覆盖、且未提交版本历史）。结论以真机端到端通过为准，并以零正则重写 + ICU 绊线测试作为永久防线。
- **收取成功后 stepper 显示 `○○○○✓`（F-13，进度快照不累计）**：每份快照把未列出的步骤一律补 `PENDING`，已完成的步骤被后续快照打回待执行。修复：新增 `MailImportStepTracker` 累积步骤状态（每账号一份实例），同一快照序列正确显示 `✓✓✓✓✓`。
- **被跳过的邮箱在 UI 上不可见（F-13，多账号真机验证暴露）**：跳过分支只往 `itemLines` 追加 ❌ 而不发进度快照 → UI 永远看不到，用户会误以为「全部收取」覆盖了所有邮箱（静默漏数据提示缺失）。修复：跳过账号也发快照（stepper「连接邮箱」标 `FAILED` + 原因，其余 `PENDING`，携带累积逐封明细）。

### Security

- **F-13 隐私/权限边界（DEC-023）**：本项目**首次引入网络与外部凭据**，对 PRD §7「所有功能离线可用、无网络依赖」做**显式例外化**（仅 F-13 邮箱账单拉取）。边界写死为：邮箱授权码与 SQLCipher master key **同级保护**（Keystore AES-256/GCM，不落明文、不进日志）；IMAP **只读**（不删除/不标已读/不移动/不改 flag）；出网点仅 `imap.qq.com:993` 与 `tenpay.wechatpay.cn:443`（均 TLS）；**不上传任何用户数据**、不接统计/上报 SDK、不做后台联网；临时文件只落 `cacheDir` 且 `finally` 删除。

## [1.0.0] - 2026-08-20

### Added

- **收支记录（F-01）**：收入/支出记录，金额按「分」存 Long（杜绝浮点误差），支持日期/分类/备注。
- **债务管理（F-02）**：借/还平级模型（lend/repay × owe_me/i_owe 四态），明细 + 按对象双视图；净额口径 CASE WHEN SQL（lend×owe_me=+、lend×i_owe=−、repay×owe_me=−、repay×i_owe=+）。
- **分类管理（F-03）**：18 条内置分类（12 支出 + 6 收入，不可删/不可改 type/isBuiltIn，DEC-007）+ 自定义分类；删除被引用分类走迁移。
- **数据导入导出（F-04）**：明文 JSON 全量备份（version 1 JSON / BackupV1，DEC-005）；legacy 旧版 JSON 导入默认账本（DEC-004）；备份排除 DB 与密钥文件（DEC-006），事务原子。
- **应用锁（F-05）**：生物识别解锁 + 冷启动锁门（key(isLocked)）+ 离开超时重锁；无锁屏不降级明文（DEC-003）。
- **暗色模式（F-09）**：跟随系统 / 手动亮暗，Material 3 语义色 + 动态配色（Android 12+）。
- **数据加密（F-10）**：SQLCipher 整库加密落盘 + Android Keystore（AES-256/GCM）密钥保护。
- **多账本（F-11）**：数据按账本隔离，创建/切换/删除（默认账本禁删），账本切换自动重订阅。
- **月度结余（月度汇总）**：交易按月汇总统计，展示月度结余与收支视图。
- **技术栈**：Kotlin 2.2.0 + KSP + Jetpack Compose + Material 3 全面重构；Room 2.8.4 + SQLCipher 4.6.1、Hilt 2.58、Biometric 1.1.0、DataStore。
- **UI 体系**：首页仪表板、收支/债务/分类/设置/账本管理页面、导航壳、通用组件库（C-01~C-26）与主题设计系统；Phase 2 功能（搜索/报表/预算）占位页。
- **测试**：JVM 单元测试 56 例（全绿）+ instrumented 测试骨架 3 用例（净额口径 / 月度汇总 / 迁移原子性）。

### Changed

- Hilt 版本收敛 2.60.1 → 2.58（DEC-001）。
- 仓库根对齐 minibook-3.0，文档编号规范化。

### Deprecated

### Removed

### Fixed

- 修复 DEC-002 双钥系统完整性缺陷（半实现补全）（b4aa338）。
- Stage 8 警告项修复：domain/data 层错误分类/校验/事务/圈账本（5315fc3）。
- Stage 8 警告项修复：UI 三页 Error 态/重试/snackbar 重放（2ea7424）。
- 修复 data_extraction_rules.xml 非法 domain 值（5107b3b）。
- 显式加载 SQLCipher native 库，修复「自动解锁失败」（b46e551）。
- 首启引导接线 + 账本图标选择器（68d9eca）。

### Security

- **双密钥系统（DEC-002）**：SQLCipher master key（32B）由 AndroidKeyStore 两个别名包裹——`minibook_master_key_auth`（锁屏认证）与 `minibook_master_key_plain`（无锁明文），两者包装同一主密钥；切换锁状态时用当前可用 key 解出 master key 再以目标 key 重包裹覆盖 `minibook_key.bin`。
- **加密落盘**：SQLCipher 整库加密，关闭应用锁时 DB 仍加密，不降级明文。
- **冷启动锁门（ARCH §12）**：NavHost 以 `key(isLocked)` 重建，锁定态仅注册 Lock 目标（不可达即安全）。
