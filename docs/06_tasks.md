# 任务清单 (06_tasks.md)

> 项目：MiniBook Android 重构版（本地优先记账应用）
> 维护者：全员可更新（项目状态区块由当前阶段负责角色更新）
> 最后更新：2026-10-21（**F-15 上云（TASK-088）**：数据库迁至腾讯云（`<远程公网IP>`）自建 `mysql:8`，后台服务按用户要求「**在服务器打镜像 + Docker 部署**」；迁移后真实账号逐账号行数哈希与源库一致、线上 `pytest` **73 例 / 0 失败**、线上 API 冒烟全绿（令牌轮换 / 幂等 no-op / 注销自清理）、模拟器经隧道连**线上**跑设备端 E2E —— 细节见 `14_cloud_sync.md §14`、DEC-051 与 `09_test_report.md` 顶部章节；**2026-10-22 追补**：TASK-089（`_touch_device` 设备归属缺陷已修 + 重打镜像部署 + 线上实测）、TASK-090（公网安全接入方案：只开 443 + Caddy，**待审批**））
> 上一轮更新：2026-10-21（**真机换版安装与启动验证**：真机由 TASK-084 的包 `2FD3869…`（2026-10-13 15:22:06 安装、**不含 F-16i 门控修复**）原地覆盖为 TASK-087 的构建 `A6DD265B…`（含 F-16i / F-16k / F-16l；JVM 571 例 / 0 失败 / 1 跳过）；装前 / 装后数据 tar 逐字节一致（11,510,272 字节，sha256 `F25D2C30…BD5E9`）、`firstInstallTime` 未变、手机内 `base.apk` 与本地产物 sha256 一致、启动无崩溃、只读 dump 确认 F-16k 小链接已上真机 —— 细节见 `09_test_report.md` 顶部章节）
> 更早更新：2026-10-10（F-14 短信账单导入：完成真机 + 模拟器验收，实测修复 5 个真机静默失效缺陷；验收后完成 UI 改版（TASK-059：未识别「已看过」标记 / 导入页三入口 / 设置分组汇总 + 四二级页，见 DEC-029）+ 两种扫描方式与白名单空态修复（TASK-060 / DEC-030）；JVM 全量单测 52 类 / 410 例全绿；TASK-051~060 全部收口；版本 1.1.0 已提交推送并发布（commit `3578aea` / tag `v1.1.0` / GitHub Release 附正式签名包）；**F-15 云端同步：设计完成（`14_cloud_sync.md`、DEC-031~034、TASK-061~068）+ 服务端 API 已实现并在真 MySQL 8 上验证（`server/`：pytest `52 例 / 0 失败` 于 MySQL 与 SQLite **双 DSN 双绿**；`alembic upgrade head` 真连库建表 + `downgrade base ⇄ upgrade head` 双向通过；中文经 API→MySQL→拉回逐字一致；TASK-061/069 收口）+ Android 云端网络栈 TASK-065 已完成（JVM 单测 `30 例 / 0 失败`）；Android 同步核心 TASK-066 与同步 UI TASK-067 已完成（`SyncPlanTest` 19 + `SyncPayloadContractTest` 1 + `SyncPresenterTest` 21，全量 JVM `481 例 / 0 失败`）；TASK-068 部分完成（真实 HTTP 端到端通过，真机段待用户决定）**）
> 状态：F-12 微信/支付宝账单导入已全流程落地（PRD→架构→UI→实现→测试→评审→修复收口 9 commit）；P2 技术债清账 + 文档收口完成；**F-13 邮件账单自动导入（Phase 3）已实现并通过本地构建 + 全量单测（TASK-045~050 已回写）；真机验收待设备**；**F-14 短信账单导入已实现并完成真机 + 模拟器验收与验收后口径定案（`13_sms_bill_import.md` §14、DEC-027、DEC-028（含修订），TASK-051~060 全部收口；`:app:assembleDebug` 与 JVM 全量单测 52 类 / 410 例 0 失败；实测修复 6 个静默失效缺陷，F2 角标方案经用户决策撤销、最终为三层兜底（到达即记账 → 开 App 补扫 → 手动导入），并复测 4 类边界短信；验收后按用户三诉求完成 UI 改版（TASK-059 / DEC-029：未识别「已看过」折叠标记、导入页三入口、设置分组汇总 + 四二级页）与**两种扫描方式（TASK-060 / DEC-030：增量 / 全量 + 白名单空态）**，双端实测通过），仍未覆盖项见 TASK-057/058/060**；版本 1.1.0 已**提交并推送**（commit `3578aea` + tag `v1.1.0`，2026-10-10 经用户当次批准）并在 GitHub Release v1.1.0 上传正式签名包；**F-15 云端同步：服务端已实现并在内网真 MySQL 8 上验证（TASK-061/069 收口），Android 云端网络栈 TASK-065 完成，同步核心与 UI（TASK-066/067）进行中**
>
> 文档编号映射（已对齐 AI-work-model 编号规范，document_rules §文档编号体系）：
> - `docs/00_idea.md`（已归档，被 01_prd 取代）
> - `docs/01_prd.md`（已确认）
> - `docs/02_architecture.md`（已确认）
> - `docs/03_database.md`（已确认）
> - `docs/04_api.md`（已确认）
> - `docs/05_ui.md`（阶段 5 双向确认后已转已确认）
> - `docs/06_tasks.md`（本文件）
> - `docs/07_decisions.md`（已确认）
> - `docs/08_review.md` / `docs/09_test_report.md`（Stage 7/8 工作回填后已确认）
> - `docs/10_release_notes.md`（v1.0.0 发布说明）
> - `docs/CHANGELOG.md`（v1.0.0 变更已补）

---

## 项目状态区块

- 运行模式：**编排模式**
- 当前阶段：**Phase 2 全部闭环（F-06/07/08 + F-12 均实现+测试+评审收口）；P2 技术债已闭环；收尾阶段（本地 debug 交付）**
- 当前任务：P2 技术债清账 + 文档收口——GRANULARITY 改名（b9c0e6f）/ BillParserFactory 分层归位（7c42f4c）/ 三文件超 300 行拆分（f4fd92d）/ 账本管理导航统一 CompositionLocal（63eaa26）/ §12.3 契约回写 + CHANGELOG「v1.0.0 收尾」；JVM 单测 186 例全绿（`gradlew :app:testDebugUnitTest`，33 类 0 失败）+ instrumented 迁移测试编译绿（真机因设备未连接未执行）
- 下一步行动：Phase 2 全量待 push。**收尾阶段明确不做（本地 debug 交付，无真机发布）**——① release 签名 keystore ② instrumented 真机执行 ③ Phase 3 云端同步 均挂账不处理
- 阻塞项：无

---

## 协调日志

- 2026-08-18：编排模式激活，「运行模式」字段首次落盘（此前编排状态仅存在于会话上下文，未持久化）。
- 2026-08-18：协调者复盘越界行为（直接改 ARCHITECTURE.md 正文、欲直接写业务代码）并纠正；8 项架构决策已闭环，决策记录见 07_decisions.md。
- 2026-08-18（Architect 接管后收口）：ARCHITECTURE.md 复核并由 Architect 正式接管（§2 版本口径对齐 DEC-001 既定栈）；DATABASE.md 状态由「草稿」转「已确认」并修正 §8.3 待定措辞（D-6 最终口径）；新建 `docs/API.md`（= 04_api，已确认）展开全部 DataSource 契约、RemoteDataSource 出口与 JSON version 1 结构；决策记录落盘 `docs/07_decisions.md`（DEC-001~DEC-009）；阶段 3 收口完成。
- 2026-08-18（Architect 阶段 5）：完成 Designer 三项契约外假设 A-1/A-2/A-3 双向确认（全部确认、无契约推翻、未新增决策）；A-2 的 `DebtFilter{ kind?, direction? }` 契约补充直接落 API.md §4；UI.md 状态由「草稿」转「已确认」（§6.3 标注确认结论）；任务清单填充 **TASK-001~TASK-030**（构建骨架 2 / 数据层 4 / 领域契约层 4 / 业务层 8 / UI 层 12），版本锁定写入 TASK-001（与 DEC-001 既定栈完全一致）。
- 2026-08-20（阶段 6~9 收尾）：阶段 6 编码（TASK-001~030 + UI 接线 + 连接债务收尾）全部完成；阶段 7 测试落地（4 JVM 单测全绿 + 3 instrumented 骨架）；阶段 8 评审（3 层并行 Reviewer）发现并修复 2 个严重安全 bug（DEC-002 双钥系统：缺失关→开重包裹路径 + 首启 master key 生成未接线 + unlock 兜底）+ 9 个高价值警告项（错误分类/Update 校验/Bootstrap 事务/数据源原子/圈账本/三页 Error 态/重试/snackbar 重放）；阶段 9 发布——修复 release lintVital 拦截（data_extraction_rules.xml 非法 domain）、assembleRelease 成功产出 `app-release-unsigned.apk`、发布说明落盘 `docs/RELEASE_NOTES.md`。v1.0.0 MVP 交付，全部提交推送 GitHub。
- 2026-08-21（v1.0.0 收尾补漏，编排者调度）：① 修复首页最近交易分类联表 TODO——HomeViewModel 注入 ObserveCategoriesUseCase、HomeUiState.Success 携带 categoriesById、HomeScreen 传真实分类名/图标/色（替代 null 占位 ❔），commit 0c041a3；② 可测性改造——DatabaseGate 提炼 DatabaseProvider 接口（加密语义零改动），RoomTransactionRunner/RoomDataSourceImpl 依赖接口化，commit 3f042b6；③ Tester 补全 TransactionRunnerAtomicityTest 三个空骨架用例为真实断言（内存库 + DatabaseProvider 替身：deleteById 三表分发 / runInTransaction 回滚 / deleteByLedger 四表批删），commit ab7214c；④ 构建验证全绿（compileDebugKotlin + testDebugUnitTest 56 例 + compileDebugAndroidTestKotlin）；⑤ 任务清单 TASK-001~030 状态统一翻转为「已完成」。instrumented 用例仍待设备执行（标注待人工，见 09_test_report.md / 10_release_notes.md）。
- 2026-08-21（Phase 2 全线落地，编排者调度）：PM DEC-010 + PRD F-06/07/08 细化（fc28d98）→ 协调者需求确认（2818aa0）→ Architect DEC-011~013 + 架构/DB/API 设计 + TASK-031~039 拆分（e9aa4ee）→ Designer 三页 UI + C-27~C-32（306ed7b）→ 编码九任务（数据层 3fbe724、搜索 UseCase d7e8d0b、收支页联表 911f6ed、预算 UseCase+BudgetProgress 守卫 5ba92e4、图表组件 fb63970、搜索页 ccff71a、报表页 efbe338、预算页 07154fe、接线收口 8b0588e）→ Tester 补测 56→95 例全绿 + 09_test_report（a3764b4）→ Reviewer 评审 08_review（20cea82，P1-1 孤儿预算 + P2 六项）→ Developer 修 P1-1 删除分类级联清理预算（ac5579d）。三大技术口径落地：DEC-011 Canvas 自绘零第三方图表、DEC-012 App 内告警无通知栏、DEC-013 扩展 observeFiltered 零 Repository 改。全量 95 例单测 + compileDebugAndroidTestKotlin 全绿，未 push（待人工批准）。
- 2026-08-24（F-12 架构设计，Architect 调度，无人值守自决）：PM DEC-015 + PRD F-12 落盘（15cfc7a）→ Architect 补 DEC-016~019（XLSX 零依赖手动解析 / 文件读取与临时文件 / DB version 2 迁移 / 自动分类与幂等计数）+ 02/03/04 设计（transactions 加 4 列 + UNIQUE(ledgerId,externalId)、MIGRATION_1_2、BillParser 抽象、ImportBillUseCase、import 路由）+ TASK-040~044 拆分。口径：DB 1→2 首个迁移、UNIQUE 对 NULL 互异（手动记录不参与去重）、过滤行级+单事务整批写入（延续 DEC-015）、解析纯 Kotlin 可单测（SafBillFilePreparer 为唯一 Android 边界）。
- 2026-08-24（F-12 全流程落地，编排者无人值守调度）：Designer UI 设计 P-15 + C-33~C-37（be1ccdd）→ Developer 实现 TASK-040~043 数据层 4 列迁移 / 支付宝 GBK-CSV / 微信 XLSX 解析 / ImportBillUseCase（e13eee0）+ TASK-044 UI 四态 / 字段展示 / 首页入口（913e7f0）→ Tester 单测 111→132 例 + 迁移测试基建 + 真机遗留清单（d9a091a，设备未连接未执行真机）→ Developer 修三缺陷 androidTest 方法名 dex / 对方账号精确匹配 / 临时文件 uuid（49c0868）→ Reviewer 评审 FAIL-with-issues（9515907，1×P1+6×P2）→ Developer 修 P1-1 备份四字段 + 5×P2（cb679a7）。终态：JVM 132 例 + assembleDebug / assembleDebugAndroidTest 全绿；P2-4 BillParserFactory 分层挂账；真机 3 项（迁移保数据 / UI 结构 / 端到端）待设备连接后按 09_test_report §F-12 清单执行。
- 2026-08-24（P2 技术债清账 + 文档收口，Developer 调度）：Reviewer 挂账的 P2 代码债与文档收尾全部清掉——① GRANULARITY 枚举改名 Granularity（b9c0e6f，此前已落）② BillParserFactory 接口归位 data/datasource（7c42f4c）③ Search/Budgets 超限文件拆分 ≤300 行（f4fd92d：SearchScreen 604→173、BudgetsScreen 605→258、SearchViewModel 429→278）④ 账本管理导航统一 CompositionLocal（63eaa26）⑤ docs/04_api §12.3 BudgetProgress 契约回写 + CHANGELOG「v1.0.0 收尾」+ 06_tasks 项目状态更新 + 过时注释清理（随本次 docs commit）。真机 / release / Phase 3 收尾阶段明确不做（本地 debug 交付，无真机发布）。JVM 单测 186 例（33 类 0 失败）+ assembleDebug 全绿。

---

## 任务列表

> 阶段 6 编码任务拆分（Architect 产出，2026-08-18）。Developer 按依赖链顺序执行；每个任务为可独立编码、可独立验收的单元。
>
> 任务分层概览：
> - **构建骨架**（2）：TASK-001 工程骨架与版本锁定、TASK-018 Hilt 装配收口
> - **数据层**（4）：TASK-002 Room 实体、TASK-003 Room DAO、TASK-004 SQLCipher/密钥、TASK-005 DataStore
> - **领域/契约层**（4）：TASK-006 Domain 模型、TASK-007 DataSource 接口、TASK-008 mapper+RoomDataSource、TASK-009 Repository
> - **业务层**（8）：TASK-010 util 门面、TASK-011 种子引导、TASK-012 账本业务、TASK-013 交易业务、TASK-014 债务业务、TASK-015 分类业务+迁移 UseCase、TASK-016 导入导出、TASK-017 应用锁
> - **UI 层**（12）：TASK-019 主题、TASK-020 组件库、TASK-021 导航壳、TASK-022 解锁门、TASK-023 首页、TASK-024 收支、TASK-025 债务、TASK-026 分类、TASK-027 设置、TASK-028 表单 Sheets、TASK-029 账本管理、TASK-030 占位页
>
> 公共约定（不逐条重复）：
> - 包根：`app/src/main/java/com/minibook/`；所有任务涉及文件均在此根下，路径省略根前缀（ARCHITECTURE §3.2 目录结构）。
> - 版本锁定：一律以 `gradle/libs.versions.toml`（TASK-001）为准，与 DEC-001 既定栈一致，**不得擅改**。
> - 所有 DAO/查询第一过滤条件为 `ledgerId`（DATABASE §5 / ARCHITECTURE §8.1）；金额一律 `Long` 分，禁止业务代码手写 `*100`/`/100f`（DATABASE §9）。
> - 测试：Developer 按 coding_rules 写 `tests/unit/`（白盒）；instrumented 用例（SQLCipher/Keystore/Room 迁移）与 integration/e2e 属阶段 7 Tester；各任务验收标准中「需测试覆盖点」为测试要求标注，具体用例由 Tester 编写。
> - UI 层任务依赖统一的前置链（TASK-018 装配收口 → TASK-019 主题 → TASK-020 组件 → TASK-021 导航壳），此后各页面任务可在导航壳上并行推进。

---

### TASK-001: 工程骨架与版本锁定（Gradle 初始化）

- 状态：已完成
- 优先级：P0
- 负责角色：Developer
- 所属层：构建骨架
- 关联需求：F-10/F-11 架构前置；DEC-001、DEC-006
- 估算：1 天
- 依赖关系：无（本任务为全项目起点）
- 涉及文件：
  - `settings.gradle.kts`、`build.gradle.kts`（根）、`gradle/libs.versions.toml`、`gradle.properties`
  - `gradle/wrapper/gradle-wrapper.properties`（Gradle 8.14.3）
  - `app/build.gradle.kts`、`app/proguard-rules.pro`
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/java/com/minibook/MiniBookApp.kt`（空 Application 壳，注册 manifest）
  - `app/src/main/res/values/strings.xml`、`app/src/main/res/values/themes.xml`（最小资源）
  - `.gitignore`
- 描述：
  - 搭建单模块 `:app` 工程，按包分层；配置 `gradle/libs.versions.toml` 锁定**全部**版本。
  - **必须锁定（协调者已验证，不可擅改）**：
    | 项 | 值 |
    |----|----|
    | Kotlin | `2.2.0` |
    | KSP | `2.2.0-2.0.2` |
    | Compose BOM | `2025.09.01` |
    | AGP | `8.13.0` |
    | Gradle | `8.14.3`（wrapper） |
    | compileSdk / targetSdk | `36` |
    | minSdk | `26`（Android 8.0） |
    | JDK | `21`（jvmTarget 21 + compileOptions） |
    | Room（KSP） | `2.8.4` |
    | SQLCipher | `net.zetetic:sqlcipher-android:4.6.1` |
    | Hilt（KSP） | `2.58` |
    | Biometric | `androidx.biometric:biometric:1.1.0` |
  - **其余库版本（Architect 在 DEC-001 栈内选定，含依据）**：
    | 库 | 版本 | 选择依据 |
    |----|----|---------|
    | `androidx.core:core-ktx` | `1.16.0` | 稳定线，要求 compileSdk≥35（本工程 36 满足）；兼容 minSdk 26 |
    | `androidx.activity:activity-compose` | `1.10.1` | 与 Compose BOM 2025.09 同期稳定；enableEdgeToEdge/SystemBarStyle 支撑（ARCHITECTURE §11） |
    | `androidx.lifecycle:lifecycle-runtime-ktx` / `-viewmodel-compose` / `-runtime-compose` | `2.9.1` | 稳定线；`stateIn(WhileSubscribed)`/`collectAsStateWithLifecycle` 支撑 ARCHITECTURE §4.3/§5.3 |
    | `androidx.navigation:navigation-compose` | `2.9.0` | 稳定线；单 Activity + NavHost（ARCHITECTURE §12） |
    | `androidx.hilt:hilt-navigation-compose` | `1.2.0` | 官方稳定；`@HiltViewModel` + Compose 装配 |
    | `androidx.datastore:datastore-preferences` | `1.1.1` | 稳定线；Preferences DataStore（ARCHITECTURE §5.1） |
    | `androidx.sqlite:sqlite-ktx` | `2.5.0` | 与 Room 2.8.4 依赖的 SQLite 驱动 2.5.x 对齐；SQLCipherOpenHelperFactory 基于 SupportOpenHelperFactory（ARCHITECTURE §7.1） |
    | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | `1.10.2` | 稳定线，支持 Kotlin 2.2.0；协程/Flow 基础 |
    | `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.8.1` | 稳定线，兼容 Kotlin 2.x serialization 编译插件 2.2.0；导入导出 JSON（API §11） |
    | 测试 | `junit:4.13.2`、`org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2`、`androidx.test.ext:junit:1.2.1`、`androidx.room:room-testing:2.8.4` | Developer 单测 + Tester instrumented 迁移测试基线 |
  - `app/build.gradle.kts`：启用 Kotlin + KSP + Compose + Hilt + Serialization 插件；Room `exportSchema=true`，`schemaLocation` 指向 `app/schemas`（DATABASE §2/§7）；`buildFeatures { compose = true }`。
  - `AndroidManifest.xml`：`android:allowBackup="false"` + `dataExtractionRules`（DEC-006）；Application 类注册。
  - `.gitignore`：排除 `build/`、`.gradle/`、`local.properties`、`*.apk` 等构建产物（orchestrator_rules §8 要求构建产物被忽略）。
- 验收标准：
  - [ ] `libs.versions.toml` 版本与上表完全一致（重点核 Kotlin/KSP/BOM/AGP/Gradle/SDK/Room/SQLCipher/Hilt/Biometric 十项锁定值）
  - [ ] `./gradlew --version` 显示 Gradle 8.14.3
  - [ ] `./gradlew :app:assembleDebug` 编译通过（空壳可安装运行）
  - [ ] JDK 21（jvmTarget/compileOptions 均为 21）
  - [ ] Manifest 含 `allowBackup=false` + `dataExtractionRules`（DEC-006）
  - [ ] Room schema 目录已配置（`app/schemas` 首次生成见 TASK-003）
- 需测试覆盖点：无（构建验证为主）；后续任务依赖本工程可编译基线。

---

### TASK-002: Room 实体与 TypeConverter（6 表）

- 状态：已完成
- 优先级：P0
- 负责角色：Developer
- 所属层：数据层
- 关联需求：F-01~F-11 数据底座；DEC-001、DEC-008
- 估算：1 天
- 依赖关系：TASK-001
- 涉及文件：
  - `data/local/db/entity/TransactionEntity.kt`
  - `data/local/db/entity/DebtEntity.kt`
  - `data/local/db/entity/CategoryEntity.kt`
  - `data/local/db/entity/LedgerEntity.kt`
  - `data/local/db/entity/BudgetEntity.kt`
  - `data/local/db/entity/SyncChangeEntity.kt`
  - `data/local/db/Converter.kt`
- 描述：
  - 6 张表 Entity 严格对齐 `DATABASE §4.1~§4.6`：字段名/Kotlin 类型/SQL 约束/默认值/索引逐字段一致；金额 `Long` 分；日期/时间 ISO 字符串；`syncStatus` 默认 `'local'`。
  - 索引对齐 `DATABASE §5.1` 概览表：业务表复合索引一律 `ledgerId` 打头。
  - `Converter.kt`：枚举↔String、Boolean↔Int（`INTEGER(0/1)`）TypeConverter，覆盖全部枚举类型（DATABASE §9.6）。
- 验收标准：
  - [ ] 6 Entity 与 DATABASE §4 逐字段一一对应（名称/类型/约束/默认值）
  - [ ] 复合索引与 §5 表一致；`budgets` 含 `(ledgerId, month, categoryId)` UNIQUE（DATABASE §4.5 / DEC-008）
  - [ ] `sync_changes` 三索引对齐 §4.6
  - [ ] 金额全为 `Long`，无浮点字段；日期为 `yyyy-MM-dd`、审计时间为完整时间戳
  - [ ] TypeConverter 覆盖全部枚举 + Boolean，编译通过
- 需测试覆盖点：类型转换往返（枚举/Boolean↔存储值）单测。

---

### TASK-003: Room DAO 与 Database（6 DAO + schema 导出）

- 状态：已完成
- 优先级：P0
- 负责角色：Developer
- 所属层：数据层
- 关联需求：F-01/F-02/F-03；DEC-001、DEC-007、DEC-008
- 估算：1.5 天
- 依赖关系：TASK-002
- 涉及文件：
  - `data/local/db/dao/TransactionDao.kt`
  - `data/local/db/dao/DebtDao.kt`
  - `data/local/db/dao/CategoryDao.kt`
  - `data/local/db/dao/LedgerDao.kt`
  - `data/local/db/dao/BudgetDao.kt`
  - `data/local/db/dao/SyncChangeDao.kt`
  - `data/local/db/MiniBookDatabase.kt`
  - `data/local/db/QueryRow.kt`（`SummaryRow` / `NetByPartyRow` 查询 DTO）
  - `app/schemas/com.minibook.data.local.db.MiniBookDatabase/1.json`（首次编译导出）
- 描述：
  - 6 个 DAO 严格对齐 `DATABASE §6.1~§6.5`：全部查询带 `ledgerId` 过滤；列表/汇总返回 `Flow`；CRUD 用 `suspend`。
  - `TransactionDao`：`observeAll` / `observeFiltered`（type/categoryId/dateFrom/dateTo 可空参数化写法，Phase 2 搜索复用）/ `monthSummary`（月结余 SQL）/ `countByCategory`。
  - `DebtDao`：`observeAll` / `observeNetByParty`（`CASE WHEN` 四态净额，口径见 DATABASE §4.2 表）/ `observeFiltered(kind?, direction?)`。
  - `CategoryDao`：`upsertList`（REPLACE）/ `insert` / `update` / `deleteCustom`（防误删内置 `isBuiltIn=0`）/ `observeByType`（order ASC）/ `countByLedger` / `countRef`。
  - `LedgerDao`：`observeAll` / `getDefault` / `getById` / `count`。
  - `BudgetDao` / `SyncChangeDao`：骨架（`deleteTotal` 先删后插语义，DEC-008；`pendingChanges`/`markSynced` Phase 3 占位）。
  - `MiniBookDatabase`：`@Database(version=1, entities=[6 实体], exportSchema=true)`，注册 Converter；`fallbackToDestructiveMigration(false)`（DATABASE §7.3 显式禁止破坏迁移）。
- 验收标准：
  - [ ] 6 DAO 方法签名与 DATABASE §6 逐条一致
  - [ ] `observeNetByParty` 的 CASE WHEN 四态贡献与 PRD F-02 净额口径逐项对应
  - [ ] `observeFiltered` 参数化写法（`AND (type=:type OR :type IS NULL)`）与 §6.1 一致
  - [ ] Room 编译导出 `1.json` schema（`exportSchema=true`），文件入库
  - [ ] `fallbackToDestructiveMigration(false)` 生效
  - [ ] DAO 可被 Hilt 注入（见 TASK-004 模块装配）
- 需测试覆盖点：净额 `CASE WHEN` 聚合正确性；月结余 SQL 正确性（instrumented，Tester）。

---

### TASK-004: SQLCipher 加密与密钥管理（security 层）

- 状态：已完成
- 优先级：P0
- 负责角色：Developer
- 所属层：数据层（安全）
- 关联需求：F-10/F-05；DEC-001、DEC-002、DEC-003、DEC-006
- 估算：2 天
- 依赖关系：TASK-003
- 涉及文件：
  - `data/local/security/KeyManager.kt`
  - `data/local/security/PasswordCipher.kt`
  - `data/local/security/DatabaseGate.kt`
  - `di/DatabaseModule.kt`
  - `di/SecurityModule.kt`
  - `app/src/main/res/xml/data_extraction_rules.xml`
- 描述：
  - `KeyManager`：Keystore 双别名——`minibook_master_key_auth`（AES-256/GCM，`setUserAuthenticationRequired(true)`）+ `minibook_master_key_plain`（无认证要求）；32 字节 `SecureRandom` master key；AES-GCM 加解密 master key blob（Base64 写 `noBackupFilesDir/minibook_key.bin`）；锁状态切换时用当前 key 重加密覆盖（DEC-002「一把 master key + 两把包裹 key」）。
  - `DatabaseGate`：`open(passphraseBytes)` / `close()`；DB 打开成功后才执行 Room migration；passphrase 用后 `Arrays.fill(0)` 清零（ARCHITECTURE §7.2）。
  - `DatabaseModule`：`Room.databaseBuilder(...).openHelperFactory(SQLCipherOpenHelperFactory(passphrase)).fallbackToDestructiveMigration(false)`，提供 `MiniBookDatabase` / 各 DAO / `DatabaseGate`（ARCHITECTURE §7.1）。
  - `SecurityModule`：提供 `KeyManager` / `BiometricHelper`（TASK-017 补全）/ 锁设置读取。
  - `data_extraction_rules.xml`：排除 DB 与 key 文件（DEC-006）。
- 验收标准：
  - [ ] 首次启动生成 32B master key，写 `minibook_key.bin`（私有目录，非外置）
  - [ ] Keystore 双别名创建成功；auth 与 plain 均能加解密**同一** master key（DEC-002）
  - [ ] 锁状态切换后重加密覆盖 key blob，旧 blob 失效
  - [ ] SQLCipher DB 首次打开成功（`isOpen()` 为 true，`PRAGMA cipher_version` 可查）
  - [ ] DB 文件头非明文 SQLite（抽查十六进制，确为加密）
  - [ ] 未解锁时 `DatabaseGate` 不提供可用连接
  - [ ] passphrase 解密后清零（代码审查点）
  - [ ] `allowBackup=false` + dataExtractionRules 生效（DEC-006）
- 需测试覆盖点：Keystore 加解密往返、master key 双包裹、DB 文件加密特征、passphrase 清零（instrumented，Tester）。

---

### TASK-005: DataStore 存储（Settings / Ledger）

- 状态：已完成
- 优先级：P0
- 负责角色：Developer
- 所属层：数据层
- 关联需求：F-05/F-09/F-11；DEC-001、DEC-002
- 估算：0.5 天
- 依赖关系：TASK-001
- 涉及文件：
  - `di/DataStoreModule.kt`
  - `data/local/datastore/SettingsDataStore.kt`
  - `data/local/datastore/LedgerDataStore.kt`
- 描述：
  - `SettingsDataStore`：`DarkModeSetting`（SYSTEM/LIGHT/DARK）、`appLockEnabled`、`lockTimeout`（立即/30s/1min/5min/永不 五档）、加密态只读标记（ARCHITECTURE §9/§11）。
  - `LedgerDataStore`：`currentLedgerId: Flow<String>`（Preferences DataStore，即时同步）+ `setCurrentLedgerId()`；账本列表缓存可选（ARCHITECTURE §8.2）。
  - `DataStoreModule`：单例提供两个 Preferences DataStore（`datastore-preferences:1.1.1`）。
- 验收标准：
  - [ ] 主题/锁开关/超时五档读写，`Flow` 可观察（软切换）
  - [ ] `currentLedgerId: Flow<String>` 写后即时生效（ARCHITECTURE §8.2）
  - [ ] 单例注入（Hilt `@Singleton`）
  - [ ] 无值默认态：theme=SYSTEM、锁默认关、超时默认 1min、currentLedgerId 初始空（待 TASK-011 Bootstrap 写入）
- 需测试覆盖点：DataStore 读写与默认态单测（JVM + `datastore-preferences-core`）。

---

### TASK-006: Domain 模型（纯 Kotlin）

- 状态：已完成
- 优先级：P0
- 负责角色：Developer
- 所属层：领域/契约层
- 关联需求：全功能数据基座；DEC-001
- 估算：0.5 天
- 依赖关系：TASK-001
- 涉及文件：
  - `domain/model/Transaction.kt`、`Debt.kt`、`Category.kt`、`Ledger.kt`、`Budget.kt`、`SyncChange.kt`
  - `domain/model/MonthSummary.kt`、`NetByPartyRow.kt`
  - `domain/model/Enums.kt`（`TransactionType`/`CategoryType`/`DebtKind`/`DebtDirection`/`SyncStatus`）
  - `domain/model/TransactionFilter.kt`、`DebtFilter.kt`
- 描述：
  - 纯 Kotlin data class，字段与 DATABASE §4 / API §10 一致；`amount` 一律 `Long` 分。
  - 枚举取值与 DATABASE §4 常量字符串一致（`expense/income`、`lend/repay`、`owe_me/i_owe`、`local/syncing/synced/conflict`）。
  - `TransactionFilter(type?, categoryId?, dateFrom?, dateTo?)` 对齐 API §3；`DebtFilter(kind?, direction?)` 对齐 API §4（A-2 确认）。
  - **不得 import 任何 Android 框架类**（ARCHITECTURE §4 可测试性）。
- 验收标准：
  - [ ] domain 包无 `android.*` 依赖（纯 Kotlin 编译为普通 JVM 可测）
  - [ ] 模型字段与 DATABASE §4 / API §10 逐字段对应
  - [ ] `DebtFilter{ kind: DebtKind?, direction: DebtDirection? }` 与 A-2 确认一致
  - [ ] `MonthSummary(incomeFen/expenseFen/balanceFen)` 三字段齐备
- 需测试覆盖点：枚举字符串取值一致性单测。

---

### TASK-007: DataSource 抽象接口（6 + Remote 占位）

- 状态：已完成
- 优先级：P0
- 负责角色：Developer
- 所属层：领域/契约层
- 关联需求：F-01~F-11；DEC-001、DEC-008
- 估算：0.5 天
- 依赖关系：TASK-006
- 涉及文件：
  - `data/datasource/TransactionDataSource.kt`
  - `data/datasource/DebtDataSource.kt`
  - `data/datasource/CategoryDataSource.kt`
  - `data/datasource/LedgerDataSource.kt`
  - `data/datasource/BudgetDataSource.kt`（Phase 2 预留）
  - `data/datasource/SyncChangeDataSource.kt`（Phase 3 预留）
  - `data/remote/RemoteDataSource.kt`（仅接口占位）
- 描述：
  - 6 接口逐条对齐 `API.md §3~§8`：方法名/参数/返回一致；所有业务查询首参 `ledgerId`（ARCHITECTURE §8.1）。
  - `RemoteDataSource` 仅契约占位（注释掉的 `push`/`pull`），本期**不实现、不接线**（API §9）。
  - 注释标注 Phase 2/3 扩展点（搜索复用 filter、云同步）。
- 验收标准：
  - [ ] 接口签名与 API.md §3~§8 逐方法一致
  - [ ] `observeByLedger(ledgerId, filter: DebtFilter)` 与 A-2 展开后字段一致
  - [ ] 纯契约，无实现依赖；`RemoteDataSource` 无任何实现代码
- 需测试覆盖点：无（契约编译期校验）。

---

### TASK-008: 数据映射与 RoomDataSource 实现（+ DataSourceModule）

- 状态：已完成
- 优先级：P0
- 负责角色：Developer
- 所属层：数据层
- 关联需求：F-01/F-02/F-11；DEC-001、DEC-008
- 估算：1.5 天
- 依赖关系：TASK-003、TASK-004、TASK-006、TASK-007
- 涉及文件：
  - `data/mapper/TransactionMapper.kt`、`DebtMapper.kt`、`CategoryMapper.kt`、`LedgerMapper.kt`、`BudgetMapper.kt`、`SyncChangeMapper.kt`
  - `data/local/datasource/RoomDataSource.kt`
  - `data/local/datasource/RoomDataSourceImpl.kt`
  - `di/DataSourceModule.kt`
- 描述：
  - Entity↔Domain 双向映射；`amount` 保持 `Long` 分不转换；日期/时间字符串透传。
  - `RoomDataSourceImpl` 聚合 6 DAO，实现全部 `*DataSource` 接口：`observeByLedger` 转发对应 DAO 的 observe 方法并映射；`netByParty` 由 `observeNetByParty` 转 `Map<String, Long>`；`monthlySummary` 聚合 `SummaryRow`；insert/update/delete 维护 `createdAt/updatedAt`（insert 生成 UUID + createdAt，API §3 语义）。
  - `DataSourceModule`：`@Binds` 接口→`RoomDataSourceImpl`；注释注明「未来接云端只改此模块」（ARCHITECTURE §6.3）。
- 验收标准：
  - [ ] 每个 DataSource 接口方法均有实现；DAO→Domain 映射正确（抽查：amount 分不丢精度、date 原样）
  - [ ] `netByParty` 返回 Map 净额，口径（借出+、借入−、收到还款−、我还款+）正确
  - [ ] `monthlySummary` income/expense/balance 三字段正确
  - [ ] `DataSourceModule` `@Binds` 编译通过，Hilt 图可注入 6 个 DataSource
- 需测试覆盖点：映射往返、净额口径（instrumented，Tester 重点）。

---

### TASK-009: Repository 接口与实现

- 状态：已完成
- 优先级：P0
- 负责角色：Developer
- 所属层：领域/契约层
- 关联需求：全功能；DEC-001、DEC-008
- 估算：1 天
- 依赖关系：TASK-007、TASK-008
- 涉及文件：
  - `domain/repository/TransactionRepository.kt` + `TransactionRepositoryImpl.kt`
  - `domain/repository/DebtRepository.kt` + `DebtRepositoryImpl.kt`
  - `domain/repository/CategoryRepository.kt` + `CategoryRepositoryImpl.kt`
  - `domain/repository/LedgerRepository.kt` + `LedgerRepositoryImpl.kt`
  - `domain/repository/BudgetRepository.kt`（Phase 2 预留接口）
  - `domain/repository/SyncChangeRepository.kt`（Phase 2 预留接口）
- 描述：
  - Repository 组合 DataSource 接口，为 UseCase 提供领域聚合入口（ARCHITECTURE §4.1）；只依赖 `data/datasource` 接口，不感知 Room/SQL/SQLCipher。
  - `CategoryRepository` 提供删除前关联检查 + 迁移的原子方法入口（供 TASK-015 UseCase 编排）。
  - `BudgetRepository` 注明「先删后插」业务唯一语义（DEC-008）。
- 验收标准：
  - [ ] Repository 只依赖 DataSource 接口（无 Room/SQL import）
  - [ ] 每个方法有对应 UseCase 或预留使用方
  - [ ] Budget 总预算「先删后插」注释落位（DEC-008）
  - [ ] 编译通过
- 需测试覆盖点：Repository→DataSource 委托正确性（Fake DataSource 单测）。

---

### TASK-010: util 工具门面（MoneyFormatter / TimeFormatter / Validators / JsonMappers 骨架）

- 状态：已完成
- 优先级：P0
- 负责角色：Developer
- 所属层：业务层（工具）
- 关联需求：F-01/F-02 金额口径；DEC-001
- 估算：0.5 天
- 依赖关系：TASK-006
- 涉及文件：
  - `util/MoneyFormatter.kt`
  - `util/TimeFormatter.kt`
  - `util/Validators.kt`
- 描述：
  - `MoneyFormatter`：`toFen(String): Long`（`BigDecimal(inputWithScale2).setScale(2, HALF_UP).movePointRight(2).longValueExact()`，DATABASE §9.2 推荐法）；`formatFen(fen, withSymbol): String`（整数拆分，不经 Float/Double，DATABASE §9.3）；千分位（DecimalFormat 整数分组）。
  - `Validators`：金额>0、≤`99_999_999_99` 分、备注≤200、姓名非空、小数≤2 位（DATABASE §9.4）。
  - `TimeFormatter`：`yyyy-MM-dd` 展示本地化、月份/年月、ISO 时间戳生成（`DateTimeFormatter`，DATABASE §9.5）。
  - JsonMappers 骨架放 TASK-016 一并完成。
- 验收标准：
  - [ ] `toFen("12.345")=1235`（半舍）；`toFen("12.34")=1234`；往返无损
  - [ ] `formatFen(123456)="¥1,234.56"`（整数拆分，无浮点中间态）
  - [ ] 金额上限/下限、备注长度、姓名空校验生效
  - [ ] 纯 Kotlin 无 Android 依赖，JVM 单测可直接跑
  - [ ] 禁止业务代码手写 `*100`/`/100f`（代码审查点）
- 需测试覆盖点：MoneyFormatter 元↔分往返、HALF_UP 边界、上限溢出；Validators 全边界（**单测重点**，Developer `tests/unit/`）。

---

### TASK-011: BootstrapUseCase 与内置分类种子

- 状态：已完成
- 优先级：P0
- 负责角色：Developer
- 所属层：业务层
- 关联需求：F-03/F-11；DEC-007
- 估算：1 天
- 依赖关系：TASK-005、TASK-008、TASK-009
- 涉及文件：
  - `domain/model/BuiltInCategories.kt`（种子常量表：12 支出 + 6 收入）
  - `domain/usecase/ledger/BootstrapUseCase.kt`
  - `domain/usecase/category/CloneBuiltInCategoriesUseCase.kt`（新账本复用）
- 描述：
  - 首次打开（DB 首次连接成功）幂等引导：`ledgers` 空 → 插入默认账本（`isDefault=1`，name="默认账本"，内置 icon/color）→ 为该账本克隆 18 内置分类 → `LedgerDataStore` 写入默认账本 ID（ARCHITECTURE §8.4）。
  - 种子 12 支出 + 6 收入，字段对齐 DATABASE §8.1/§8.2（icon/name/color/order）；`isBuiltIn=true`；「其他」`order=99` 恒末尾（DEC-007）。
  - 幂等守卫：`countByLedger(ledgerId)==0` 才 clone（DATABASE §8.3）；分类 id 每账本独立 UUID，不跨账本复用（DATABASE §8.3）。
  - `CloneBuiltInCategoriesUseCase` 供新账本创建（TASK-012）在事务内复用。
- 验收标准：
  - [ ] 首次打开自动创建默认账本 + 18 内置分类
  - [ ] 种子 12 支出 + 6 收入与 DATABASE §8 两张表逐行一致
  - [ ] 「其他」`order=99`、`isBuiltIn=true`、默认名锁定（DEC-007）
  - [ ] 幂等：二次启动不重复插入；重装/恢复 KeyStore 后不重复刷
  - [ ] `LedgerDataStore` 被写入默认账本 ID
- 需测试覆盖点：种子表逐行一致性、幂等（instrumented，Tester）。

---

### TASK-012: 账本业务 UseCase

- 状态：已完成
- 优先级：P0
- 负责角色：Developer
- 所属层：业务层
- 关联需求：F-11；DEC-001、DEC-009
- 估算：1 天
- 依赖关系：TASK-005、TASK-009、TASK-011
- 涉及文件：
  - `domain/usecase/ledger/CreateLedgerUseCase.kt`
  - `domain/usecase/ledger/SwitchLedgerUseCase.kt`
  - `domain/usecase/ledger/DeleteLedgerUseCase.kt`
  - `domain/usecase/ledger/ObserveLedgersUseCase.kt`
  - `domain/common/LedgerProvider.kt`
- 描述：
  - `CreateLedgerUseCase`：插入 Ledger 行 → **同一事务**内 clone 18 内置分类 → 切换为新账本（ARCHITECTURE §8.4）。
  - `SwitchLedgerUseCase`：`LedgerDataStore.setCurrentLedgerId` 纯状态变更，无数据搬移（§8.3）。
  - `DeleteLedgerUseCase`：默认账本业务拒绝；非默认删除 = `withTransaction` 内按 `ledgerId` 批量删 4 表（transactions/debts/categories/budgets）再删 Ledger 行（DATABASE §4.4）；至少保留一个账本。
  - `LedgerProvider`：`suspend currentLedgerId()`，UseCase 一律经其取当前账本，禁止硬编码（§8.5）。
- 验收标准：
  - [ ] 新建账本后自动切为当前账本（无「无当前账本」态）
  - [ ] 删除默认账本被业务拒绝
  - [ ] 删除非默认账本单事务清 4 表 + 删账本行，任一失败回滚
  - [ ] 切换为纯状态写
  - [ ] UseCase 不硬编码 ledgerId，一律经 `LedgerProvider`
- 需测试覆盖点：建账本事务（分类克隆）、删账本原子性（instrumented）。

---

### TASK-013: 交易业务 UseCase

- 状态：已完成
- 优先级：P0
- 负责角色：Developer
- 所属层：业务层
- 关联需求：F-01；DEC-001
- 估算：1 天
- 依赖关系：TASK-009、TASK-010、TASK-012
- 涉及文件：
  - `domain/usecase/transaction/AddTransactionUseCase.kt`
  - `domain/usecase/transaction/UpdateTransactionUseCase.kt`
  - `domain/usecase/transaction/DeleteTransactionUseCase.kt`
  - `domain/usecase/transaction/FetchMonthSummaryUseCase.kt`
  - `domain/usecase/transaction/ObserveTransactionsUseCase.kt`
  - `domain/common/BusinessErrors.kt`（`LedgerNotFound`/校验异常）
- 描述：
  - `AddTransactionUseCase`：校验（金额>0 分、分类必填、备注≤200、日期合法，复用 Validators）→ 生成 UUID + `createdAt/updatedAt` → Repository insert（ARCHITECTURE §4.1 + DATABASE §9.5）。
  - `UpdateTransactionUseCase`：仅改 `type/amount/categoryId/description/date`，`createdAt` 不可改、`updatedAt` 刷新（API §3）。
  - `DeleteTransactionUseCase`：物理删除。
  - `FetchMonthSummaryUseCase`：`monthlySummary(ledgerId, YearMonth)`，账本维度。
  - `ObserveTransactionsUseCase`：`currentLedgerId().flatMapLatest { observeByLedger(it, filter) }` 返回 `Flow<List<Transaction>>`（ARCHITECTURE §4.3/§8.2）。
- 验收标准：
  - [ ] 记一笔：金额「元→分」入库正确（12.34→1234）；分类必填校验生效
  - [ ] 金额≤0 / 超限 / 备注>200 拒绝保存
  - [ ] 编辑不改 createdAt、刷新 updatedAt
  - [ ] 月度汇总 income/expense/balance 口径正确（DB 端聚合）
  - [ ] Flow 随当前账本切换自动重订阅
- 需测试覆盖点：AddTransaction 校验分支、金额转换、月度汇总口径（单测 + instrumented）。

---

### TASK-014: 债务业务 UseCase

- 状态：已完成
- 优先级：P0
- 负责角色：Developer
- 所属层：业务层
- 关联需求：F-02；DEC-001
- 估算：1 天
- 依赖关系：TASK-009、TASK-010、TASK-012
- 涉及文件：
  - `domain/usecase/debt/AddDebtUseCase.kt`
  - `domain/usecase/debt/UpdateDebtUseCase.kt`
  - `domain/usecase/debt/DeleteDebtUseCase.kt`
  - `domain/usecase/debt/ObserveDebtsUseCase.kt`
  - `domain/usecase/debt/NetByPartyUseCase.kt`
- 描述：
  - `AddDebtUseCase`：校验姓名非空、金额>0、四态 `kind×direction` 合法 → insert。
  - 四态字典：`lend×owe_me(借出+)` / `lend×i_owe(借入−)` / `repay×owe_me(收到还款−)` / `repay×i_owe(我还款+)`（DATABASE §4.2）。
  - `ObserveDebtsUseCase`：明细 Flow（账本维度 + 可选 `DebtFilter` kind/direction，A-2）。
  - `NetByPartyUseCase`：`netByParty` Map → 领域聚合（按对象视图数据源）。
  - 还款金额可大于当前净额（业务允许，UI 提示不拦截，P-08 边界）。
- 验收标准：
  - [ ] 四态录入校验正确，拒绝非法组合
  - [ ] 姓名空 / 金额≤0 拒绝保存
  - [ ] 净额口径：借出+、借入−、收到还款−、我还款+，与 PRD F-02 逐项对应
  - [ ] 明细 Flow 支持 kind/direction 过滤（A-2）
  - [ ] 同一对方多次还款独立成行、净额实时重算
- 需测试覆盖点：**净额口径单测**（重点，覆盖四态与分组）、四态校验。

---

### TASK-015: 分类业务 + 删除迁移 UseCase（A-1 采纳）

- 状态：已完成
- 优先级：P0
- 负责角色：Developer
- 所属层：业务层
- 关联需求：F-03；DEC-007
- 估算：1.5 天
- 依赖关系：TASK-009、TASK-012
- 涉及文件：
  - `domain/usecase/category/ObserveCategoriesUseCase.kt`
  - `domain/usecase/category/CreateCategoryUseCase.kt`
  - `domain/usecase/category/UpdateCategoryUseCase.kt`
  - `domain/usecase/category/DeleteCategoryUseCase.kt`
  - `domain/usecase/category/DeleteCategoryWithMigrationUseCase.kt`（**A-1 采纳方案**）
- 描述：
  - `CreateCategoryUseCase`：自定义分类 `order` 在 1~98 分配（不与「其他」99 冲突，DATABASE §8.3）；name 非空。
  - `UpdateCategoryUseCase`：内置可改 `name/icon/color/order`；`type`/`isBuiltIn` 锁定（DEC-007）；「其他」name 只读。
  - `DeleteCategoryUseCase`：内置禁删（业务拒绝）；`countTransactionsByCategory==0` 才允许直接删。
  - **`DeleteCategoryWithMigrationUseCase`（A-1）**：单事务内编排——`countTransactionsByCategory(旧)` → 取关联交易（`observeByLedger(filter.categoryId=旧)`）→ 逐条 `update(tx.copy(categoryId=目标))` → `deleteById(旧)`；目标分类校验（同 `type`、非自身、存在）。UI 单调用（P-05 迁移流程，TASK-026 接线）。
- 验收标准：
  - [ ] 删除被引用分类走迁移 UseCase：N 笔交易全部重挂目标分类后删除成功
  - [ ] **事务原子性**：注入第 k 条 update 失败 → 全部回滚，原分类与交易均未变（无悬空引用）
  - [ ] 内置分类删除被业务拒绝；「其他」不可改名
  - [ ] 目标分类校验：同 type、非自身、存在
  - [ ] 0 引用直接删路径仍走 `DeleteCategoryUseCase`
- 需测试覆盖点：迁移事务原子性（单测 + instrumented）、迁移目标校验、内置禁删。

---

### TASK-016: 导入导出 UseCase（version 1 JSON / legacy / 事务原子）

- 状态：已完成
- 优先级：P0
- 负责角色：Developer
- 所属层：业务层
- 关联需求：F-04；DEC-004、DEC-005、DEC-006
- 估算：2 天
- 依赖关系：TASK-008、TASK-009、TASK-010、TASK-012
- 涉及文件：
  - `domain/usecase/export/ExportAllUseCase.kt`
  - `domain/usecase/export/ImportAllUseCase.kt`
  - `util/BackupJsonModels.kt`（version 1 JSON DTO，kotlinx.serialization）
  - `util/BackupJsonMapper.kt`（domain ↔ JSON 序列化/反序列化）
- 描述：
  - `ExportAllUseCase`：全量读（ledgers/categories/transactions/debts/budgets）→ version 1 JSON（`scope="all"`，**不含 syncChanges**）→ 写文件 `minibook_backup_yyyyMMdd_HHmmss.json`（明文 JSON，DEC-005）；`@IoDispatcher`（ARCHITECTURE §10.1/§5.2）。
  - `ImportAllUseCase`：
    1. 严格校验：`version==1`；字段类型/必填/引用完整性（`transaction.categoryId ∈ categories` 且同账本、`*.ledgerId ∈ ledgers`、金额正整分、UUID 合法）→ 失败**零落库**（API §11.3）。
    2. legacy 检测：缺 `ledgers` 字段 → 按「导入到默认账本」处理，legacy 时间戳归一化为 `yyyy-MM-dd`（DEC-004）。
    3. 单事务全量覆盖：`withTransaction { 清空 4 表 → 保留/重建 ledgers → 逐行写入 }`，任一失败整体回滚（ARCHITECTURE §10.2）。
    4. 写入统一 `syncStatus='local'`（API §11 尾部说明）。
  - 导入期间 UI 锁入口由 UI 层配合（TASK-027），本任务提供 UseCase 成功/失败回调。
- 验收标准：
  - [ ] 导出 JSON 含全部 5 数组 + `version=1` + `scope=all`，字段与 API §11 逐项一致
  - [ ] 导出文件为明文可人工 inspect（DEC-005）
  - [ ] 导入合法文件：单事务覆盖，成功后数据与源一致（**往返一致性**）
  - [ ] 导入非法文件（缺字段/引用悬空/version=2）→ 报错零落库
  - [ ] legacy JSON（缺 ledgers）导入默认账本、时间归一化正确
  - [ ] 事务原子性：中途失败整体回滚（注入失败验证）
  - [ ] 不包含 syncChanges
- 需测试覆盖点：往返一致性、legacy 分支、校验失败零落库、原子性（instrumented，Tester 重点）。

---

### TASK-017: 应用锁控制器与冷启动分诊

- 状态：已完成
- 优先级：P0
- 负责角色：Developer
- 所属层：业务层（安全联动）
- 关联需求：F-05/F-10；DEC-002、DEC-003
- 估算：2 天
- 依赖关系：TASK-004、TASK-005
- 涉及文件：
  - `data/local/security/BiometricHelper.kt`
  - `data/local/security/AppLockController.kt`
  - `di/SecurityModule.kt`（补全 BiometricHelper/AppLockController 提供）
  - `MiniBookApp.kt`（注册锁生命周期观察者）
  - `ui/lock/LockState.kt`（锁状态模型，UI 层 TASK-022 消费）
- 描述：
  - `AppLockController`（单例）：持 `DatabaseGate` + 生命周期观察者；`lock()`/`unlock()`；`onStop` 后闲置计时（立即/30s/1min/5min/永不，读 SettingsDataStore）；重锁时 `DatabaseGate.close()` + master key 内存清零 + 广播 lock 事件 → 各 ViewModel 清态（ARCHITECTURE §7.5/§9）。
  - `BiometricHelper`：`BiometricPrompt` 封装（`BIOMETRIC_STRONG or DEVICE_CREDENTIAL`），成功回调返回 `cryptoObject.cipher`（ARCHITECTURE §7.4）；`isDeviceSecure` 检测（DEC-003）。
  - 冷启动分诊：锁开→LockScreen；锁关→plain key 静默开库直达（ARCHITECTURE §7.4 分诊 A）。
  - 锁状态切换（开→关）：用 plain key 重加密 master key 覆盖 blob（DEC-002）。
- 验收标准：
  - [ ] 冷启动锁开启：未解锁 DB 不可达（`isOpen=false`），LockScreen 唯一路由
  - [ ] 生物识别成功 → master key 解出 → DB 打开 → 进入 home
  - [ ] 锁关闭：静默开库直达，无锁屏
  - [ ] 离开超时五档重锁：DB 关闭 + 内存清零 + UI 回锁屏
  - [ ] 设备无锁屏 + 锁开启：引导设锁屏，不降级明文（DEC-003）
  - [ ] 锁状态切换重加密覆盖 blob，旧 blob 失效
- 需测试覆盖点：锁状态机、超时重锁、密钥重加密（instrumented，Tester）。

---

### TASK-018: Hilt 装配收口与 Application/MainActivity 壳

- 状态：已完成
- 优先级：P0
- 负责角色：Developer
- 所属层：构建骨架（汇聚点）
- 关联需求：全局；DEC-001、DEC-009
- 估算：0.5 天
- 依赖关系：TASK-001~TASK-017
- 涉及文件：
  - `di/DispatcherModule.kt`
  - `MiniBookApp.kt`（`@HiltAndroidApp` Application；安全模块初始化不触碰 DB；注册锁生命周期观察者）
  - `MainActivity.kt`（`@AndroidEntryPoint`；`setContent { MiniBookTheme + MiniBookNavHost }`）
  - `ui/navigation/Destinations.kt`、`ui/navigation/MiniBookNavHost.kt`（先建壳，TASK-021 填充）
- 描述：
  - `DispatcherModule`：`@IoDispatcher`/`@DefaultDispatcher`/`@MainDispatcher` 限定符 + `@ApplicationScope`（ARCHITECTURE §5.1/§5.2）。
  - Application 初始化：安全模块懒加载，首帧不触碰 DB（冷启动 ≤2s，ARCHITECTURE §13）。
  - MainActivity 单 Activity + Compose 壳；验证 Hilt 全图可注入。
- 验收标准：
  - [ ] `./gradlew :app:assembleDebug` 编译通过（Hilt/KSP 全量处理）
  - [ ] `@HiltAndroidApp` + `@AndroidEntryPoint` 生效（抽查可注入 DataSource/Repository/UseCase）
  - [ ] Application 初始化不触碰 DB（未解锁无数据可达）
  - [ ] DispatcherModule 限定符注入正确
- 需测试覆盖点：无（构建集成验证）。

---

### TASK-019: 主题与设计系统（theme）

- 状态：已完成
- 优先级：P1
- 负责角色：Developer
- 所属层：UI 层
- 关联需求：F-09；DEC-001
- 估算：1 天
- 依赖关系：TASK-018
- 涉及文件：
  - `ui/theme/Color.kt`（M3 角色 token + 语义 token：semanticIncome/semanticExpense/semanticNetZero）
  - `ui/theme/Theme.kt`（`MiniBookTheme`：darkTheme/dynamicColor 三态装配）
  - `ui/theme/Type.kt`（字体轨 + tabular figures 工具）
  - `ui/components/MoneyText.kt`（tnum 封装，UI §4 组件约定）
- 描述：
  - 对齐 ARCHITECTURE §11 + UI §1.1/§1.2：颜色一律引用 token；API 31+ 动态配色、26~30 基线降级；语义 token 静态双色值（收入绿/支出红/两清灰）。
  - `resolvedDark = when(setting){ SYSTEM -> isSystemInDarkTheme(); else -> setting==DARK }`；随 DataStore 流 `collectAsState` 软切换（ARCHITECTURE §11）。
  - 金额大字 tabular figures（`FontFeatureSettings("tnum")`），封装 `MoneyText` 全量复用。
  - `enableEdgeToEdge()` + `SystemBarStyle`（UI §5.4）。
- 验收标准：
  - [ ] 页面无硬编码色值（token 全量引用，代码审查点）
  - [ ] 亮/暗主题切换即时生效（全页面）
  - [ ] API 26 模拟器动态色缺省不崩溃（基线主题）
  - [ ] 语义色双主题可辨（UI §1.2）
  - [ ] 金额展示 tabular figures 生效（tnum）
- 需测试覆盖点：主题三态切换（UI 可选测试）。

---

### TASK-020: 通用组件库（components）

- 状态：已完成
- 优先级：P1
- 负责角色：Developer
- 所属层：UI 层
- 关联需求：全 UI；DEC-001
- 估算：2 天
- 依赖关系：TASK-019
- 涉及文件：
  - `ui/components/*.kt`：`MoneyInputField` / `CategoryPickerSheet` / `CategoryIconTile` / `EmptyState` / `LoadingState` / `SkeletonBlock` / `PrimaryFAB` / `BottomNavItem` / `TransactionTypeTab` / `DebtViewTab` / `DatePickerField` / `DateSectionHeader` / `NetBalanceCard` / `TransactionListItem` / `PartyNetRow` / `DebtDetailRow` / `LedgerSelector` / `SheetFormScaffold` / `ConfirmDialog` / `MigrationDialog` / `FullScreenBusyOverlay` / `SnackbarHost` / `ChoiceDialog` / `RadioRow` / `SettingSection` / `SettingRow` / `SwitchRow` / `RepayDirectionCard` / `PlaceholderPage` / `LogoLockScreen` / `BioAuthButton` / `GuidanceCard` / `MoneyText`
- 描述：
  - 按 UI §4 组件表逐件实现（C-01~C-26）；props 公开状态、不携带业务文案（由页面注入）、触达目标≥48dp、间距网格/圆角对齐 §1.4/§1.5。
  - `MoneyInputField`：36sp 大字、数字键盘、2 位小数限制、金额校验错误位、元↔分（DATABASE §9）。
  - 金额展示全部走 `MoneyText`（tnum），禁止手写格式化。
- 验收标准：
  - [ ] 每个组件编译通过（可独立 Preview 更佳）
  - [ ] `MoneyInputField` 2 位小数拦截、校验错误位、元↔分转换正确
  - [ ] 无业务文案硬编码在组件内
  - [ ] 金额展示全部走 MoneyText（代码审查点）
- 需测试覆盖点：金额输入边界（UI 可选）。

---

### TASK-021: 导航壳与主页面框架

- 状态：已完成
- 优先级：P1
- 负责角色：Developer
- 所属层：UI 层
- 关联需求：F-01~F-11；DEC-001
- 估算：1 天
- 依赖关系：TASK-018、TASK-019、TASK-020
- 涉及文件：
  - `ui/navigation/Destinations.kt`（route 常量）
  - `ui/navigation/MiniBookNavHost.kt`（锁门控 + 5 Tab + sheets + 占位页注册）
  - `ui/main/MainScreen.kt`（底部 NavigationBar + 5 页面壳）
  - `ui/main/BottomNavItem.kt`（C-07）
- 描述：
  - 未解锁态 `isLocked` 门控：只可达 `app-locked`（真解密门可达性即安全性，ARCHITECTURE §12）。
  - 解锁后 5 主页面走底部 `NavigationBar`（`saveState+restoreState` 保留 Tab 状态）。
  - 注册 sheets（`sheet-transaction`/`sheet-debt`/`sheet-repay`/`sheet-category-picker`/`sheet-category-editor`/`sheet-ledger`）与 `ledger-manage`。
  - 占位页注册但默认不可达（P-12~P-14）。
  - 页面间只传 `id` 类参数。
- 验收标准：
  - [ ] 锁开启时导航只能到 `app-locked`
  - [ ] 5 Tab 切换保留各自状态
  - [ ] 全部 sheet/占位 route 注册编译通过
  - [ ] 页面间只传 id 类参数（UUID）
- 需测试覆盖点：导航门控（UI 可选）。

---

### TASK-022: 解锁门页（P-01）

- 状态：已完成
- 优先级：P0
- 负责角色：Developer
- 所属层：UI 层
- 关联需求：F-05/F-10；DEC-002、DEC-003
- 估算：1 天
- 依赖关系：TASK-017、TASK-021
- 涉及文件：
  - `ui/lock/LockScreen.kt`（LogoLockScreen/BioAuthButton/GuidanceCard 组合）
  - `ui/lock/LockViewModel.kt`
- 描述：
  - 真解密门渲染：品牌页 + 生物识别按钮 + 系统凭据入口（ARCHITECTURE §7.4/§9）。
  - 冷启动分诊 B（无锁屏）→ 引导卡跳系统设置并回检 `isDeviceSecure`（DEC-003）。
  - 失败/取消停留锁屏可重试；解锁成功不保留于返回栈。
- 验收标准：
  - [ ] 锁态下本页无任何数据绑定（DB 未开无可达数据）
  - [ ] 生物识别/系统凭据通道正确触发 BiometricPrompt
  - [ ] 设备无锁屏引导卡展示 + 跳系统设置
  - [ ] 解锁成功直达 home，返回键不回锁屏
- 需测试覆盖点：解锁流（instrumented，Tester）。

---

### TASK-023: 首页仪表板（P-02）

- 状态：已完成
- 优先级：P1
- 负责角色：Developer
- 所属层：UI 层
- 关联需求：F-01/F-11；DEC-001
- 估算：1 天
- 依赖关系：TASK-013、TASK-021、TASK-020
- 涉及文件：
  - `ui/home/HomeScreen.kt`、`ui/home/HomeViewModel.kt`
- 描述：
  - 本月结余（`monthlySummary`）+ 收支合计；最近交易（`observeByLedger(filter=null)` **前 10 条 UI 截断**，A-3）+「查看全部」→ transactions 页。
  - `LedgerSelector` 顶部；FAB「＋」→ `sheet-transaction`。
  - `currentLedgerId.flatMapLatest` 随账本切换重订阅（ARCHITECTURE §8.2/§8.3）；Loading/Empty/Error 三态（UI §3 通用约定）。
- 验收标准：
  - [ ] 结余/收支合计正确（monthlySummary），金额 tnum + 千分位
  - [ ] 最近交易前 10 条（UI `take(10)`），空态引导
  - [ ] 切账本整页瞬时刷新（`WhileSubscribed(5000)` 防闪烁）
  - [ ] 账本不存在（LedgerNotFound）→ 引导回账本管理
- 需测试覆盖点：最近交易截断（UI 可选）。

---

### TASK-024: 收支记录页（P-03）

- 状态：已完成
- 优先级：P1
- 负责角色：Developer
- 所属层：UI 层
- 关联需求：F-01；DEC-001
- 估算：1 天
- 依赖关系：TASK-013、TASK-021、TASK-020
- 涉及文件：
  - `ui/transactions/TransactionsScreen.kt`、`ui/transactions/TransactionsViewModel.kt`
- 描述：
  - 全部/支出/收入筛选（`TransactionFilter.type`）→ 重订阅。
  - 按日期分组列表（`LazyColumn` + `key=id` + `animateItem`）；组内 `createdAt DESC`。
  - 点击条目 → `sheet-transaction` 编辑态；FAB 新增。
  - 筛选空态 + 「清除筛选」。
- 验收标准：
  - [ ] 类型筛选切换正确（expense/income/null）
  - [ ] 按 date 分组、组内 createdAt DESC
  - [ ] 千条级滚动流畅（LazyColumn + key）
  - [ ] 备注超长省略
- 需测试覆盖点：列表滚动（UI 可选）。

---

### TASK-025: 债务管理页（P-04）

- 状态：已完成
- 优先级：P1
- 负责角色：Developer
- 所属层：UI 层
- 关联需求：F-02；DEC-001
- 估算：1.5 天
- 依赖关系：TASK-014、TASK-021、TASK-020
- 涉及文件：
  - `ui/debts/DebtsScreen.kt`、`ui/debts/DebtsViewModel.kt`
- 描述：
  - 明细 / 按对象双 Tab。
  - 按对象：`netByParty` 渲染（net>0 对方欠我绿 / net<0 我欠对方红（绝对值）/ net==0 两清灰）+ 行尾「＋还款」→ `sheet-repay` 预填。
  - 顶部净额卡（正/负合计）+ 口径说明 dialog（UI P-04）。
  - 明细：按日期分组 + kind 标签 + ±金额（按净额贡献语义着色，非收支语义）。
  - 四态渲染守卫：未知组合中性灰 + 净额 0（不崩溃）。
- 验收标准：
  - [ ] 净额口径逐项正确（借出+/借入−/收到还款−/我还款+）
  - [ ] 按对象行红绿灰三态正确
  - [ ] 快捷还款预填 partyName + 方向推导正确
  - [ ] 明细±金额按净额贡献着色
  - [ ] 四态守卫：非法组合不崩溃
- 需测试覆盖点：净额展示、方向推导（UI 可选）。

---

### TASK-026: 分类管理页 + 迁移流程接线（P-05）

- 状态：已完成
- 优先级：P1
- 负责角色：Developer
- 所属层：UI 层
- 关联需求：F-03；DEC-007
- 估算：1.5 天
- 依赖关系：TASK-015、TASK-021、TASK-020
- 涉及文件：
  - `ui/categories/CategoriesScreen.kt`、`ui/categories/CategoriesViewModel.kt`
  - `ui/categories/CategoryEditorSheet.kt`、`ui/categories/MigrationDialog.kt`
- 描述：
  - 支出/收入分组网格（数量标头）；点击分类块 → 分类编辑 Sheet。
  - 内置：可改 `name/icon/color`，`type`/`isBuiltIn` 锁定，禁删；「其他」name 只读（DEC-007）。
  - 自定义：可增删改。删除流程：`countTransactionsByCategory==0` → ConfirmDialog 直接删；`>0` → MigrationDialog → 分类选择（同 type 排除自身）→ **调用 `DeleteCategoryWithMigrationUseCase`（A-1，单调用）** → snackbar。
  - 新建：`sheet-category-editor` 名称/图标/颜色/类型必填。
- 验收标准：
  - [ ] 内置分类无删除入口；「其他」改名只读
  - [ ] 自定义分类增删改全流程可用
  - [ ] 删除被引用分类走迁移 UseCase（事务原子，UI 单调用）
  - [ ] 迁移目标选择器排除自身且同 type
  - [ ] 0 引用直接确认删除
- 需测试覆盖点：迁移 UI 流程（UI 可选，核心逻辑在 TASK-015）。

---

### TASK-027: 设置页（P-06）

- 状态：已完成
- 优先级：P1
- 负责角色：Developer
- 所属层：UI 层
- 关联需求：F-04/F-05/F-09/F-10/F-11；DEC-002、DEC-003、DEC-005
- 估算：1.5 天
- 依赖关系：TASK-005、TASK-016、TASK-017、TASK-021、TASK-020
- 涉及文件：
  - `ui/settings/SettingsScreen.kt`、`ui/settings/SettingsViewModel.kt`
- 描述：
  - 账本入口 → `ledger-manage`。
  - 暗色模式三态 ChoiceDialog → SettingsDataStore 软切换（F-09）。
  - 应用锁开关：开启前 `isDeviceSecure` 检测（无锁屏引导，DEC-003）；关闭 ConfirmDialog 提示「数据仍保持加密」（DEC-002）。
  - 超时五档 ChoiceDialog（立即/30s/1min/5min/永不）。
  - 加密态只读行（SQLCipher + Keystore 已启用，F-10 不可关）。
  - 导出/导入：SAF 目录/文件选择 → ConfirmDialog（导入强确认 danger 样式）→ `FullScreenBusyOverlay` 锁数据入口 → 结果 snackbar/banner。
  - 版本行 v1.0.0。
- 验收标准：
  - [ ] 暗色三态切换即时生效
  - [ ] 锁开关/超时五档持久化到 DataStore
  - [ ] 导出走 SAF 生成 `minibook_backup_yyyyMMdd_HHmmss.json`
  - [ ] 导入强确认 + 校验失败零落库提示 + 覆盖后数据正确
  - [ ] 导入/导出期间全屏忙碌遮罩锁入口（禁返回/禁 Tab）
  - [ ] 加密态只读（无开关）
- 需测试覆盖点：导入导出 UI 流（instrumented，Tester）。

---

### TASK-028: 表单 Sheets（P-07 / P-08 / P-09 / P-10）

- 状态：已完成
- 优先级：P1
- 负责角色：Developer
- 所属层：UI 层
- 关联需求：F-01/F-02；DEC-001
- 估算：2 天
- 依赖关系：TASK-013、TASK-014、TASK-021、TASK-020
- 涉及文件：
  - `ui/sheets/TransactionSheet.kt`（记一笔）
  - `ui/sheets/DebtSheet.kt`（债务录入）
  - `ui/sheets/RepaySheet.kt`（快捷还款）
  - `ui/sheets/` 相关 ViewModel
  - `ui/components/CategoryPickerSheet.kt`（P-10 分类选择）
- 描述：
  - P-07 记一笔：类型切换（候选分类随之切 type）、金额输入（2 位小数拦截）、分类必填、日期默认今天、备注≤200 计数、编辑态含删除；保存调 Add/UpdateTransactionUseCase。
  - P-08 债务：kind 分段（借出/借入）+ repay 方向二选一卡（收到还款/我还款）；姓名必填；还款金额可超净额仅提示。
  - P-09 快捷还款：partyName 只读预填、方向自动推导、金额默认 0；插入独立成行。
  - P-10 分类选择器：入参 type/排除 id/初选值，网格单选。
- 验收标准：
  - [ ] 记一笔：插入一笔交易成功，金额元↔分正确，分类必填校验生效
  - [ ] 编辑回填正确（getById），删除有强确认
  - [ ] 债务四态录入，repay 必选方向
  - [ ] 快捷还款预填方向正确
  - [ ] 金额「0.00」/负数/超限禁保存
- 需测试覆盖点：表单校验 UI（UI 可选）。

---

### TASK-029: 账本管理页（P-11）

- 状态：已完成
- 优先级：P1
- 负责角色：Developer
- 所属层：UI 层
- 关联需求：F-11；DEC-001
- 估算：1 天
- 依赖关系：TASK-012、TASK-021、TASK-020
- 涉及文件：
  - `ui/settings/LedgerManageScreen.kt`、`ui/settings/LedgerManageViewModel.kt`
  - `ui/settings/LedgerEditorSheet.kt`（新建/编辑账本）
- 描述：
  - 账本列表（`observeAll`）当前账本行高亮；点击切换（纯状态）。
  - 新建：名称/图标/颜色必填 → `CreateLedgerUseCase`（事务克隆分类 + 自动切换）。
  - 编辑当前账本 name/icon/color。
  - 删除非默认：danger ConfirmDialog「将同时删除该账本下全部数据」→ `DeleteLedgerUseCase`。
  - 默认账本不显示删除。
- 验收标准：
  - [ ] 新建账本后自动切换、克隆 18 分类
  - [ ] 切换账本全 app Flow 重订阅
  - [ ] 默认账本无删除入口；删除非默认清数据正确
  - [ ] 至少保留一个账本
- 需测试覆盖点：账本 CRUD UI（UI 可选）。

---

### TASK-030: Phase 2 占位页（P-12~P-14）

- 状态：已完成
- 优先级：P3
- 负责角色：Developer
- 所属层：UI 层
- 关联需求：F-06/F-07/F-08（占位）
- 估算：0.5 天
- 依赖关系：TASK-021、TASK-020
- 涉及文件：
  - `ui/reserved/PlaceholderPage.kt`（C-25）
  - `ui/reserved/SearchPlaceholder.kt`、`ReportsPlaceholder.kt`、`BudgetsPlaceholder.kt`
- 描述：
  - 占位页骨架：标题 + 禁用示意 + 文案 + 返回（UI §3 P-12~P-14）。
  - route 注册但默认不可达（底部栏不展示）。
- 验收标准：
  - [ ] 三占位页可渲染，无业务交互
  - [ ] 导航注册编译通过、默认不可达
- 需测试覆盖点：无（静态占位）。

---

> Phase 2 任务拆分（Architect 产出，2026-08-21）。覆盖 F-06 搜索 / F-07 报表 / F-08 预算 + 收支页分类联表收尾；对齐 DEC-011/012/013 与 DEC-008。分层概览：
> - **搜索**（3）：数据层 TASK-031 → 业务层 TASK-032 → UI 层 TASK-033
> - **报表**（3）：数据层 TASK-034 → 组件 TASK-035 → 业务层 + UI 层 TASK-036
> - **预算**（2）：业务层 TASK-037 → UI 层 TASK-038
> - **收尾**（1）：TASK-039 收支页分类联表
> 公共约定同前（版本锁定 / ledgerId 首参 / Long 分 / 测例归属）；Phase 2 无新表无新索引（DATABASE §10），无第三方依赖（DEC-001/DEC-011）。

---

### TASK-031: 搜索数据层扩展（DAO 加参 + filter 模型 + DataSource 转发）

- 状态：已完成
- 优先级：P2
- 负责角色：Developer
- 所属层：数据层 / 领域契约层
- 关联需求：F-06；DEC-013
- 估算：0.5 天
- 依赖关系：TASK-003、TASK-006、TASK-008
- 涉及文件：
  - `domain/model/TransactionFilter.kt`（加 remark/amountMin/amountMax）
  - `domain/model/DebtFilter.kt`（加 partyName）
  - `data/local/db/dao/TransactionDao.kt`（observeFiltered 扩 3 参）
  - `data/local/db/dao/DebtDao.kt`（observeFiltered 扩 1 参）
  - `data/local/datasource/RoomDataSourceImpl.kt`（transaction/debt 两处 observeByLedger 转发新字段）
- 描述：
  - DEC-013「扩展 observeFiltered 加参，不新建 search 方法」：SQL 用既有参数化写法追加 `remark LIKE '%'||:remark||'%'`、`amount>=:amountMin`、`amount<=:amountMax`（交易）与 `partyName LIKE`（债务），见 DATABASE §10.2。
  - `TransactionFilter` 默认 7 字段全 null == 原缺省行为；`DebtFilter` 默认 3 字段全 null == 原缺省行为；`TransactionDataSource.observeByLedger`/`DebtDataSource.observeByLedger` 签名不变（Repository 零改动）。
  - 金额区间 `amountMin/amountMax` 单位「分」、含端点。
- 验收标准：
  - [ ] `observeFiltered` 加参后 SQL 与 DATABASE §10.2 一致（LIKE '%'||:remark||'%'、amount>=:amountMin、amount<=:amountMax、partyName LIKE）
  - [ ] filter 新字段可空、默认不过滤；原 null 调用结果与改前一致（回归：收支页/债务页行为不变）
  - [ ] Repository/DataSource 接口签名无变化（diff 只动 filter data class 与 DAO 实现）
  - [ ] 编译通过
- 需测试覆盖点：模糊 LIKE 与金额区间参数化正确性（instrumented，Tester）。

---

### TASK-032: 搜索业务 UseCase

- 状态：已完成
- 优先级：P2
- 负责角色：Developer
- 所属层：业务层
- 关联需求：F-06；DEC-013
- 估算：0.5 天
- 依赖关系：TASK-031、TASK-013、TASK-014
- 涉及文件：
  - `domain/usecase/search/SearchTransactionsUseCase.kt`
  - `domain/usecase/search/SearchDebtsUseCase.kt`
- 描述：
  - `SearchTransactionsUseCase(filter)`：`currentLedgerId().flatMapLatest { transactionRepository.observeByLedger(it, filter) }`，复用 `TransactionFilter` 扩展字段（remark/金额区间/分类/日期/类型）。
  - `SearchDebtsUseCase(filter)`：同径，`DebtFilter`（partyName/direction/kind）。
- 验收标准：
  - [ ] 两 UseCase 经 `LedgerProvider` 取当前账本，随切账本自动重订阅
  - [ ] 首参 `ledgerId` 贯穿（ARCHITECTURE §8.1）
  - [ ] amountMin/amountMax 语义为「分、含端点」
- 需测试覆盖点：搜索组合过滤（Fake DataSource 单测）。

---

### TASK-033: 搜索页（替换 SearchPlaceholder）+ 只读明细

- 状态：已完成
- 优先级：P2
- 负责角色：Developer
- 所属层：UI 层
- 关联需求：F-06；DEC-013
- 估算：1.5 天
- 依赖关系：TASK-032、TASK-020、TASK-021
- 涉及文件：
  - `ui/search/SearchScreen.kt`、`ui/search/SearchViewModel.kt`
  - 只读明细（`ui/search/TransactionDetail.kt` / `DebtDetail.kt`，或复用 `TransactionSheet`/`DebtSheet` 的只读态）
  - `ui/navigation/MiniBookNavHost.kt`（Search 路由替换 `SearchPlaceholder` → `SearchScreen`）
  - 首页顶部搜索入口接线（F-06 从首页顶部搜索入口进入）
- 描述：
  - 条件字段分两组：交易（备注关键词、金额 min/max、分类、日期区间、类型）+ 债务（姓名关键词、方向）；分类/日期/类型选择复用现有组件（CategoryPickerSheet/DatePickerField/TransactionTypeTab）；搜索词防抖（`debounce`）+ `flatMapLatest` 重订阅。
  - 结果分「交易 / 债务」两组、按时间倒序；点击展示**只读明细**（不可编辑）；无结果显示空态（PRD F-06）；金额区间 min > max 视为无效并提示（ViewModel 校验）。
  - 替换 `ui/reserved/SearchPlaceholder.kt` 接线（route `search` 已有，接首页入口后可达）。
- 验收标准：
  - [ ] 交易按备注模糊 + 金额区间 + 分类 + 日期区间 + 类型组合过滤可命中且互不干扰
  - [ ] 债务按姓名模糊 + 方向过滤可命中
  - [ ] 结果分两组，组内时间倒序
  - [ ] 点击弹只读明细（无保存/编辑入口）
  - [ ] 空态提示；min > max 视为无效并提示
  - [ ] 切账本搜索重跑、仅当前账本
- 需测试覆盖点：搜索 UI 流（UI 可选；核心过滤在 TASK-031/032）。

---

### TASK-034: 报表聚合数据层（DAO 3 查询 + DataSource/Repository + 报表模型）

- 状态：已完成
- 优先级：P2
- 负责角色：Developer
- 所属层：数据层 / 领域契约层
- 关联需求：F-07；DEC-011
- 估算：1 天
- 依赖关系：TASK-003、TASK-006、TASK-007、TASK-008、TASK-009
- 涉及文件：
  - `data/local/db/dao/TransactionDao.kt`（新增 observeDailyTrend / observeMonthlyTrend / observeCategoryAgg）
  - `data/local/db/QueryRow.kt`（新增 PeriodTrendRow / CategoryAggRow DTO）
  - `domain/model/PeriodTrend.kt`、`domain/model/CategoryAgg.kt`
  - `data/datasource/TransactionDataSource.kt`（新增 3 方法）
  - `data/local/datasource/RoomDataSourceImpl.kt`（实现 3 方法与行→domain 映射）
  - `domain/repository/TransactionRepository.kt` + `TransactionRepositoryImpl.kt`（新增 3 方法委托）
- 描述：
  - 三条聚合 SQL 对齐 DATABASE §10.3（趋势按天/按月、分类占比），命中 `(ledgerId, date)` / `(ledgerId, categoryId)` 索引。
  - `observeCategoryAgg` 同时供 F-07 占比与 F-08「已用」复用（`type=expense` 当月）。
  - `PeriodTrend` 派生 `balanceFen = incomeFen - expenseFen`（供结余走势，domain 层整数跑表）。
  - DataSource/Repository 方法签名对齐 API §12.2；金额 `Long` 分、返回 `Flow`。
- 验收标准：
  - [ ] 3 聚合 SQL 与 DATABASE §10.3 一致，返回 `Flow`
  - [ ] `observeDailyTrend` 月度按天、`observeMonthlyTrend` 年度按月（`substr(date,1,7)`）分组正确
  - [ ] `observeCategoryAgg` 按 type + categoryId 分组、`totalFen` 为 `Long` 分
  - [ ] DataSource/Repository 新增方法委托正确、接口与实现一致
  - [ ] 编译通过
- 需测试覆盖点：聚合正确性（收支分桶、分类占比；instrumented，Tester）。

---

### TASK-035: 报表图表组件（Compose 原生 Canvas）

- 状态：已完成
- 优先级：P2
- 负责角色：Developer
- 所属层：UI 层（组件）
- 关联需求：F-07；DEC-011
- 估算：1.5 天
- 依赖关系：TASK-019、TASK-020、TASK-034
- 涉及文件：
  - `ui/reports/components/LineChart.kt`（收支趋势/结余走势折线，drawPath/drawLine）
  - `ui/reports/components/BarChart.kt`（柱状，drawRoundRect，可选）
  - `ui/reports/components/DonutChart.kt`（环形占比，drawArc）
  - `ui/reports/components/ChartLegend.kt`（图例）
  - `ui/reports/components/ChartEmptyState.kt`（空态）
- 描述：
  - 零第三方图表库（DEC-011），按 ARCHITECTURE §15.4 图表「五要素」实现：数据口径（吃 `PeriodTrend`/`CategoryAgg`，不算术）、坐标系与刻度（时间桶 X 轴 + 分→元 Y 轴，tabular figures）、图元绘制、图例与交互、主题配色（`colorScheme` 语义 token，暗色适配）。
  - 折线/柱状 X 轴 = 天（月视图）/月（年视图）等距序列，Y 轴自适应最大刻度 + 网格线；环形 `sweep` 按占比映射 360°，收入/支出分开。
  - 空态与单点降级（1 桶/0 数据不崩，画点或占位）。
- 验收标准：
  - [ ] Canvas 自绘，无第三方图表库、无 toml/build.gradle 改动（DEC-011）
  - [ ] 折线/环形/柱状三图可渲染，色值走 token 随暗色切换
  - [ ] 单数据点 / 空数据不崩溃（空态）
  - [ ] 金额展示经 MoneyFormatter（分→元），无手写 float
- 需测试覆盖点：图表纯渲染 + 空态（UI 可选）。

---

### TASK-036: 报表页（替换 ReportsPlaceholder）+ 报表 UseCase

- 状态：已完成
- 优先级：P2
- 负责角色：Developer
- 所属层：业务层 + UI 层
- 关联需求：F-07；DEC-011
- 估算：1 天
- 依赖关系：TASK-034、TASK-035、TASK-021
- 涉及文件：
  - `domain/usecase/report/ObserveTrendUseCase.kt`、`ObserveCategoryBreakdownUseCase.kt`、`ObserveBalanceTrendUseCase.kt`
  - `ui/reports/ReportsViewModel.kt`、`ui/reports/ReportsScreen.kt`
  - `ui/navigation/MiniBookNavHost.kt`（Reports 路由替换 `ReportsPlaceholder` → `ReportsScreen`）
- 描述：
  - 三个 report UseCase（业务层，本任务一并落地）：`ObserveTrendUseCase`（按天/月）、`ObserveCategoryBreakdownUseCase`（type 入参）、`ObserveBalanceTrendUseCase`（每桶 balanceFen + 累计结余整数跑表），均为 `currentLedgerId().flatMapLatest { repository.observe... }` 薄封装。
  - 页面：月/年切换 + 三图（①收支趋势折线/柱状 ②分类占比环形收入/支出分开切换 ③结余走势折线）；统计范围限定当前账本（`flatMapLatest`）。
  - 无数据显示空态（PRD F-07）；替换占位页接线。
- 验收标准：
  - [ ] 月视图按天、年视图按月切换正确
  - [ ] 三图数据与数据源一致（金额分口径、分类占比分开展示）
  - [ ] 该账本无任何数据显示空态
  - [ ] 切账本整页重算、仅当前账本
  - [ ] 替换 ReportsPlaceholder 完成（route `reports` 可达）
- 需测试覆盖点：报表 UI 流（UI 可选；聚合正确性在 TASK-034）。

---

### TASK-037: 预算业务 UseCase + 超支口径

- 状态：已完成
- 优先级：P2
- 负责角色：Developer
- 所属层：业务层
- 关联需求：F-08；DEC-008、DEC-012
- 估算：1 天
- 依赖关系：TASK-009、TASK-034、TASK-015
- 涉及文件：
  - `domain/usecase/budget/SetTotalBudgetUseCase.kt`
  - `domain/usecase/budget/SetCategoryBudgetUseCase.kt`
  - `domain/usecase/budget/ClearCategoryBudgetUseCase.kt`
  - `domain/usecase/budget/ObserveBudgetProgressUseCase.kt`
  - `domain/model/BudgetProgress.kt`
- 描述：
  - `SetTotalBudgetUseCase`/`SetCategoryBudgetUseCase`：校验金额 ≥0 分、月份合法 → 委托 `BudgetRepository`（总额「先删后插」已在 Impl 事务内闭合，DEC-008；分类走 DB UNIQUE）。
  - `ObserveBudgetProgressUseCase(ledgerId, month)`：`combine(budgetRepository.observeByMonth, transactionRepository.observeCategoryAgg(type=expense, 当月), categories.observeByLedger)` → `Flow<BudgetProgress>`；总额已用 = 各分类 `totalFen` 之和（等价 monthSummary.expenseFen）。
  - `BudgetProgress` 派生 `remainingFen`（预算−已用）、`overBudget`（已用>预算）、`hasOverBudget`（总额或任一分类超支），口径 = PRD F-08（已用/剩余/超支）。
  - 超支提醒形态 = App 内告警（DEC-012），本任务只算 `hasOverBudget`，展示在 TASK-038。
- 验收标准：
  - [ ] 总额/分类预算写入口径与 DEC-008 一致（总额先删后插，分类 UNIQUE）
  - [ ] 已用 = 当月支出累计（分）；剩余 = 预算 − 已用；超支 = 已用 > 预算
  - [ ] `hasOverBudget` 在总额或任一分类超支时为 true
  - [ ] 金额 ≤0 / 未选月份拒绝保存（PRD F-08 异常处理）
  - [ ] Flow 随交易/预算/账本变化自动重算
- 需测试覆盖点：**预算超支口径单测**（重点，总额/分类/剩余/超支边界、已用与 monthSummary 等价性；Fake DataSource）。

---

### TASK-038: 预算页（替换 BudgetsPlaceholder）+ 超支页内告警与入口提示

- 状态：已完成
- 优先级：P2
- 负责角色：Developer
- 所属层：UI 层
- 关联需求：F-08；DEC-012
- 估算：1.5 天
- 依赖关系：TASK-037、TASK-020、TASK-021、TASK-023
- 涉及文件：
  - `ui/budgets/BudgetsViewModel.kt`、`ui/budgets/BudgetsScreen.kt`
  - `ui/budgets/BudgetEditorSheet.kt`（总预算/分类预算编辑）
  - `ui/home/HomeScreen.kt`（首页/入口超支提示标记，可选增强）
  - `ui/navigation/MiniBookNavHost.kt`（Budgets 路由替换 `BudgetsPlaceholder` → `BudgetsScreen`）
- 描述：
  - 月度总预算 + 分类预算两区；进度条展示已用/剩余；**超支标红 + 超支文案**（DEC-012 页内告警）。
  - 编辑 sheet：总额（先删后插）与分类（UNIQUE）设置；金额元↔分经 MoneyFormatter。
  - 首页/入口提示（可选增强）：当前月 `hasOverBudget` → 入口展示醒目标记，点击进预算页；不引入通知栏/`POST_NOTIFICATIONS`。
  - 替换占位页接线。
- 验收标准：
  - [ ] 总预算与分类预算进度「已用/剩余」正确展示
  - [ ] 超支时进度条标红 + 超支文案（页内告警）
  - [ ] 编辑保存/删除生效（总额先删后插、分类 UNIQUE）
  - [ ] 首页入口提示在超支月展示、未超支隐藏（若有）
  - [ ] 无 POST_NOTIFICATIONS 申请、无通知栏（DEC-012）
  - [ ] 切账本/切月份重算，仅当前账本
- 需测试覆盖点：预算页 UI 流（UI 可选；口径在 TASK-037）。

---

### TASK-039: 收支页分类联表收尾

- 状态：已完成
- 优先级：P2
- 负责角色：Developer
- 所属层：UI 层
- 关联需求：F-01（展示）；DEC-001
- 估算：0.5 天
- 依赖关系：TASK-015、TASK-024
- 涉及文件：
  - `ui/transactions/TransactionsViewModel.kt`
  - `ui/transactions/TransactionsScreen.kt`
- 描述：
  - 收尾 `TransactionsScreen.kt` 分类联表 TODO（现 `categoryName/categoryIcon/categoryColor` 传 null 走 ❔/outline fallback）：ViewModel 注入 `ObserveCategoriesUseCase`（TASK-015），`TransactionsUiState.Success` 携带 `categoriesById: Map<String, Category>`，页面按 `tx.categoryId` 取真实分类名/图标/色传入 `TransactionListItem`。
  - 对齐首页同类收尾（`HomeViewModel` 注入 ObserveCategoriesUseCase 的既有模式，协调日志 2026-08-21 commit 0c041a3）。
- 验收标准：
  - [ ] 收支列表每条交易显示真实分类名/图标/色（无 ❔ fallback）
  - [ ] 分类信息随分类改名/换色实时联动（同源 `observeByLedger` 流）
  - [ ] 分类已删（边缘态）不崩溃，回退 id 或空占位
  - [ ] 复用 `ObserveCategoriesUseCase`，不新增查询/不扩展 DAO
- 需测试覆盖点：分类名映射（UI 可选）。

---

> **F-12 账单导入任务拆分**（Architect 产出，2026-08-24）。覆盖微信/支付宝账单导入 + 自动分类 + 幂等去重；对齐 DEC-015/016/017/018/019（详见 02_architecture §16 / 03_database §4.1·§7.5 / 04_api §13）。分层概览：
> - **数据层 migration/模型**（1）：TASK-040
> - **解析器**（2）：TASK-041 支付宝 CSV、TASK-042 微信 XLSX
> - **UseCase**（1）：TASK-043 编排 + 自动分类 + 数据访问增量
> - **UI**（1）：TASK-044 入口/提示/文件选择/结果
> 公共约定同前（版本锁定 / ledgerId 首参 / Long 分 / 测例归属）；**零第三方依赖**（DEC-016 手动解析 XLSX）；**首个 DB 迁移 1→2**（DEC-018），`fallbackToDestructiveMigration(false)` 不变。

---

### TASK-040: F-12 数据层 migration + 模型四层贯通（DB version 2 / 4 新列 / 唯一索引）

- 状态：已完成
- 优先级：P2
- 负责角色：Developer
- 所属层：数据层 / 领域契约层
- 关联需求：F-12；DEC-018
- 估算：1 天
- 依赖关系：TASK-002、TASK-003、TASK-006、TASK-008
- 涉及文件：
  - `data/local/db/entity/TransactionEntity.kt`（加 4 字段 + 唯一索引 + `ColumnInfo`）
  - `data/local/db/MiniBookDatabase.kt`（`version = 2` + `MIGRATION_1_2`）
  - `domain/model/Transaction.kt`（加 4 字段）
  - `domain/model/Enums.kt`（加 `TransactionSource`）
  - `data/mapper/TransactionMapper.kt`（4 字段互转）
  - `data/local/datasource/RoomDataSourceImpl.kt`（`Cursor.toTransactionEntity` 补读新列）
- 描述：
  - DB version 1→2 + `MIGRATION_1_2`，SQL 逐字对齐 03_database §7.5：4 条 `ADD COLUMN`（`externalId TEXT`/`counterparty TEXT`/`paymentMethod TEXT`/`source TEXT NOT NULL DEFAULT 'manual'`）+ `CREATE UNIQUE INDEX index_transactions_ledgerId_externalId ON transactions(ledgerId, externalId)`。
  - `TransactionEntity` 加 `externalId/counterparty/paymentMethod`（可空，默认 null）+ `source`（默认 `"manual"`，`@ColumnInfo(defaultValue = "'manual'")`），`@Index(value=["ledgerId","externalId"], unique=true, name="index_transactions_ledgerId_externalId")`。
  - `Transaction` 领域模型加同名字段（`source: TransactionSource = MANUAL`）；`Enums.kt` 加 `TransactionSource{ MANUAL/wechat_import/alipay_import }`；mapper 双向转码（source 同 type 以 value 串落盘）。
  - `RoomDataSourceImpl.getById` 的 Cursor 映射补读 4 新列（否则回读丢列）。
  - 手动记账路径零改动：domain 默认值 + entity 默认值即保证 `source=manual`、三列 null（`AddTransactionUseCase` 不感知新列）。
- 验收标准：
  - [ ] `MIGRATION_1_2` SQL 与 03_database §7.5 逐字一致（索引名 `index_transactions_ledgerId_externalId` 与实体注解一致）
  - [ ] `exportSchema=true` 编译导出 `2.json`；`fallbackToDestructiveMigration(false)` 不变
  - [ ] 既有手动记账编译通过、行为不变（source=manual，其余三列 null）
  - [ ] mapper 往返含 4 新字段不丢（source 枚举 value 串一致）
  - [ ] `getById` 回读 4 新列正确
- 需测试覆盖点：迁移往返（instrumented，MigrationTestHelper：version 1 旧库升级、新列可读、唯一索引生效）。

---

### TASK-041: F-12 解析器抽象 + 支付宝 CSV 解析器

- 状态：已完成
- 优先级：P2
- 负责角色：Developer
- 所属层：业务层（解析器，纯 Kotlin）
- 关联需求：F-12；DEC-015、DEC-017
- 估算：1 天
- 依赖关系：TASK-040、TASK-010
- 涉及文件：
  - `domain/model/ParsedBillRow.kt`、`domain/model/BillParseResult.kt`
  - `domain/usecase/import/BillParser.kt`、`BillSourceInput.kt`
  - `domain/model/Enums.kt`（加 `ImportSkipReason`，或并入 TASK-040 一并落）
  - `data/local/import/AlipayCsvParser.kt`
  - `data/local/import/BillParserFactory.kt`（骨架：仅 CSV 分支，TASK-042 补 XLSX）
- 描述：
  - 定义统一中间模型 `ParsedBillRow`+`BillParseResult(Success/Unrecognized)`+`ParseSkippedRow(rowNumber, reason, detail)` 与 `ImportSkipReason{ EXISTS/NEUTRAL/CLOSED/PARSE_ERROR }`，字段对齐 PRD §F-12 字段映射表（02_architecture §16.3）。
  - `BillParser` 接口（`format + parse(BillSourceInput)`）+ `BillSourceInput{ Csv(Reader)/Xlsx(File) }`（纯 Kotlin，JVM 可测）。
  - `AlipayCsvParser`：`InputStreamReader(GBK)`；动态表头探测（逐行至同含「交易时间」「收/支」及「金额/金额(元)」记列索引）；字段尾 `\t` trim；金额 `MoneyFormatter.toFen`（非法/0/负 → PARSE_ERROR）；`收/支` → expense/income，不计收支/「/」/空 → NEUTRAL、交易状态「交易关闭」→ CLOSED；`externalId=alipay:<交易订单号>`（单号空→PARSE_ERROR）；`description` = 商品说明 + 备注拼接（DEC-015）。
  - 工厂按 `resolve(displayName, magicBytes)` 分发 CSV 分支。
- 验收标准：
  - [ ] GBK 解码中文正确；前导说明行增减不影响表头定位
  - [ ] `\t` 尾随被 trim（交易订单号/商家订单号）
  - [ ] 金额 8.70→870、6.5→650（`MoneyFormatter.toFen`）；0/负/非数字 → PARSE_ERROR 计数
  - [ ] 过滤规则计数正确（NEUTRAL/CLOSED/PARSE_ERROR 分原因，含行号）
  - [ ] `externalId` 前缀 `alipay:`、空单号计 PARSE_ERROR
- 需测试覆盖点：CSV fixture（造小样本）、动态表头、金额转分、过滤计数（JVM，tests/unit，重点）。

---

### TASK-042: F-12 微信 XLSX 解析器（手动 zip+XML）

- 状态：已完成
- 优先级：P2
- 负责角色：Developer
- 所属层：业务层（解析器，纯 Kotlin）
- 关联需求：F-12；DEC-016
- 估算：1.5 天
- 依赖关系：TASK-041
- 涉及文件：
  - `data/local/import/WechatXlsxParser.kt`
  - `data/local/import/BillParserFactory.kt`（补 XLSX 分支 + 内容嗅探）
  - `app/src/test/resources` 或代码内构造 `.xlsx` fixture（zip：`[Content_Types].xml`+`xl/sharedStrings.xml`+`xl/worksheets/sheet1.xml`）
- 描述：
  - `ZipFile(file)` 读 `xl/sharedStrings.xml`（`<si>` 顺序建 `List<String>`，拼接富文本 `<r><t>`，保留 `xml:space`）+ `xl/worksheets/sheet1.xml`（`XmlPullParser` localName 匹配）。
  - cell 类型兼容 `t="s"`（`<v>` 下标反查 sharedStrings）/`t="inlineStr"`（`<is><t>`）/`t="str"`；列定位按 `r` 前缀列字母，稀疏 cell 不假设等量。
  - 表头行动态探测（交易时间/收/支/金额(元)/交易对方/收付款方式/商品/备注/交易单号/交易类型 等关键词记列偏移）。
  - 日期序列数：`substringBefore('.')` → `LocalDate.of(1899,12,30).plusDays(n)` 得 `yyyy-MM-dd`，禁 Double；金额 `<v>` 字面量 → `MoneyFormatter.toFen`；`收/支` 中性「/」/空 → NEUTRAL。
  - `externalId=wechat:<交易单号>`；`source=wechat_import` 由 UseCase 装配（本任务只出 ParsedBillRow）。
- 验收标准：
  - [ ] sharedStrings 反查正确（含富文本拼接、`xml:space` 空格）；inlineStr 兼容
  - [ ] 日期序列数换算正确（如 46257 → 2026-08-… 仅取日期）；禁 Double（代码审查点）
  - [ ] 金额转分无精度损失；列定位/稀疏 cell 正确
  - [ ] 表头动态探测不依赖列序（换列仍命中）
  - [ ] 「当前状态/对方账号/商户单号」不落库（不产出 field）
- 需测试覆盖点：构造 `.xlsx` fixture 单测（sharedStrings/inlineStr/日期/金额/稀疏 cell；JVM 写临时 zip）。

---

### TASK-043: F-12 数据访问增量 + ImportBillUseCase + 自动分类

- 状态：已完成
- 优先级：P2
- 负责角色：Developer
- 所属层：业务层 + 数据层（增量）
- 关联需求：F-12；DEC-015、DEC-019
- 估算：1.5 天
- 依赖关系：TASK-041、TASK-042、TASK-009、TASK-012
- 涉及文件：
  - `data/local/db/dao/TransactionDao.kt`（`insertAll(IGNORE)` + `getAllExternalIds`）
  - `data/datasource/TransactionDataSource.kt`（`insertBatch` + `getExistingExternalIds`）
  - `data/local/datasource/RoomDataSourceImpl.kt`（实现两方法）
  - `domain/repository/TransactionRepository.kt` + `TransactionRepositoryImpl.kt`（委托两方法）
  - `domain/model/BillCategoryMapping.kt`、`domain/model/BillImportResult.kt`
  - `domain/usecase/import/BillFilePreparer.kt`（域接口）+ `data/local/import/SafBillFilePreparer.kt`（Android 实现）
  - `domain/usecase/import/ImportBillUseCase.kt`
- 描述：
  - `TransactionDao.insertAll(@Insert(onConflict = IGNORE)): List<Long>`（`-1`=冲突忽略）+ `getAllExternalIds(ledgerId)`；DataSource/Repository 增 `insertBatch`（返回实插数）/`getExistingExternalIds`（Set）。
  - `BillFilePreparer.prepare(uriString): PreparedBillFile`（域接口）；`SafBillFilePreparer`（数据层 Android）：XLSX 落 `cacheDir/minibook_import_<uuid>.xlsx` 再 `ZipFile`、CSV 直接 GBK Reader；`close()` 关流 + 删 tempFile（DEC-017）。
  - `BillCategoryMapping`：键 `(platform, platformCategory, type) → 内置分类名`，与 PRD §F-12 表逐行一致，未列入 → null（DEC-019）。
  - `ImportBillUseCase.invoke(uriString, ledgerId): BillImportResult`（`withContext(IO)`）：preparer.prepare().use → factory.resolve → parser.parse → 快照分类建 `(type,name)→categoryId` map → 逐行装配 `Transaction`（UUID + source + 4 新列 + 自动分类，兜底「其他」；「其他」亦缺→整体 Failure）→ `transactionRunner.runInTransaction { 预查既有 externalId → 去重 → insertBatch → 补 -1 计数 }` → 聚合 `BillImportResult`。过滤行级、写入整批单事务（DEC-015）。
  - `parseErrors: List<ParseSkippedRow>` 携带行号+原因；`unrecognized` 标识未识别格式（零落库）。
- 验收标准：
  - [ ] `insertAll` IGNORE 冲突返回 `-1` 且不落重复行；`insertBatch` 实插数正确
  - [ ] 幂等：重复导入同一账单，已存在行全部跳过（计 existing），无重复行；手动记录（externalId=null）不受影响
  - [ ] 自动分类：PRD 映射表逐行正确；未映射归该 type「其他」；预填可事后改
  - [ ] 事务原子性：注入写入异常 → 整体回滚零落库（复用 F-04 原子口径）
  - [ ] 临时文件 finally 清理；未识别格式/全部跳过 → 正确反馈，零落库
  - [ ] UseCase 不 import android.*（uri 走 String，可 JVM 单测）
- 需测试覆盖点：ImportBillUseCase 聚合（Fake DataSource：各跳过原因计数、文件内重复、外部冲突、IGNORE -1 聚合、快照分类兜底）、BillCategoryMapping 映射（JVM）。

---

### TASK-044: F-12 UI 接线（入口 / 提示页 / 文件选择 / 结果反馈）

- 状态：已完成
- 优先级：P2
- 负责角色：Developer
- 所属层：UI 层
- 关联需求：F-12；DEC-015
- 估算：1.5 天
- 依赖关系：TASK-043、TASK-020、TASK-021、TASK-024
- 涉及文件：
  - `ui/navigation/Destinations.kt`（`data object Import : Destination("import")`）
  - `ui/navigation/MiniBookNavHost.kt`（注册根图 `import` 全屏页，顶栏返回）
  - `ui/import/ImportScreen.kt`、`ui/import/ImportViewModel.kt`
  - `ui/home/HomeScreen.kt` 或 `ui/transactions/TransactionsScreen.kt`（入口接线，位置 Designer 定）
  - `ui/components/TransactionListItem.kt`（`source` 中文标签展示：手动记录/微信/支付宝）
- 描述：
  - `ImportViewModel` 状态机 `ImportUiState = Idle | Busy | Result(summary) | Error(msg)`；事件 `pickFile()`/`onFilePicked(uri)`/`retry()`/`done()`；SAF `ActivityResultContracts.OpenDocument`（`application/vnd.ms-excel`+`text/csv`+`*/*`），**不唤起第三方 App**。
  - 提示页（Idle）：宣导微信/支付宝手动导出路径（PRD §F-12 入口层级）+「选择账单文件」。
  - Busy：`FullScreenBusyOverlay` 锁数据入口（禁返回/禁 Tab，对齐 F-04 导入交互）。
  - Result：一次性反馈「成功 N / 跳过 M（分原因：已存在/不计收支/交易关闭）」，解析失败逐条行号+原因；全部跳过提示「本次无新增交易」；Error：未识别格式/文件损坏/事务回滚。
  - 入口：首页或收支页顶栏「导入账单」（具体位置 Designer 定）+ 设置「数据」区可选。
  - `source` 列表行展示中文标签（可选增强：按 source 筛选可后续扩 `TransactionFilter`，DEC-013 同径，本期可不做）。
- 验收标准：
  - [ ] `import` 路由可达、返回正常；提示页展示两平台导出路径
  - [ ] 文件选择器只选文件、不跳第三方 App；选后可触发解析
  - [ ] Busy 期间锁入口；Result 分原因计数正确、解析失败给行号
  - [ ] 错误态（未识别/损坏/回滚）提示且不落库
  - [ ] 切账本导入落入当前账本（`ledgerId` 贯穿）
- 需测试覆盖点：ImportViewModel 状态机（JVM，Fake UseCase）；SAF/真账单走真机（Tester）。

---

> **F-13 邮件账单自动导入任务拆分**（Architect 产出，2026-09-23）。覆盖 QQ 邮箱取件 + 加密码 zip 自动解密 + 复用 F-12 导入链路；对齐 DEC-022/023（设计详见 `12_mail_bill_import.md`）。分层概览：
> - **凭据与配置**（1）：TASK-045 凭据存储 + 设置页邮箱配置
> - **取件**（2）：TASK-046 IMAP 客户端与本地过滤、TASK-047 附件/链接取件与文件名解码
> - **解密**（1）：TASK-048 ZipCrypto 口令穷举 + 解密解压
> - **编排**（1）：TASK-049 MailBillImportUseCase + 导入入口重载
> - **UI 与验收**（1）：TASK-050 一键收取 UI + 真机端到端
> 公共约定同前（版本锁定 / ledgerId 首参 / Long 分 / 测例归属）。**新增依赖仅 JavaMail（约 1 MB）+ INTERNET 权限；不引入 zip4j**（DEC-022 第 5/11 条）。**禁止依赖 IMAP 服务端 SEARCH**（DEC-022 第 2 条）。

---

### TASK-045: F-13 邮箱凭据存储 + 设置页「邮箱账单」配置

- 状态：已完成（本地构建 + 全量单测绿；真机验收待设备）
- 优先级：P2
- 负责角色：Developer
- 所属层：数据层 + UI 层
- 关联需求：F-13；DEC-022、DEC-023
- 估算：1 天
- 依赖关系：TASK-004（KeyManager）、TASK-005（DataStore）、TASK-021
- 涉及文件（按实际落地修正）：
  - `data/local/security/MailCredentialCipher.kt`（新增：授权码 Keystore 加解密，独立别名 `minibook_mail_credential_key`，与 master key 体系严格隔离）
  - `data/local/mail/MailAccountStore.kt`（`@Singleton @Inject constructor`，**无需 Hilt Module**——原计划的 `di/MailModule.kt` 未创建）
  - `ui/mail/MailSettingsScreen.kt`、`ui/mail/MailSettingsViewModel.kt`
  - `ui/navigation/Destinations.kt`（`data object MailAccount : Destination("mail_account")`）+ `ui/navigation/MiniBookNavHost.kt`（路由注册 + 两处接线）
  - `ui/settings/SettingsScreen.kt`（「数据」区增设「邮箱账单」`SettingRow`）
- 描述：
  - `MailAccountStore`：`email: Flow<String?>`、`isConfigured: Flow<Boolean>`、`suspend save(email, authCode)`、`suspend getAuthCode(): String?`、`suspend clear()`。邮箱地址可明文落 DataStore；**授权码必须经 Android Keystore（AES-256/GCM）加密**后落私有目录（复用 `KeyManager` 的基座模式，独立 alias，不与 DB master key 混用）。
  - 设置页：邮箱地址输入 + 授权码掩码输入（可切显示）+ 保存 + 「清除邮箱凭据」+ 文案指引（QQ 邮箱网页版「设置→账号→IMAP/SMTP服务」生成 16 位授权码，**强调需开启 IMAP/SMTP 而非仅 POP3**）。
  - 空/格式非法（非邮箱格式、授权码非 16 位）拒绝保存并给提示。
- 实施记录：`MailCredentialCipher`（AES-256/GCM、12B IV + 128bit tag、`setUserAuthenticationRequired(false)`、懒创建、`Base64(IV||ct)`、异常 message 不含明文）+ `MailAccountStore`（落盘 `noBackupFilesDir/minibook_mail_account.txt` + `minibook_mail_auth.bin`，构造时同步读盘初始化 StateFlow，16 位授权码校验，`getAuthCode()` 解密失败防御性返回 null）+ `MailSettingsViewModel`/`MailSettingsScreen`（表单态/已配置态/清除 ConfirmDialog danger）+ `mail_account` 路由与设置入口接线。本任务**无 JVM 单测**（Store 依赖 `android.content.Context`、Cipher 依赖真 Keystore，项目无 Robolectric/Mockito 且未新增依赖）；落盘加密语义待真机复核。
- 验收标准：
  - [x] 授权码不以明文出现在任何落盘文件中（代码审查：`save()` 先 `encrypt()` 后落盘，全库无明文授权码写入路径；落盘文件检索复核待真机）
  - [x] 授权码/邮箱不出现在日志（代码审查：`MailAccountStore`/`MailSettingsViewModel`/`MailCredentialCipher` 不持有 Log 工具，异常文案为固定中文）
  - [ ] `mail_account` 路由可达、返回正常；配置后设置项显示「已配置」（待真机）
  - [ ] 「清除凭据」后 `isConfigured=false` 且密文被抹除（待真机）
  - [ ] 未配置时「一键收取」入口反馈正确（待真机；实现为引导态而非按钮禁用，见 TASK-050 实施记录）
- 需测试覆盖点：`MailAccountStore` 存取往返与 `clear()`（JVM，Keystore 以替身隔离）；ViewModel 表单校验（JVM）。（实际：因 Context/Keystore 依赖无 JVM 替身，未落 JVM 单测，属已知限制，见 04_api §14.9）

---

### TASK-046: F-13 IMAP 客户端与账单邮件本地过滤

- 状态：已完成（本地构建 + 全量单测绿；真机验收待设备）
- 优先级：P2
- 负责角色：Developer
- 所属层：数据层
- 关联需求：F-13；DEC-022（第 1/2 条）
- 估算：1.5 天
- 依赖关系：TASK-045
- 涉及文件（按实际落地修正）：
  - `gradle/libs.versions.toml` + `app/build.gradle.kts`（JavaMail 1.6.7 两个 library + `packaging` 元数据排除块 + R8 keep 规则在 `app/proguard-rules.pro`）
  - `app/src/main/AndroidManifest.xml`（`android.permission.INTERNET`，项目首个权限）
  - `data/local/mail/ImapMailClient.kt`、`data/local/mail/MailBillMatcher.kt`（含 `MailAuthException` / `MailNetworkException`）
  - `domain/model/MailModels.kt`（`BillPlatform`/`MailBillHeader`/`MailBillMail`——原计划的 `MailBillCandidate.kt` 未采用该命名）
- 描述：
  - `ImapMailClient`：`imap.qq.com:993` + SSL；连接后**显式发送 `ID` 命令**（`mail.imap.id` 属性 + `IMAPStore.id(map)` 双保险）；`Folder.READ_ONLY` 打开 INBOX；用 `FetchProfile` 只取过滤所需头（`FROM`/`SUBJECT`/`Date`）与结构，命中后才取正文/附件；超时可控（连接/读各 ~20~30 s）。
  - `MailBillMatcher`：**本地过滤**——支付宝 `FROM service@mail.alipay.com` 且主题含「支付宝交易流水明细」；微信 `FROM wechatpay@tencent.com` 且主题含「微信支付-账单流水文件」。按时间窗口（默认 30 天）与最大封数上限裁剪。
  - 时间字段用服务端 `Date`（`sentDate`）做窗口判断，**不依赖服务端 SEARCH**。
  - 异常分类明确：认证失败（`AuthenticationFailedException`）→「登录失败（请确认已开启 IMAP/SMTP 服务、授权码为最新）」；网络/超时 →「网络异常」。
- 实施记录：`ImapMailClient.fetchBillMails(email, authCode, sinceDays=30)`（ENVELOPE+FLAGS 头部拉取 → 本地过滤 → 命中取件；正文 plain+html 两路拼接、`stripHtml` 先保留 href/src URL 再去标签——微信链接不丢的修正）；时间窗口 sentDate 回退 receivedDate、皆空视为在窗口内；30s 双超时；`MailBillMatcher` 发件人+主题同时命中（大小写不敏感、容忍显示名形态）。测试：`MailBillMatcherTest` **9 例**（JVM 全绿）；`ImapMailClient` 本身无 JVM 单测（JavaMail 无官方假服务器，需 greenmail 等新依赖，未引入）。
- 验收标准：
  - [ ] 配置正确凭据后可连接并列出收件箱邮件数与命中账单邮件数（待真机）
  - [x] **不发送任何 IMAP `SEARCH` 命令**（代码审查：全库无 `folder.search(...)` 调用，过滤在 `MailBillMatcher` 本地完成）
  - [ ] 邮箱**只读**：收取前后邮件数、`\Seen` 状态、所在文件夹均不变（待真机核对；代码侧已 `READ_ONLY` + `close(false)`、无 flag 写操作）
  - [ ] 凭据错误/网络不可达分别给出**不同**的明确提示，且提示中不含凭据（文案已实现：`MailAuthException`/`MailNetworkException` 固定中文；真机触发路径待设备）
  - [ ] 连接具备超时，不出现永久挂起（连接/读超时各 30s 已配置；待真机）
- 需测试覆盖点：`MailBillMatcher` 过滤规则（JVM，含正例/反例/边界：发件人大小写、主题变体、超期邮件）——已落 9 例；IMAP 连通性与只读性走真机（Tester）。

---

### TASK-047: F-13 取件（支付宝附件 / 微信链接下载 + RFC2047 文件名解码）

- 状态：已完成（本地构建 + 全量单测绿；真实下载待真机/受限网络）
- 优先级：P2
- 负责角色：Developer
- 所属层：数据层
- 关联需求：F-13；DEC-022（第 3/4 条）
- 估算：2 天
- 依赖关系：TASK-046
- 涉及文件：
  - `data/local/mail/MailFilenameDecoder.kt`
  - `data/local/mail/WechatBillLinkExtractor.kt`
  - `data/local/mail/MailBillDownloader.kt`（含 `ContentDispositionParser` object、`BillDownloadException`、`DownloadedFile`）
  - `data/local/mail/ImapMailClient.kt`（补「取附件字节」与「取正文文本」——已并入 TASK-046 的 walkPart/ExtractedContent）
- 描述：
  - **支付宝**：从邮件取 `APPLICATION/OCTET-STREAM` 附件字节；文件名走 `MailFilenameDecoder`——处理 `=?GB18030?Q?…?=` / `=?GB18030?B?…?=`（RFC2047）与 `name*0=`/`name*1=` 续行拼接（RFC2231），解出可读中文名（如 `支付宝交易明细(20260619-20260919).zip`）。
  - **微信**：从 `text/plain` + `text/html` 正文提取 `https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?…` 链接（HTML 实体 `&amp;` 反转义、`href` 与裸链双路）；`MailBillDownloader` 走 HTTPS GET（跟随重定向 ≤5 跳、带浏览器 UA、超时），从 `Content-Disposition` 或兜底名确定 zip 文件名。
  - 取件产物落 `cacheDir/mail_bill_<uuid>/`，返回 `File` 给上层；**上层负责清理**（`finally`）。
  - 链接过期/失效（非 200 或返回 HTML 错误页）→ 明确区分「链接已过期（>7 天），请在微信重新申请」。
- 实施记录：`MailFilenameDecoder.decode/joinRfc2231Parts`（RFC2231 续行直接相接 + RFC2047 Q/B + 相邻 encoded-word 空白丢弃 + 失败原样返回）；`WechatBillLinkExtractor.extract`（host 精确 `tenpay.wechatpay.cn`（可带端口）+ 固定路径前缀匹配、`&amp;` 反转义、不被图片链接干扰）；`MailBillDownloader.download`（每跳强制 https、手动重定向 ≤5、30s 双超时、浏览器 UA、非 200 折叠为「链接失效」文案；异常不含 URL/token）；`ContentDispositionParser.parse`（RFC5987 `filename*` 优先、回退 `filename` 含 `%XX` 解码）。
  - **实现差异**：取件产物**不落盘、以 `ByteArray` 返回**（`DownloadedFile(fileName, bytes)`）；落盘（`cacheDir` 临时文件）与清理由 TASK-049 编排层统一负责，与「上层负责清理」语义一致。
  - 测试：`MailFilenameDecoderTest` **16 例** + `WechatBillLinkExtractorTest` **9 例** + `ContentDispositionParserTest` **6 例**（共 31 例 JVM 全绿）；`MailBillDownloader` 网络路径无 JVM 单测。
- 验收标准：
  - [x] RFC2047 中文附件名解码正确（GB18030 的 Q/B 两种编码均覆盖；`name*N` 续行正确拼接）（JVM 单测 16 例）
  - [ ] 支付宝邮件附件能落成可用的 zip 文件，字节与邮件附件一致（待真机；本地过滤/附件 mime 命中已实现并经真机 spike 验证过协议路径）
  - [x] 微信邮件能提取到下载链接；无链接/多链接时取账单下载域名那条（JVM 单测 9 例，含图片链接干扰用例）
  - [ ] 微信链接下载成功得到 `application/zip`，非 200/超时/过期分别给明确提示（待真机/受限网络；文案与分支已实现）
  - [x] 下载产物只落 `cacheDir`，失败路径也清理（代码审查：下载器只回内存字节；落盘/清理统一在编排层 `try/finally`，见 TASK-049）
- 需测试覆盖点：`MailFilenameDecoder` / `WechatBillLinkExtractor` / `ContentDispositionParser`（JVM）——已落 31 例；真实下载走真机/受限网络（Tester）。

---

### TASK-048: F-13 ZipCrypto 口令穷举 + 解密解压（纯 Kotlin，含已知答案自校验）

- 状态：已完成（本地构建 + 全量单测绿；真实账单验收已由临时测试实跑确认）
- 优先级：P2
- 负责角色：Developer
- 所属层：业务层（纯 Kotlin，可 JVM 单测）
- 关联需求：F-13；DEC-022（第 5/6/7/8 条）
- 估算：2 天
- 依赖关系：TASK-010
- 涉及文件（按实际落地补充）：
  - `domain/usecase/bill/zip/ZipCrypto.kt`（密钥派生 + 流解密核心算法）
  - `domain/usecase/bill/zip/ZipArchiveInspector.kt`（加密类型判定 + 手写 zip 结构解析 `ZipStructureReader`）
  - `domain/usecase/bill/zip/ZipPasswordCracker.kt`
  - `domain/usecase/bill/zip/EncryptedZipExtractor.kt`
  - `app/src/test/.../zip/ZipCryptoTest.kt`（含**已知口令自造 zip fixture**）、`ZipPasswordCrackerTest.kt`
- 描述：
  - **先探测加密类型**：解析 zip 中央目录/本地头，读 `method` 与 general purpose flag；命中 WinZip AES（`method=99` 或存在 `0x9901` 扩展字段）→ **直接返回「不支持自动解密」**，禁止盲目穷举。
  - **口令穷举（两段式，DEC-022 第 7 条为准）**：
    ① 快筛：候选口令派生密钥 → 解 12 字节加密头 → 取第 12 字节，**宽松接受 `CRC32 高字节` 或 `DOS 时间高字节`**；
    ② 权威判定：整包解密 → `Inflater(true)` 解压 → 对**解压后内容**算 `CRC32` 与中央目录记录值比对。**严禁拿压缩数据算 CRC**。
  - 性能：预计算前 3 位密钥状态（1000 状态）+ 后 3 位循环；多核并行（`Dispatchers.Default`）；支持协程取消与硬超时。
  - 命中后用 `EncryptedZipExtractor.extract(zip, password, destDir)` 解出账单文件（处理中文名；文件名优先取 `Content-Disposition`/中央目录名并解码）。
  - 穷举失败 → 返回 null，上层降级为「手动输入口令」（复用同一 extract 路径）。
- 实施记录：`ZipEncryptionInfo`（`ZipCrypto` / `Unsupported(reason)`，AES 以 method 99 **或** 中央目录/本地头 extra `0x9901` 判定）；`ZipPasswordCracker.crack(zipFile, digits=6..6, charset="0123456789", onProgress)` 两段式 + 前 `len/2` 位密钥状态预计算 + `availableProcessors()` 分片并发 + 4096 批次取消检查；`EncryptedZipExtractor.extract`（校验字节宽松校验 + raw deflate/stored + UTF-8/GBK/CP437 条目名解码 + 基名防穿越）。
  - **真实账单验收**（协调者以临时测试实跑后删除该测试）：三份真实账单 `inspect` 均判 `ZipCrypto`、穷举**全部命中 6 位数字口令**（单份 90~760 ms，多核亚秒级）、解出字节与独立 Python oracle 完全一致。
  - **降级实现差异（如实）**：穷举失败当前降级为**记录 `MailItemFailure.PasswordNotFound` 并在结果页提示**，**手动输入口令 UI 未实现**（复用同一 extract 路径的组件已具备，UI 属后续增强，DEC-022 第 8 条已同步修订）。
  - 测试：`ZipCryptoTest` **12 例**（已知答案向量、deflate/stored 正误口令、DOS 时间校验字节、GBK 中文名、inspect 五分支）+ `ZipPasswordCrackerTest` **6 例**（命中/时间字节/stored/未命中/未加密/进度回调）= **18 例 JVM 全绿**。
- 验收标准：
  - [x] **已知答案自校验（强制）**：用自造「已知口令 zip」验证穷举能命中该口令（`ZipCryptoTest`/`ZipPasswordCrackerTest` 以 Python zip 口径自造已知口令 fixture 实现交叉验证，DEC-022 附则）
  - [x] 6 位数字穷举在真实账单 zip 上命中口令并解出文件，内容与手动解压一致（临时验收测试实跑：三份真实账单全部命中，解出字节与独立 Python oracle 完全一致，穷举亚秒级；测试本体已按约定删除）
  - [x] 快筛宽松择优：模拟 data descriptor 场景下正确口令不被误杀（单测「穷举命中 - 校验字节为 DOS 时间高字节」「校验字节为 DOS 时间高字节 - 仍能正确解出」）
  - [x] AES 加密 zip → 明确「不支持自动解密」，不进入穷举（单测 inspect method 99 / extra 0x9901 两分支）
  - [x] 穷举可取消（实现：每 4096 候选 `ensureActive`；单测覆盖进度回调与尝试数有界，**取消路径本身无直接单测**，属已知限制）；不 import `android.*`（JVM 可测）（代码审查）
  - [x] 解压产物文件名中文正确（单测「GBK 文件名 - 无 UTF8 标志也能解出中文名」）
- 需测试覆盖点：ZipCrypto 已知向量、快筛/权威两段判定、AES 拒绝、穷举命中与未命中、取消与超时（JVM，重点）——已落 18 例（取消直接单测缺，见上）。

---

### TASK-049: F-13 MailBillImportUseCase 编排 + 导入入口重载

- 状态：已完成（本地构建 + 全量单测绿；真机端到端待设备）
- 优先级：P2
- 负责角色：Developer
- 所属层：业务层
- 关联需求：F-13；DEC-022（第 9/10 条）
- 估算：1.5 天
- 依赖关系：TASK-047、TASK-048、TASK-043
- 涉及文件（按实际落地修正）：
  - `domain/usecase/mail/MailBillImportUseCase.kt`（**原计划的 `FetchBillFromMailUseCase` 未单独创建**——取件编排并入本类，`fetchBillMails` 直连 IMAP 客户端）
  - `domain/model/MailImportModels.kt`（原计划的 `MailImportResult.kt` 命名调整为 `MailImportModels.kt`）
  - `domain/usecase/bill/BillFilePreparer.kt`（新增 `prepareLocalFile` / `createTempFile` 两个接口方法）
  - `domain/usecase/bill/ImportBillUseCase.kt`（新增 `importLocalFile`，与 `invoke` 共用 `importPrepared` 核心）
  - `data/local/bill/SafBillFilePreparer.kt`（`prepareLocalFile`/`createTempFile` 实现 + `XlsxPrepared(deleteOnClose)` 参数化）
- 描述：
  - `FetchBillFromMailUseCase`：编排「取凭据 → IMAP 连接 → 本地过滤 → 取件 → 落临时文件」，产出 `List<FetchedBill>`（平台/主题/日期/zip 文件/来源类型）。
  - `MailBillImportUseCase`：对每个取件结果——探测加密类型 → 穷举口令 → 解密解压 → 生成 `File` → 调 `ImportBillUseCase(filePath, ledgerId)` → 聚合为 `MailImportResult`（每封邮件：成功 N / 跳过 M 分原因 / 失败原因；整体汇总）。
  - `ImportBillUseCase` 增加文件入口重载（**不改动原 uri 入口与内部逻辑**）：把 `File` 适配为既有 `BillSourceInput`（XLSX 直接用该文件、CSV 用其 GBK Reader），复用 `BillParserFactory` + 事务写入口径。
  - 每种账单只取最近一封（原描述「按平台 + 账期去重」）：**按平台**（`BillPlatform` = 账单种类）在 FILTER 后各保留 `sentDateMillis` 最新一封（同毫秒按主题字典序兜底），其余同平台旧邮件**不处理**，进度 `itemLines` 留 `⏭ {平台} · {主题} — 已有更新的同类账单，跳过`、FILTER 摘要披露「→ 待处理 K 封」；入库层 `externalId` 仍作最后防线。
  - 临时文件统一在 `finally` 清理（zip + 解出文件 + 解压目录）。
- 实施记录：`MailBillImportUseCase.invoke(ledgerId, accountIds, sinceDays=30, onProgress): MailImportOutcome` 全流程（读凭据 → 连接拉取 → 本地过滤 → **每种账单只取最近一封** → 逐封「取件→落盘→判型→穷举→解压→导入」→ 汇总）；凭据不可用返回 `NotConfigured` 不抛异常；`MailAuthException`/`MailNetworkException` 向上透传；单封未预期异常折叠 `Unexpected` 不中断整批；临时文件 `try/finally` 全路径清理；**`totalMailCount`（窗口内参与筛选邮件数，`fetchBillMails` 返回）与 `billMailCount`（命中数）已分离**（如实，不伪造）。
  - **关键实现约束（重要）**：`importLocalFile` 的 `displayName` 参与判型——支付宝邮件侧文件名是 `.zip`，直接传会每封被判未识别；实现为「邮件名本身是 `.csv`/`.xlsx` 才用，否则回退 zip 条目名」。
  - **实现差异（如实）**：①原计划两个 UseCase 合并为一；②原定「同一账期重复邮件按主题+账期去重」曾**未实现**，已于 **2026-10-09 补齐**（`domain/usecase/mail/MailBillSelector.kt`）——落地粒度为**按平台**：`MailBillMatcher` 的判定颗粒度只到平台（支付宝交易流水明细 / 微信支付账单流水文件），不存在可用的「账期」子类，故同平台只处理最新一封；跳过行为在 UI 显式披露（`⏭` 行 + FILTER 摘要），`externalId` 幂等仍是最后防线。
  - 测试：`ImportBillUseCaseTest` 扩至 **12 例**（F-13 新增 4 例：本地文件导入成功同口径 / prepareLocalFile 异常零落库 / Unrecognized 透传 / close 不删除调用方文件）+ 新增 `MailBillSelectorTest` **6 例**（空输入 / 每平台各一封 / 同平台多封取最新 / 真机形态 2 微信 + 3 支付宝 → 只留 2 封 / 同毫秒主题兜底且与输入顺序无关 / 单平台多封仅留一封并校验 `hitCount = kept + skipped`；顺序保持由前两例断言）；`MailBillImportUseCase` 编排主体**无 JVM 单测**（依赖 IMAP/下载等 I/O 边界，fake 化需引入新依赖，属已知限制）。
- 验收标准：
  - [ ] 端到端：配置邮箱后「一键收取」→ 支付宝/微信账单均入库，且与**手动导入同一账单**的成功/跳过计数一致（待真机；本地已验证解密/解压/导入三环各自正确——真实账单 zip 验收 + 单测同口径）
  - [ ] 重复收取同一批邮件：不产生重复交易（计「已存在」）（待真机；`externalId` 幂等由 F-12 单测与实现保证）
  - [x] 每种账单（平台）多封邮件只处理最新一封（**已实现** `MailBillSelector`，2026-10-09；JVM 单测 6 例覆盖选取口径；**真机确认待设备空档**——两路会话共用真机，见 `.claude-runs/PHONE-COORD.md`）
  - [x] 任一环节失败不影响其他邮件，且结果按邮件分条反馈（代码审查：逐封 `try` 折叠 `Unexpected`；失败映射表 9 分支见 04_api §14.7）
  - [x] 全程 `finally` 清理临时文件；UseCase 不 import `android.*`（代码审查 + `ImportBillUseCaseTest`「close 不删除调用方文件」用例（fake 同语义）；**`SafBillFilePreparer` 真实 `deleteOnClose=false` 无直接单测**，建议 androidTest 补「`prepareLocalFile`+`close()` 后源文件仍在 / `prepare()`+`close()` 后临时副本已删」两条回归）
- 需测试覆盖点：编排聚合（Fake 取件/破解/导入）、多邮件与去重、失败隔离与清理（JVM）。（实际：落 `ImportBillUseCaseTest` 4 例覆盖文件入口 + `MailBillSelectorTest` 6 例覆盖「每种账单只取最新一封」；编排主体无单测，见实施记录）

---

### TASK-050: F-13 UI 接线（一键收取入口 / 进度 / 结果）+ 真机验证

- 状态：已完成（本地构建 + 全量单测绿；**真机端到端验证待设备**）
- 优先级：P2
- 负责角色：Developer（UI）+ Tester（真机）
- 所属层：UI 层
- 关联需求：F-13；DEC-022、DEC-023
- 估算：2 天
- 依赖关系：TASK-049、TASK-045、TASK-020、TASK-021
- 涉及文件：
  - `ui/billimport/ImportScreen.kt`、`ui/billimport/ImportViewModel.kt`（扩展：邮箱收取入口与状态）
  - `ui/billimport/MailImportPresenter.kt`（新增：收取结果 → 展示模型纯函数）
  - `ui/main/MainScreen.kt`（`onNavigateToMailAccount` 参数穿透）+ `ui/navigation/MiniBookNavHost.kt`（Import/MailAccount 两处接线）
  - `ui/settings/SettingsScreen.kt`（邮箱账单入口，随 TASK-045 落地）
  - 注：未创建 `ui/import/components/*` 收取专用组件——收取结果为 `ImportScreen` 私有 composable（`ImportMailResultContent` 等），复用 C-20/C-04，不新增编号组件
- 描述：
  - 导入页 Idle 态新增「**从邮箱一键收取**」按钮：未配置邮箱 → 禁用 + 引导去设置；已配置 → 可点。
  - `ImportUiState` 扩展兼容收取流程：连接邮箱 → 找到 N 封账单 → 逐封下载/解密/导入 → 汇总结果（进度可见、**可取消**）。
  - 结果展示：整体「成功 N / 跳过 M / 失败 K」+ 按邮件分条（主题、账期、平台、结果），失败给明确原因（链接过期 / 口令穷举失败 / 格式未识别 / 网络异常）。
  - 无账单邮件 → 「未找到账单邮件」+ 两平台申请入口指引（复用 F-12 提示文案）。
  - 复用 F-12「Busy 锁数据入口」交互口径。
- 实施记录：`ImportUiState` 新增 `MailBusy(progress)` / `MailResult(summary)` / `MailNotConfigured` / `MailError(msg)` 四态 + `onFetchMailBills()`（进度回调直刷状态；`MailAuthException`/`MailNetworkException`/账本异常/兜底四路固定中文文案）；Idle 态次要按钮「一键收取邮箱账单」；`MailBusy` 经 C-20 全屏遮罩 + `mailStageMessage` 四阶段文案；`MailImportPresenter`（纯函数 `failureLabel` `when` 穷尽 9 分支 + `present` 汇总展示模型，JVM 可单测）；`MailNotConfigured` EmptyState「去配置」→ `onNavigateToMailAccount`。测试：`MailImportPresenterTest` **12 例** JVM 全绿。
  - **实现差异（如实）**：①未配置时不**禁用**按钮，而是点击进入 `MailNotConfigured` 引导态（可直达设置页，比静默禁用更可自救）；②「无账单邮件」独立文案未加——`MailResult` 汇总 headline 计数为 0/0 即承载该信息；③「可取消」实现：收取期间全屏遮罩锁入口（返回被屏蔽），底层协程取消链路具备（`ensureActive`），无显式取消按钮。
- 验收标准：
  - [x] 未配置/已配置两种入口状态正确；未配置时引导可达设置页（JVM 层状态机 + 导航接线已实现并编译绿；**页面实际渲染与路由跳转待真机**）
  - [ ] 收取过程有进度、可取消，取消后无半导入数据、临时文件已清理（进度已实现（四阶段文案随回调刷新）；取消路径与清理正确性待真机——清理在 `finally` 已覆盖取消分支（代码审查））
  - [x] 结果按邮件分条展示，失败原因明确且与真实原因一致（`MailImportPresenterTest` 12 例：9 失败分支文案穷尽 + 汇总计数 + headline）
  - [ ] 真机端到端：支付宝 + 微信各至少一封真实账单成功入库，计数正确（待真机）
  - [ ] 收取后核对邮箱：邮件数/已读状态未变（只读验证）（待真机）
  - [ ] 日志中检索不到授权码与解压口令（代码审查已保证无凭据日志路径；真机 logcat 检索复核待设备）
  - [ ] 断网场景：明确「网络异常」，F-12 手动导入路径不受影响（文案已实现 `MAIL_NETWORK_MESSAGE`；F-12 路径与 F-13 完全独立（代码结构保证）；真机触发待设备）
- 需测试覆盖点：`ImportViewModel` 收取状态机（Fake UseCase）（实际：`MailImportPresenterTest` 12 例覆盖展示映射；ViewModel 状态机本身无 JVM 单测，属已知限制）；真机端到端与只读性核对（Tester，写入 `09_test_report.md`，待设备）。

---

---

## Phase 3：F-14 短信账单导入（TASK-051~057）

> 设计依据：`13_sms_bill_import.md`（完整方案）、`01_prd.md §F-14`、DEC-025（技术路线）、DEC-026（权限与隐私边界）。
> 总原则：**不加 DB 字段**、**不新建写库路径**（复用 `ImportBillUseCase.importPrepared`）、**未识别必留痕**、**默认零权限申请**。
> ⚠️ **验证限制**：当前无真实短信账单样本 → 解析/匹配以 fixture 单测为验收基线；**真机验收须先向用户申请**（用户明确要求）。

### TASK-051: F-14 权限与开关骨架（Manifest + 设置区块 + 权限引导）

- 状态：已完成（2026-10-09）（设计已定稿）
- 优先级：P1
- 负责角色：Developer（Android 权限 + UI）
- 所属层：UI 层 + 清单
- 关联需求：F-14；DEC-026
- 估算：0.5 天
- 依赖关系：无
- 涉及文件：
  - `AndroidManifest.xml`（新增 `READ_SMS` / `RECEIVE_SMS` / `POST_NOTIFICATIONS` + receiver 声明占位）
  - `ui/settings/SmsSettingsSection.kt`（新增：开关矩阵 + 白名单列表 + 权限状态）
  - `ui/settings/SettingsScreen.kt`（接入区块）
- 描述：三项权限**默认不申请**；总开关关闭时零运行时请求；权限引导页可直达系统设置，并提供「改用分享导入」退路。
- 验收标准：
  - [ ] 总开关默认关闭；关闭状态下 App 不请求任何短信权限（运行时零调用）
  - [ ] 三个开关可独立开合，开启时按需申请对应权限
  - [ ] 权限被拒/受限 → 明确提示 + 去设置 + 分享导入退路
  - [ ] `:app:assembleDebug` 绿
- 需测试覆盖点：开关状态机（JVM，Fake 权限检查器）；真机授权路径待申请。

---

### TASK-052: F-14 读取层 `SmsBillReader`（分页 / 水位 / 双卡 / 内容哈希）

- 状态：已完成（2026-10-09；仪器测试待真机）
- 优先级：P1
- 负责角色：Developer（Android 数据层）
- 所属层：data 层
- 关联需求：F-14；DEC-025
- 估算：1 天
- 依赖关系：TASK-051
- 涉及文件：
  - `data/local/sms/SmsBillReader.kt`（新增：`ContentResolver` 查询，**唯一 Android I/O 边界**）
  - `data/local/sms/SmsBillCursor.kt`（新增：分页游标 + 水位模型）
  - `domain/model/SmsModels.kt`（新增：`SmsMessageRow` 等）
- 描述：`date ASC, _id ASC` 分页（批量 200，防 CursorWindow 溢出）；水位 `max(_id)`/`max(date)` + 重叠回扫 3~7 天；内容哈希 `sha256(address|receivedAtMs|body)`；双卡 `sub_id` 分组；注解 `READ_SMS`。
- 验收标准：
  - [ ] 分页在万条量级不溢出、可取消
  - [ ] 水位增量：重复调用不重复返回；重叠窗口可覆盖迟到短信
  - [ ] `_id` 复用场景由内容哈希兜住
  - [ ] 单测：分页/水位/双卡/重复 `_id`/空库 5 类
- 需测试覆盖点：`SmsBillCursor` 纯逻辑可单测；`SmsBillReader` 的 Cursor 交互建议 androidTest（待申请设备）。

---

### TASK-053: F-14 模板注册表与解析器（`SmsTemplateRegistry` / `SmsBillParser`）

- 状态：已完成（2026-10-09；单测 21 例绿，真实样本回归待补）
- 优先级：P1
- 负责角色：Developer（领域层）
- 所属层：domain 层
- 关联需求：F-14；DEC-025
- 估算：1 天
- 依赖关系：TASK-052
- 涉及文件：
  - `domain/usecase/sms/SmsTemplateRegistry.kt`（新增）
  - `domain/usecase/sms/SmsBillParser.kt`（新增）
  - `app/src/test/.../sms/SmsBillParserTest.kt`（新增，fixture 驱动）
- 描述：模板 = 发送方号码 + 抬头锚点 + 结构化捕获；产出统一 `ParsedBillRow`，`externalId = sms:<sha256(...)[:16]>`；三态结果 `Parsed` / `Unrecognized` / `NotBill`；金额转分、方向词表互斥判定、渠道归类（`BANK_DIRECT`/`ALIPAY`/`WECHAT`/`OTHER`）。
- 验收标准：
  - [ ] 每个模板 4 类 fixture：命中 / 金额边界 / 方向歧义（→Unrecognized）/ 格式漂移（→Unrecognized，**不得误记**）
  - [ ] 非账单类（验证码/营销/快递）→ `NotBill`（带依据）
  - [ ] 金额 ≤0 / 时间缺失 → `Unrecognized`
  - [ ] JVM 单测全绿
- 需测试覆盖点：解析器纯函数全覆盖；**无真实样本** → fixture 为唯一基线。

---

### TASK-054: F-14 跨源匹配器 + 干跑预览 + 待确认/未识别清单

- 状态：已完成（2026-10-09）
- 优先级：P1
- 负责角色：Developer（领域层 + UI）
- 所属层：domain + UI
- 关联需求：F-14；DEC-025
- 估算：1 天
- 依赖关系：TASK-053
- 涉及文件：
  - `domain/usecase/sms/SmsBillMatcher.kt`（新增：双向共用）
  - `domain/usecase/sms/BalanceChainAuditor.kt`（新增）
  - `ui/sms/SmsPendingScreen.kt`（新增：待确认 / 未识别清单）
  - `ui/billimport/SmsImportContent.kt`（新增：干跑预览与结果）
- 描述：匹配规则「同账本 ∧ 同 type ∧ amountFen 相等 ∧ date 相等」→ 唯一自动判重 / ≥2 疑似（人工）/ 0 入库；**永不自动删**；账单文件入口也调用同一匹配器（双向）；预览展示四类计数与被抑制项。
- 验收标准：
  - [ ] 两向顺序（先短信后台账 / 先账单后短信）各有用例，判定一致
  - [ ] 唯一/多候选/零候选三态用例
  - [ ] 未识别清单可见、可手工转一笔、可标记非账单
  - [ ] 余额链缺口可检出（缺余额数据时静默跳过）
- 需测试覆盖点：匹配器与审计器纯函数单测；UI 状态机 JVM 测试。

---

### TASK-055: F-14 实时通道（`SmsReceivedReceiver` + 静默聚合通知）

- 状态：已完成（2026-10-09；广播投递待真机验证）
- 优先级：P2
- 负责角色：Developer（Android + UI）
- 所属层：data + UI
- 关联需求：F-14；DEC-025
- 估算：0.5 天
- 依赖关系：TASK-054
- 涉及文件：
  - `data/local/sms/SmsReceivedReceiver.kt`（新增）
  - `ui/notification/SmsNotificationHelper.kt`（新增：Channel 分级）
  - `AndroidManifest.xml`（receiver 正式接线）
- 描述：`SMS_RECEIVED` 静态接收器（`goAsync` ≤10s，只处理本条）；命中模板 → 「待确认」累积；**默认静默聚合不弹通知**；「待确认」Channel 低优先级、「自动记账完成」普通优先级；非账单短信零提醒。
- 验收标准：
  - [ ] 验证码/营销/快递类零通知（fixture 断言）
  - [ ] 账单类进待确认且默认不弹通知
  - [ ] 广播处理不超时（≤10s）、异常不影响系统短信
  - [ ] HyperOS 后台策略有引导说明（真机验证待申请）
- 需测试覆盖点：通知决策纯函数（JVM）；广播投递真机待申请。

---

### TASK-056: F-14 白名单与自动记账（L2）

- 状态：已完成（2026-10-09；L2 真机两态待验证）
- 优先级：P2
- 负责角色：Developer（领域 + 存储）
- 所属层：domain + data
- 关联需求：F-14；DEC-025
- 估算：1 天
- 依赖关系：TASK-055
- 涉及文件：
  - `data/local/sms/SmsWhitelistStore.kt`（新增：DataStore）
  - `domain/usecase/sms/SmsBillImportUseCase.kt`（新增：编排 + 计数闭合）
  - `domain/usecase/sms/SmsWhitelistPolicy.kt`（新增：晋级/降级/四条件判定，纯函数）
  - `di/SmsModule.kt`（新增）
- 描述：四条件（白名单 ∧ 模板命中 ∧ 判无重复 ∧ 渠道允许）同时成立才自动写；「手动确认 3 次且无纠错」晋级；用户纠错（删/改自动记录）→ 立即降级；**应用锁开启时 L2 置灰**（`AppLockController` 状态判定）。
- 验收标准：
  - [ ] 四条件缺一即不自动写（4 个负向用例 + 1 个正向）
  - [ ] 3 次确认晋级可复现；纠错降级可复现
  - [ ] 自动写入走 `importPrepared`（同事务），可撤销
  - [ ] 应用锁开启 → L2 不可用且给出原因
  - [ ] 计数闭合断言在编排层生效
- 需测试覆盖点：`SmsWhitelistPolicy` 纯函数全覆盖；编排 Fake 化单测。

---

### TASK-057: F-14 收口（构建 + 全量单测 + 文档回写 + 真机/模拟器验收）

- 状态：已完成（含真机 + 模拟器验收，2026-10-10）
- 优先级：P1
- 负责角色：Developer + Tester
- 所属层：全层
- 关联需求：F-14；DEC-025、DEC-026、**DEC-027**
- 估算：0.5 天
- 依赖关系：TASK-051~056
- 涉及文件：`02_architecture.md`、`04_api.md`、`05_ui.md`、`CHANGELOG.md`、`README.md`、`09_test_report.md`、`13_sms_bill_import.md`、`07_decisions.md`
- 描述：单点串行全量构建 + 全量单测；文档回写（设计 §11 清单）；真机验收（用户 2026-10-10 授权后执行，含模拟器真实 PDU 注入）。
- 验收标准：
  - [x] `:app:assembleDebug` + `:app:testDebugUnitTest` 全绿（**52 类 / 406 例 / 0 失败**，2026-10-10 复跑）
  - [x] 文档清单 §11 全部回写（并新增设计文档 §14 验收章节、DEC-027）
  - [x] 真机验收完成（小米 Android 16 / HyperOS）：L0 真实数据干跑通过、权限授权后不重启即生效
  - [x] 模拟器端到端验收完成：L0 计数闭合、方向、余额缺口、确认入库、幂等重扫、L1 待确认、**L2 后台自动记账**、非账单零提醒
  - [x] 仍未覆盖（如实挂账）：应用锁开启态 L2 门控、真实银行账单短信、通知「逐条提醒」、L4 分享导入（其中 **L3 增量补扫场景已由 TASK-058 补做**）
- 需测试覆盖点：全量单测；真机 L0/权限流；模拟器 L0/L1/L2 端到端。
- 执行记录（2026-10-10）：构建与测试由主代理串行执行（规则 3），真机 `A1B2C3D4` 与模拟器 `emulator-5554` 亦由主代理独占操作；实测发现并修复 5 个「JVM 绿但真机静默失效」缺陷（详见 `13_sms_bill_import.md §14.3`）；**未提交 git**（规则 2，待用户批准）。

---

### TASK-058: F-14 验收后口径定案——三层兜底（F1 修复保留 / F2 角标撤销）+ 边界短信复测

- 状态：已完成（2026-10-10；当日下午按用户决策**二次收口**：撤销角标）
- 优先级：P1（默认配置下的可见性缺口）
- 负责角色：Developer + Tester
- 所属层：domain / ui
- 关联需求：F-14；**DEC-028**（新增，当日修订：撤销角标）、DEC-026（隐私边界不变）、DEC-027
- 估算：0.5 天（含二次收口）
- 依赖关系：TASK-057
- 涉及文件（**最终态**）：`SmsBillImportUseCase.kt`、`HomeViewModel.kt`、`13_sms_bill_import.md`、`09_test_report.md`、`07_decisions.md`、`CHANGELOG.md`、`README.md`；`HomeScreen.kt` 角标接线与 `SmsPendingTracker(.Test).kt` **已随撤销一并删除**
- 描述：默认「静默聚合」下「未白名单发送方短信到达后 App 内毫无提示」的可见性缺口处置：① 补扫触发点由导入页改为**打开 App**（F1，保留）；② 水位「不越过待确认」+ 进程内跟踪器 + 首页角标（F2a/b/c）曾实现并验证通过，但真机实测角标**常驻去不掉**（F3：未识别条目无销账出口）→ 用户 2026-10-10 拍板「角标不要了，不需要已读未读」→ **全部撤销**，最终模型 = 三层兜底（①到达即记账 → ②打开 App 补扫 → ③手动导入）；并补做 4 类边界短信复测。
- 验收标准：
  - [x] `:app:assembleDebug` + `:app:testDebugUnitTest` 全绿（**52 类 / 406 例 / 0 失败**；F-14 占 7 类 / 73 例）
  - [x] F1：真机每次开 App 均出补扫日志（`logcat -s MiniBookSms` → `L3 补扫：扫描 1，自动 0，待确认 1，跳过=-`）
  - [x] F2 撤销落地：两端首页**无任何「待确认」节点**（角标已移除）、`SmsPendingTracker` 与单测文件已删除、无残留引用
  - [x] F3（角标常驻）由决策消解：不再有任何「待确认/已读」状态；「不遗漏」由 L0 手动导入兜底（与水位无关，永远可找回）
  - [x] 边界短信：通知类（106 端口）与「银行端口无金额」→ **非账单**（零提醒）；未登记模板 → **未识别**（清单页留痕）；**跨源判重**：手工 ￥77.00 与未入库同额同日短信 → 由「可导入」转「已存在/重复」
  - [ ] 仍未覆盖（如实挂账）：双卡（`sub_id`）与长时间跨度（跨月/跨年，`emu sms send` 无法指定时间）；应用锁开启态；真实银行样本；L4；`catchUp` 编排本身无 JVM 单测
- 需测试覆盖点：撤销后**无**跟踪器单测（文件一并删除，不保留死代码）；补扫分类计数（自动/待确认）作为日志口径；边界短信分类。
- 执行记录（2026-10-10）：主代理单点执行（规则 3）；证据链见 `13_sms_bill_import.md §14.6`（含 §14.6.4 决策变更）、`09_test_report.md`（验收后口径定案节）；**未提交 git**（规则 2，待用户批准）。

---

### TASK-059: 验收后 UI 改版——未识别「已看过」标记 + 导入页三入口 + 设置分组汇总页（用户三诉求）

- 状态：已完成（2026-10-10，双端实测）
- 优先级：P1（体感：优先级②「不烦人」/ ②③ 信息架构）
- 负责角色：Developer + Tester
- 所属层：domain（水位）/ data（DataStore 键）/ ui（导入页、设置页）
- 关联需求：F-14；**DEC-029**（新增）、DEC-026（隐私边界不变：只存时间戳）、DEC-028（撤销角标后的必然补位）、F-04/05/09（设置分区搬运，语义不变）
- 估算：0.5 天
- 依赖关系：TASK-058
- 涉及文件（新增/改动）：
  - 新增：`ui/settings/SettingsAppearanceScreen.kt`、`SettingsSecurityScreen.kt`、`SettingsDataScreen.kt`、`SettingsSmsScreen.kt`、`SettingsNavigation.kt`
  - 改动：`SettingsScreen.kt`（重写为汇总页）、`SmsSettingsStore.kt`、`SmsModels.kt`、`SmsBillImportUseCase.kt`、`ImportViewModel.kt`、`SmsImportPresenter.kt`、`SmsImportContent.kt`、`ImportScreen.kt`、`SmsSettings.kt`、`SmsSettingsSection.kt`（`displayName()` 提为 internal）、`ui/components/Settings.kt`、`Destinations.kt`、`MiniBookNavHost.kt`、`MainScreen.kt`、`SmsImportPresenterTest.kt`
- 描述：用户验收后三点要求 —— ①「扫描过的做标记，否则每次重新扫描都有之前未处理的」；②「导入账单页面…区分微信支付宝账单的不要了，只留下三个按钮选项，三个按钮上面简短的说明，然后点击进入不同的功能」；③「设置页面太长了，每种设置设计汇总，然后点击进入二级页面设置」。
- 验收标准：
  - [x] ① 未识别「已看过」= 单一时间戳水位（`unrecognized_ack_ms`，**只存时间戳**）→ `SmsPendingItem.seen`；清单上屏后 `acknowledgeUnrecognized()` 推进；`lines()` 只出未看过、`seenLines()` 进「▸ 此前已看过 N 条（点击展开）」折叠区
  - [x] ① 口径不动：计数不变（模拟器 `扫描 17 条 = 可导入 0 + 未识别 1 + 非账单 3 + 已存在/重复 13` 两次扫描完全一致）、不删除/不过滤条目、待确认清单页仍可逐条看 + 手工记一笔
  - [x] ① 真机复现并消除：`A1B2C3D4` 第 1 次扫描显快递短信，第 2 次折叠为「此前已看过 1 条」
  - [x] ② 导入页三入口（账单文件 / 邮箱账单 / 短信账单，各带一行说明 + 按钮）；微信/支付宝导出指引折叠进「如何导出账单？」（文案未丢）；返回语义逐层退（结果态回三入口，入口页才退出）
  - [x] ③ 设置首页 = 5 行分组汇总（账本 / 外观 / 安全 / 数据 / 短信账单，每行一行状态）+ 版本页脚；四个二级页 `settings-appearance/security/data/sms` 全部可达且内容与原分区逐条对应；账本仍走 `ledger-manage`
  - [x] ③ `MainScreen` 去掉 `onNavigateToImport` / `onNavigateToMailAccount` 参数穿透（入口移入「数据」页，由该页在 NavHost 接线）；新增 `LocalSettingsNavigation` 跨层导航桥
  - [x] `:app:assembleDebug` + `:app:testDebugUnitTest` 全绿（**52 类 / 408 例 / 0 失败**；F-14 7 类 / 75 例，本轮 +2 例）
  - [ ] 未覆盖（如实挂账）：折叠区展开态真机截图未单独采集（同一 presenter 纯函数 + 单测覆盖）；真机仅抽验「短信账单」一个二级页
- 需测试覆盖点：`SmsImportPresenterTest` +2 例（已看过移出主列表且计数闭合不变；`unrecognizedLine` 文案与主列表统一）。
- 执行记录（2026-10-10 11:00–11:25）：构建/测试与双端安装验证均由主代理单点串行执行（规则 3）；证据链见 `13_sms_bill_import.md §15`、`09_test_report.md`（UI 改版章）、`07_decisions.md` DEC-029；截图见 `.claude-runs/f14b-*.png`；**未提交 git**（规则 2，待用户批准）。

### TASK-060: 短信导入两种扫描方式（增量 / 全量）+ 白名单空态修复（用户第三次反馈 + 同日提问）

- 状态：**已完成（2026-10-10 13:2x，真机实测通过）**
- 依赖关系：TASK-059
- 用户口径：「我本次导入已经看过这条短信，并且不用处理了，下次再重新导入时候就不要再标记出来了，做两种扫描方式，一种全量扫描，一种增量扫描，比如我 12:00 扫描过了，增量只扫描 12:00 以后的，只有全量的才全部扫描出来。」＋「另外白名单在哪看，就只有清空」
- 涉及文件（新增/改动）：
  - 改动：`data/local/sms/SmsBillReader.kt`（新增 `readAfter()` 严格增量读）、`domain/model/SmsModels.kt`（`SmsScanMode` + `SmsScanPreview.scanMode/sinceMs`）、`domain/usecase/sms/SmsBillImportUseCase.kt`（`scan(ledgerId, mode)`）、`ui/billimport/SmsImportPresenter.kt`（`scanModeLine()`）、`SmsImportContent.kt`（说明行）、`ImportViewModel.kt`（入口带 mode）、`ImportScreen.kt`（入口卡两按钮 + 重新扫描沿用模式）、`ui/settings/SmsSettingsSection.kt`（白名单空态）、`SmsImportPresenterTest.kt`
- 验收标准：
  - [x] 增量 = 只读节点之后的短信（严格 `date > 节点`，**不复用** 3 天重叠窗口的 `readSince()`），读完推进节点 ⇒ 已看过、不打算处理的条目**完全不出现**
  - [x] **节点 = 本次扫描的墙钟时间**（用户 13:4x 即时纠正：「不应该是最后一条短信的时间，应该是当前扫描的时间」）；在读之前取 ⇒ 读完到写节点之间到达的短信下次仍会被读到；`catchUp`（L3）一并统一为「本次补扫的墙钟时间」
  - [x] 全量 = 全部历史（**不动节点**），含「▸ 此前已看过 N 条」折叠区 ⇒ 不遗漏兜底不破
  - [x] 入口卡两个按钮（`增量扫描` 主 / `全量扫描` 次）+ 一行小字说明差别；预览页显示扫描方式、本次覆盖起点与更新后的节点（仅时间戳，DEC-026 不变）
  - [x] 计数口径不动：`SmsScanPreview.assertClosed()`、「未识别 N」全量口径、「待确认」数字语义（DEC-029 修订）一字未改
  - [x] 真机 `A1B2C3D4`：增量 → `扫描 0 条`（快递短信不出现）；全量 → `扫描 1 条 … + ▸ 此前已看过 1 条`；节点复验 → L3 写 `13:47` → 增量 `本次覆盖 13:47 …，起点已更新为 13:48`，全量后节点不动，再增量 `本次覆盖 13:48 …，起点已更新为 13:49`
  - [x] 白名单空态：不再显示「清空白名单」，说明改为「…后自动加入；加入后会在这里逐条列出，可单独关闭它的自动记账。」
  - [x] `:app:assembleDebug` + `:app:testDebugUnitTest` 全绿（**52 类 / 410 例 / 0 失败**；F-14 7 类 / 77 例，本轮 +2 例）
  - [ ] 未覆盖（如实挂账）：①白名单**非空态**本轮未目视验证（真机白名单为空；模拟器 AVD 重启后数据被重置，白名单亦空），该分支 = 一行 `HintText` + 原 `forEach` 不变 + 清空按钮移入 `else`；②**增量首次无节点**分支修正后未在设备上重现（节点已存在），由单测锁定文案
- 需测试覆盖点：`SmsImportPresenterTest` +2 例（扫描方式三态文案；预览携带扫描方式且闭合不变）。
- 执行记录（2026-10-10 13:0x–13:2x）：构建/测试与真机安装验证均由主代理单点串行执行（规则 3）；证据链见 `13_sms_bill_import.md §16`、`09_test_report.md`（增量/全量章）、`07_decisions.md` DEC-030；截图 `.claude-runs/f14d-real-incr-empty.png`、`f14d-real-full.png`、`f14d-real-settings-wl.png`；**未提交 git**（规则 2，待用户批准）。

### TASK-061: F-15 服务端骨架（FastAPI + SQLAlchemy + Alembic + 配置 + 健康检查 + 测试基建）

- 状态：**已完成（SQLite + MySQL 双 DSN，2026-10-10）；MySQL 建表验收已由 TASK-069 收口**
- 依赖关系：无（SQLite 路径早已可用）；**MySQL 建表验收已完成（TASK-069，2026-10-10）**
- 涉及文件（新增，均已落地）：`server/{requirements.txt,alembic.ini,pytest.ini,README.md}`（其中 `.env.example` 已于 2026-10-23 拆分为 `.env.local.example` / `.env.remote.example`）、
  `server/alembic/{env.py,script.py.mako,versions/0001_init.py}`、
  `server/app/{main,config,db,models,schemas,security,deps,ratelimit,entity_spec,timeutil,errors}.py`、
  `server/app/routers/{health,auth,sync}.py`、`server/app/services/sync_service.py`、`server/tests/`（9 文件）
- 验收标准：
  - [x] `python -m venv .venv` + `pip install -r requirements.txt` 可装（**Python 3.12.0**）
  - [x] `alembic upgrade head` 在 **SQLite** 下建表成功（`users`/`refresh_tokens`/`devices`/`sync_records`/`sync_counters`
        + `server_seq` 首行；`downgrade base` 可回滚）
  - [x] `alembic upgrade head` 在 **MySQL 8** 下建表成功（utf8mb4 + InnoDB + DATETIME(6) + 中文备注无乱码）→ **2026-10-10 已验（TASK-069）**
        　替代核验已做：`alembic upgrade head --sql` **离线渲染** + `test_migration.py`
        　（断言 `DATETIME(6)`/`JSON`/`BIGINT`/`VARCHAR(32|36|64)`/复合主键/`InnoDB`+`utf8mb4` 均在 DDL 中）
  - [x] `GET /healthz` 返回 200；`/docs`（OpenAPI）可用（`test_health.py` 2 例）
  - [x] `pytest` 可跑通（**47 例 / 0 失败**，跑的是真接口）

### TASK-062: F-15 用户系统（注册 / 登录 / 刷新轮换 / 登出 / me + 唯一性 + 限流）

- 状态：**已完成（2026-10-10）**（DEC-032）
- 依赖关系：TASK-061
- 验收标准（A1）—— 全部通过，证据 `server/tests/test_auth.py`（11 例）：
  - [x] 注册 / 登录 / 刷新（**旧刷新令牌立即失效**）/ 登出 / `/me` 全通
  - [x] 重复用户名 → `username_taken`（**大小写不敏感**：`Alice` 之后 `alice` 同样被拒）；错口令 → `invalid_credentials`
        （与「用户不存在」共用同一错误码，不泄露账号是否存在）；口令 <8 位 → `validation_error`
  - [x] 过期 access → `token_expired`；已撤销 refresh → `token_revoked`；登出**幂等**
  - [x] 库中口令为 argon2id 哈希（断言 `$argon2` 前缀且不含明文）
  - [x] 登录限流生效（超限 → `rate_limited`）

### TASK-063: F-15 同步接口（push / pull / status + 墓碑 + server_seq + 冲突 + 幂等）

- 状态：**已完成（2026-10-10）**（DEC-033）
- 依赖关系：TASK-062
- 验收标准（A3~A6）—— 全部通过，证据 `test_sync_push.py`(10) / `test_sync_pull.py`(5) /
  `test_idempotency.py`(3) / `test_conflict.py`(4)：
  - [x] push：新增 / 更新 / **no-op（同 `updated_at` 不涨 `server_seq`）** / 冲突时返回服务端版本（`reason=server_newer`）
  - [x] `payload` 逐实体白名单校验（`app/entity_spec.py`）：未知字段、类型错（含 `bool` 冒充整数、浮点金额）、
        信封与内容不一致、缺必填 → **整批回滚** + `validation_error`（含 `index` 与 `entity_id`，且批量上限 1000）
  - [x] pull：按 `since_seq` 分页（默认 200 / 上限 500），`next_seq` / `has_more` 正确，分页**无重无漏**；
        墓碑（`deleted=true`）随 pull 下发
  - [x] `GET /sync/status` 返回本账号 `server_seq`（游标上限）/ 各实体计数 / `server_seq_global`
        （设计差分见 `14_cloud_sync.md §12.2`）

### TASK-064: F-15 越权隔离与安全测试（隔离 / 隐私 / 日志）

- 状态：**已完成（2026-10-10）—— 隔离 / 伪头 / 不回显输入 / 日志不含 payload 四项全部闭环**
- 依赖关系：TASK-063
- 验收标准（A2）：
  - [x] 用户 A 的 token 无法读/写 B 的任何 `sync_records` 行（含同 `entity_id` 场景与 `since_seq=0` 全量拉取）—— `test_isolation.py` 4 例
  - [x] 伪造的 `X-User-Id` 之类头无效（只认 Bearer JWT）
  - [x] 校验失败错误体**不回显 `input`**（Pydantic 默认详情会带原始输入，可能含口令/备注）
  - [x] 服务端日志**不含 payload 内容**的自动化断言 —— 新增 `server/tests/test_log_privacy.py` 3 例，
        覆盖三条最易顺带打日志的路径：**push**（断言日志里查不到备注哨兵 `隐私哨兵-8f3a2b1c9d`、
        金额哨兵 `987654321`，以及 `"payload"` / `"description"` 这类**字段名**——字段名出现即等价于整个 body 被打了）、
        **pull**（响应里必须有备注、日志里不许有）、**整批校验失败**（被拒条目的原始值不得进日志）。
        双 DSN 各 **52 例 / 0 失败 / 0 跳过**（含 SQLite 与 MySQL 8，两次 `exit=0`）

### TASK-065: F-15 Android 网络栈与云账号存储（OkHttp + 令牌 Keystore 包裹）

- 状态：**已完成（2026-10-10）**（DEC-031 / DEC-034；`14_cloud_sync.md §5.1/§5.2`）
- 依赖关系：无（可与 TASK-061~064 并行，文件不重叠）
- 涉及文件（实际）：新增 `app/src/main/java/com/minibook/data/remote/{SyncDtos,SyncErrors,SyncUrlValidator,SyncApi,SyncRemote,SyncAuthStore,SyncTokenAuthenticator}.kt`、
  `data/local/security/SecretCipher.kt`、`di/SyncModule.kt`、`app/src/debug/AndroidManifest.xml`；
  改动 `gradle/libs.versions.toml` + `app/build.gradle.kts`（新增 **OkHttp 4.12.0** 单一依赖）
- 验收标准：
  - [x] 只新增 OkHttp（JSON 复用既有 kotlinx-serialization 1.8.1），**不引 Retrofit / Ktor / WorkManager** —— 4.12.0 取自本机 Gradle 缓存既有版本
  - [x] 令牌经 Android Keystore 包裹后落 DataStore（别名 `minibook_sync_token`，**不动**开库路径的 `KeyManager`）；
        单测用真 DataStore 文件字节断言「不含明文令牌」
  - [x] 401 → 刷新一次 → 重试；**网络故障不清账号态**、令牌撤销才清并提示重新登录（实现为 suspend 包装器而非 OkHttp `Authenticator`，差分见 §12.2）
- 证据：`:app:testDebugUnitTest --tests "com.minibook.data.remote.*"` → **30 例 / 0 失败**（`SyncDtosTest` 7 / `SyncErrorsTest` 5 /
  `SyncUrlValidatorTest` 6 / `SyncAuthStoreTest` 6 / `SyncTokenAuthenticatorTest` 6；junit XML 逐类核验，`BUILD SUCCESSFUL`）
- 附带：debug-only 明文 HTTP（release 仍禁明文）；被系统拦截时单独映射 `CLEARTEXT_BLOCKED`

### TASK-066: F-15 同步核心（SyncPlan 纯函数 + SyncUseCase 三模式 + Room v3→v4）

- 状态：**已完成（2026-10-10；instrumented 迁移测试待设备）**（DEC-033 / **DEC-035 / DEC-036**）
- 依赖关系：TASK-065（客户端网络）+ TASK-063（服务端契约）
- 涉及文件（实际）：
  - 新增：`domain/usecase/sync/{SyncModels,SyncPlan,SyncUseCase}.kt`、`data/local/sync/SyncEntityAdapter.kt`（6 类适配器 + 注册表）、
    `data/local/db/entity/{SyncStateEntity,SyncConflictEntity}.kt`、`data/local/db/dao/{SyncStateDao,SyncConflictDao,SyncDao}.kt`
  - 改动：`MiniBookDatabase.kt`（version 3→4 + `MIGRATION_3_4`）、`DatabaseGate.kt`（注册迁移）、`DatabaseModule.kt`（3 个 DAO 提供者）、
    6 张业务实体（`@Serializable` + 可空字段 `= null`）
  - 生成：`app/schemas/com.minibook.data.local.db.MiniBookDatabase/4.json`（**待 `git add`**）
- 验收标准（A7~A11）：
  - [x] 三模式各自可跑；dry-run 预览给出「上传 N / 下载 M / 冲突 K / 删除 D」（`SyncPlan` 计数 + `SyncUseCase.preview`）
  - [x] 「仅下载」**不删本地多余行**；云端墓碑下发时删除本地对应行（`SyncPlanTest` 两例直接断言）
  - [x] 冲突留档进 `sync_conflicts`（预演判定 + push 时服务端判定两条路径都留档、按类型+id 去重）；
        **失败不推进游标**（`server_cursor`/`last_pushed_at` 只在全部成功后写）
  - [x] 同步前写 `files/sync_backup/<ts>.json`（复用 F-04 导出，保留最近 3 份；写失败不阻断同步）
  - [x] `MIGRATION_3_4` 落地 + `4.json` 已生成：表数 7 → 9，**既有 7 表 CREATE 与索引逐字未变**（静态比对改动数 = 0）
  - [ ] `MigrationV3To4Test` **已写但未跑**（instrumented 需真机/模拟器；真机有用户真实数据，禁装测试 APK）
- 证据：`SyncPlanTest` **19 例 / 0 失败**、`SyncPayloadContractTest` **1 例 / 0 失败（未跳过）**；`:app:testDebugUnitTest` BUILD SUCCESSFUL
- 设计修订（两条，均已在 `14_cloud_sync.md §12.2` 记为差分）：
  - **DEC-035**：墓碑不再由删除路径写 `sync_changes`，改为「`sync_state` 有、本地已无」的**差分补发**
    （零写路径侵入、覆盖删账本/导入替换等批量删除；硬约束：差分必须用完整本地集合）
  - **DEC-036**：payload = Room 行**原样 JSON**（实体 `@Serializable`）+ `SyncPayloadContractTest` 跨语言键集断言；
    上行判据同时改为「与基线不一致」（时钟回拨不再漏推）

### TASK-067: F-15 UI：设置 → 数据 → 云同步（账号 / 三按钮 / 状态 / 冲突）

- 状态：**已完成（2026-10-10）**
- 依赖关系：TASK-066 ✅
- 涉及文件（实际）：新增 `ui/settings/{SettingsCloudScreen,SettingsCloudViewModel,SyncPresenter}.kt`；
  改动 `ui/settings/SettingsDataScreen.kt`（新增「云同步」入口行 + `onNavigateToCloudSync` 参数）、
  `ui/navigation/Destinations.kt`（`settings-cloud`）、`MiniBookNavHost.kt`（新二级页 + 入口回调）
- 验收标准：
  - [x] 服务器地址 + 用户名/口令（登录 / 注册 / 退出）可用；未登录时三个模式行不可点并显示「登录后可同步」
        （注册后**自动登录**；退出先尽力撤销刷新令牌再清本地会话，**不动任何业务数据**）
  - [x] 三个按钮（双向同步 / 仅上传 / 仅下载）+ 预览确认 + 结果计数（成功给四计数；失败给原因 + 「游标未推进」）
  - [x] 状态行：上次同步时间、待处理冲突数；文案**只有计数与时间，不含账目明细**（沿用 F-14 隐私口径）
  - [x] 页面明示「仅同步账目数据，不含短信原文/邮箱凭据/白名单」（DEC-034）+ 同步前自动留备份说明
  - [x] 冲突入口：`sync_conflicts` 最近 20 条，展示类型 / 短 id / 处置口径 / **两份副本原文**（可手工恢复）
- 证据：`SyncPresenterTest` **21 例 / 0 失败**；全量 JVM **481 例 / 0 失败 / 0 跳过**（60 套件）
- 口径说明：业务判定与全部文案抽到 `SyncPresenter`（纯函数，有单测）与 VM，Composable 不含业务规则——
  避免出现「界面说的」和「代码做的」不一致。

### TASK-068: F-15 端到端联调（本机 API + 真机）+ 文档回写

- 状态：**基本完成（2026-10-10）**：客户端↔服务端**真实 HTTP 端到端**通过，**模拟器设备端端到端也通过**；
  仅剩**真机**（`A1B2C3D4`，需用户决定是否卸载正式版）
- 依赖关系：TASK-061~067 ✅（TASK-069 环境 ✅）
- 验收标准：
  - [x] **SQLite 全量测试绿**（47 例 / 0 失败，2026-10-10，`server/tests/`）
  - [x] **客户端↔服务端真实 HTTP 端到端（无设备部分，新增）**：`SyncApiE2ETest` 用**生产代码路径**
        （`SyncApi` 真 OkHttp + 真 DTO + 真错误码映射）打真在跑的 API（真 MySQL），覆盖
        注册 → 登录 → `me` → 推送 3 类实体 → 拉回（中文 + emoji **逐字一致、UTF-8 44 字节相等**）→
        重复推送 **no-op**（`applied=0` 且 `server_seq` 不前进）→ 墓碑下行（`deleted=true`）→ `status` 计数 →
        `refresh` 轮换 → **旧 refresh token 再用被拒** → 坏令牌统一归类 `TOKEN_INVALID`。
        另做 **MySQL 侧独立核验**：`sync_records` 3 条 / 1 条墓碑 / `updated_at_raw` 与客户端原样逐字一致 /
        墓碑 `payload` 为 **JSON literal null**（`JSON_TYPE='NULL'`，非 SQL NULL，且旧描述已不残留）。
        **2026-10-10 通过：1 例 / 0 失败 / 3.05s**（默认由 `app/build/e2e-base-url.txt` 开关控制，未配置时如实 skipped）
  - [x] **模拟器（Pixel_7，`emulator-5554`）设备端端到端（新增，2026-10-10）**：`SyncDeviceE2ETest` 走**生产代码路径**
        （真 Room v4 + 真 OkHttp + 真 UseCase）：A 注册 → 推 5 个实体 → B 注册并「仅下载」→ 两端条数/金额一致 →
        两端各改一条 → 双向同步 → **冲突留档可见** → 断网/中断后**游标不前进、本地数据不变** → 重试成功。
        `:app:connectedDebugAndroidTest` → **18 例 / 0 跳过 / 0 失败**（含 `MigrationV3To4Test` 18/18），`BUILD SUCCESSFUL`；
        **DEC-037 存储改版后复跑同样全绿**（证明客户端对「云端换成 6 张业务表」零感知）
  - [ ] 真机 `A1B2C3D4` 走同一流程（**需用户决定**：真机装着用户真实数据的正式版且签名不同，装 debug 包会要求卸载 ⇒
        可批准「导出备份 → 装 debug → 验证 → 还原」，或明确维持「真机不测」）
  - [x] SQLite 全量测试绿；**再用 MySQL 8（TASK-069 的环境）跑同一套测试**全绿 —— 引擎差异必须在这一步暴露（双 DSN）
        → **2026-10-10 已完成**：MySQL（`minibook_test`）与 SQLite 各 **61 例 / 0 失败**（含 TASK-064 的 3 例日志隐私、
        TASK-070 的 6 例类型化存储与迁移往返），两次 `exit=0`；正是这一步暴露了「排序规则落到 `utf8mb4_0900_ai_ci`」
        与「`downgrade` 先 `DROP INDEX` 撞外键」等真缺陷
  - [x] 文档回写：`14_cloud_sync.md`（§12 实现状态 / 差分 / 挂账）、`09_test_report.md`（MySQL 双 DSN 轮）、`CHANGELOG.md`、
        `server/README.md`（MySQL 段 + 两个坑）→ **已完成 2026-10-10**；`10_release_notes.md` 已补
        「**未发布：F-15 云端同步**」节（交付内容 / 构建与运行 / 发布前清单 / 已知限制），**仍未发版**
- 备注：构建 / 测试 / 真机操作由**主代理单点串行**执行（规则 3）；**git 提交需逐次申请**（规则 2）。

### TASK-069: F-15 MySQL 环境落地（**已完成，2026-10-10**）

- 状态：**已完成** —— 用户选定「路径 1：提供 MySQL 连接信息」，并交付了内网 MySQL 的部署文档（`E:\aiCode\云服务\`）
- 依赖关系：已解除对 TASK-061 MySQL 建表验收、TASK-068 MySQL 联调的阻塞
- 实际环境：内网 **MySQL 8.0.46**（docker `mysql:8.0`，`<内网主机IP>:49779`，`utf8mb4` / `utf8mb4_unicode_ci`，
  `default-time-zone=+08:00`）；业务库 `minibook`、测试库 `minibook_test`（**测试库需管理账号经宿主机 `docker exec` 建库并授权**：
  业务账号无 GRANT OPTION）。凭据只落两处：`server/.env`（gitignored）与用户的部署文档，**不入库**；
  文档与测试里只用脱敏示例（`user@123` → `user%40123`）。
- 验收标准：
  - [x] `mysql+pymysql://…?charset=utf8mb4` 可连（口令走 `.env`，不入库、不打日志；PS 侧按 URL 编码写 `%40`）
  - [x] `alembic upgrade head` 建表成功：**六张表全 InnoDB + `utf8mb4_unicode_ci` + `DATETIME(6)` + JSON 列**；
        且 `downgrade base → upgrade head` 双向真跑通过
  - [x] 同套 pytest 在 MySQL 上全绿（**52 例 / 0 失败**，与 SQLite 结果一致；junitxml 双份核验）
- 顺带收口：`test_health` 不再把 `db == "sqlite"` 写死；新增 `test_mysql_downgrade_drops_tables_not_indexes_first`
  与本轮 `%` 编码回归用例（`test_migration` 5 例）
- 未做（如实挂账）：在 `minibook` 上跑「生产形态」的真实用户数据迁移演练（当前业务库为空，仅建表 + 种子行；
  待 Android 客户端联调时用真数据走一遍）

### TASK-070: F-15 云端存储改版 —— JSON 通用表 → 6 张真实业务表 + `sync_log`（**已完成，2026-10-10**）

- 状态：**已完成**。触发：用户验收云端库时否决「业务字段塞进 `payload` JSON」的通用记录表
  （原话「不要这样，要在云端做明确的表，设计表结构」「你上云的数据都啥，用户的账单数据没有上？」）——
  JSON 存储让云端**无法用 SQL 查账**，违背「数据上云」的初衷。
- 依赖关系：TASK-061~069 ✅
- 验收标准：
  - [x] 云端按实体建 **6 张类型化业务表**（`ledgers`/`categories`/`transactions`/`debts`/`budgets`/`user_category_rules`，
        PK `(user_id,id)`，金额 `BIGINT` 存「分」、`date` VARCHAR(10)、`month` VARCHAR(7)，按查询形状建索引）
        + **`sync_log`**（PK `user_id`+`server_seq`，**拉取游标的唯一来源**）
  - [x] 墓碑 = 业务表**物理删行** + 日志 `deleted=1`（云端查账不被墓碑污染）；`status.counts` 改按日志 distinct `entity_id`
        （含墓碑）⇒ 与旧「记录行数」口径一致，客户端无需改
  - [x] **写协议零改动**：`payload` 形状/键名/DEC-036 语义/三模式/冲突留档全部不变 ⇒ **Android 零改动**；
        证据：JVM `SyncApiE2ETest`（真实 HTTP）**1 例 / 0 跳过 / 0 失败**，模拟器设备 E2E **18 例 / 0 失败**，改版后复跑
  - [x] 迁移 `0002_typed_entities`：建表 → 拆列回填 → 删旧表；`downgrade` 反向拼回；对脏行宽容（只留日志 + 告警，不中断迁移）
  - [x] 双 DSN `pytest` 各 **61 例 / 0 失败**（新增 `test_typed_storage.py` 6 例：映射三方对齐 / 真列落库 / SQL 聚合 /
        墓碑不落业务表 / 六类往返逐字相等 / 超长 422 / 省略 null 键；`test_migration.py` 补回填与降级往返）
  - [x] 真实开发库 `minibook`：`0001_init ⇄ 0002_typed_entities` 往返真跑，5 行旧数据**全部拆列、0 行跳过**；
        云端 SQL 聚合可用（`SELECT date, SUM(amount) FROM transactions GROUP BY date` → 88.88 元）；核验后测试数据已清理
  - [x] 文档回写：`07_decisions.md`（DEC-037）、`14_cloud_sync.md`（§3.3/§3.4/§4.6/§13 + 计数修订）、
        `03_database.md §12.2`、`CHANGELOG.md`、`server/README.md`、`06_tasks.md`
- 踩坑（全部已修并在双 DSN/真库上验证）：① 客户端 `explicitNulls=false` ⇒ 可空字段**整个缺键**，拆列必须 `.get()`
  （用下标会把真实历史行判成脏数据跳过 —— 实测真库 10 行里 6 条交易被跳过）；② 业务表的实体列叫 `id` 而不是 `entity_id`
  （`entity_id` 只存在于 `sync_log`）；③ 离线 `--sql` 模式没有连接，迁移里的数据搬运必须用 `op.get_context().as_sql` 判掉；
  ④ 整链 DDL 里必然出现 `JSON`（0001 建 `sync_records`、0002 才删），断言只能渲染 `0001_init:0002_typed_entities` 这一段。
- 未做（如实挂账）：`sync_log` 目前**只增不删**（无归档/裁剪）；云端不做物化视图与报表 UI（本轮只要「表在、能聚合」）。

---

### TASK-071: App 账号登录可用性 + 自动云同步（**已完成，2026-10-12**）

- 状态：**已完成**。触发：用户纠正方向——原话「**我要的是 app 登录，不是做一个云同步的功能。是 app 能登录，
  在设置里可以自动云同步或者手动同步**」；同轮选定「自动 = 打开 / 回到前台静默同步」「登录 = 设置首页「账号」行」。
- 依赖关系：TASK-065~070 ✅
- 交付物：
  1. 设置首页一级「**账号**」行（未登录／已登录两态）→ 直达 `settings-cloud`；
  2. 「账号与同步」页新增**自动同步**开关（默认开，可关）；
  3. `AutoSyncManager`：`MainActivity.onStart()` 触发、≥5 分钟节流、已登录 + 开关开才跑、固定双向；
     **危险计划（`SyncPlan.needsConfirm`）不自动执行**；失败不打扰；
  4. 登录失败**可见化**：`saveSession` 落盘后读回验证（读不回即抛可读错误）；`KeystoreSecretCipher` 失败重试一次 + 自解释消息。
- 验收标准：
  - [x] JVM 单测：`AutoSyncManagerTest`（节流边界 + 危险计划口径）、`SyncAuthStoreTest` 新增「令牌不可读必须抛错」
        ——实跑 **489 例 / 0 失败 / 0 跳过**
  - [x] 设备测：`SyncAuthStoreDeviceTest`（真 Keystore 包裹往返 + 会话落盘可读回）
        ——实跑 **21 例 / 0 失败 / 0 跳过**（含本任务新增的启动冒烟 `MainActivityStartupTest`）
  - [x] 模拟器实测：登录后**重启 App 仍为已登录**；设置首页「账号」行显示「已登录：demo_user_b」
  - [x] 手动三模式回归不受影响（原设备 E2E 全绿）
  - [x] **自动同步实测**（导入用户真实手机备份后）：`GET /sync/pull` + **6× `POST /sync/push` 全 200**；
        云端 `ledgers 1 / categories 25 / transactions 若干 / debts 8 / sync_log 若干`，与备份逐项一致；
        同步状态显示「上次同步 10-12 01:45（双向同步）」、待处理冲突 0
- 边界（如实挂账）：不做后台定时同步（无明确需求 + 需新依赖 + 常驻 worker）；自动同步**永不**执行会删本地数据的计划。
- 教训（已补测）：自动同步起初把 `AutoSyncManager` 直接注入 `MainActivity`，而它依赖 `SyncUseCase` ⇒ `SyncStateDao`，
  数据库只经 `DatabaseGate` 暴露 ⇒ **解锁前构造直接崩**，App 一启动就退。当时 **20 例设备测全绿**——因为它们都不启动 Activity。
  现已新增 `MainActivityStartupTest`（真 Application + 真 Hilt 图 + `ActivityScenario` 启动 `MainActivity`）守住这条线。

---

### TASK-072: 导入后「当前账本」指针归位（**已完成，2026-10-12**）

- 状态：**已完成**。触发：用户要求「在我手机导出的备份上模拟器导入，然后试验一下」。
- 症状：导入**成功后**首页显示 **¥0.00 / 暂无交易记录**（账本页只剩「默认账本」且无「当前」标记），
  用户会判定「导入失败」——而数据其实**已全部落库**（同一份数据随后被自动同步推上云 若干条）。
- 根因：非 legacy 导入会**重建 ledgers 并重新生成 id**（TASK-008 既定行为），`LedgerDataStore` 的
  `current_ledger_id` 仍指向旧 id ⇒ 悬空 ⇒ 一切「按当前账本」的查询为空。原实现把责任推给 UI 层
  （旧注释「刷新由 UI 层配合」），实际无人执行。
- 修复：`ImportAllUseCase` 注入 `LedgerDataStore`，在**事务成功后**用 `ledgerRepository.getDefault()?.id`
  写回当前账本（刻意放在事务外：回滚时指针不动，绝不指向不存在的账本）。
- 验收：模拟器 `pm clear` → 重新导入同一备份 → 首页**直接**显示 `2026年10月结余 ¥-2,474.02`
  （修复前同为 `¥0.00`）；全量回归 JVM 489 例 / 设备 21 例全绿。

### TASK-073: 「账号」二级页 + 服务地址内置不暴露（**已完成，2026-10-12**）

- 状态：**已完成**。触发：用户四条要求——① 设置里「账号」点进去二级页标题必须是「账号」（与入口同名）；
  ② 「云端账号配置不要了」；③ 增加用户信息区（改资料 / 登录 / 退出登录 / 注销）；④ 「服务地址不要暴漏出来」。
- 做法（②+④ 合并解读：**地址整块从界面拿掉**）：
  1. 新增根图路由 `settings-account` + `SettingsAccountScreen`（顶栏标题「**账号**」）+ `AccountViewModel`；
     设置首页「账号」行改走 `SettingsNavigation.openAccount()`——原先错走到「云同步」页，所以标题与入口不一致。
  2. 账号能力全部搬到该页：登录 / 注册、**用户信息**（显示名 / 用户名 / 注册时间）、**修改资料**（显示名，可清空）、
     **修改口令**、**退出登录**、**注销账号**（口令二次确认）。
  3. 「云同步」页只留同步（自动同步开关 + 手动三模式 + 预览 / 结果 / 冲突留档）；未登录时给一行「未登录 → 去账号页」。
  4. 服务地址不再由用户填写 / 看到：`BuildConfig.SYNC_BASE_URL` 按构建类型注入（debug 默认 `http://127.0.0.1:8000`；
     release **留空 = 待填部署域名**，界面提示「本安装包未内置服务器地址」）；仅**调试版**保留一个地址输入框
     （开发临时改指向），正式版界面里没有地址。
- 服务端配套：`users.nickname`（迁移 `0003_user_nickname`）、`PATCH /api/v1/me`（改昵称 / 改口令；改口令会撤销
  **全部**旧刷新令牌并换发新令牌对）、`POST /api/v1/auth/delete-account`（口令确认后删云端 6 张业务表 + `sync_log`
  + 刷新令牌 + 设备 + 用户行）。
- **不删本地数据**：退出登录 / 注销只动云端账号，本机账目一条不删（界面文案明确写出）。
- 验收：服务端 pytest **SQLite 68 例 / MySQL 68 例，0 失败**（新增 7 例）+ `alembic upgrade head` 已打到真库
  `0003_user_nickname`；JVM 单测 **495 例 / 0 失败**（新增 6 例：显示名回退、改口令前置校验、改资料请求的 null 语义、
  响应缺字段降级）；主机直连新端点 7 步全通过（register → login → me → PATCH 昵称 → PATCH 空串清除 → 注销 → 删除后 me 401）；
  模拟器实测见 `09_test_report.md`（账号页标题「账号」、改显示名、退出、注销；注销用**一次性账号**，
  `demo_user_b` 里用户真实数据的云端副本未被触碰）。
- 顺带修掉一个**只在真机暴露**的缺陷：`SyncApi.callRaw` 把「读响应体」放在 `withContext(ioDispatcher)` **外面**
  （小响应侥幸命中缓冲区，大响应就在调用方线程真读 socket）⇒ 账号页点「登录」报
  `操作失败：NetworkOnMainThreadException`。自动同步没暴露它，因为 `SyncUseCase` 整段已在 IO 上。
  现已把 `execute()` 与读 body 一起收进 IO 上下文，并在调试版打印异常栈便于定位。

---

（本文件为任务清单，随实现对细节的差分由后续修订更新；版本历史见 CHANGELOG.md。）

### TASK-074: 登录 / 注册独立页 + 账号资料扩充 + 云同步入口去重（**已完成，2026-10-12**）

- 需求（用户原话）：「注册时间展示的不对」「登录和注册单独做页面，要做的像一个 APP 的登录注册页面一样」
  「云同步功能在账号页面有，在数据页面还有，只保留一个就行」「用户的资料有点少，你看相应的增加几个」。
- 实现：
  1. **注册时间修正**：服务端 `UserOut` 用 `@field_serializer` 统一输出**带 `Z` 的 UTC** ISO 串（原先
     `UTCDateTime` 读出来是 naive UTC、序列化不带时区 ⇒ 客户端 `Instant.parse` 解析失败 ⇒ 原样显示 UTC，
     比本地时间早 8 小时）；客户端 `SyncPresenter.humanTime` 兜底「没带时区按 UTC 解释」，并新增
     `fullTime`（`yyyy-MM-dd HH:mm`）用于「注册时间 / 上次登录」。
  2. **登录 / 注册独立成页**：新增路由 `settings-login` / `settings-register` + `AuthViewModel` +
     `AuthComponents`（`AuthPage` / `AuthField` / `AuthPrimaryButton` / `AuthErrorMessage` / `AuthSwitchRow`）；
     注册页带「确认口令」，注册成功即自动登录并返回账号页；错误文案写在按钮下方（不再只靠 toast）；
     服务器地址输入框只在调试版登录页出现；两页互切时把对方从回退栈换掉，避免来回弹。
  3. **云同步入口去重**：删掉账号页的「云同步」行，入口只留 **设置 → 数据 → 云同步**。
  4. **资料扩充**：`users.email`(254) / `bio`(80) / `region`(32)（迁移 `0004_user_profile_fields`）+
     只读「上次登录」（最近一次**首发**刷新令牌时间，令牌轮换不算）与「账号 ID」；账号页共 8 项；
     新增「编辑资料」独立页（`settings-profile-edit`），保存**只提交改动过的字段**（null = 不改，空串 = 清除）。
- 测试：见 `09_test_report.md` 最新章节（服务端 pytest SQLite + MySQL、JVM 单测、模拟器验收）。
- 影响文件：`server/app/{models,schemas,routers/auth}.py`、`server/alembic/versions/0004_user_profile_fields.py`（新）、
  `server/tests/test_auth.py`、`app/src/main/java/com/minibook/ui/settings/{AuthViewModel,AuthComponents,LoginScreen,RegisterScreen,ProfileEditScreen,AccountViewModel,SettingsAccountScreen,SyncPresenter}.kt`、
  `app/src/main/java/com/minibook/data/remote/SyncDtos.kt`、`app/src/main/java/com/minibook/ui/navigation/{Destinations,MiniBookNavHost}.kt`。
- 结果（实跑，2026-10-12）：
  - 服务端 `pytest` 双 DSN 各 **73 例 / 0 失败**（68→73，+5）；`alembic upgrade head` 已打到真库
    `0004_user_profile_fields`；主机直连 E2E 通过（`created_at` / `last_login_at` 均**带 `Z`**；
    只传 `{bio:""}` 时其它字段保持不变；非法邮箱 422；空 body 400 `nothing_to_update`）。
  - 客户端 JVM `:app:testDebugUnitTest` **504 例 / 0 失败 / 0 跳过**（499→504，+5）；`:app:assembleDebug` 出包。
  - 模拟器（Pixel_7 / emulator-5554，时区 GMT）实测：账号页未登录态 → 注册页 → 注册即登录 → 账号页 8 项 →
    编辑资料保存后自动返回且只有改动字段生效 → **登录页**渲染 → 数据页只剩一处云同步入口，全部通过。
- 附带修复 **F-15o**（只有跑起来才暴露）：登录 / 注册页的应用图标原先用 `painterResource(R.mipmap.ic_launcher)`，
  而该资源是**自适应图标 XML** ⇒ `IllegalArgumentException: Only VectorDrawables and rasterized asset types
  are supported ex. PNG, JPG, WEBP`（栈首帧 `AuthComponents.kt:68`）⇒ 点「注册新账号」直接闪退；
  **编译绿 + JVM 504 例全绿都抓不到**。已改用矢量 `R.drawable.ic_launcher_foreground` + 圆角底衬。
  同类隐患未改：`SmsNotificationHelper.kt:72` / `:94` 的通知小图标仍是 `R.mipmap.ic_launcher`。
- 未做：真机 `A1B2C3D4` 安装与验收（用户明确要求先不动手机）；头像上传（无文件上传接口）。

---

### TASK-075: 登录 / 注册页交互规范化（**已完成，2026-10-12**）

- 状态：已完成
- 优先级：P1
- 负责角色：Developer
- 所属层：UI 层
- 关联需求：F-15m；DEC-042；`16_auth_ux_and_visual_plan.md` §1
- 涉及文件：
  - `app/src/main/java/com/minibook/ui/settings/AuthComponents.kt`（`AuthField` 加 errorText / imeAction / onImeAction / contentType / onToggleReveal / enabled；`AuthErrorMessage` 加 `LiveRegion`；返回键换矢量箭头）
  - `app/src/main/java/com/minibook/ui/settings/LoginScreen.kt`、`RegisterScreen.kt`
- 描述：
  - 口令可见性改为输入框 trailing 眼睛图标（`MiniBookIcons.Visibility / VisibilityOff`，`contentDescription` 随状态变）。
  - `KeyboardOptions(capitalization = None, autoCorrectEnabled = false, imeAction = …)`；`imeAction` 串成「下一步 → 完成」，最后一项回车即提交（`KeyboardActions(onNext/onDone/onGo)`，无自定义动作时 `moveFocus(Down)`）。
  - autofill 语义：`Modifier.semantics { contentType = ContentType.Username / Password / NewUsername / NewPassword }`。
  - 错误落到字段：`isError` + `supportingText` 三态（error 优先）；页面级错误用 `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` 供读屏播报。
  - 注册页「两次口令不一致」用 `SyncPresenter.confirmError` 归因到确认口令字段，页面级不重复显示同一句。
- 验收标准：
  - [x] `:app:testDebugUnitTest` 全绿（506 例 / 0 失败）
  - [x] `:app:assembleDebug` 成功
  - [x] 模拟器实测：登录页眼睛图标、返回箭头、错误内联展示、提交中禁用输入

### TASK-076: 隐私政策 / 用户协议与首启告知（**部分完成，2026-10-12**）

- 状态：部分完成。**已完成**：两份正文（唯一副本 `res/raw`）+ 应用内页面 + 设置页「关于」入口 + 注册页勾选（默认不勾）+ 登录页提示 + 守卫单测。**未完成**：首启告知弹窗（决策 D，用户未表态）、运营主体 / 联系方式 / 注册地（用户 2026-10-12 明确暂不声明）。
- 优先级：P1
- 负责角色：Developer
- 所属层：UI 层 + 文档
- 关联需求：DEC-043；《App违法违规收集使用个人信息行为认定方法》一.1–一.4 / 三.2 / 三.4 / 六.1–六.4；《移动互联网应用程序信息服务管理规定》第十六条
- 涉及文件：
  - `app/src/main/res/raw/privacy_policy.txt`（新，11 节）、`app/src/main/res/raw/terms_of_service.txt`（新，10 节）——**界面与上架材料同源**
  - `app/src/main/java/com/minibook/ui/settings/LegalScreen.kt`（新，`LegalDocument` 枚举 + 纯文本滚动展示）
  - `ui/settings/{SettingsNavigation,SettingsScreen}.kt`（「关于」两行 + 两个 open 方法）、`ui/navigation/{Destinations,MiniBookNavHost}.kt`（`settings-privacy-policy` / `settings-terms`）
  - `ui/settings/{AuthComponents,RegisterScreen,LoginScreen,AuthViewModel,SyncPresenter}.kt`（`AuthLegalNotice` + 勾选状态 + `agreementError`）
  - `app/src/test/java/com/minibook/ui/settings/LegalTextTest.kt`（新，3 例守卫关键条款与纯文本格式）
  - `docs/17_privacy_policy.md`（新）、`docs/18_terms_of_service.md`（新）
- 说明：注册页勾选**默认不勾**（191 号文三.4），未勾选时本地拦下并直说原因；登录页只放「登录即表示你已阅读并同意」+ 一对链接，不放勾选框（三.2 相反要求）。入口 2 跳可达（设置 → 关于 → 具体文本），满足一.2。
- 验收标准：
  - [x] 两份文本可点开读全文；正文里明写「上线前草稿，主体与联系方式尚未声明」
  - [x] 未勾选协议时注册被拦下且文案说清原因（JVM 单测覆盖 `agreementError`）
  - [x] `LegalTextTest` 3 例通过（关键条款仍在、无 Markdown 记号）
  - [ ] 首启告知弹窗（**待用户拍板 D**；严格按 191 号文一.1 仍是缺口，见 `docs/17` §5.2）
  - [ ] 运营主体 / 联系方式 / 注册地（**上架前必须补**）

### TASK-077: 口令策略与弱口令黑名单（**已关闭：用户拍板不做，2026-10-12**）

- 状态：已关闭。用户原话（2026-10-12）："A 弱口令的不用加，就简单的校验就行"。
- 关联需求：NIST SP 800-63B-4；ASVS 5.0 V6（6.2.1 / 6.2.4）；OWASP Authentication Cheat Sheet
- 结论：维持「用户名 3–32 位 + 口令 ≥ 8 位」的简单校验（客户端 `SyncPresenter.registerError` 与服务端 `RegisterRequest` 同规则）。**这是已知偏离而非疏漏**：NIST 对单因子口令建议 ≥15 位、ASVS 6.2.4 要求比对本机 top-3000 弱口令。若将来收紧，只改这两处 + `AUTH_RULES` 文案。
- 说明：弱口令黑名单未实施；服务端仍是唯一权威校验，客户端只做同规则前置提示（F-15k）。

### TASK-078: 视觉 token 层（色彩 / 形状 / 字体）（**已完成，2026-10-12**）

- 状态：已完成
- 优先级：P1
- 负责角色：Developer
- 所属层：UI 层（主题）
- 关联需求：DEC-042；`16_auth_ux_and_visual_plan.md` §6.4 第 1/2/4/5/6/14 条
- 涉及文件：
  - `app/src/main/java/com/minibook/ui/theme/Color.kt`（青绿基线 + `surfaceContainerLowest…Highest` / `surfaceDim` / `surfaceBright` / `inverse*` 全族；语义色静态）
  - `app/src/main/java/com/minibook/ui/theme/Theme.kt`（形状 6/12/16/**20**/28dp、`FullShape` 28dp、`onColorFor` 按亮度取 on 色）
  - `app/src/main/java/com/minibook/ui/theme/Type.kt`（`MoneyLarge` 28sp / `MoneyMedium` 20sp，均 `fontFeatureSettings = "tnum"` + SemiBold）
- 说明：`dynamicColor = true` 仍为默认（壁纸取色下强调色来自系统）；静态青绿基线用于 API<31、关闭动态取色与自定义主题色路径。

### TASK-079: 自带矢量图标层（**已完成，2026-10-12**）

- 状态：已完成
- 优先级：P1
- 负责角色：Developer
- 所属层：UI 层（组件）
- 关联需求：DEC-042；决策 E（不引入 `material-icons-extended`）
- 涉及文件：
  - `app/src/main/java/com/minibook/ui/components/MiniBookIcons.kt`（新，18 个 24dp 图标；路径取自 Material Icons / Apache-2.0；`internal val all` 供单测遍历）
  - `app/src/test/java/com/minibook/ui/components/MiniBookIconsTest.kt`（新，2 例：可解析 + 名称唯一）
  - 调用方：`ui/main/MainScreen.kt`、`ui/home/HomeScreen.kt`、`ui/components/{Cards,Settings,Navigation,States}.kt`、`ui/settings/LedgerManageScreen.kt`、`ui/budgets/BudgetsScreen.kt`
- 说明：替换底部导航 5 个 emoji、🔍、✏️/🗑、返回 ←、月切换 ‹›、FAB ＋、快捷入口图标；**账本图标选择器（用户可选 emoji）与文案中的「＋」字符刻意保留**。

### TASK-080: 面板分层替换与逐页视觉回归（**已完成，2026-10-12**）

- 状态：已完成
- 优先级：P1
- 负责角色：Developer / Tester
- 所属层：UI 层
- 关联需求：`16_auth_ux_and_visual_plan.md` §6.4 第 3 条
- 涉及文件：`ui/components/States.kt`、`ui/budgets/BudgetsScreen.kt`、`ui/billimport/{ImportScreen,ImportComponents,SmsImportContent}.kt`、`ui/home/HomeScreen.kt`、`ui/reports/ReportsScreen.kt`、`ui/reports/components/{ChartEmptyState,ReportSummaryCard}.kt`
- 说明：`surfaceVariant + alpha` 面板与显式 `surface` 卡片改 `surfaceContainerLow / High / Highest`；Chip、进度/环图轨道、`MoneySemantic.NONE` 等**语义用色保留**。
- 验收标准：
  - [x] JVM 506 例 / 0 失败；`assembleDebug` 成功
  - [x] 模拟器浅色逐页（首页 / 设置 / 账号 / 登录）+ 暗色回归通过（详见 `09_test_report.md` 顶部章节）

### TASK-081: 首页 Hero 卡环比（只给可核对的绝对差额）（**已完成，2026-10-12**）

- 状态：已完成
- 优先级：P2
- 负责角色：Developer / Tester
- 所属层：UI 层（首页）+ 领域读取
- 关联需求：DEC-043（决策 G）；`16_auth_ux_and_visual_plan.md` §6.4 第 4/12 条与 §6.6 反例第 5 条
- 涉及文件：
  - `app/src/main/java/com/minibook/ui/components/Cards.kt`（`NetBalanceTrend` + `NetTrendLine`；`NetBalanceCard` 新增可选 `trend`）
  - `app/src/main/java/com/minibook/ui/home/HomeViewModel.kt`（`HomeUiState.Empty/Success` 新增 `previousBalanceFen`；纯函数 `previousBalanceOrNull`）
  - `app/src/main/java/com/minibook/ui/home/HomeScreen.kt`（透传 `previousBalanceFen` 到 `MonthBalanceCard` → `NetBalanceCard`）
  - `app/src/test/java/com/minibook/ui/components/NetBalanceTrendTest.kt`（新，5 例）、`app/src/test/java/com/minibook/ui/home/HomeViewModelTrendTest.kt`（新，3 例）
- 描述：结余金额下方一行环比——「较上月 +¥X / 较上月 ¥-X / 与上月持平 / 上月无记录」。
- 口径（写进 KDoc 与单测）：
  - **只给绝对差额，不给百分比**：上月基准为 0 时百分比无定义，硬算等于编造数据。
  - 上月收支全为 0 ⇒ 视为「上月无记录」，不显示对比（否则本月任何金额都会显示成「较上月 +¥X」）。
  - 涨跌用**符号 + 语义色双编码**（收入色/支出色），不依赖颜色单独传达方向。
- 验收标准：
  - [x] JVM 新增 8 例全绿（`NetBalanceTrendTest` 5 + `HomeViewModelTrendTest` 3）
  - [x] 模拟器实测：首页结余卡出现环比行，且「上月无记录」与「较上月 ±」两种形态都正确

### TASK-082: 未登录态收为登录按钮 + 设置页「账号」第一行 + 内置分类新图标集（**已完成，2026-10-12**）

- 状态：已完成
- 优先级：P1
- 负责角色：Developer / Tester
- 所属层：UI 层（设置页 / 账号页 / 分类全链）
- 关联需求：用户 2026-10-12 三条指令（原话：「1、新修改的还是不对，用户未登录时候不要展示一个大框，做成一个让用户登录的按钮即可。2、账号设置提到设置页面第一行。再还有现在的内置分类这套图标感觉太旧了，能不能设计一批新的」）；DEC-044
- 涉及文件：
  - `app/src/main/java/com/minibook/ui/settings/SettingsAccountScreen.kt`（`GuestLoginCard` → `GuestLoginButtons`：满宽「登录」+ 文字链「还没有账号？注册新账号」）
  - `app/src/main/java/com/minibook/ui/settings/SettingsScreen.kt`（「账号」行提到设置页第一行，在「账本」之前）
  - `app/src/main/java/com/minibook/ui/components/CategoryIcons.kt`（新：24 个 Material Symbols Rounded 矢量，稳定 key + `LEGACY_EMOJI` 兜底）
  - `app/src/main/java/com/minibook/domain/model/BuiltInCategories.kt`（24 条模板 `icon` 由 emoji 换成 key）
  - `app/src/main/java/com/minibook/ui/components/CategoryComponents.kt`（新渲染入口 `CategoryGlyph`；分类格改用它）
  - `app/src/main/java/com/minibook/ui/components/ListItems.kt`（交易列表行分类圆底改 `CategoryGlyph`）
  - `app/src/main/java/com/minibook/ui/transactions/TransactionsScreen.kt` / `TransactionDetailScreen.kt`（分类下拉 / 详情分类行）
  - `app/src/main/java/com/minibook/ui/budgets/BudgetProgressCards.kt` / `BudgetEditorSheet.kt`（预算卡 / 预算编辑）
  - `app/src/main/java/com/minibook/ui/search/components/SearchConditionComponents.kt`（搜索筛选）
  - `app/src/main/java/com/minibook/ui/categories/CategoryEditorSheet.kt`（手输 emoji 换成 6×4 图标网格）
  - `app/src/test/java/com/minibook/ui/components/CategoryIconsTest.kt`（新，3 例）
- 描述与口径：
  - ① 未登录态：**不做大框**，只留满宽「登录」主按钮 + 「还没有账号？注册新账号」文字链（用户明确否决 F-15t 资料头卡）。
  - ② 设置页「账号」提为第一行：它是唯一身份 / 跨设备入口，未登录时是最可能的行动点。
  - ③ 内置分类图标：emoji → 自带矢量（Iconify 抓取 Material Symbols Rounded，24×24 网格）；`Category.icon`
    仍是 String（**不改 Room schema、不做数据迁移**），历史 emoji 由 `LEGACY_EMOJI` 兜底映射；渲染统一走
    `CategoryGlyph`（key → 矢量、可按分类色 `tint` 着色；未知串 / 历史 emoji → 文本兜底，绝不空白）；
    分类编辑器不再提供手输 emoji（老数据 emoji 不点就不改、原样保留）。
- 验收标准：
  - [x] JVM 521 例 / 0 失败 / 1 跳过（新增 `CategoryIconsTest` 3 例：24 路径逐个可解析 + key 唯一 + 旧 emoji 兜底齐全）
  - [x] `assembleDebug` 成功（APK 39,090,923 字节，sha256 `456A17C0…C5D1`）
  - [x] 模拟器实测（见 `09_test_report.md` 顶部章节）
  - [x] 真机安装验收（2026-10-13，用户当次批准「给我手机装上」）：`install -r` 原地覆盖成功，装前/装后数据 tar 逐字节一致（7,434,240 字节，sha256 `2CF95C5B…E18D`），`firstInstallTime` 未变、`lastUpdateTime` 更新，启动无崩溃，新矢量图标在真机「编辑分类预算」表单中确认渲染（细节见 `09_test_report.md` 真机章节）

### TASK-083: 五条体验问题（昵称 / 账号 ID / 账本图标 / 预算总额删除 / 分类预算删除刷新）（2026-10-13）

- 来源：用户原话「1、显示名改一下，叫昵称 2、账号ID不要展示。3、账本的图标也换一下 4、预算填写了不能删除或者清空。5 分类预算删除后不立马刷新，返回再进来才会删除。」（2026-10-13 消息）
- ① 显示名 → 昵称（纯文案）：账号页 / 编辑资料页 / 登录页统一改口径「昵称」；`AccountViewModel.UiState.userId` 保留但不再有 UI 引用。
- ② 账号 ID 不展示：账号页 `ProfileSection` 由 8 行减为 7 行（昵称 / 用户名 / 邮箱 / 个性签名 / 地区 / 注册时间 / 上次登录）。
- ③ 账本图标换新：新建 `ui/components/LedgerIcons.kt`——16 个 Material Symbols Rounded 矢量（稳定 key：`book` / `notebook` / `journal` / `stories` / `library` / `notes` / `receipt` / `stack` / `work` / `savings` / `bank` / `card` / `chart` / `shopping` / `home` / `car`）+ `LEGACY_EMOJI`（📒→book、📓→notebook、📔→journal、📕→stories、📗→library、📘→notes、📙→receipt、📚→stack、💼→work、💰→savings、🏦→bank、💳→card、📊→chart、🛒→shopping、🏠→home、🚗→car）+ `resolve()` / `keyVector()` / `pickerKeys` / `all` / `allKeys` / `legacyEmojiCount`；`@Composable LedgerGlyph(icon, tint, …)` 历史 emoji 由 LEGACY_EMOJI 兜底映射，未知串 → 文本兜底，绝不空白。渲染点收口：`ui/components/Pickers.kt:140`（18dp）、`ui/settings/LedgerManageScreen.kt:248`（26dp）、`ui/components/LockComponents.kt:66`（BOOK，44dp）；`ui/settings/LedgerEditorSheet.kt` 的 emoji 快捷行 + 手输框换成 6 列矢量网格（44dp 圆、选中 `primaryContainer` + primary 描边）。`BootstrapUseCase` / `ImportAllUseCase` 的 `DEFAULT_ICON` 由 `"📒"` 改 `"book"`（写字符串字面量，避免 domain 依赖 ui）。**不做数据库迁移**：DB 里仍是字符串，历史 emoji 靠映射渲染。
- ④ 总额预算可删除 / 清空：新建 `domain/usecase/budget/ClearTotalBudgetUseCase.kt`；`BudgetsViewModel.clearTotalBudget()`（成功 → 提示「已删除月度总预算」+ 触发刷新）；`BudgetsScreen` 总额弹层在「当月已设过总额」时传 `onDelete`；`BudgetEditorSheet` 删除按钮条件改为 `canDelete = when (mode) { CATEGORY -> selectedCategory != null; TOTAL -> initialAmountFen != null }`，文案按模式分「删除该分类预算」/「删除月度总预算」，总额模式强确认文案「确定删除当月的月度总预算吗？已记录的交易与分类预算不受影响。」
- ⑤ 分类预算删除后立即刷新——**根因**：删分类预算走的是 `RoomDataSourceImpl.deleteByCategory` 的裸 `writableDatabase().delete("budgets", …)`，绕过 Room 失效通知 ⇒ 预算进度 Flow 不重发（`WhileSubscribed(5s)` 超时重连后才刷新，于是「退出页面再进才生效」）；这也解释了「保存会刷新、删除不会」（保存走 DAO `upsert`）。**修复**：`BudgetDao` 新增 `@Query("DELETE FROM budgets WHERE ledgerId=:ledgerId AND month=:month AND categoryId=:categoryId") suspend fun deleteCategoryBudget(…)`，`RoomDataSourceImpl.deleteByCategory` 改走它，写入与失效通知同一路径闭合。
- 涉及文件：`app/src/main/java/com/minibook/ui/settings/SettingsAccountScreen.kt`（「显示名」→「昵称」；删「账号 ID」行，7 项）、`app/src/main/java/com/minibook/ui/settings/ProfileEditScreen.kt`、`app/src/main/java/com/minibook/ui/settings/LoginScreen.kt`、`app/src/main/java/com/minibook/ui/settings/AccountViewModel.kt`（KDoc）、`app/src/main/java/com/minibook/ui/components/LedgerIcons.kt`（新）、`app/src/main/java/com/minibook/ui/components/Pickers.kt` / `app/src/main/java/com/minibook/ui/settings/LedgerManageScreen.kt` / `app/src/main/java/com/minibook/ui/components/LockComponents.kt`、`app/src/main/java/com/minibook/ui/settings/LedgerEditorSheet.kt`、`app/src/main/java/com/minibook/domain/usecase/ledger/BootstrapUseCase.kt` / `app/src/main/java/com/minibook/domain/usecase/export/ImportAllUseCase.kt`、`app/src/main/java/com/minibook/domain/usecase/budget/ClearTotalBudgetUseCase.kt`（新）、`app/src/main/java/com/minibook/ui/budgets/BudgetsViewModel.kt` / `BudgetsScreen.kt` / `BudgetEditorSheet.kt`、`app/src/main/java/com/minibook/data/local/db/BudgetDao.kt` + `app/src/main/java/com/minibook/data/local/db/RoomDataSourceImpl.kt`、`app/src/test/java/com/minibook/ui/components/LedgerIconsTest.kt`（新）/ `app/src/test/java/com/minibook/domain/usecase/budget/ClearTotalBudgetUseCaseTest.kt`（新）/ `ClearCategoryBudgetUseCaseTest.kt`（新）、`app/src/test/java/com/minibook/testutil/FakeRepositories.kt`。
- 验收标准：
  - [x] JVM 527 例 / 0 失败 / 0 错误 / 1 跳过（521 → 527，新增 6 例；唯一跳过项为需真实服务端的 `SyncApiE2ETest`）
  - [x] `assembleDebug` 成功（APK 39,092,700 字节，2026-10-13 09:19:43，sha256 `3099F8193E815EC0A6837036E01FED08FDCF5B0D699FFC3F843555F2459647DC`）
  - [x] 模拟器实测（见 `docs/09_test_report.md` 顶部章节）：账本矢量图标 / 新建账本 6 列网格 + 选中态 / 总额删除入口 + 专属确认文案 / 删除后立即回空态 / 分类预算删除后**不离开页面**立即刷新 / 账号页 7 项无账号 ID / 编辑资料页「昵称」
  - [x] 真机安装验收（2026-10-13，用户当次批准「装手机上」）：`install -r` 原地覆盖成功；装前 / 装后数据 tar **逐字节一致**（10,849,280 字节，sha256 `EF992D3710C00F980CB87E4CC39E62B25F90996C833773C40FEA2D8A659AB620`）；`firstInstallTime` 未变、`lastUpdateTime` → 2026-10-13 09:53:12；启动无崩溃（`logcat -b crash` 空）；**手机内 `base.apk` 与本地产物 sha256 一致**（`3099F819…647DC`）。未做真机逐页点击走查（见 `docs/09_test_report.md` 真机章节）

### TASK-084: docs/15 分类规则与短信导入整改（七条）+ 首页/留白即时需求（2026-10-13，已完成）

- 触发：用户两条即时需求（「页面与上部空白太多」「首页增加登录信息展示」）+ docs/15 七条逐条讨论后全部拍板（用户 2026-10-13）。
- 完成清单（全部已实现并通过 JVM 单测与模拟器实测）：
  1. 即时需求一：`MainScreen` `Scaffold(contentWindowInsets = WindowInsets(0,0,0,0))` 修顶部双倍留白（F-16a）。
  2. 即时需求二：首页 `HomeAccountRow` 账号信息行（未登录「未登录 + 登录」/ 已登录「昵称 + 账号设置 ›」，整行可点跳 `settings-account`）；`SyncAuthStore` 增 `KEY_NICKNAME` / `updateNickname` / `displayName`；`AccountViewModel` 在 `/me` 与保存资料后回写；`HomeViewModel.accountDisplay`（F-16a）。
  3. docs/15 第 1 条：`CategoryRuleNormalizer`（4 步归一化 + STRONG/WEAK 强度 + 后缀表【刻意不剥裸「店/市/区/县/中心/广场」】）；学习侧 `UpsertUserCategoryRuleUseCase` 存核心词；`ImportBillUseCase` 按 STRONG → 核心词更长 → 更新新排序，`allowWeakRuleMatch` 仅预览/待确认路径，L2 只认 STRONG；算法文档 `docs/19_category_rule_normalization.md`（F-16b）。
  4. docs/15 第 2 条：导入来源清单——`SmsWhitelistEntry.importEnabled`、`SmsSenderSummary`、`SmsScanPreview.senders`（codec 5 段、旧 4 段兼容默认允许）；`SmsWhitelistStore.setImportEnabled` / `rememberSenders`；`SmsSenderNamer` 猜机构名（猜不出 null）；`SmsSenderSummaryBuilder`（账单数降序、同数按号码数值升序）；`scan()` 跳过未勾选来源进「非账单」；UI = 导入页「导入来源」弹框多选（默认全选）+ 设置页白名单每源两行开关（自动记账 / 允许导入）（F-16c）。
  5. docs/15 第 3 条：`ImportScreen` 主动申请 `READ_SMS`+`RECEIVE_SMS`（Android 13+ 加 `POST_NOTIFICATIONS`），授权后按同一 mode 继续扫（F-16d）。
  6. docs/15 第 4 条：贷款/授信营销短信三层预筛（回复指令特征 / 授信语义无强交易锚点 / 账单准入硬门槛），「利息」「消费」降级为需交易锚点；用户两条真实原文（398000）作 fixture；真实交易带「退订回T」不误杀；已入账脏数据按用户决定不清理（F-16e）。
  7. docs/15 第 5 条：日期区间联动——`OptionalDateField` / `DateFilterChip` 增 `minDateMillis`/`maxDateMillis`，M3 `SelectableDates` 把不可选日期置灰（F-16g）。
  8. docs/15 第 6 条：退款记「收入」+ 单独「退款」分类（`REFUND_KEYWORD` 优先判 INCOME，`platformCategory = "退款"`；`BillCategoryMapping` 短信侧条目；不做配对/红冲/模型改动）（F-16f）。
  9. docs/15 第 7 条：内置商户词典（用户定案「联网搜一波大的当内置，学习留在 App 本地」，否决众包）——`res/raw/merchant_dictionary.json` 1315 条 + `MerchantDictionary`/`BuiltInMerchantDictionary`/`MerchantDictionaryStore` + Hilt 绑定 + `ImportBillUseCase` 第 1.5 步（仅预览建议、L2 不查）；`docs/20_builtin_merchant_dictionary.md`（F-16h）。
- 涉及文件：`ui/main/MainScreen.kt`、`ui/home/HomeScreen.kt`、`ui/home/HomeViewModel.kt`、`ui/navigation/MiniBookNavHost.kt`、`data/remote/SyncAuthStore.kt`、`ui/settings/AccountViewModel.kt`、`domain/usecase/category/CategoryRuleNormalizer.kt`、`domain/usecase/category/UpsertUserCategoryRuleUseCase.kt`、`domain/usecase/bill/ImportBillUseCase.kt`、`domain/model/SmsModels.kt`、`data/local/sms/SmsWhitelistStoreCodec.kt`、`data/local/sms/SmsWhitelistStore.kt`、`domain/usecase/sms/SmsSenderNamer.kt`、`domain/usecase/sms/SmsSenderSummaryBuilder.kt`、`domain/usecase/sms/SmsBillImportUseCase.kt`、`domain/usecase/sms/SmsBillParser.kt`、`domain/model/BillCategoryMapping.kt`、`ui/billimport/ImportScreen.kt`、`ui/billimport/ImportViewModel.kt`、`ui/billimport/SmsImportContent.kt`、`ui/settings/SmsSettingsViewModel.kt`、`ui/settings/SmsSettingsSection.kt`、`ui/components/OptionalDateField.kt`、`ui/transactions/TransactionsScreen.kt`、`ui/search/components/SearchConditionComponents.kt`、`domain/usecase/category/MerchantDictionary.kt`、`domain/usecase/category/BuiltInMerchantDictionary.kt`、`data/local/dictionary/MerchantDictionaryJson.kt`、`data/local/dictionary/MerchantDictionaryStore.kt`、`di/DataSourceModule.kt`、`res/raw/merchant_dictionary.json` + 对应测试与文档（docs/09/10/15/19/20、CHANGELOG）。
- 测试：JVM **566 例 / 0 失败 / 1 跳过**（新增 39 例：归一化 13 + 短信解析 10 + 导入来源与词典 16）；APK 39,194,040 字节 sha256 `2FD386973C39D200D28C6EBE87128F59638B811EA970BB2AF9E9422E7864F9B1`。
- 实测：模拟器逐条（首页账号行两态、顶部留白坐标、交易页日期联动 14 个置灰日期、设置页「账号」第一行、短信账单页渲染）。
- 待办：真机安装需用户当次批准；`git commit` 待逐次批准；运营主体/首启弹窗（D）仍待拍板。
### TASK-085: 解密门门控 Flow —— 修复重锁瞬间主线程崩溃（F-16i，2026-10-13，已完成）

- 背景：真机安装后拉取 crash buffer，发现 **2026-10-13 10:11:13** 的 FATAL（`java.lang.IllegalStateException: DatabaseGate 未解锁/未打开，无法提供 LedgerDao`），发生在装前那一次的包上；本轮修复。
- 根因：`AppLockController.lock()`（`app/src/main/java/com/minibook/data/local/security/AppLockController.kt:72-73`）**先 `databaseGate.close()`、后 `_isLocked.value = true`**；UI 生命周期恢复会让 `collectAsStateWithLifecycle` 立刻重启上游 Flow，旧实现在调用点 `ledgerDao() ?: error(...)` 同步抛出 ⇒ `Dispatchers.Main.immediate` 上无人捕获，主线程崩溃。
- 改动：
  1. `app/src/main/java/com/minibook/data/local/security/DatabaseProvider.kt`：新增 `openState: Flow<MiniBookDatabase?>`（默认 `flowOf(database)`，够测试替身用）。
  2. `app/src/main/java/com/minibook/data/local/security/DatabaseGate.kt`：以 `MutableStateFlow` 覆写 `openState`——`open()` 成功后发射实例、失败与 `close()` 归 `null`。
  3. 新增 `app/src/main/java/com/minibook/data/local/datasource/GatedFlow.kt`：`gatedFlow { db -> ... }` = `openState.flatMapLatest { if (it == null) emptyFlow() else block(it) }`（KDoc 写明「为什么不能 `filterNotNull()`」）。
  4. `app/src/main/java/com/minibook/data/local/datasource/RoomDataSourceImpl.kt`：七个 `observe*`（交易 `observeByLedger` / `observeDailyTrend` / `observeMonthlyTrend` / `observeCategoryAgg`、债务、分类、预算）改走门控；`app/src/main/java/com/minibook/data/local/datasource/RoomDataSource.kt` 适配器 `observeAll` 同改；一次性 `suspend` 方法与 `RoomTransactionRunner` **保持硬失败**。
  5. 新增 `app/src/test/java/com/minibook/data/local/datasource/GatedFlowTest.kt`（3 例，`UnconfinedTestDispatcher`）。
- 验收：
  - [x] JVM `569 例 / 0 失败 / 1 跳过`；`:app:assembleDebug` 成功（APK 39,196,755 字节 sha256 `B297835C81F0513D9B6DB38392A960BF4064C893B1842171944AFAC739F2AA71`）。
  - [x] 门控语义三例（门关不发射 / 解锁发射当前库值 / 重锁取消上游再解锁切新库）全绿。
  - [ ] 真机复现路径回归（加锁→解锁→立刻回桌面/回前台）：**未做**，需用户当次批准安装。
- 遗留：一次性 `suspend` 读取若恰在「关库→锁屏」窗口内触发仍会抛错（有意不静默返空，避免把「没解锁」误报成「没有数据」）；后台接收器路径（`SmsReceivedReceiver`、自动同步）本就 catch。
### TASK-086: 应用锁前置分诊 —— 未录入生物识别时开启应用锁不再崩溃（F-16j，2026-10-13，已完成）

**背景**：F-16i 模拟器回归时顺手验证「设置 → 安全」，点「应用锁」开关直接崩溃。

**根因**：模拟器已用 `locksettings set-pin 1234` 设了系统 PIN、但**未录入指纹/人脸**。Android 13+ 的 Keystore 在生成 `setUserAuthenticationRequired(true)` 且不设 validity duration 的「每次使用都要认证」key 时，要求设备至少录入一个生物识别，否则抛 `InvalidAlgorithmParameterException: At least one biometric must be enrolled to create keys requiring user authentication for every use`；`KeyManager.createMasterKeyEncryptCipher(useAuth = true)` 在**主线程**执行且无人捕获 ⇒ `FATAL EXCEPTION: main / Process: com.minibook, PID: 4040 / RuntimeException: InvocationTargetException`。

**改动**：
- `app/src/main/java/com/minibook/data/local/security/BiometricHelper.kt`：新增 `hasEnrolledBiometric()`（`BIOMETRIC_STRONG` / `BIOMETRIC_WEAK` 任一 `BIOMETRIC_SUCCESS` 即真；**不含 `DEVICE_CREDENTIAL`**——只设 PIN 时它也 SUCCESS，正是崩溃场景）。
- `app/src/main/java/com/minibook/ui/settings/SettingsViewModel.kt`：`setAppLockEnabled(true)` 在 `isDeviceSecure()` 之后增加分诊，不满足发 `needBiometric`（`MutableSharedFlow`），**不启用、不降级明文**。
- `app/src/main/java/com/minibook/ui/settings/SettingsSecurityScreen.kt`：新增生物识别引导卡（跳 `Settings.ACTION_SECURITY_SETTINGS`），与 DEC-003 无锁屏引导卡并列。
- `app/src/main/java/com/minibook/data/local/security/AppLockController.kt`：解锁 / 降级 / 升级 / 首启认证四处 cipher 创建改 `runCatching { ... }.getOrNull() ?: run { onResult(...); return }`，失败回调 `false` / `AUTH_FAILED`（保持锁定）。

**验收**：
- [x] `:app:testDebugUnitTest` **569 例 / 0 失败 / 1 跳过**（本次无新增用例）
- [x] `:app:assembleDebug` BUILD SUCCESSFUL；APK 39,508,475 字节 sha256 `C334F7F480F3BD5B4F3D55FA7F67132D162AC81A34C51F9586A4B894DB60DA9D`
- [x] 模拟器实测：点开关不崩溃（`pidof` 前后同为 4728）、显示引导卡原文、返回后安全行仍「应用锁未开启」、`logcat -d -b crash` 0 行
- [ ] 真机（有指纹）走查「录入生物识别 → 开启应用锁 → 冷启动认证 → 解锁」成功路径（需当次安装授权）

**遗留**：F-16i 的真机复现路径（锁屏瞬间重订阅）同样未验证。
### TASK-087: 首页账号入口收成标题旁小链接 + 礼金/红包图标去重（F-16k / F-16l，2026-10-13，已完成）

**背景**：用户反馈「1、首页账号设置不用那么大  在首页两个字后面加个 "未登录"  "昵称" 作为链接就可以了 2、分类中收入的礼金和红包的图标重复了」。

**① 首页账号入口（F-16k）**
- 撤掉 `HomeScreen` 的 `LazyColumn` 首项大卡 `HomeAccountRow`（36dp 圆底 + 标题 + 副行 + 行尾箭头/「登录」）与 `HomeDashboardContent` 的 `accountName` / `onOpenAccount` 两个参数。
- `HomeTopBar` 新增 `accountName: String?` / `onOpenAccount: () -> Unit` 参数：「首页」标题后紧跟可点小字（未登录显示「未登录」、已登录显示昵称，`labelLarge` + `primary`、`clickable(role = Role.Button)`、`defaultMinSize(minHeight = 44.dp)`、行尾省略号），点击进账号设置页。
- imports 清理：删 `layout.Column` / `layout.height`，加 `layout.defaultMinSize` / `text.style.TextOverflow`；类 KDoc 与 `@param` 同步。
- 验收 `[x]`：`:app:testDebugUnitTest` + `:app:assembleDebug` 成功；模拟器 uiautomator dump 同一行 `首页 [63,179][177,261]` + `未登录 [203,194][313,247]` + `默认账本 [810,189][981,252]`。

**② 礼金 / 红包图标去重（F-16l）**
- 根因：收入「礼金」内置 `icon = "redpacket"`，历史 🧧 也映射到同一矢量；`Gift` 与 `RedPacket` 两条路径都是「盒 + 蝴蝶结」，人眼难分。
- `CategoryIcons.kt` 新增 `const val GIFT_MONEY = "giftmoney"` 与 `val GiftMoney`（Material Symbols `volunteer_activism` 手捧心，24×24，`addPathNodes`）；`byKey` 登记；`pickerKeys` 24 → **25**（礼金位换 `GIFT_MONEY`，`REDPACKET` 追加到末尾仍可选）。
- `fun resolve(raw: String, name: String? = null): ImageVector?`：原有 key / 旧 emoji 解析后，若 `name == "礼金"` 且解析结果是 `RedPacket` 或 `Gift`，改返回 `GiftMoney` —— **只按名改道，不动任何用户数据**。
- `CategoryGlyph` 增加 `name: String? = null`（`remember(icon, name)`）；9 个调用点中持有 `Category` 的全部传入 `name`，图标选择器 `CategoryEditorSheet` 刻意保持 `null`（所见即所选）。
- `BuiltInCategories.kt` 收入「礼金」`icon = "redpacket"` → `"giftmoney"`。
- 验收 `[x]`：`CategoryIconsTest` +2 例（礼金与红包解析结果不同；名为「礼金」的旧分类换 `GiftMoney` 且其它分类不受影响）；模拟器分类页目视确认礼金为手捧心、与「人情」礼物盒及末尾「红包」均不同。

**测试与产物**：JVM **571 例 / 0 失败 / 0 错误 / 1 跳过**（75 套件）；APK 39,918,654 字节，2026-10-13 16:58:15，sha256 `A6DD265B02B172EE4A3B0FE16DD71B5BC51EF67C20C0605A4C0C00D5FA14B736`。服务端本轮未改（仍 73/73）。

**遗留**：`Gift`（人情）与 `RedPacket`（红包）两条矢量视觉仍相近，本轮只解掉「礼金 vs 红包」同图，未重绘 `RedPacket`；`CategoryEditorSheet` 里误点图标格会立即改选中项（既有交互，未改）。

**真机安装验收（2026-10-21，用户当次批准「装手机里吧」）**
- `install -r app/build/outputs/apk/debug/app-debug.apk` 原地覆盖 **Success**（同 Android Debug 签名 + 同 applicationId，无需卸载）。
- 数据完整性：装前 / 装后 `run-as tar` 快照**逐字节一致**（**11,510,272 字节**，sha256 `F25D2C308FA06BD3FFABA9740ACC56F2D21718FAC4D9EA097271680F698BD5E9`）⇒ 本地数据未丢、无迁移改写。
- 包信息：`versionCode=2 / versionName=1.1.0`；`firstInstallTime=2026-05-18 18:28:54` **未变**；`lastUpdateTime` 2026-10-13 15:22:06 → **2026-10-21 14:14:12**。
- 产物核对：手机内 `/data/app/.../base.apk` sha256 = 本地产物 = `a6dd265b02b172ee4a3b0fe16dd71b5bc51ef67c20c0605a4c0c00d5fa14b736` ⇒ 手机跑的就是 571 例通过的那个构建；解包检索 `classes*.dex` 确认 `GatedFlow`（classes6）与 `giftmoney`（classes6/16）确在包内。
- 启动与崩溃：`am start -n com.minibook/.MainActivity` → `pid=24856` 存活、前台 `com.minibook/.MainActivity`；`logcat -b crash` **当日 0 行**、主缓冲无 `FATAL/AndroidRuntime`。
- 真机 UI（只读 dump，F-16k）：`首页 [72,200][190,280]` + 账号小链接 `[220,213][365,267]`，可点祖先 `[48,168][389,312]`（高 132px ≈ 44dp，`clickable=true`），右侧 `默认账本` 未变 ⇒ 大卡已消失、小链接在真机路径成立。
- **换版含义（诚实记录）**：真机此前 8 天跑的是 **TASK-084 的包**（`2FD3869…`），该包构建早于 TASK-085 / F-16i 门控修复，其 2026-10-17 ~ 10-20 的 `DatabaseGate 未解锁/未打开` 崩溃实录（3 次，栈内无 `GatedFlow` 帧）已随本轮换版被替换掉——细节与证据见 `09_test_report.md` 顶部章节。
- 未做：真机逐页点击走查、F-16l 图标真机目视、F-16i 复现路径（加锁 → 解锁 → 立刻回桌面）回归（本轮只有「启动后数分钟无崩溃」，不构成回归证明）。

---

### TASK-088: F-15 上云（数据库迁腾讯云 + 服务器打镜像 Docker 部署 + 模拟器联测线上服务器）

- 状态：已完成（遗留 2 项待批）
- 优先级：P0
- 负责角色：运维 / Developer（主代理执行；真机与模拟器为全局独占资源）
- 所属层：基础设施 + 服务端
- 关联需求：F-15 云同步的前置能力；DEC-051；`docs/14_cloud_sync.md` §9 / §14
- 估算：0.5 天
- 依赖关系：TASK-086（服务端 MySQL 双 DSN 引擎）
- 用户原话：「1 把数据库迁移到腾讯云这台服务器中，2 后台服务部署到这台服务器中，3 用模拟器测试线上服务器。」中途纠正：「后台服务器不要直接部署，在服务器打镜像，然后用 docker 部署」。

**① 数据库迁移（host → 腾讯云）**
- 源：host `<内网主机IP>` 的 `mysql:8.0` 容器（`0.0.0.0:49779`，数据 `/opt/docker/mysql/data`）；目标：腾讯云 `VM-0-4-ubuntu` 的 `mysql:8.0.46` 容器（只绑 `127.0.0.1:49779`，限制 640m）。
- 路径：`mysqldump --single-transaction --routines --triggers` → `minibook-full.sql`（1,596,813 字节）→ 目标库导入 → 逐项核对。
- 核对结论（**全部一致**）：11 张表行数一致；`alembic_version = 0004_user_profile_fields`；全部 InnoDB / `utf8mb4_unicode_ci`；规范化后 canonical dump **逐字节相同**（sha256 `e0ec23b8…`；`CHARACTER SET utf8mb4` 的渲染差异用 `sed -E 's/ CHARACTER SET [a-z0-9_]+ COLLATE/ COLLATE/g'` 归一后比对）；`information_schema.columns` 哈希 `c36ee8a4…`；表元数据哈希 `e55ba085…`；**按账号逐行哈希 `326161b5…`**（6 个非测试账号：`demo_user` 若干交易 / 若干日志、`demo_user_b` 若干 / 若干、`emu1012a` 1 / 27，其余 3 个均 0）。
- 数据量：6 用户 / 2203 交易 / 2301 `sync_log`。
- **如实披露的删除**：源库 10 个账号里的 4 个 `dev_*`（E2E 遗留，各 3 交易 + 1 分类 + 1 账本 + 7 日志，可用 `E2e!Passw0rd` 登录证明来源）+ 1 个 `smoke_*` 残留，共 5 个测试账号在迁移前按项目既有约定走 `delete-account` 清除；**真实账号未动**，回滚源（host 库，未 DROP 任何东西）仍完整，必要时重导 `minibook-full.sql` 即可复原。
- 保留物：腾讯云 `/home/ubuntu/minibook-migrate/`（`minibook-full.sql`、迁移前后 canonical dump、`smoke_live.py`、`cleanup_test_accounts.py`、`per-user-tencent.txt`）；host `/root/minibook-migrate/`（dump + `per-user-host.txt`）。

**② 后台服务容器化部署（按用户要求：服务器上打镜像 + Docker）**
- 新增 `server/Dockerfile`：`python:3.12-slim`；非 root `appuser`（uid 10001）；`TZ=Asia/Shanghai`；`PYTHONDONTWRITEBYTECODE` / `PYTHONUNBUFFERED` / 禁 pip 缓存；COPY `app/ alembic/ alembic.ini pytest.ini`；`HEALTHCHECK /healthz`；`CMD python -m uvicorn app.main:app --host 0.0.0.0 --port 8000` —— **刻意不加 `--workers`**（限流是单进程内存实现，多进程会失效）。
- 新增 `server/.dockerignore`：排除 `.venv/`、`__pycache__/`、`.env`、`*.db`、`tests/`、`*.md` ⇒ **`.env` 不进镜像**，密钥只由 `env_file` 注入。
- 新增 `server/docker-compose.yml`：`name: minibook-api`；`image: minibook-api:latest`；`restart: unless-stopped`；端口**只绑回环** `127.0.0.1:8000:8000`；接入既有外部网络 `mysql_default`；`read_only: true` + `tmpfs /tmp`；`mem_limit 448m` / `cpus 1.0` / `pids_limit 256` / nofile 65536；json-file 日志 20m×3。
- `server/requirements.txt` 增加并锁定 `cryptography==50.0.2`：MySQL 8 的 `caching_sha2_password` 在非 TLS TCP 下需要它走 full-auth（首次连接 / 改密后）。
- 构建与部署：镜像**在服务器上**构建（`minibook-api:latest 7c3e39166f84 199MB`）→ `docker compose up -d` → 容器 `Up (healthy)`；镜像内 12 个依赖可导入（含 `cryptography 50.0.2`）；`/healthz`、`/docs`、`/openapi.json` 全部 200。
- DSN：`mysql+pymysql://minibook:…@mysql:49779/minibook?charset=utf8mb4`；`JWT_SECRET` 与 host **逐字符一致** ⇒ 迁移前签发的设备令牌继续有效，用户无需重新登录。
- 测试基线：腾讯云 MySQL 上 **73 passed**（70.10 s）；本地 SQLite 同样 **73 passed**（35.06 s）。
- 发布冒烟（容器内 httpx 打真实 API，**自清理无残留**）：注册 201 → 登录 200 → me 200 → push `{applied:1, skipped:0, conflicts:[], server_seq:2842}` → pull 取回该项 → status `counts:{ledger:1}` → refresh 200 且旧 refresh 401 → **幂等重推 `applied:0, skipped:1` 且 `server_seq` 不变 2842→2842** → delete-account 200 → 删后登录 401。

**③ 模拟器联测线上服务器（任务书第 3 条）**
- 通道：模拟器 `emulator-5554`（Pixel_7 / API 35）内 App 调试版默认 `SYNC_BASE_URL=http://127.0.0.1:8000` → `adb reverse tcp:8000 tcp:18000` → 本机 18000 → SSH 隧道 → 腾讯云 `127.0.0.1:8000`。**全程不改地址、不用公网端口**。
- 路径 A（插桩 E2E `SyncDeviceE2ETest`）：**部分成立，仍卡死**。服务端侧证实它确实打到了线上（新建账号 `dev_4b7b695cf7` @ 07:41:03 UTC，1 设备 + 1 refresh_token），但用例随后**卡在 `preview()` 的本地阶段**：`files/sync_backup/` 无新增（`exportAll` 未执行）、没有第三个 socket、服务端 15 分钟只收到容器的健康检查、账号业务行数恒为 0。`DatabaseProvider.openState` 默认实现是 `flowOf(database)`，**已排除门控为卡点**；`runBlocking` 卡在测试主线程且取不到栈（AVD `ro.debuggable=0` / `ro.build.type=user`，`/data/anr` 不可读，`kill -3` 的 trace 进 tombstoned）。已按遗留处理，未粉饰。
- 路径 B（UI 真实操作，**闭环成立**）：App → 设置 → 账号 → 注册新账号 → 填 `emu1022a` / `EmuPassw0rd123` / 服务器地址 `127.0.0.1:8000`（调试版自动补 `http://`）→ 勾选协议 → 注册并登录 ⇒ App 直接进「用户信息」页。服务端日志同一连接三条：`POST /api/v1/auth/register 201` → `POST /api/v1/auth/login 200` → `GET /api/v1/me 200`；库内 `emu1022a` + 1 设备 + 1 refresh_token。
- 继续在 App 内记一笔（餐饮 ¥12.34）→ 设置 → 数据 → 云同步 → 双向同步：预览「上行 **26** 条改动 · 下行 0 条」→ 执行 → 结果卡「上传 **26** 条 · 云端删除 0 条 · 下载 0 条 · 删除本地 0 条 · 冲突 0 条」，并按设计自动留同步前备份 `minibook_backup_20261021_081811.json`。服务端：`GET /api/v1/sync/pull?since_seq=0&limit=200 → 200`、`POST /api/v1/sync/push → 200`；库内该账号 `categories 24 / ledgers 1 / transactions 1 / sync_log 26`，`sync_log.server_seq` 2843–2868（**承接迁移前的 2842 继续递增**，说明序列随数据一起迁过来了），交易行 `amount=1234 / date=2026-10-21 / server_seq=2868` ⇒ **模拟器 → 线上服务器完整闭环成立**。

**④ 联测中发现的服务端缺陷（2026-10-22 已修）**
- 现象：同一台设备上注册**第二个**账号，App 报「该用户名已被占用，请换一个」，而该用户名在库里**根本不存在**。
- 根因：`server/app/routers/auth.py` 的 `_touch_device()` 在「设备行已存在但 `user_id` 属于别人」时**不是改归属，而是又 `db.add(Device(id=device_id, …))`**；`devices.id` 是**主键**（`UNIQUE(user_id,id)` 只约束同一用户名下）⇒ `IntegrityError` ⇒ `register()` 在 `auth.py:165-167` 把它一并译成 `ApiError("username_taken")`，于是提示指向了用户名。
- 复现与对照：App 私有存储 `files/datastore/sync.preferences_pb` 中 `device_id=aeafb982-6fd0-4b5a-9848-e46ce67ee961`（明文可读），而线上 `devices` 表该行属于 2026-10-12 建的 `emu1012a`；`pm clear com.minibook` 换成全新 `device_id b53bf895…` 后，**同一套 UI 操作立刻注册成功**（干净的 A/B 对照），注册 201。
- 影响面：任何「一台设备换账号再注册」的用户都会撞上，且提示是错的（误导排查方向）；与本次迁移无关——是迁移过来的老数据暴露出的**既有缺陷**。
- **修法（2026-10-22 已落地，TASK-089）**：`_touch_device` 遇到已存在的设备行**改归新用户**（`user_id = user.id` + 刷新 `last_seen_at`/`name`/`platform`/`app_version`），不再重复 INSERT；`register()` 里 `IntegrityError → username_taken` 只留给真正的「并发注册同名」。**同日补证**：`login()`（`auth.py:187`）也调用同一函数且**没有异常兜底** ⇒ 「同一台设备换账号登录」会直接 **500**，不只是注册被误报。
- **验证（四条证据）**：① 新增 2 条回归测试（`test_register_reclaims_device_of_another_user` / `test_login_reclaims_device_of_another_user`）；② 把函数临时还原成缺陷版 → 两条**均失败**（`UNIQUE constraint failed: devices.id`）→ 恢复修复版 → 本地 SQLite **75 passed**（32.56 s）；③ 容器内对腾讯云 MySQL **75 passed**（73.19 s）；④ 新镜像 `6f29b8c354b7` 部署后容器 `healthy`、容器内 `app/routers/auth.py:107` 含改归属行，**线上实测**用属 `emu1012a` 的 `device_id=aeafb982…` 登录 `minibook_test` → **200** 且设备行改归 `minibook_test`。回滚标签 `minibook-api:pre-fix-20261021`。详见 `14_cloud_sync.md` §15.0。

**验收**
- [x] 数据库迁到腾讯云，逐字节 / 逐行核对一致，`alembic_version` 就位
- [x] 后台服务在服务器上打镜像并用 Docker 部署；容器 `healthy`；`/healthz`、`/docs` 200
- [x] 服务端测试 73/73（腾讯云 MySQL 与本地 SQLite 双跑）
- [x] 模拟器对线上服务器完成「注册 → 登录 → 记账 → 双向同步」闭环，服务端日志与库内数据双向对证
- [ ] 插桩 E2E `SyncDeviceE2ETest` 卡死问题（遗留，仅定位到 `preview()` 本地阶段）
- [x] `_touch_device` 设备归属缺陷修复（2026-10-22：设备改归属 + 2 条回归测试 + 重打镜像部署 + 线上实测 200，见 TASK-089）
- [ ] 公网入口未开放（仅 3228 可达；API 只绑回环 + SSH 隧道，属安全默认）—— **方案已出**：只开 **443** + Caddy（TLS-ALPN，不开 80），**待用户审批后执行**，见 TASK-090 与 `docs/14_cloud_sync.md §15`
- 涉及文件：`server/Dockerfile`、`server/.dockerignore`、`server/docker-compose.yml`、`server/requirements.txt`；文档 `docs/14_cloud_sync.md`、`docs/07_decisions.md`（DEC-051）、`docs/09_test_report.md`、`docs/CHANGELOG.md`、`docs/06_tasks.md`

### TASK-089: `_touch_device` 设备归属缺陷修复 + 重打镜像部署（2026-10-22）

- 来源：TASK-088 第 ④ 节查出的缺陷；用户 2026-10-22 指示「1、修复你说的那个。」
- 关联：DEC-052；`docs/14_cloud_sync.md` §10 风险行 / §14.7 / §15.0；`docs/09_test_report.md` 顶部章节
- 改了什么：`server/app/routers/auth.py` 的 `_touch_device()` —— 设备行已存在即**改归属**（`user_id` + `last_seen_at` / `name` / `platform` / `app_version`），不再重复 INSERT；`register()` 的 `IntegrityError → username_taken` 保留给真正的「并发注册同名」。**未新增错误码、未改客户端**（客户端 `SyncErrorCode.UNKNOWN` 本就前向兼容）。
- 同日补证的第二个面：`login()`（`auth.py:187`）也调用同一函数且**没有异常兜底** ⇒ 「同一台设备换账号**登录**」会直接 **500**，不只是注册被误报。
- 测试：新增 `test_register_reclaims_device_of_another_user` / `test_login_reclaims_device_of_another_user`；**先把函数还原成缺陷版确认两条均失败**（`UNIQUE constraint failed: devices.id`）；修复后本地 SQLite **75 passed**（32.56 s）、容器内腾讯云 MySQL **75 passed**（73.19 s）。
- 部署：在服务器上 `docker compose build` → 新镜像 `minibook-api:latest` = `6f29b8c354b7`；旧镜像留标签 `minibook-api:pre-fix-20261021`（回滚用）；`docker compose up -d` 后容器 `Up (healthy)`、`/healthz` 200、容器内 `app/routers/auth.py:107` 含改归属行。
- 线上实测：以属于 `emu1012a` 的 `device_id=aeafb982-6fd0-4b5a-9848-e46ce67ee961` 登录 `minibook_test` → **200**（旧版该路径 500），`devices` 该行 `user_id` 由 `emu1012a` 变为 `minibook_test`（诚实说明：这条设备行因此从测试账号 `emu1012a` 转到了 `minibook_test`，两者都属于待清理的测试账号）。
- 验收：[x] 缺陷修复且被回归测试覆盖（含「旧代码上会红」的反证）；[x] 线上部署生效并实测；[x] 文档同步（DEC-052 / 风险行 / 测试报告 / CHANGELOG / 任务台账）。

### TASK-090: 公网安全接入（**待用户审批；方案已出，未动手**，2026-10-22）

- 来源：用户 2026-10-22 指示「2 服务器你先列出一个安全部署的计划，不用动手，我审批以后部署，保证在其他地方能连接到。用最少得端口，需要开放端口权限我在控制台帮你开。」
- 方案（详见 `docs/14_cloud_sync.md §15`）：**只开 443/tcp**；反代用 **Caddy + TLS-ALPN-01**（ACME 挑战走 443，因此**不需要开 80**）；API 与 MySQL 继续只绑 `127.0.0.1`（现状即如此）；3228(SSH) 维持现状，可选限制来源 IP。
- 待用户决定：① **域名**（Let's Encrypt 不给裸 IP 签证书；没有域名只能选自签 + 改 App，或继续隧道联调）；② 是否把 release 的 `SYNC_BASE_URL`（现为空串）改成 `https://<域名>` 并重出正式包；③ 是否给 3228 限来源 IP。
- 执行前必须先做：全库 dump + `/opt/docker/*` 配置备份；**反代后的真实 IP 与限流**（应用内登录限流按 `request.client.host` 计数，经反代后会全变成网关 IP ⇒ 必须先只信任来自反代的 `X-Forwarded-For`，否则限流形同虚设或误伤所有人）；仍**不得**给 uvicorn 加 `--workers`（限流是单进程内存实现）。
- 状态：[x] **已于 2026-10-23 执行完成**（用户否决 §15 的 443/Caddy 方案）：安全组放行 **38080**、compose 加 `"38080:8000"`、`.env` 设 `ENABLE_DOCS=false`、镜像重建并部署；**外网直连实测**（探针 200 / 文档三处 404 / 测试账号登录+push+pull 全绿 / 容器日志记为真实公网 IP）见 `docs/09_test_report.md` 最新章节与 `docs/14_cloud_sync.md §16`；App 正式包同步改指该地址（DEC-053）。
- 关联：DEC-033（单 worker 限流未决）、`docs/14_cloud_sync.md §15.2` 前置条件。

### TASK-091: 配置分仓（本地/远程两份模板）+ 远程地址出仓库 + 提交前体检脚本（2026-10-23）

- 来源：用户 2026-10-23 指示「2、远程服务器配置文件拆分成两份，一份本地开发的一份远程开发的。提交代码前一定要检查，千万不要把远程服务器地址以及一些账号密码提交到仓库中。」
- 关联：DEC-054；`docs/14_cloud_sync.md §17`；`docs/09_test_report.md` 顶部章节。
- 做了什么：`app/build.gradle.kts` 改为从仓库根 `sync.properties`（已 gitignore，模板 `.example`）注入 `SYNC_BASE_URL`；`app/src/main/res/xml/network_security_config.xml` 去掉写死的 host（平台层不再固定 host，应用层 `SyncUrlValidator` 仍只放行内置 host），删除 debug 覆盖资源；新增 `sync.properties.example`；`server/.env.example` → `.env.local.example` + `.env.remote.example`（后者仅占位符）并在服务器同步两份模板；`.gitignore` 加固（`server/.env*` 只放行 `*.example`、忽略 `sync.properties`）；新增 `tools/check-secrets.ps1`；`docs/*.md` 真实地址占位符化；`server/README.md` 更新用法与体检命令。
- 验收：[x] 受版本控制文件里真实公网 IP 0 命中（ripgrep 全仓）；[x] `check-secrets.ps1 -All` → `Checked 518 file(s) … OK`、退出码 0；[x] `:app:testDebugUnitTest` + `assembleDebug` + `assembleRelease` **BUILD SUCCESSFUL in 5m31s**；[x] 服务器上两份模板落地且 `.env`（823B，真实值）未动；[x] 文档同步（DEC-054 / §17 / 测试报告 / CHANGELOG）。
- 诚实挂账：① 平台层 host 级明文固定被放弃（只留应用层校验，见 DEC-054「已知代价」）；② 服务器上的旧 `server/.env.example` 仍在（删除命令被操作者策略拒绝，未重试、未绕行）；③ 体检脚本初版在 PS 5.1 下用 ANSI 读 UTF-8 产生 2 处假命中，改 `-Encoding UTF8` 后清零 —— 过程已写入 DEC-054 与 §17.2。

### TASK-092: 正式包真机安装（可连远程、无地址栏）+ 数据备份与恢复路径（2026-10-23）

- 来源：用户 2026-10-23 指示「1、给我真机安装一个，可以连远程的。」；中途追问「手机端为什么还展示服务器地址的输入栏」后选定「直接换成正式包（不显示地址栏）」。
- 执行（真机 = 全局独占资源，本轮由主代理单独执行，未开模拟器）：手机原装的是 **debug 签名**包 ⇒ 先用 debug 包原地覆盖验证「升级不丢数据」（`firstInstallTime` 保持 2026-05-18、首页账目完好）；用户选定换正式包后，**先用 App「导出数据」备份**（（大小略））→ 卸载 → 装 `app-release.apk`。
- 证据：手机内 `base.apk` sha256 = 本地 `app-release.apk`（`48887B68…B67198`）；签名 `CN=Minibook`（证书 sha256 `e137b7e5…`）；登录页 uiautomator 全量文本 12 条且 `含「服务器地址」输入栏: False`；手机自带 `curl http://<远程公网IP>:38080/healthz` → **HTTP 200**；`firstInstallTime` = 2026-10-23 10:25:56（新装，本地数据按预期清空）。
- 恢复路径：登录（用户自己的账号与口令）→「设置 → 数据 → 导入数据」→ 选 `Download/minibook_backup_20261023_102433.json`（手机 Download 里两份，本机另存一份 `%TEMP%\minibook_backup_phone.json`）。
- 验收：[x] 正式包装上且能连远程（内置地址来自 `sync.properties` + 手机侧 curl 200）；[x] 界面无地址栏；[x] 数据有备份、恢复路径明确；[ ] 真机登录 POST 的服务端日志见证（用错误账号做 UI 探针时被手机**拼音输入法**把注入文本转成中文、口令框为空 ⇒ 未发出登录请求；最终确认留给用户登录后看服务端访问日志）。

### TASK-093: 云同步 push 全 500 的根因修复 —— 计数器缺行自愈 + 清库边界（2026-10-23）

- 来源：用户 2026-10-23 追问「手机上的云同步怎么失败了」；随后拍板「1、`next_seq` 缺行自愈 + 回归测试；2、清库边界写进文档；3、重打镜像部署」（其中「是否从宿主机恢复旧云数据」用户明确**不需要**）。
- 关联：DEC-055；`docs/14_cloud_sync.md §18`；`docs/09_test_report.md` 顶部章节。
- 根因：清库把 `sync_counters` 种子行与 `alembic_version` 一起删掉 ⇒ `next_seq()` 的 `UPDATE` 影响 0 行 ⇒ 抛 `ApiError 500`「server_seq 计数器缺失」；`current_seq()` 对缺行容忍 ⇒ 「pull 200 / push 500」的不对称，且日志无异常行。
- 做了什么：① 数据面恢复：按 `MAX(sync_log.server_seq)` 补种计数器 + `alembic stamp 0004_user_profile_fields`（**刻意不跑 `upgrade head`**）；② 代码面：新增 `_bump` / `_seed_counter`，`next_seq` 缺行自愈（重试一次，仍失败才 500）；③ 新增 `tests/test_counter_selfheal.py` 2 例；④ 文档：§18 + `server/README.md` 清库边界 + DEC-055 + CHANGELOG + 测试报告。
- 验收：[x] 缺陷版注入 ⇒ 2 例均失败；[x] 双 DSN 各 **78 例 / 0 失败**（SQLite 30.819 s / 腾讯云 MySQL 457.733 s，junitxml `failures=0 errors=0 skipped=0`）；[x] 镜像重建部署后容器 `healthy`、`/healthz` 200、容器内含新代码；[x] 线上发号实测 `server_seq_global` 2314→2315 + `pull` 取回该条；[x] 用户手机 1157 条数据上云成功（`demo_user` 若干账本 / 分类 / 交易 / 债务 / 日志）。
- 诚实挂账：① 线上发号实测用我的探针账号 `probe723685`（本就待清理），该账号因此多落 **1 行**；② 探针账号 `probe723551` / `probe723685` 仍在库里，清理 SQL 中含 `DELETE` 的命令被操作者策略拒绝（**未重试、未绕行**）—— SQL 已交付用户；③ 清空前的旧云数据按用户决定**不恢复**，宿主机原始 MySQL 未动，需要时可再迁；④ 自愈只在缺行时生效，计数器值被人工改小仍会撞主键。
