# 任务清单 (06_tasks.md)

> 项目：MiniBook Android 重构版（本地优先记账应用）
> 维护者：全员可更新（项目状态区块由当前阶段负责角色更新）
> 最后更新：2026-08-21
> 状态：阶段 9 已完成（v1.0.0 MVP 发布）
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
- 当前阶段：**Phase 2 开发中（需求已确认，架构设计进行中）**
- 当前任务：F-06/F-07/F-08 需求细化完成（DEC-010 已确认），进入架构设计
- 下一步行动：Phase 2 进行中——Architect 完成 §Phase 2 架构/数据库/API 设计 + DEC-011~013（图表/通知/搜索技术口径）+ 任务拆分 TASK-031+；遗留待办挂账（① release 签名 keystore ② instrumented 真机执行 ③ transactions 页分类联表 TODO ④ Phase 3 云端同步）
- 阻塞项：无

---

## 协调日志

- 2026-08-18：编排模式激活，「运行模式」字段首次落盘（此前编排状态仅存在于会话上下文，未持久化）。
- 2026-08-18：协调者复盘越界行为（直接改 ARCHITECTURE.md 正文、欲直接写业务代码）并纠正；8 项架构决策已闭环，决策记录见 07_decisions.md。
- 2026-08-18（Architect 接管后收口）：ARCHITECTURE.md 复核并由 Architect 正式接管（§2 版本口径对齐 DEC-001 既定栈）；DATABASE.md 状态由「草稿」转「已确认」并修正 §8.3 待定措辞（D-6 最终口径）；新建 `docs/API.md`（= 04_api，已确认）展开全部 DataSource 契约、RemoteDataSource 出口与 JSON version 1 结构；决策记录落盘 `docs/07_decisions.md`（DEC-001~DEC-009）；阶段 3 收口完成。
- 2026-08-18（Architect 阶段 5）：完成 Designer 三项契约外假设 A-1/A-2/A-3 双向确认（全部确认、无契约推翻、未新增决策）；A-2 的 `DebtFilter{ kind?, direction? }` 契约补充直接落 API.md §4；UI.md 状态由「草稿」转「已确认」（§6.3 标注确认结论）；任务清单填充 **TASK-001~TASK-030**（构建骨架 2 / 数据层 4 / 领域契约层 4 / 业务层 8 / UI 层 12），版本锁定写入 TASK-001（与 DEC-001 既定栈完全一致）。
- 2026-08-20（阶段 6~9 收尾）：阶段 6 编码（TASK-001~030 + UI 接线 + 连接债务收尾）全部完成；阶段 7 测试落地（4 JVM 单测全绿 + 3 instrumented 骨架）；阶段 8 评审（3 层并行 Reviewer）发现并修复 2 个严重安全 bug（DEC-002 双钥系统：缺失关→开重包裹路径 + 首启 master key 生成未接线 + unlock 兜底）+ 9 个高价值警告项（错误分类/Update 校验/Bootstrap 事务/数据源原子/圈账本/三页 Error 态/重试/snackbar 重放）；阶段 9 发布——修复 release lintVital 拦截（data_extraction_rules.xml 非法 domain）、assembleRelease 成功产出 `app-release-unsigned.apk`、发布说明落盘 `docs/RELEASE_NOTES.md`。v1.0.0 MVP 交付，全部提交推送 GitHub。
- 2026-08-21（v1.0.0 收尾补漏，编排者调度）：① 修复首页最近交易分类联表 TODO——HomeViewModel 注入 ObserveCategoriesUseCase、HomeUiState.Success 携带 categoriesById、HomeScreen 传真实分类名/图标/色（替代 null 占位 ❔），commit 0c041a3；② 可测性改造——DatabaseGate 提炼 DatabaseProvider 接口（加密语义零改动），RoomTransactionRunner/RoomDataSourceImpl 依赖接口化，commit 3f042b6；③ Tester 补全 TransactionRunnerAtomicityTest 三个空骨架用例为真实断言（内存库 + DatabaseProvider 替身：deleteById 三表分发 / runInTransaction 回滚 / deleteByLedger 四表批删），commit ab7214c；④ 构建验证全绿（compileDebugKotlin + testDebugUnitTest 56 例 + compileDebugAndroidTestKotlin）；⑤ 任务清单 TASK-001~030 状态统一翻转为「已完成」。instrumented 用例仍待设备执行（标注待人工，见 09_test_report.md / 10_release_notes.md）。

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
