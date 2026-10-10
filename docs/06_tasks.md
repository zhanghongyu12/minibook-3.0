# 任务清单 (06_tasks.md)

> 项目：MiniBook Android 重构版（本地优先记账应用）
> 维护者：全员可更新（项目状态区块由当前阶段负责角色更新）
> 最后更新：2026-10-10（F-14 短信账单导入：完成真机 + 模拟器验收，实测修复 5 个真机静默失效缺陷；验收后完成 UI 改版（TASK-059：未识别「已看过」标记 / 导入页三入口 / 设置分组汇总 + 四二级页，见 DEC-029）+ 两种扫描方式与白名单空态修复（TASK-060 / DEC-030）；JVM 全量单测 52 类 / 410 例全绿；TASK-051~060 全部收口）
> 状态：F-12 微信/支付宝账单导入已全流程落地（PRD→架构→UI→实现→测试→评审→修复收口 9 commit）；P2 技术债清账 + 文档收口完成；**F-13 邮件账单自动导入（Phase 3）已实现并通过本地构建 + 全量单测（TASK-045~050 已回写）；真机验收待设备**；**F-14 短信账单导入已实现并完成真机 + 模拟器验收与验收后口径定案（`13_sms_bill_import.md` §14、DEC-027、DEC-028（含修订），TASK-051~060 全部收口；`:app:assembleDebug` 与 JVM 全量单测 52 类 / 410 例 0 失败；实测修复 6 个静默失效缺陷，F2 角标方案经用户决策撤销、最终为三层兜底（到达即记账 → 开 App 补扫 → 手动导入），并复测 4 类边界短信；验收后按用户三诉求完成 UI 改版（TASK-059 / DEC-029：未识别「已看过」折叠标记、导入页三入口、设置分组汇总 + 四二级页）与**两种扫描方式（TASK-060 / DEC-030：增量 / 全量 + 白名单空态）**，双端实测通过），仍未覆盖项见 TASK-057/058/060**；**未提交 git（规则 2，待批准）**；待人工批准 push（收尾阶段明确不做真机 / release / Phase 3）
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

---

（本文件为任务清单，随实现对细节的差分由后续修订更新；版本历史见 CHANGELOG.md。）
