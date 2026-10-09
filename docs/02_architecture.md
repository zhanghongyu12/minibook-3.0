# MiniBook Android 重构版 - 架构设计文档

> 状态：**已确认**（8 项决策已闭环，见 §14）
> 维护者：Architect
> 最后更新：2026-08-18
> 依据：`docs/01_prd.md`（已确认，唯一需求事实来源）；旧项目 `minibook/docs/ARCHITECTURE.md`、`minibook/docs/DATA_MODEL.md` 仅供数据字段语义参考，旧技术栈（Taro/Dexie）已废弃。

---

## 目录

- [1. 概述与架构目标](#1-概述与架构目标)
- [2. 技术栈总览](#2-技术栈总览)
- [3. 模块划分与目录结构](#3-模块划分与目录结构)
- [4. MVVM 分层职责与数据流](#4-mvvm-分层职责与数据流)
- [5. DI 与线程模型](#5-di-与线程模型)
- [6. 数据源抽象出口设计（云端预留）](#6-数据源抽象出口设计云端预留)
- [7. SQLCipher 加密与密钥管理方案](#7-sqlcipher-加密与密钥管理方案)
- [8. 多账本 ledgerId 作用域方案](#8-多账本-ledgerid-作用域方案)
- [9. 应用锁流程](#9-应用锁流程)
- [10. 导入导出设计](#10-导入导出设计)
- [11. 暗色模式](#11-暗色模式)
- [12. 导航结构](#12-导航结构)
- [13. 关键非功能需求落地口径](#13-关键非功能需求落地口径)
- [14. 待人工决策项汇总](#14-待人工决策项汇总)

---

## 1. 概述与架构目标

MiniBook 是纯 Android 原生个人记账应用，本地优先、隐私优先。本重构版在架构上的一次性关键投入（从第一天就做对）：

| # | 硬性要求 | 落地原则 |
|---|---------|---------|
| 1 | **多账本（F-11）** | 所有业务实体带 `ledgerId` 外键，数据从第一天按账本隔离，默认一个「默认账本」 |
| 2 | **数据加密（F-10）** | 数据库从第一天用 SQLCipher 整体加密，密钥由 Android Keystore 保护，生物识别通过后才取密钥解密 |
| 3 | **云端出口预留** | DataSource 抽象层：`RoomDataSource` 本期实现、`RemoteDataSource` 仅接口；实体保留 `syncStatus`、预留 `SyncChange` 表 |
| 4 | **数据规范** | 金额整数「分」、UUID 主键、ISO 8601 时间字符串 |

架构目标：

- **可测试**：分层 + 接口抽象，业务层（domain）不依赖任何 Android 框架类与具体存储实现。
- **可演进**：本期只有 Room，未来加云端、加搜索/报表/预算，业务层零改动或极小改动。
- **安全默认**：加密、密钥、应用锁三位一体，从第一天启用真实解密门，而非补丁式遮罩。

---

## 2. 技术栈总览

| 层级 | 选型 | 版本/要点 |
|------|------|----------|
| 语言 | Kotlin | Kotlin 2.2.0，JDK 21 |
| UI | Jetpack Compose + Material 3 | Compose BOM 2025.09.01；compileSdk 36，minSdk 26（Android 8.0） |
| 本地存储 | Room + SQLCipher | Room 2.8.4（KSP）；`net.zetetic:sqlcipher-android` 4.6.1（AndroidX SQLite 兼容线） |
| 密钥 | Android Keystore | AES-256 / GCM，auth-gated key 保护 SQLCipher 密码 |
| 生物识别 | `androidx.biometric` | BiometricPrompt 1.1.0，支持设备凭据回退 |
| DI | Hilt | Hilt 2.58（KSP 编译） |
| 异步 | 协程 + Flow | Dispatcher 注入，Room Flow 观察 |
| 架构 | MVVM + Repository + UseCase + DataSource | 单模块、按包分层 |
| 构建 | Gradle 8.14.3 / AGP 8.13.0 | JDK 21，compileSdk 36，KSP 2.2.0-2.0.2 |

> 版本口径说明：本表版本为**已定技术栈**（见 `DEC-001` / `docs/07_decisions.md`），工程初始化与 `libs.versions.toml` 一律以此为准，不得再改。

---

## 3. 模块划分与目录结构

### 3.1 模块策略

MVP 采用**单 Gradle 模块 `:app` + 按包分层**：三层（data / domain / ui）+ 汇聚点（di / util / theme）。

理由：
- 领域分层清晰度优先级高于构建拆分；单模块可避免一开始就引入多模块的构建复杂度。
- 包边界即模块边界：若后续拆分，`com.minibook.domain.*` → `:domain`，`com.minibook.data.*` → `:data` 等，包内代码几乎无需改动。

### 3.2 目录结构

```
app/src/main/java/com/minibook/
├── MiniBookApp.kt                    # Application：初始化 Security/Crash、注册锁生命周期
├── MainActivity.kt                   # 单 Activity + Compose
├── di/                               # Hilt 装配
│   ├── DispatcherModule.kt           # 注入 CoroutineDispatcher + 应用级 scope
│   ├── DatabaseModule.kt             # SQLCipher Room 实例 / DAO / DatabaseGate
│   ├── SecurityModule.kt             # KeyManager / BiometricHelper / 锁设置
│   ├── DataStoreModule.kt            # SettingsDataStore / LedgerDataStore
│   └── DataSourceModule.kt           # @Binds：DataSource 接口 → RoomDataSource 实现
├── data/                             # 数据层实现
│   ├── local/
│   │   ├── db/
│   │   │   ├── MiniBookDatabase.kt   # RoomDatabase（SQLCipher openHelperFactory）
│   │   │   ├── entity/               # TransactionEntity / DebtEntity / CategoryEntity
│   │   │   │                         #   / LedgerEntity / BudgetEntity / SyncChangeEntity
│   │   │   ├── dao/                  # TransactionDao / DebtDao / CategoryDao
│   │   │   │                         #   / LedgerDao / BudgetDao / SyncChangeDao
│   │   │   └── Converter.kt          # 枚举<->String 等 TypeConverter
│   │   ├── datasource/
│   │   │   ├── RoomDataSource.kt     # 聚合实现，实现 data/datasource 全部接口
│   │   │   └── RoomDataSourceImpl.kt
│   │   └── security/
│   │       ├── KeyManager.kt         # Keystore 密钥生成/解密 SQLCipher 密码
│   │       └── PasswordCipher.kt     # AES-GCM 加解密 master key blob
│   ├── remote/
│   │   └── RemoteDataSource.kt       # 仅接口，本期空实现（见 §6）
│   ├── datasource/                   # ★ 抽象层唯一宿主
│   │   ├── TransactionDataSource.kt
│   │   ├── DebtDataSource.kt
│   │   ├── CategoryDataSource.kt
│   │   ├── LedgerDataSource.kt
│   │   ├── BudgetDataSource.kt
│   │   └── SyncChangeDataSource.kt
│   └── mapper/                       # Entity ↔ Domain 双向映射
└── domain/                           # 业务层（纯 Kotlin，不依赖 Android 框架）
    ├── model/                        # Transaction / Debt / Category / Ledger / Budget
    │                                 #   / SyncChange / Summary 等纯数据类
    ├── repository/                   # 接口 + 实现
    │   ├── TransactionRepository.kt（接口）
    │   ├── TransactionRepositoryImpl.kt
    │   ├── DebtRepository.kt / DebtRepositoryImpl.kt
    │   ├── CategoryRepository.kt / CategoryRepositoryImpl.kt
    │   ├── LedgerRepository.kt / LedgerRepositoryImpl.kt
    │   ├── BudgetRepository.kt（Phase 2）
    │   └── SyncChangeRepository.kt（Phase 2）
    ├── usecase/
    │   ├── transaction/              # 记一笔 / 编辑 / 删除 / 本月汇总
    │   ├── debt/                     # 记欠款 / 还款 / 按人净额
    │   ├── category/                 # 内置种子 / 自定义增删改 / 删除前关联检查
    │   ├── ledger/                   # 默认账本引导 / 创建 / 切换
    │   ├── export/                   # ExportAllUseCase / ImportAllUseCase
    │   ├── budget/                   #（Phase 2 预留）
    │   └── report/                   #（Phase 2 预留）
    └── common/                       # 结果类型 / 校验异常 / 业务错误模型
└── ui/
    ├── theme/                        # Color.kt / Theme.kt / Type.kt / DarkModeSetting
    ├── navigation/                   # MiniBookNavHost.kt / Destinations.kt
    ├── components/                   # 通用组件（金额输入、分类选择器、空态、TabBar 项等）
    ├── main/                         # 5 个主页面壳 + 底部导航
    ├── home/                         # 首页仪表板
    ├── transactions/                 # 收支记录
    ├── debts/                        # 债务管理
    ├── categories/                   # 分类管理
    ├── settings/                     # 设置（含安全/外观/数据）
    ├── lock/                         # 解锁门（冷启动锁 / 重锁）
    ├── sheets/                       # 编辑表单 BottomSheet（记一笔/债务等）
    └── reserved/                     # search / reports / budgets（Phase 2 占位）
└── util/                             # MoneyFormatter / TimeFormatter / Validators / JsonMappers
```

关键命名约定：

| 层 | 命名 | 说明 |
|----|------|------|
| Entity | `*Entity`（如 `TransactionEntity`） | Room 表映射，仅存在于 `data/local/db` |
| Domain model | `Transaction` / `Debt` 等 | domain 层纯净数据类，UI 只依赖它 |
| DataSource 接口 | `TransactionDataSource` | `data/datasource` 包，业务层唯一数据入口抽象 |
| Room 实现 | `RoomDataSource`（内部聚合各 DAO） | 实现全部 `*DataSource` 接口 |
| Repository | `TransactionRepository` 接口 + `Impl` | domain 层，依赖 `*DataSource` |

---

## 4. MVVM 分层职责与数据流

### 4.1 分层职责

| 层 | 职责 | 依赖方向 | 禁止事项 |
|----|------|---------|----------|
| **UI（Compose）** | 渲染 StateFlow 状态、派发 UserIntent / 事件 | 只依赖 ViewModel 暴露的 State 与事件函数 | 不直接碰 Repository/DataSource/存储；不做业务规则 |
| **ViewModel** | 持有 UI 状态（`StateFlow<UiState>`）、调 UseCase、事件→UseCase | 依赖 UseCase（或极轻量时 Repository） | 不直接依赖 DAO/DataSource；不持有 Context 做 I/O |
| **UseCase** | 单用途业务动作：规则校验、事务编排、错误归一 | 依赖 Repository 接口 | 无跨数据源原生实现感知 |
| **Repository** | 领域模型的聚合入口：组合本地+未来远程、缓存、映射 | 依赖 `data/datasource` 接口 | 不依赖 Room / SQL 细节 |
| **DataSource 接口** | 存储能力契约（CRUD / 查询 / 变更追踪） | 无 | — |
| **RoomDataSource** | 本期唯一实现：DB + mapper + DAO 组装 | 依赖 DAO / DatabaseGate | 不承载业务规则 |

### 4.2 数据流图

```
┌─────────────────────────────────────────────────────────────┐
│  UI (Compose)                                               │
│   • collect StateFlow       • 派发 UserIntent             │
└──────────────┬──────────────────────────────┬──────────────┘
               │ 订阅                         │ 事件
               ▼                             ▼
┌─────────────────────────────────────────────────────────────┐
│  ViewModel (@HiltViewModel)                                  │
│   UiState = StateFlow (stateIn/WhileSubscribed)              │
│   Intent → UseCase.invoke()                                 │
└──────────────┬──────────────────────────────┬──────────────┘
               │ suspend / Flow                │
               ▼                              ▼
┌─────────────────────────────────────────────────────────────┐
│  UseCase（校验、编排、错误归一化）                            │
│   e.g. AddTransactionUseCase / FetchMonthSummaryUseCase      │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Repository 接口（domain 层）                                │
│  TransactionRepository / DebtRepository / ...               │
└──────────────────────────┬──────────────────────────────────┘
                           │ 只依赖 DataSource 接口
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  data/datasource 接口        ┌────────────────────────────┐  │
│  TransactionDataSource  ◀─── │  RemoteDataSource（未来）  │  │
│  DebtDataSource             │  本期无实现，仅契约          │  │
│  CategoryDataSource         └────────────────────────────┘  │
│  LedgerDataSource                                           │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  RoomDataSourceImpl（本期实现）                              │
│  DAO.suspend / DAO.Flow → Entity → mapper → Domain          │
└──────────────────────────┬──────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Room + SQLCipher 加密数据库                                │
└─────────────────────────────────────────────────────────────┘
```

### 4.3 状态管理约定

- **单向数据流**：`State<UiState>`（`StateFlow`）+ `Event`（一次性 UI 动作用小工具 `EventBus`/`Channel`）。
- **冷 Flow 订阅**：Repository 中列表/汇总为 Room 冷 Flow；ViewModel 用 `stateIn(viewModelScope, WhileSubscribed(5000), initial)` 聚合，离开页面 5 秒自动回收，配合 ROOM 查询只在下游订阅时才执行。
- **账本维度切换重订阅**：凡「当前账本」相关的 Flow，一律 `currentLedgerId().flatMapLatest { ... }`，切账本自动重跑查询（见 §8）。
- **进程级唯一状态**：当前账本 ID、主题、锁设置放 DataStore；不把「当前账本」存在内存单例。

---

## 5. DI 与线程模型

### 5.1 Hilt 模块清单

| 模块 | 提供内容 | 说明 |
|------|---------|------|
| `DispatcherModule` | `@IoDispatcher` / `@DefaultDispatcher` / `@MainDispatcher`（限定符注入 `CoroutineDispatcher`）、`@ApplicationScope CoroutineScope` | 所有切线程必须经限定符，便于测试替换 |
| `DatabaseModule` | `MiniBookDatabase`、各 `*Dao`、`DatabaseGate` | 见 §7，DB 经「解密门」才可打开，模块不直接产生裸实例 |
| `SecurityModule` | `KeyManager`、`BiometricHelper`、`AppLockController` | Keystore + BiometricPrompt 封装 |
| `DataStoreModule` | `SettingsDataStore`（主题/锁/超时）、`LedgerDataStore`（当前账本 ID、账本列表缓存） | Preferences DataStore / DataStore |
| `DataSourceModule` | `@Binds` 各 `*DataSource` 接口 → `RoomDataSourceImpl` | **未来接云端只改这个模块**：Room 实现替换/聚合为混合实现 |

### 5.2 线程模型

| 动作 | Dispatcher | 说明 |
|------|-----------|------|
| 所有 DB 访问（suspend/Flow） | Room 内建 executor | Room 自动切后台；DAO 方法标注 `@Query` 即跳过主线程检查 |
| 导入导出文件 I/O、JSON（反）序列化 | `@IoDispatcher` | `ExportAllUseCase`/`ImportAllUseCase` 内显式 `withContext(io)` |
| Keystore 加解密（master key 解包） | `@IoDispatcher` | Keystore 调用通常很快，但避免主线程 |
| 组合/压缩多个源的数据（如导出打包） | `@DefaultDispatcher` | CPU 型聚合适用 |
| `BiometricPrompt` 回调 | 主线程 | androidx.biometric 要求主线程回调 |
| UI 收集 / `stateIn` | `Dispatchers.Main.immediate` | Compose 订阅强制主线程 |

### 5.3 Flow 生命周期与泄暴露线

- 所有暴露给 UI 的数据流都收拢到 ViewModel 的 `StateFlow`，UI 不直接 `flow.collect` Room 源。
- 用 `SharingStarted.WhileSubscribed(5000)`，防止锁门/锁屏/后台时白白执行查询。
- **重锁（DB 关闭）时的失效策略**：`AppLockController.lock()` 会触发 `viewModelClear` 事件（通过 `ClearableViewModel` 标记 / `SavedStateHandle` 存证），各 ViewModel 取消订阅并回初始态；解锁后随新 Flow 重建（见 §9 时序）。

---

## 6. 数据源抽象出口设计（云端预留）

### 6.1 接口定义（`data/datasource/`）

按实体拆分能力契约，跨实体聚合（导出）走专用 UseCase：

```kotlin
interface TransactionDataSource {
    suspend fun insert(tx: Transaction): String
    suspend fun update(tx: Transaction)
    suspend fun deleteById(id: String)
    fun observeByLedger(ledgerId: String, filter: TransactionFilter): Flow<List<Transaction>>
    suspend fun monthlySummary(ledgerId: String, month: YearMonth): MonthSummary
    suspend fun getById(id: String): Transaction?
    // Phase 2（搜索复用同一过滤器扩展）：value range / remark like
}

interface DebtDataSource {
    suspend fun insert(debt: Debt): String
    suspend fun update(debt: Debt)
    suspend fun deleteById(id: String)
    fun observeByLedger(ledgerId: String, filter: DebtFilter): Flow<List<Debt>>
    suspend fun netByParty(ledgerId: String): Map<String, Long>   // 净额（分）
}

interface CategoryDataSource {
    suspend fun upsertList(categories: List<Category>)          // 种子/导入用
    suspend fun insert(category: Category): String
    suspend fun update(category: Category)
    suspend fun deleteById(id: String)
    suspend fun countByLedger(ledgerId: String): Long
    fun observeByLedger(ledgerId: String, type: CategoryType?): Flow<List<Category>>
    suspend fun countTransactionsByCategory(categoryId: String): Int  // 删除前关联检查
}

interface LedgerDataSource {
    suspend fun insert(ledger: Ledger): String
    suspend fun update(ledger: Ledger): Ledger
    suspend fun getById(id: String): Ledger?
    suspend fun getDefault(): Ledger?
    fun observeAll(): Flow<List<Ledger>>
}

// Phase 2 预留（现在只定义，不实现）
interface BudgetDataSource { /* ... */ }
interface SyncChangeDataSource {
    suspend fun enqueue(entityType: String, entityId: String, action: String, data: String?)
    suspend fun pendingChanges(): List<SyncChange>
    suspend fun markSynced(ids: List<String>)
}
```

### 6.2 RoomDataSource 与 RemoteDataSource 的关系

```
                ┌─────────────────────────────────────────────┐
                │         Repository（业务层，零感知）          │
                │  TransactionRepository / DebtRepository ...  │
                └──────────────────────┬──────────────────────┘
                                       │ 仅依赖契约
                ┌──────────────────────▼──────────────────────┐
                │   DataSource 接口                            │
                └──────────────┬─────────────────┬────────────┘
                               │                 │
          ┌────────────────────▼────┐   ┌───────▼─────────────┐
          │  RoomDataSourceImpl     │   │ RemoteDataSource    │
          │  本期唯一实现            │   │ 仅接口，本期空       │
          │  （本机 SQLCipher）     │   │ Phase 3 实现        │
          └───────────┬────────────┘   └─────────────────────┘
                      ▼
          Room + SQLCipher 数据库
```

### 6.3 未来接云端接入点（设计成零业务改动）

1. **新实现 `RemoteDataSource`**（网络仓储，仅接口先行定义），对接 REST/GraphQL。
2. **混合模式**：新增一个 `SyncingDataSource`（组合 Room + Remote），一并交给 `DataSourceModule`。
   - 本地优先（Offline-First）：写路径先落 Room；读路径本地流即可（网络不可见）。
   - 变更推送：Room 写路径在事务内调用 `SyncChangeDataSource.enqueue()`，后台 `SyncWorker`（WorkManager）消费队列。
   - 冲突解决：优先后台行级 `updatedAt` LWW（Last-Write-Wins），复杂冲突进 UI「conflict 列表」人工选择（对应 `syncStatus=conflict`）。
3. **切换处只有一个**：`DataSourceModule` 的 `@Binds` 绑定换成混合实现，Repository 不动。
4. 预留字段：所有业务实体带 `syncStatus`（`local/syncing/synced/conflict`）；`SyncChange` 表（详见 03_database.md §3.6）。

> 本期 **不实现、不接线** SyncChange 写入。表与字段先落库，仅保证未来不迁移数据即接入。

---

## 7. SQLCipher 加密与密钥管理方案

### 7.1 加密选型

- 数据库整体加密：**SQLCipher for Android**（`net.zetetic:sqlcipher-android`），通过 `androidx.sqlite` 的 `SupportOpenHelperFactory` 接入 Room。
- Room 构建方式：

```
Room.databaseBuilder(context, MiniBookDatabase::class.java, "minibook-encrypted.db")
    .openHelperFactory(SQLCipherOpenHelperFactory(passphraseBytes))  // 自定义 Factory 注入密码
    .fallbackToDestructiveMigration(false)   // 严格迁移
    ...
```

- 密码（passphrase / key material）：**32 字节随机数**（`SecureRandom`），以 hex 字符串形式交给 SQLCipher。

### 7.2 密钥体系（两层：一把 master key + 两把包裹 key）

```
┌────────────────────────────────────────────────────────────────┐
│  Android Keystore（硬件级，永不出域）                             │
│  alias = "minibook_master_key_auth"   ← 锁「开启」时用           │
│    AES-256 / GCM / NoPadding                                    │
│    setUserAuthenticationRequired(true)                          │
│    setInvalidatedByBiometricEnrollment(true)                    │
│  alias = "minibook_master_key_plain"  ← 锁「关闭」时用           │
│    AES-256 / GCM / NoPadding                                    │
│    （无认证要求，可静默自动开库）                                  │
└───────────────────────────┬────────────────────────────────────┘
                            │ AES-GCM 加密 / 解密（两把 key 均可）
                 ┌──────────▼──────────────┐
                 │  SQLCipher master key   │  ← 32 字节随机数
                 │  （唯一用于 DB 的密码）   │
                 └─────────────────────────┘
```

- **master key 从不出域**：被 Keystore 的 AES-GCM Key 加密后，以密文 blob（Base64）持久化在应用私有目录（`context.noBackupFilesDir/minibook_key.bin`）。
- **两把包裹 key 对应同一把 master key**（D-1 决定「锁可关」）：应用锁开启时用 `_auth` key（生物识别/系统凭据通过后才放行），关闭时用 `_plain` key（静默开库）。两者均加密同一 master key；切换锁状态时用新 key 重加密并覆盖 `minibook_key.bin`，实现「关锁不降级为明文」。
- SQLCipher passphrase 只在**解密打开 DB 的瞬间**存在于内存，使用后 `Arrays.fill(byte[], 0)` 清零。

### 7.3 密钥生成 / 存储 / 读取流程

1. **首次启动（First Run）**：
   - 检查 DB 文件是否存在；不存在 → 生成 32 字节随机 master key → 用当前锁状态对应的 Keystore key 做 GCM 加密 → 写 `minibook_key.bin`。
   - 随后打开 DB、`BootstrapUseCase` 种子：默认账本 + 12 支出 + 6 收入分类（见 §8.4 / 03_database.md §7）。
2. **每次冷启动 / 重锁解锁**（读取流程）：
   - 读 `minibook_key.bin`（密文）。
   - **锁开启**：BiometricPrompt 验证通过拿到 CryptoObject（内含可用的 Cipher）→ 用 auth 后的 `_auth` key 解密 master key（**解密动作绑定在 CryptoObject 上，Keystore 才会放行 auth-gated key**）。
   - **锁关闭**：直接用 `_plain` key（无认证要求）解密 master key，静默开库。
   - 解出的字节作为 SQLCipher password，`DatabaseGate.open()` 打开 DB；用后立即清零。

### 7.4 与生物识别解锁的联动时序（冷启动）

```
冷启动
  │ 创建 MiniBookApp（安全模块不触碰 DB）
  ▼
分诊 A：应用锁是否开启？（SettingsDataStore.appLockEnabled）
  ├─ 否 → 用 _plain key 静默解出 master key → 打开 DB → 直达主入口（无锁屏）
  └─ 是 ▼
分诊 B：设备已设系统锁屏？（isDeviceSecure / 生物识别可用）
  ├─ 否 → 见 §14 D-2（引导设锁屏，不降级明文）
  └─ 是 ▼
导航到 LockScreen（此时 DB 未打开，任何数据不可达）
  │
  ▼
用户触发：BiometricPrompt.newInstance(...).authenticate(credential, promptInfo, cryptoObject)
  │  回调 onAuthenticationSucceeded(result)
  │   result.cryptoObject.cipher  ← 已被 Keystore 授权可用
  ▼
withContext(@IoDispatcher) {
    val plain = cipher.doFinal(encryptedMasterKey)      // 解出 32B master key
    DatabaseGate.open(plain)                            // 打开 SQLCipher DB
    plain.fill(0x00)
}
  │
  ▼
解锁完成 → 主入口（Home）→ 各 ViewModel 收集账本 Flow
```

### 7.5 与生物识别解锁的联动时序（离开超时重锁）

```
onStop（后台）/ ON_STOP → 启动闲置计时（立即/30s/1min/5min/永不）
   │ 回前台：elapsed >= 阈值 ?
   ├─ 否 → 恢复使用（DB 保持打开）
   └─ 是 → AppLockController.lock()
            ├── DatabaseGate.close()          // 关闭 SQLCipher 连接
            ├── master key 内存清零、cipher 置空
            ├── 广播 lock 事件 → 各 ViewModel 清态、取消订阅
            └── UI 切回 LockScreen（返回必走 §7.4 全流程）
```

技术要点：
- `BiometricPrompt` 配置 `setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)`，实现「指纹/人脸 + 系统 PIN/图案/密码」双重能力（符合 PRD F-05「系统凭据回退」）。
- minSdk 26 注意：API 26/27 对 auth-gated key 的认证语义与 28+ 不同——`setInvalidatedByBiometricEnrollment` 自 API 24 起即可用（本版正常支持），但 26/27 上 auth-gated key 绑定的是指纹体系，多模态生物识别与 enrollment 失效行为以 28+ 为基准；低版本上优先使用 `setUserAuthenticationValidForDurationSeconds(-1)` 让每次使用都要求重新认证，实现期以安全提示注释收敛。
- **备份/云恢复矛盾**（已作为决策 D-5）：Keystore 密钥不随备份迁移 → 若用户换机恢复备份，本地 DB 密文将无法解密。本期设定 `android:allowBackup=false`（或 `dataExtractionRules` 排除 DB 与 key 文件），换机迁移走「应用内导出 JSON → 新机导入」路径。

---

## 8. 多账本 ledgerId 作用域方案

### 8.1 数据模型作用域

| 实体 | ledgerId | 说明 |
|------|:--------:|------|
| `Ledger` | — | 账本表本身无 ledgerId |
| `Transaction` | ✔ | 事务永远属于且只属于一个账本 |
| `Debt` | ✔ | 债务明细按账本隔离 |
| `Category` | ✔ | 分类按账本隔离（新账本创建时克隆内置集） |
| `Budget` | ✔ | 预算按账本 + 月份 |
| `SyncChange` | ✔ | 变更追踪带账本维度（本期建表不写数据） |

所有业务 DAO 查询第一过滤条件统一为 `WHERE ledgerId = :ledgerId`；数据表复合索引一律以 `ledgerId` 打头（03_database.md §5）。

### 8.2 当前账本的提供方式

- 单例 `LedgerDataStore`：`currentLedgerId: Flow<String>`（Preferences DataStore，即时同步）。
- ViewModel 统一模式：

```kotlin
val monthSummary = ledgerProvider.currentLedgerId()
    .flatMapLatest { id -> transactionRepository.observeMonthSummary(id, month) }
    .stateIn(viewModelScope, WhileSubscribed(5000), Loading)
```

- Repository / UseCase 中需要当前账本时，注入 `LedgerProvider`（轻接口）获取 `suspend currentLedgerId()`。

### 8.3 切换账本

1. 设置/账本管理页「切换到账本 X」→ `LedgerDataStore.setCurrentLedgerId(x.id)`。
2. 所有 `flatMapLatest` Flow 自动重订阅 → 首页/收支/债务/分类/预算全部切换到 X 的数据。
3. 统计口径：报表/汇总均按「当前账本」计算，不混账。
4. 切换是**纯状态变更**，不涉及任何数据搬移。

### 8.4 默认账本初始化

- 数据库首次打开（`BootstrapUseCase`，幂等）：
  1. 检查 `ledgers` 是否为空 → 空则插入「默认账本」（`isDefault=1`，name="默认账本"，icon/color 内置值）。
  2. 为该默认账本克隆内置分类集（12 支出 + 6 收入，`isBuiltIn=true`）。
  3. `LedgerDataStore` 无值时写入默认账本 ID。
- **新账本创建流程**：插入 Ledger 行 → 同一事务克隆内置分类 → 切换为当前账本。
- 账本列表页：展示全部账本（含 `isDefault` 标记）、默认账本不可删除。

### 8.5 ledgerId 派生与审计

- 一律从 `LedgerProvider` 取，禁止 UseCase 内部硬编码或从 UI 参数透传未校验的 ledgerId。
- UI 传入的账本 ID 若不存在 → Repository/UseCase 抛 `LedgerNotFound`，UI 引导回账本管理。

---

## 9. 应用锁流程

沿用「真实解密门」定位（F-05 + F-10 联动）：锁即「DB 不可解密」，不做防偷瞄遮罩。

| 阶段 | 行为 |
|------|------|
| 冷启动 | 锁开启时恒锁：未解锁前不打开 DB、无可达数据（LockScreen 为唯一路由）；锁关闭时静默开库直达主入口（§7.4 分诊 A） |
| 离开超时 | `立即 / 30s / 1min / 5min / 永不`（存 SettingsDataStore）。onStop 后计闲置，回前台上超阈 → 重锁 |
| 生物识别 | BiometricPrompt 支持指纹/人脸/系统凭据；失败可重试；多次失败停留锁屏 |
| 设备无解锁方式 | 提示「不支持」（PRD F-05 异常处理），见 D-2 |

状态机：

```
        ┌──────────────────────────────────────────────┐
        │  Locked                                         │
        │  DB closed · key 未解 · 无可达数据              │
        └───────────────┬──────────────────────────────┘
                        │ BiometricPrompt 成功（§7.4）
                        ▼
        ┌──────────────────────────────────────────────┐
        │  Unlocked                                       │
        │  DB open · Flow 正常 · 正常使用                 │
        └──┬──────────────────────────────▲─────────────┘
           │ onStop 闲置超阈              │ 回前台且未超阈
           ▼                              │
        Locked ◀──────────────────────────┘
```

锁状态由 `AppLockController`（单例，持 Gate + 生命周期观察者）驱动；`MainActivity` 通过 `collectAsState` 决定根级组合是 LockScreen 还是主入口。

---

## 10. 导入导出设计

### 10.1 导出

- 路径：设置 → 数据 → 导出。触发 `ExportAllUseCase`。
- 产出：`minibook_backup_yyyyMMdd_HHmmss.json`，写入 `context.getExternalFilesDir` / 用户选择的目录（SAF）。
- **数据层外是明文 JSON**（可读、可人工 inspect；导出本身加密属可选增强，见 D-4）。
- 结构（version 1）：

```jsonc
{
  "version": 1,
  "appVersion": "1.0.0",          // 生成方版本，便于人工判断
  "exportedAt": "2026-08-18T09:00:00.000Z",
  "scope": "all",                 // all | ledger:<id>（§10.4）
  "ledgers": [ { "id": "...", "name": "默认账本", "isDefault": true, ... } ],
  "categories": [ { ...含 ledgerId, isBuiltIn, order... } ],
  "transactions": [ { ...含 ledgerId, amount(分)... } ],
  "debts": [ { ...含 ledgerId, kind, direction, amount(分)... } ],
  "budgets": []                    // Phase 2 落库后导出；本期恒空数组
}
```

> 不含 `syncChanges`（同步簿记不随全量迁移；未来云端迁移另配协议）。

### 10.2 导入

- `ImportAllUseCase`：
  1. 解析校验（严格）：`version` 支持 1；字段类型/必填校验失败 → 停止、报错、**零落库**。
  2. **引用完整性校验**成对执行：`transaction.categoryId` 必须存在于 `categories`；`*.ledgerId` 必须存在于 `ledgers`；金额为正整数；成对 UUID 合法。
  3. 通过后在 **单事务** 内执行：清空 `transactions/debts/categories/budgets`（保留/重建 ledgers 按导入内容）→ 逐行写入。
  4. 事务用 Room `withTransaction`，任一失败整体回滚。
- **导入语义 = 全量覆盖**（导入的目标设备以此文件为准）。
- 旧版兼容：检测无 `ledgers` 字段的 legacy JSON → 视为「导入到默认账本」（每行补默认账本 ledgerId），见 D-3。

### 10.3 原子性与约束

- 唯一写入路径是 `ImportAllUseCase`；清空+写入在同一事务与同一线程。
- 导入期间 UI 锁数据入口（防并发改写的「快照冲突」）。
- 导入成功后强制刷新所有账本 Flow（Dao 变更自动触发，无需手动）。

### 10.4 与多账本的关系

- `scope: "all"`：整库备份。
- 导出可被过滤为 `scope: "ledger:<id>"`（仅某账本）；文档级约定：单账本导出文件仍含 `ledgers` 中该账本行与关联分类，导入时按单账本语义合入目标账本（Phase 2 细化）。
- F-11「每账本独立统计与导入导出」→ 本期先支持「全量导出/导入」作为底线能力，单账本子集导入的冲突合并策略随 Phase 2 一并定义。

---

## 11. 暗色模式

- **设置**：`跟随系统 / 手动亮 / 手动暗`，枚举 `DarkModeSetting { SYSTEM, LIGHT, DARK }`，存 SettingsDataStore。
- **Material 3 动态配色**：Android 12+（API 31+）用 `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)` 取壁纸主题色；低于 31 用内置 `LightColorScheme` / `DarkColorScheme` 降级。
- **装配点**：`MiniBookTheme(darkTheme = resolvedDark, dynamicColor = ...)`，`resolvedDark = when(setting){ SYSTEM -> isSystemInDarkTheme(); else -> setting==DARK }`，随 DataStore 流软切换（`collectAsState`）。
- **全页面生效**：颜色一律引用 `MaterialTheme.colorScheme.*` token 与 `SystemBarStyle`（状态栏/导航栏同步沉浸）。
- Phase 1 收口：色彩 token 集中 `ui/theme/Color.kt`，避免页面散落硬编码色值。

---

## 12. 导航结构

- 技术：`androidx.navigation:navigation-compose`，单 Activity。
- **未解锁态**：NavGraph 顶层 `isLocked` 门控 → 只可达 `lock` 目标（真解密门，可达性即安全性）。
- **解锁后 5 个主页面**（底部 `NavigationBar`，Material 3）：

| 目的地 | route | 内容 | 对应 PRD |
|--------|-------|------|---------|
| 首页仪表板 | `home` | 本月结余/收支合计/最近交易/FAB「记一笔」 | F-01 |
| 收支记录 | `transactions` | 筛选（全部/支出/收入）+ 按日期分组列表 | F-01 |
| 债务管理 | `debts` | 明细视图（时间排）+ 按对象视图（净额汇总） | F-02 |
| 分类管理 | `categories` | 内置（禁删）+ 自定义增删改 | F-03 |
| 设置 | `settings` | 安全（锁/超时/加密态）、外观（暗色）、数据（导出/导入）、账本 | F-04/05/09/11 |
| 账单导入 | `import` | 入口提示页（微信/支付宝导出路径）+ 文件选择 + 结果反馈（F-12） | F-12 |

- 预留目的地（Phase 2 注册，本期不可达或占位页）：`search`、`reports`、`budgets`。
- Phase 2 新增 `import` 路由（F-12）：入口在首页/收支页/设置「数据」区，点入 `import` 提示页（§16.8），不进底部 `NavigationBar`、作为根图全屏页注册。
- 弹层/全屏：`sheets`（记一笔/编辑债务/还款/分类选择）、`ledger-manage`、`app-locked`。
- 标签切换用 `saveState + restoreState` 保留各 Tab 状态；页面间参数只传 `id` 类（UUID），大数据走共享 `LedgerProvider`。

---

## 13. 关键非功能需求落地口径

| 需求 | 落地 |
|------|------|
| 冷启动 ≤ 2s（含解锁） | 安全路径后置：首帧先出 LockScreen UI，DB 打开耗时不影响首帧；DB 打开为 3.x 毫秒级（本机小库） |
| 千条级列表流畅 | `LazyColumn` + `key=id` + 稳定 Diff；汇总用 DB 端 SQL 聚合（不拉全量内存算）；Phase 2 若上搜索/报表可引入 Paging 3 |
| 金额整数分 | `Long` 存分；`util/MoneyFormatter` 统一元↔分转换与格式化（03_database.md §8） |
| 离线可用 | 无网络依赖；网络仅出在未来的 RemoteDataSource |
| 兼容 API 26+ | minSdk 26；Biometric/Keystore 低版本分支按 §7.5 处理；Compose 编译目标 36 |
| 可测试性 | domain 纯 Kotlin 无 Android 依赖；DataSource/Repository 接口便于 Fake；UseCase 单测覆盖金额与净额口径 |

---

## 14. 决策记录（已闭环）

> 8 项待决策已全部拍板（2026-08-18）。安全/产品类由用户确认，技术类由协调者代决。实现期以此为唯一口径。

| # | 决策点 | 最终决策 | 影响面 |
|---|--------|---------|--------|
| D-1 | 应用锁关闭时数据库解锁策略 | **锁可关，DB 仍加密**：关闭应用锁后改用无认证要求的备用 Keystore 密钥自动开库，数据库仍整体加密；锁只是「多加一道生物认证」，关锁不等于明文 | §7、§9 |
| D-2 | 设备无系统锁屏时的处理 | 首启动检测 `isDeviceSecure()`，无锁屏则引导用户去设置锁屏，**不降级为明文**（安全默认，符合 PRD F-05） | §7.4 |
| D-3 | 旧版（Taro）导出 JSON 兼容导入 | 支持 legacy 检测（缺 `ledgers` 字段），导入到默认账本 | §10.2 |
| D-4 | 导出文件是否加密 | **明文 JSON**（SAF 用户自管文件）；加密导出留待 Phase 2 可选增强 | §10.1 |
| D-5 | 换机/云备份路径 | `allowBackup=false` + `dataExtractionRules` 排除库与 key 文件；换机走「导出→导入 JSON」（密钥无法随备份迁移的必然选择） | §7.5 |
| D-6 | 内置分类是否允许改名 | 允许改图标/颜色/名称，但**不可删、不可改 type/isBuiltIn**；「其他」兜底分类保持默认名 | 03_database.md §8 |
| D-7 | 预算表唯一约束策略 | Repository 层事务内「先删后插」保证语义唯一，DB 不加冗重约束 | 03_database.md §4 |
| D-8 | 多模块拆分时机 | MVP 单模块；包边界已预留，出现双依赖方再拆 | §3.1 |

> D-1 衍生实现要点：密钥体系需同时维护两个 Keystore 别名——`minibook_master_key_auth`（`setUserAuthenticationRequired(true)`，锁开启时用）+ `minibook_master_key_plain`（无认证要求，锁关闭时自动开库用）；两者均加密同一 SQLCipher master key，切换锁状态时重加密并落盘新 blob。§7 的「两层密钥」图据此扩展为「一个 master key、两把包裹 key」。

---

## 15. Phase 2 设计：搜索 / 报表 / 预算（F-06 / F-07 / F-08）

> 依据：`docs/01_prd.md §6 F-06/F-07/F-08`（已确认）+ `docs/07_decisions.md` DEC-011/012/013 与 DEC-008。
> 定位：Phase 2 三项延展功能**复用既有 MVVM + Repository + UseCase + DataSource 分层**（§4.1 职责表不变），**不新起架构范式、不加 Gradle 模块（DEC-009）、不加第三方依赖（DEC-001）**。仅做「层内加法」：扩 DAO SQL → 扩 filter 模型与 DataSource/Repository 方法 → 新增 UseCase / ViewModel / 页面，逐一替换三个占位页（`ui/reserved/SearchPlaceholder` / `ReportsPlaceholder` / `BudgetsPlaceholder`）。

### 15.1 落地总览

| 功能 | 数据层（DAO / DataSource / Repository） | 业务层（UseCase） | UI 层（ViewModel / 页面） | 替换占位 |
|------|------|------|------|------|
| F-06 搜索 | 扩 `TransactionDao.observeFiltered`（remark/金额区间）、`DebtDao.observeFiltered`（partyName）；扩 `TransactionFilter`/`DebtFilter`（DEC-013） | `SearchTransactionsUseCase` / `SearchDebtsUseCase` | `ui/search/SearchViewModel` + `SearchScreen`（交易/债务双组结果 + 只读明细） | SearchPlaceholder |
| F-07 报表 | `TransactionDao` 新增 `observeDailyTrend` / `observeMonthlyTrend` / `observeCategoryAgg`；`TransactionDataSource`/`TransactionRepository` 同增 3 方法；新增 domain `PeriodTrend`/`CategoryAgg`（DEC-011） | `ObserveTrendUseCase` / `ObserveCategoryBreakdownUseCase` / `ObserveBalanceTrendUseCase` | `ui/reports/ReportsViewModel` + `ReportsScreen` + 自绘图表组件（§15.4 五要素） | ReportsPlaceholder |
| F-08 预算 | 复用已接线的 `BudgetDataSource`/`BudgetRepository`；「已用」复用 `observeCategoryAgg(type=expense)`；新增 domain `BudgetProgress` 模型（DEC-012 / DEC-008） | `SetTotalBudgetUseCase` / `SetCategoryBudgetUseCase` / `ClearCategoryBudgetUseCase` / `ObserveBudgetProgressUseCase` | `ui/budgets/BudgetsViewModel` + `BudgetsScreen`（进度/超支标红）+ 首页入口提示 | BudgetsPlaceholder |

### 15.2 搜索（F-06）

- 数据源：沿用 §8 账本作用域（`ledgerId` 首参），`observeFiltered` 为既有参数化查询；搜索只是「filter 字段扩展」，不新增查询路径（DEC-013）。金额区间 `amountMin/amountMax` 单位「分」，备注/姓名走 `LIKE '%kw%'` 模糊。
- 页面：`search` 路由已注册（§12），默认不可达；接线首页顶部搜索入口后可达。搜索词防抖（`debounce`）+ `flatMapLatest` 重订阅（§8.2 切账本模式）；结果分「交易 / 债务」两组、按时间倒序；点击弹**只读明细**（复用现有 sheet 的展示部分、去掉保存路径）。
- 账本切换：搜索条件服务「当前账本」，切账本重跑查询（`currentLedgerId().flatMapLatest`），不跨账本（F-11）。

### 15.3 报表（F-07）

- 聚合在 DB 端完成（§13「汇总用 DB 端 SQL 聚合，不拉全量内存算」），UI 只渲染：月度视图按「天」、年度视图按「月」输出桶序列（`date` / `substr(date,1,7)`），每桶 `expenseFen/incomeFen`；分类占比按 `categoryId + type` 分组 `SUM(amount)`。
- 三类图：①收支趋势（折线/柱状，月度按天、年度按月）②分类占比（环形，收入/支出分开展示）③结余走势（折线，`incomeFen - expenseFen` 按桶 + 累计）。空态（该账本无任何数据）显示占位（PRD F-07 异常处理）。
- 图表渲染复用 §11 主题（语义 token：收入绿/支出红/neutral）与 `MoneyFormatter`（分→元）。

### 15.4 图表「五要素」（DEC-011 原生 Canvas 落地点）

1. **数据口径与聚合**：金额全程 `Long`「分」（DATABASE §9）；数据已由 DB 端 SQL 聚合成 `PeriodTrend`/`CategoryAgg`，Canvas 不参与任何算术，只画「既得数据点」。
2. **坐标系与刻度**：X 轴 = 时间桶（月度=天 / 年度=月，等距）；Y 轴 = 金额（分→元经 `MoneyFormatter`，tabular figures）；自适应最大刻度 + 网格分隔线，刻度标签不溢出。
3. **图元绘制**：折线 `drawPath`/`drawLine`；柱状 `drawRoundRect`；环形 `drawArc`（sweep 按占比映射 360°，收入/支出分开一张）。
4. **图例与交互**：环形旁图例（分类名 + 色块 + 占比%）；数据点选中高亮为增强（本期可静态渲染）；空态与单桶降级（1 个点/0 数据不崩）。
5. **主题与配色**：色值一律引用 `MaterialTheme.colorScheme.*` 与语义 token（收入绿/支出红/neutral），随暗色模式软切换（§11）；Canvas 颜色取自 `colorScheme` 派生 `Color`。

### 15.5 预算（F-08）

- 口径（对齐 DEC-008）：预算表 `budgets` 已建表（6 表之一，version 1，见 03_database §4.5）；月度总预算 `categoryId=NULL`（Repository 层「先删后插」保证唯一），分类预算 `UNIQUE(ledgerId, month, categoryId)`。
- 进度：`已用 = 当月该分类（或总额）实际支出累计（分）`；`剩余 = 预算 − 已用`；`超支 = 已用 > 预算`（剩余 < 0）。「已用」由 `observeCategoryAgg(type=expense, 当月)` 提供（按分类分组；总额已用 = 各分类之和，等价 `monthSummary.expenseFen`，因 `categoryId` 恒非空）。
- 提醒形态（DEC-012）：**App 内告警**——页内进度条超支标红 + 超支文案；首页/入口轻量提示当前月超支。系统通知栏为可选、本期不实现（`POST_NOTIFICATIONS` 权限 API33+ + 后台触发时机权衡）。

### 15.6 无新表 / 无新索引

Phase 2 三项功能均**不新增表、不新增索引**：预算表已存在（03_database §4.5）；搜索/报表/预算全部复用既有 `(ledgerId, date)` / `(ledgerId, categoryId)` / `(ledgerId, partyName)` / `(ledgerId, month)` 复合索引，只扩 DAO SQL。数据库版本仍为 **1**，`fallbackToDestructiveMigration(false)` 不变。详见 `03_database.md §10`。

> 注：F-12 账单导入（Phase 2 同阶段）不属本节「无新表无新索引」结论——其 `transactions` 加 4 列 + 唯一索引使版本升到 2（见 §16 与 03_database §4.1/§7.5）。

---

## 16. Phase 2 设计：账单导入（F-12）

> 依据：`docs/01_prd.md §F-12`（评审中，DEC-015 口径）+ DEC-016/017/018/019。
> 定位：同 Phase 2 三项功能，**复用既有 MVVM + Repository + UseCase + DataSource 分层，不新起范式、不加 Gradle 模块、不加第三方依赖（DEC-016 零 xlsx 库）**。仅做「层内加法」：加 4 列（03_database §4.1/§7.5）→ 新增解析器 + UseCase + UI。

### 16.1 落地总览

| 层 | 产出 |
|----|------|
| 数据层 | `transactions` 4 新列 + `UNIQUE(ledgerId, externalId)`（DB version 2，MIGRATION_1_2）；`TransactionDao.insertAll(IGNORE)`/`getAllExternalIds`；`TransactionDataSource.insertBatch`/`getExistingExternalIds` |
| 解析栈 | 域接口 `BillParser` + `BillSourceInput`、域模型 `ParsedBillRow`/`BillParseResult`/`ParseSkippedRow`；数据层 `AlipayCsvParser`/`WechatXlsxParser`/`BillParserFactory`/`SafBillFilePreparer` |
| 业务层 | `BillCategoryMapping`（分类映射常量）+ `ImportBillUseCase`（编排/事务/聚合 `BillImportResult`） |
| UI 层 | `import` 路由 + `ImportViewModel` 状态机 + 提示页/文件选择/结果页，入口接线 |

### 16.2 目录归属

```
domain/
  model/            ParsedBillRow.kt / BillParseResult.kt / BillImportResult.kt
                    / BillCategoryMapping.kt（BillParser 接口也可放 domain/usecase/import）
  usecase/import/   BillParser.kt（接口）/ BillSourceInput.kt / BillFilePreparer.kt（域接口）
                    / ImportBillUseCase.kt
data/
  local/import/     AlipayCsvParser.kt / WechatXlsxParser.kt / BillParserFactory.kt
                    / SafBillFilePreparer.kt（Android 依赖，解析栈唯一触碰框架类处）
ui/
  import/           ImportScreen.kt / ImportViewModel.kt
```

> 分层约束：`ParsedBillRow`/`BillParser`/`BillCategoryMapping`/`ImportBillUseCase` **纯 Kotlin（不 import android.***）**，JVM 可单测；`SafBillFilePreparer` 是 Android 依赖边界，隔离 Uri→File/Reader 的 I/O。

### 16.3 统一中间模型与解析器抽象（§4 兼容）

**中间模型**（`domain/model/ParsedBillRow.kt`，字段语义对齐 PRD §F-12 字段映射表）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `date` | String | `yyyy-MM-dd`（交易时间仅取日期，时分秒丢弃） |
| `type` | TransactionType | 收/支 → expense/income |
| `amountFen` | Long | 金额转分（>0，经 `MoneyFormatter.toFen`） |
| `counterparty` | String? | 交易对方原样落库 |
| `paymentMethod` | String? | 空/全空白→null |
| `description` | String | 商品说明 + 备注（备注非空且≠「/」以 `｜` 追加；≤200 截断） |
| `externalId` | String | `alipay:` / `wechat:` 前缀包裹原单号 |
| `platformCategory` | String? | 原始平台分类（仅自动分类用，**不落库**） |

**解析器接口**（`domain/usecase/import/BillParser.kt`，纯 Kotlin）：

```kotlin
interface BillParser {
    val format: TransactionSource     // WECHAT_IMPORT / ALIPAY_IMPORT
    fun parse(input: BillSourceInput): BillParseResult
}

sealed interface BillSourceInput {
    data class Csv(val reader: java.io.Reader) : BillSourceInput   // 支付宝：GBK Reader
    data class Xlsx(val file: java.io.File) : BillSourceInput      // 微信：已落 cacheDir 的可 seek File
}

sealed interface BillParseResult {
    data class Success(val rows: List<ParsedBillRow>, val skipped: List<ParseSkippedRow>) : BillParseResult
    data class Unrecognized(val reason: String) : BillParseResult  // 未识别格式，零落库
}
data class ParseSkippedRow(val rowNumber: Int, val reason: ImportSkipReason, val detail: String)
```

- **工厂**（`data/local/import/BillParserFactory.kt`）：`resolve(displayName: String?, magicBytes: ByteArray?): BillParser?`，按扩展名主判（`.csv`→`AlipayCsvParser`、`.xlsx`→`WechatXlsxParser`）+ 内容嗅探兜底（`PK\x03\x04` 魔数→xlsx；否则按 CSV 试解）；解析不出 → 返回 null → 「未识别账单格式」。
- **跳过原因枚举** `ImportSkipReason{ EXISTS, NEUTRAL, CLOSED, PARSE_ERROR }` 落 `domain/model/Enums.kt`（`NEUTRAL`=不计收支/「/」/空，`CLOSED`=支付宝交易关闭，`PARSE_ERROR`=时间缺失/金额非法/单号空，数字计数对齐 PRD 过滤规则）。

### 16.4 CSV 解析（支付宝，DEC-017）

- **解码**：`InputStreamReader(stream, Charset.forName("GBK"))`（支付宝「交易流水证明」为 GBK）。
- **动态表头探测**：逐行读取，直到某行**同时含**「交易时间」「收/支」及「金额」或「金额(元)」三个关键词 → 该行为表头，按关键词记列索引；其后为数据行。前导说明行（导出信息/统计/提示）数量变化不影响解析（PRD §F-12 解析规则 1）。
- 表头用 `,` 切分（账单为逗号分隔）；字段尾 `\t` 一律 `trim()`（支付宝交易订单号/商家订单号尾随 `\t`，DEC-015/PRD）。
- 金额：`MoneyFormatter.toFen(raw)`（BigDecimal setScale(2)→movePointRight(2)→longValueExact），**禁 float/double**；非法/0/负 → 该行 `PARSE_ERROR` 跳过。
- `收/支` 映射：「支出」→expense、「收入」→income、「不计收支」/「/」/空 → `NEUTRAL` 跳过；支付宝「交易状态」=「交易关闭」→ `CLOSED` 跳过（先于此条，实测「退款成功/还款成功」均带「不计收支」已被 NEUTRAL 覆盖）。

### 16.5 XLSX 解析（微信，DEC-016）

- 实现要点（命名空间 / sharedStrings 反查 / inlineStr / 日期序列数 / 稀疏 cell / 列定位）与风险详见 **DEC-016**，简列：
  1. `ZipFile(file)` → 读 `xl/sharedStrings.xml`（`t="s"` 反查 `List<String>`）+ `xl/worksheets/sheet1.xml`（`XmlPullParser` 流式，localName 匹配）。
  2. 表头行动态探测：首 `<row>` 的 cell 按 `r`（A/B/C…）列字母 + 表头关键词（交易时间/收/支/金额(元)/交易对方/收付款方式/商品/备注/交易单号/交易类型）记列偏移。
  3. 日期序列数：`substringBefore('.')` 取整数部分 → `LocalDate.of(1899,12,30).plusDays(n)` 得 `yyyy-MM-dd`，禁 Double。
  4. 金额 `<v>` 文本字面量 → `MoneyFormatter.toFen`。
  5. `收/支`：支出→expense、收入→income、微信中性「/」/空 → `NEUTRAL` 跳过。

### 16.6 文件读取（DEC-017）

- `SafBillFilePreparer`（数据层，`context: ContentResolver`）：`prepare(uriString): PreparedBillFile`。
  - XLSX：`openInputStream(uri)` → 拷贝 `context.cacheDir/minibook_import_<uuid>.xlsx` → `ZipFile(tempFile)`；`PreparedBillFile.close()` 关闭流 + 删 tempFile。
  - CSV：`openInputStream(uri)` → `Reader`（GBK），无 tempFile。
- 编排方（`ImportBillUseCase`）`use { }` + `finally` 清理，保证成功/失败/取消均回收临时文件。

### 16.7 自动分类（DEC-019）

- 映射表 `domain/model/BillCategoryMapping`：键 `(platform, platformCategory 子串, type) → 目标内置分类名`，内容与 PRD §F-12 表一致；未列入的组合 → null（归「其他」）。
- 解析：`ImportBillUseCase` 导入开始时 `categoryDataSource.observeByLedger(ledgerId, null).first()` 快照全部分类 → 建 `Map<Pair<CategoryType,String>, String>`（(type,name)→categoryId）；每行 `builtInName = mapping.resolve(...)` → `(type, builtInName)` 查快照 → categoryId；查不到 → 该 `type` 的「其他」内置分类（DEC-007 名锁定，恒可定位）。若「其他」兜底分类亦不存在（数据异常），整体 `Failure` 报错零落库（`categoryId NOT NULL` 无法落库）。
- 分类为**预填**，可事后改。

### 16.8 UI 接线

- `Destinations.kt` 增 `data object Import : Destination("import")`；`MiniBookNavHost.kt` 注册根图全屏页（顶栏返回 `popBackStack`）。
- 入口：首页/收支页顶栏「导入账单」入口（PRD 只锁入口层级，具体位置 Designer 定）+ 设置「数据」区可选入口。
- `ImportViewModel` 状态机 `ImportUiState`：`Idle`（提示页，宣导两平台导出路径 + 选择文件按钮）→ `Busy`（选文件后解析/导入中，`FullScreenBusyOverlay` 锁数据入口）→ `Result(summary)`（成功 N / 跳过分原因 / 解析失败逐条行号+原因）→ `Error(msg)`（未识别格式/文件损坏/事务回滚）。
- 文件选择：SAF `ActivityResultContracts.OpenDocument`（`ACTION_OPEN_DOCUMENT`，`application/vnd.ms-excel` + `text/csv` + `*/*` fallback），**不唤起第三方 App**（PRD）。
- 结果反馈组件复用既有 `EmptyState`/`Dialogs`/`FullScreenBusyOverlay`；`source` 列展示中文标签（手动记录/微信/支付宝）在 `TransactionListItem`。

### 16.9 导入 UseCase 编排与事务边界

`ImportBillUseCase.invoke(uriString: String, ledgerId: String): BillImportResult`，`withContext(Dispatchers.IO)`：

1. `SafBillFilePreparer.prepare(uriString).use { handle -> ... }`（含 finally 清理）。
2. `factory.resolve(handle.displayName, magicBytes)` → parser；null → `Unrecognized`（零落库）。
3. `parser.parse(handle.input)` → `BillParseResult`（失败 = 未识别/损坏，零落库）。
4. 快照分类 + 为每个有效行装配 `Transaction`（UUID + `source=wechat_import/alipay_import` + `externalId/counterparty/paymentMethod` + 自动分类 categoryId）。
5. `TransactionRunner.runInTransaction {`——**逐行过滤（行级）+ 单事务整批写入（DEC-015）**：预查既有 externalId 集合 → 去重（文件内重复 + 与既有相交 → 计 EXISTS）→ `insertBatch`（IGNORE，`-1` 追加 EXISTS）`}`；任一真实写入异常（非跳过）→ 整体回滚 → `Failure`。
6. 返回 `BillImportResult(success/existing/neutral/closed/parseErrors)`；`unrecognized` 标识未识别。

> 口径落地（DEC-015）：过滤/去重是**行级语义**（逐行判定、计数反馈，不算失败）；通过过滤的有效行在**单事务**内整批插入；写入用 `insertBatch(IGNORE)` 而非逐行 insert，避免事务内逐条异常处理。千条级单事务性能足够。

### 16.10 测试策略（供 Tester）

| 类别 | 用例 | 环境 |
|------|------|------|
| 解析器（可单测） | AlipayCsvParser：GBK fixture、动态表头探测（前导行增减）、`\t` trim、金额转分（8.70→870 / 6.5→650）、跳过原因计数 | JVM（tests/unit） |
| 解析器（可单测） | WechatXlsxParser：构造 `.xlsx` fixture、sharedStrings 反查、inlineStr、日期序列数换算、稀疏 cell、列定位 | JVM（写临时 zip fixture） |
| 金额/表头 | `MoneyFormatter.toFen` 边界（0/负/非数字/超长）；表头关键词匹配 | JVM |
| 自动分类 | `BillCategoryMapping` 映射逐行一致（PRD 表）+ 未列入→null；快照匹配 + 「其他」兜底 | JVM |
| ImportResult 聚合 | 各跳过原因计数、文件内重复去重、外部冲突计数、`insertBatch` 返回 -1 聚合（Fake DataSource） | JVM |
| DB 迁移/幂等 | `MIGRATION_1_2`（旧库升级、新列可读、手动记录不冲突、重复 externalId 报冲突）；`insertAll(IGNORE)` 冲突计数 | instrumented（MigrationTestHelper + 内存库） |
| 文件读取/SAF/真账单 | SAF Uri 读取、真实微信 XLSX / 支付宝 CSV 端到端导入、临时文件清理 | 真机/instrumented（SAF 需真实 provider/真账单需人工 export） |

---

## 17. Phase 3 设计：邮件账单自动导入（F-13）

> 依据：`docs/01_prd.md §F-13` + **DEC-022**（技术路线）、**DEC-023**（网络/凭据隐私边界修订）。
> 完整设计与实测证据：`docs/12_mail_bill_import.md`（本节为架构落地摘要，细节以该文为准）。
> 定位：**F-12 的「取件自动化前置段」，不新起范式、不改 F-12 任何解析/去重口径**——只做「取到账单文件并交给既有 `ImportBillUseCase`」。这是本项目**首个引入网络与外部凭据**的功能，故 DEC-023 显式修订了 PRD §7「无网络依赖」条款。

### 17.1 落地总览

| 层 | 产出 |
|----|------|
| 依赖/权限 | `com.sun.mail:android-mail:1.6.7` + `android-activation:1.6.7` + R8 keep 规则；`INTERNET` 权限（**首次申请权限**）。**不引入 zip4j**（DEC-022 第 5 条） |
| 数据层 | `MailAccountStore`（授权码 Keystore AES-GCM 加密）、`ImapMailClient`、`MailBillMatcher`（本地过滤）、`MailFilenameDecoder`（RFC2047/2231）、`WechatBillLinkExtractor`、`MailBillDownloader` |
| 业务层（纯 Kotlin） | `ZipCrypto`（密钥派生+流解密）、`ZipPasswordCracker`（6 位数字穷举）、`EncryptedZipExtractor`；`FetchBillFromMailUseCase`、`MailBillImportUseCase` |
| 复用（零改动） | `BillParserFactory` / `AlipayCsvParser` / `WechatXlsxParser` / `BillCategoryMapping` / `TransactionRunner` 事务口径 / F-12 结果页态 |
| 唯一衔接改动 | `ImportBillUseCase` 增**文件路径入口重载**（邮件产物是私有目录 `File`，非 SAF uri） |
| UI 层 | 设置页「邮箱账单」配置（`mail_account` 路由）+ 导入页「从邮箱一键收取」入口/进度/结果 |

### 17.2 目录归属

```
domain/
  usecase/bill/zip/   ZipCrypto.kt / ZipPasswordCracker.kt / EncryptedZipExtractor.kt
  usecase/mail/       FetchBillFromMailUseCase.kt / MailBillImportUseCase.kt
  model/              MailBillCandidate.kt / MailImportResult.kt
data/
  local/mail/         MailAccountStore.kt / ImapMailClient.kt / MailBillMatcher.kt
                      / MailFilenameDecoder.kt / WechatBillLinkExtractor.kt / MailBillDownloader.kt
ui/
  mail/               MailSettingsScreen.kt / MailSettingsViewModel.kt
  import/             ImportScreen.kt / ImportViewModel.kt（扩展收取流程）
di/
  MailModule.kt
```

> 分层约束：`ZipCrypto`/`ZipPasswordCracker`/`EncryptedZipExtractor`/`MailBillMatcher`/`MailFilenameDecoder`/`WechatBillLinkExtractor` **纯 Kotlin（不 import android.\*）**，JVM 可单测；`ImapMailClient`/`MailAccountStore`/`MailBillDownloader` 是 Android/网络边界。

### 17.3 端到端数据流

```
MailAccountStore(凭据) → ImapMailClient(IMAP 只读拉取, 含 ID 命令)
   → MailBillMatcher(本地过滤, 禁服务端 SEARCH)
   → 取件：支付宝=附件字节 | 微信=正文链接 → MailBillDownloader(HTTPS GET)
   → ZipPasswordCracker(穷举 6 位数字口令) → EncryptedZipExtractor(ZipCrypto 解密 + Inflater)
   → 解出 .csv/.xlsx 文件
   → ImportBillUseCase(filePath, ledgerId)【F-12 既有链路，零改动】
   → MailImportResult(按邮件聚合) → UI 结果页态
```

### 17.4 邮箱凭据与 IMAP 客户端（DEC-022 第 1 条）

- `MailAccountStore`：邮箱地址可明文落 DataStore；**授权码必须经 Android Keystore（AES-256/GCM）加密**后落私有目录，独立 alias，**不与 SQLCipher master key 混用**；`getAuthCode()` 解密读出，**不进日志**。
- `ImapMailClient`：`imap.qq.com:993` SSL；连接后**显式发送 IMAP `ID` 命令**（`mail.imap.id` 属性 + `IMAPStore.id(map)` 双保险，否则 QQ 拒登）；`Folder.READ_ONLY` **只读**打开 INBOX；`FetchProfile` 只取过滤所需头，命中后才取正文/附件；连接/读超时各 20~30 s。
- 异常分类：`AuthenticationFailedException` →「登录失败」（附「需开启 IMAP/SMTP 而非仅 POP3」指引）；`SocketTimeoutException`/`IOException` →「网络异常」。

### 17.5 本地过滤（DEC-022 第 2 条，**架构红线**）

**禁止使用 IMAP 服务端 `SEARCH`。** 实测 QQ 对 `OR(FROM "alipay")(FROM "tencent")(SUBJECT "账单")` 返回 **0 封**，而同一批邮件本地过滤命中 3 封真实账单。落地方式：

- 拉取窗口内邮件（默认 30 天，含最大封数上限）→ 用 `FetchProfile` 取 `FROM`/`SUBJECT`/`Date` → 在**本地**按白名单匹配：
  - 支付宝：`FROM service@mail.alipay.com` 且主题含「支付宝交易流水明细」
  - 微信：`FROM wechatpay@tencent.com` 且主题含「微信支付-账单流水文件」
- **每种账单只取最新一封**：同一平台多封邮件（重复申请 / 覆盖区间重叠）→ 由 `MailBillSelector`（`domain/usecase/mail/MailBillSelector.kt`，纯 Kotlin、JVM 可单测）按 `sentDateMillis` 保留最新一封（同毫秒按主题字典序兜底），其余同平台旧邮件**不处理**并在进度中显式披露（FILTER 摘要「…每种账单只取最近一封 → 待处理 K 封」+ `⏭ {平台} · {主题} — 已有更新的同类账单，跳过`）。**粒度就是平台**——`MailBillMatcher` 只判到「支付宝交易流水明细 / 微信支付账单流水文件」两类，不存在更细的「账期」子类。入库层 `externalId` 仍作最后防线。

### 17.6 取件（DEC-022 第 3/4 条）

- **支付宝（附件）**：取 `APPLICATION/OCTET-STREAM` 附件字节；文件名经 `MailFilenameDecoder` 解码——覆盖 RFC2047 `=?GB18030?Q?…?=` / `=?GB18030?B?…?=` 与 RFC2231 `name*0=`/`name*1=` 续行拼接。**JavaMail `getFileName()` 返回未解码原始串，不可直接使用。**
- **微信（链接）**：账单**没有附件**，正文（`text/plain` + `text/html`）内提取 `tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?<签名 token>`（`&amp;` 反转义、`href` 与裸链双路）→ `MailBillDownloader` HTTPS GET（跟随重定向 ≤5 跳、浏览器 UA、超时）。实测 `HTTP 200` / `application/zip`，**token 即凭证、无需登录**。
- **链接有效期 7 天**：超期邮件跳过并在结果中提示「链接已过期，请在微信重新申请」。
- 产物落 `cacheDir/mail_bill_<uuid>/`，由编排方 `finally` 清理。

### 17.7 口令穷举与解密解压（DEC-022 第 5/6/7/8 条 —— 本功能唯一"非标准"处）

背景：两平台解压口令**都不随邮件发送**（支付宝在 App 服务消息、微信在【微信支付】公众号）；但实测口令**恒为 6 位纯数字且每期不同**（三封真实账单均命中；口令属凭据不落文档，两个支付宝包口令不同）→ 因此「自动穷举」是**唯一**能实现真·一键的路径，「输一次并记住」被证伪。实测两平台 zip 均为 **ZipCrypto + Deflate（非 AES）**，故用原生 `Inflater` + 自实现 ZipCrypto，**零第三方解压依赖**。

- **先探测加密类型**：非 ZipCrypto（`method=99` 或含 `0x9901` 扩展字段 = WinZip AES）→ **直接报「不支持自动解密」，禁止盲目穷举**。
- **两段式判定**：
  1. **快筛**（每候选）：派生密钥 → 解 12 字节加密头 → 取第 12 字节，**宽松接受「CRC32 高字节 或 DOS 时间高字节」**（实测 zip 带 data descriptor，`flags=0x0809/0x080B`，单一约定会误杀正确口令）。
  2. **权威判定**（仅命中者）：整包解密 → `Inflater(true)` 解压 → 对**解压后内容**算 `CRC32` 与中央目录记录值比对。
  > **红线**：zip 记录的是**原始内容**的 CRC，**绝不能拿解密后的压缩数据算 CRC**（本次 spike 实际踩过，导致正确口令被全部误杀并一度得出错误结论，见 DEC-022 附则）。
- **性能**：预计算前 3 位密钥状态（1000 状态）+ 后 3 位循环；多核并行（`Dispatchers.Default`）；可取消 + 硬超时。目标中端机 ≤ 10 s。
- **降级**：穷举失败 → 返回 null → 上层降级为「手动输入口令」，复用同一 `EncryptedZipExtractor`。

### 17.8 与 F-12 的衔接（DEC-022 第 10 条）

- **不改动** F-12 的解析规则、字段映射、过滤规则、幂等与事务口径。
- `ImportBillUseCase` 增文件路径入口重载：把 `File` 适配为既有 `BillSourceInput`（`.xlsx` → `BillSourceInput.Xlsx(file)`；`.csv` → `BillSourceInput.Csv(GBK Reader)`），随后完全复用 `factory.resolve` → `parser.parse` → 事务写入 → `BillImportResult`。
  - 替代方案（**不采用**）：把文件包成 `FileProvider` 的 `content://` Uri 走原入口——成本更高且无必要地扩大 provider 暴露面。

### 17.9 隐私/权限边界（DEC-023）

- IMAP **只读**：不删除、不标已读、不移动、不改 flag；非账单邮件不下载正文、不落盘。
- 出网点**仅** `imap.qq.com:993` 与 `tenpay.wechatpay.cn:443`（均 TLS）；**不上传任何用户数据**；不接统计/上报/广告 SDK；**不做后台联网**（仅用户点击时执行）。
- 凭据、口令、账单内容**一律不进日志**（含异常堆栈）；设置页提供「清除邮箱凭据」。
- 断网/认证失败/链接过期等网络侧异常**不得影响 F-12 手动导入路径**（两者完全独立）。

### 17.10 测试策略（供 Tester）

| 类别 | 用例 | 环境 |
|------|------|------|
| 口令穷举（**强制自校验**） | 自造「已知口令 zip」→ 穷举必须命中；该 zip 须能被**独立实现**（如 Python `zipfile`）用同口令解开 | JVM（**未通过此验证不得采信否定结论**，DEC-022 附则） |
| 口令穷举 | 快筛宽松择优（data descriptor 场景不误杀）、权威判定用**未压缩内容** CRC、AES 拒绝、命中/未命中、取消与超时 | JVM |
| 解密解压 | 真实账单 zip 解出文件与手动解压一致；中文文件名正确 | JVM + 真实样本 |
| 文件名/链接 | RFC2047 Q/B、GB18030、`name*N` 续行；HTML/纯文本链接、`&amp;`、多链接取舍 | JVM |
| 本地过滤 | 发件人大小写、主题变体、超期邮件、同账期多封去重 | JVM |
| 编排聚合 | 多邮件结果聚合、重复收取幂等、失败隔离、临时文件清理（Fake） | JVM |
| IMAP/只读性 | 真实 QQ 邮箱连接、收取前后邮件数与 `\Seen` 状态不变 | 真机（Tester） |
| 端到端 | 支付宝 + 微信真实账单各一封入库，计数与手动导入同账单一致 | 真机（Tester，写入 `09_test_report.md`） |
| 安全 | 落盘检索无明文授权码；日志检索不到授权码/口令 | 静态检索 + 真机 |

---

（本文件为设计文档，随实现对细节的差分由后续修订更新；版本历史由协调者统一管理。）