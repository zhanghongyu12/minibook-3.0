# MiniBook Android 重构版 - API 契约设计（数据访问抽象层）

> 状态：**已确认**
> 维护者：Architect
> 最后更新：2026-09-23（§14 F-13 契约实现后回写）
> 依据：`docs/01_prd.md`（已确认，唯一需求事实来源）；`docs/02_architecture.md`（§6 数据源抽象出口设计、§10 导入导出设计）。
> 配套：`docs/03_database.md`（实体字段/DAO 落地口径）、`docs/07_decisions.md`（DEC-001~DEC-009）。

---

## 目录

- [1. 定位与原则](#1-定位与原则)
- [2. 包结构与命名](#2-包结构与命名)
- [3. TransactionDataSource](#3-transactiondatasource)
- [4. DebtDataSource](#4-debtdatasource)
- [5. CategoryDataSource](#5-categorydatasource)
- [6. LedgerDataSource](#6-ledgerdatasource)
- [7. BudgetDataSource（Phase 2 预留）](#7-budgetdatasourcephase-2-预留)
- [8. SyncChangeDataSource（Phase 3 预留）](#8-syncchangedatasourcephase-3-预留)
- [9. RemoteDataSource（未来云端出口契约）](#9-remotedatasource未来云端出口契约)
- [10. 公共领域模型与通用约定](#10-公共领域模型与通用约定)
- [11. 导入导出 JSON 格式 version 1](#11-导入导出-json-格式-version-1)
- [12. Phase 2 契约：搜索 / 报表 / 预算方法签名](#12-phase-2-契约搜索--报表--预算方法签名)
- [13. F-12 契约：账单导入（Phase 2）](#13-f-12-契约账单导入phase-2)
- [14. F-13 契约：邮件账单自动导入（Phase 3）](#14-f-13-契约邮件账单自动导入phase-3)

---

## 1. 定位与原则

本项目**本地优先、无 REST 网关**。因此「API 契约」不指 HTTP 接口，而是指**数据访问抽象层**——业务层（domain）与存储实现（Room）之间唯一依赖的接口集合。

原则：

- **契约即 API**：Repository 只依赖下述 `*DataSource` 接口，不感知 Room/SQL/SQLCipher。
- **云端留出口**：未来接云端只需新增 `RemoteDataSource` 实现，业务层与其余 DataSource 接口不变（D-8 / 02_architecture §6.3）。
- **同步方法**：写/读单值用 `suspend`（Room suspend 自动切后台）；列表/汇总观察用 `Flow`（Room 冷流）。
- **账本维度**：所有业务查询第一参数统一 `ledgerId`，违反即视为契约破坏（02_architecture §8.1）。
- **金额单位**：一律整数「分」`Long`，杜绝浮点。
- **ID**：UUID v4 字符串，离线生成。
- 本期不实现 `SyncChange` 写入与 `RemoteDataSource`（仅契约落盘，见 §8 §9 及各 DEC）。

---

## 2. 包结构与命名

| 文件 | 位置 | 说明 |
|------|------|------|
| `*DataSource.kt` | `com.minibook.data.datasource/` | 能力契约接口（本节主体） |
| `RemoteDataSource.kt` | `com.minibook.data.remote/` | 云端出口契约（仅接口，本期空实现） |
| `RoomDataSourceImpl.kt` | `com.minibook.data.local.datasource/` | 本期唯一实现，聚合各 DAO |

装配点仅一处：`di/DataSourceModule` 的 `@Binds` 将接口绑定到实现。切云端/混合模式只改此处。

---

## 3. TransactionDataSource

交易（收支记录，PRD F-01）数据源。

```kotlin
interface TransactionDataSource {
    suspend fun insert(tx: Transaction): String

    suspend fun update(tx: Transaction)

    suspend fun deleteById(id: String)

    fun observeByLedger(ledgerId: String, filter: TransactionFilter): Flow<List<Transaction>>

    suspend fun monthlySummary(ledgerId: String, month: YearMonth): MonthSummary

    suspend fun getById(id: String): Transaction?
}
```

| 方法 | 参数 | 返回 | 语义 |
|------|------|------|------|
| `insert` | `tx: Transaction` | `String`（新 id） | 新增一笔；金额为正整分（≤0 由校验层拦截）。返回生成的 UUID。 |
| `update` | `tx: Transaction` | `Unit` | 按 `id` 更新；`type/amount/categoryId/description/date` 可改，`createdAt` 不可改，`updatedAt` 自动刷新。 |
| `deleteById` | `id: String` | `Unit` | 物理删除。id 不存在则为空操作。 |
| `observeByLedger` | `ledgerId, filter` | `Flow<List<Transaction>>` | 账本内按时间倒序返回交易流；`filter` 为全部/支出/收入 + 分类 + 日期区间（Phase 2 搜索复用）。冷流，仅订阅时执行。 |
| `monthlySummary` | `ledgerId, month` | `MonthSummary` | 当月收入/支出合计（分）与结余；DB 端 SQL 聚合。 |
| `getById` | `id: String` | `Transaction?` | 单条查询，不存在返回 `null`。 |

**`TransactionFilter`**：

```kotlin
data class TransactionFilter(
    val type: TransactionType? = null,       // 全部(null)/expense/income
    val categoryId: String? = null,          // null=不限分类
    val dateFrom: String? = null,            // "yyyy-MM-dd"
    val dateTo: String? = null,
)
```

**`MonthSummary`**：`incomeFen: Long`、`expenseFen: Long`、`balanceFen: Long`（收入减支出）。

---

## 4. DebtDataSource

债务/借还（PRD F-02）数据源。

```kotlin
interface DebtDataSource {
    suspend fun insert(debt: Debt): String

    suspend fun update(debt: Debt)

    suspend fun deleteById(id: String)

    fun observeByLedger(ledgerId: String, filter: DebtFilter): Flow<List<Debt>>

    suspend fun netByParty(ledgerId: String): Map<String, Long>
}
```

| 方法 | 参数 | 返回 | 语义 |
|------|------|------|------|
| `insert` | `debt: Debt` | `String`（新 id） | 新增一条借/还；`kind×direction` 四态平级（03_database §4.2）。 |
| `update` | `debt: Debt` | `Unit` | 按 `id` 更新。 |
| `deleteById` | `id: String` | `Unit` | 物理删除。 |
| `observeByLedger` | `ledgerId, filter` | `Flow<List<Debt>>` | 明细视图，账本内按时间倒序。 |
| `netByParty` | `ledgerId: String` | `Map<String, Long>` | 按对方姓名汇总净额（分）；正值=对方欠我，负值=我欠对方。净额口径见下。 |

**`DebtFilter`**（阶段 5 由 Architect 双向确认 Designer 假设 A-2 后补充展开，2026-08-18）：

```kotlin
data class DebtFilter(
    val kind: DebtKind? = null,          // 全部(null)/lend/repay
    val direction: DebtDirection? = null, // 全部(null)/owe_me/i_owe
)
```

- 字段语义与 `03_database §6.2 observeFiltered(ledgerId, kind?, direction?)` 完全对齐；可空=不过滤。
- **MVP 仅展开 `kind` / `direction` 两个可空字段**，不上 `partyName` 模糊筛：MVP 按对象净额已由 `netByParty` 聚合支撑，明细视图无 partyName 过滤需要；`partyName` 模糊筛属 Phase 2 搜索（PRD F-06）范围，随该功能对同一 `DebtFilter` 扩展（与 `TransactionFilter` 的 Phase 2 扩展同理，见 §3）。

**净额口径**（PRD F-02）：lend×owe_me=+，lend×i_owe=−，repay×owe_me=−，repay×i_owe=+。DB 端 `CASE WHEN` 一次聚合。

---

## 5. CategoryDataSource

分类（PRD F-03）数据源。

```kotlin
interface CategoryDataSource {
    suspend fun upsertList(categories: List<Category>)

    suspend fun insert(category: Category): String

    suspend fun update(category: Category)

    suspend fun deleteById(id: String)

    suspend fun countByLedger(ledgerId: String): Long

    fun observeByLedger(ledgerId: String, type: CategoryType?): Flow<List<Category>>

    suspend fun countTransactionsByCategory(categoryId: String): Int
}
```

| 方法 | 参数 | 返回 | 语义 |
|------|------|------|------|
| `upsertList` | `categories: List<Category>` | `Unit` | 种子/导入批量写入（`REPLACE` 冲突策略）。 |
| `insert` | `category: Category` | `String` | 新增自定义分类。 |
| `update` | `category: Category` | `Unit` | 改 `name/icon/color/order`；**不可改名 `type`/`isBuiltIn`**（D-6）。 |
| `deleteById` | `id: String` | `Unit` | **仅能删自定义分类**；内置（`isBuiltIn=true`）与默认「其他」由业务层拒绝。 |
| `countByLedger` | `ledgerId: String` | `Long` | 账本分类数；种子幂等判断用（==0 才 clone）。 |
| `observeByLedger` | `ledgerId, type` | `Flow<List<Category>>` | 账本内分类流，可按收支类型过滤，按 `order` 升序。 |
| `countTransactionsByCategory` | `categoryId: String` | `Int` | 删除前关联检查；>0 则提示迁移。 |

---

## 6. LedgerDataSource

账本（PRD F-11）数据源。

```kotlin
interface LedgerDataSource {
    suspend fun insert(ledger: Ledger): String

    suspend fun update(ledger: Ledger): Ledger

    suspend fun getById(id: String): Ledger?

    suspend fun getDefault(): Ledger?

    fun observeAll(): Flow<List<Ledger>>
}
```

| 方法 | 参数 | 返回 | 语义 |
|------|------|------|------|
| `insert` | `ledger: Ledger` | `String` | 新增账本；新账本创建流程在事务内联动克隆内置分类（02_architecture §8.4）。 |
| `update` | `ledger: Ledger` | `Ledger` | 更新后回读并返回最新值。 |
| `getById` | `id: String` | `Ledger?` | 单账本。 |
| `getDefault` | — | `Ledger?` | 取默认账本（`isDefault=1`），不存在返回 `null`（首启动引导）。 |
| `observeAll` | — | `Flow<List<Ledger>>` | 全部账本，按 `position` 排序。 |

> 「当前账本」由 `LedgerDataStore` 提供，不属本数据源；切换为纯状态变更（02_architecture §8.3）。

---

## 7. BudgetDataSource（Phase 2 预留）

预算（PRD F-08）数据源。接口现在落盘保证未来不迁移即接入，本期不实现不接线。

```kotlin
interface BudgetDataSource {
    suspend fun upsertTotal(ledgerId: String, month: YearMonth, amount: Long)

    suspend fun upsertCategory(ledgerId: String, month: YearMonth, categoryId: String, amount: Long)

    suspend fun deleteTotal(ledgerId: String, month: YearMonth)

    suspend fun deleteByCategory(ledgerId: String, month: YearMonth, categoryId: String)

    fun observeByMonth(ledgerId: String, month: YearMonth): Flow<List<Budget>>
}
```

**D-7 业务唯一语义**：月度总预算（`categoryId=null`）在 **Repository 层事务内「先删后插」** 保证唯一（SQLite 中 `(ledgerId,month,NULL)` 的 NULL 彼此视为不同，DB 不加触发器）；分类预算由 DB `UNIQUE(ledgerId,month,categoryId)` 约束直接保证。

---

## 8. SyncChangeDataSource（Phase 3 预留）

云同步变更簿记数据源。本期只建表不写数据，契约落盘保证 Phase 3 零迁移接入。

```kotlin
interface SyncChangeDataSource {
    suspend fun enqueue(entityType: String, entityId: String, action: String, data: String?)

    suspend fun pendingChanges(ledgerId: String): List<SyncChange>

    suspend fun markSynced(ids: List<String>)
}
```

| 方法 | 语义 |
|------|------|
| `enqueue(entityType, entityId, action, data)` | 记录一次变更；`entityType` ∈ transaction/debt/category，`action` ∈ create/update/delete，`data` 为变更载荷 JSON（delete 可为空）。 |
| `pendingChanges(ledgerId)` | 取未同步变更（按 `timestamp` 升序），供云端推送。 |
| `markSynced(ids)` | 推送成功后批量标记已同步。 |

> 签名以 03_database §6.5 `SyncChangeDao` 为准（带 `ledgerId` 维度），比 02_architecture §6.1 初稿更精确。

---

## 9. RemoteDataSource（未来云端出口契约）

仅接口、**本期空实现**（02_architecture §6.3）。未来对接 REST/GraphQL，业务层零改动。

```kotlin
interface RemoteDataSource {
    // —— 本期仅契约占位，不实现 ——

    // 推送增量变更，返回对端需合并的行（冲突进入 syncStatus=conflict）
    // suspend fun push(changes: List<SyncChange>): PullResult

    // 增量拉取：返回自 lastSyncedAt 以来的远端变更
    // suspend fun pull(lastSyncedAt: String): PullResult
}
```

接入演进：`RemoteDataSource` 实现 → 组合成 `SyncingDataSource`（本地写、异步推送、冲突 LWW）→ 在 `DataSourceModule.@Binds` 一处切换；Repository 不动。

---

## 10. 公共领域模型与通用约定

领域模型为纯 Kotlin data class（`com.minibook.domain.model`），字段与 03_database 实体一致（见 03_database §4）。UI 只依赖领域模型，不依赖 Entity。

| 类型 | 关键字段（与 03_database §4 一致） | 账本维度 |
|------|--------------------------------|:--------:|
| `Transaction` | id, ledgerId, type, amount(分), categoryId, description?, date, createdAt, updatedAt, syncStatus | ✔ |
| `Debt` | id, ledgerId, partyName, amount(分), kind, direction, date, remark?, createdAt, updatedAt, syncStatus | ✔ |
| `Category` | id, ledgerId, name, icon, color, type, isBuiltIn, order, createdAt, updatedAt | ✔ |
| `Ledger` | id, name, icon, color, isDefault, position, createdAt, updatedAt | — |
| `Budget` | id, ledgerId, month, categoryId?(null=总预算), amount(分), createdAt, updatedAt | ✔ |
| `SyncChange` | id, ledgerId, entityType, entityId, action, timestamp, data? | ✔ |

**通用约定**：

- `amount` 一律 `Long` 分；`date` 为 `yyyy-MM-dd`；审计时间为 ISO 8601 完整时间戳。
- `type` 为 `expense`/`income` 枚举（DB 以 TEXT 存，TypeConverter 转码）。
- `syncStatus` ∈ `local/syncing/synced/conflict`，本期恒 `local`。
- DataSource 接口返回值中的领域对象由实现方经 `mapper` 由 Entity 转换。

---

## 11. 导入导出 JSON 格式 version 1

备份/迁移交换格式（PRD F-04，02_architecture §10）。**导出为明文 JSON**（D-4）。文件名 `minibook_backup_yyyyMMdd_HHmmss.json`。

### 11.1 顶层结构

```jsonc
{
  "version": 1,
  "appVersion": "1.0.0",
  "exportedAt": "2026-08-18T09:00:00.000Z",
  "scope": "all",
  "ledgers":      [ ... ],
  "categories":   [ ... ],
  "transactions": [ ... ],
  "debts":        [ ... ],
  "budgets":      [ ... ]   // 本期恒空数组
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `version` | number | ✔ | 格式版本，当前恒为 `1`；导入仅支持 1。 |
| `appVersion` | string | ✔ | 生成方应用版本，便于人工判断。 |
| `exportedAt` | string | ✔ | ISO 8601 完整时间戳。 |
| `scope` | string | ✔ | 导出范围：`all` \| `ledger:<id>`（单账本子集 Phase 2 细化）。 |
| `ledgers` | array | ✔ | 账本列表。 |
| `categories` | array | ✔ | 分类列表（含 ledgerId）。 |
| `transactions` | array | ✔ | 交易列表（含 ledgerId）。 |
| `debts` | array | ✔ | 债务列表（含 ledgerId）。 |
| `budgets` | array | ✔ | 预算列表；Phase 2 落库后导出，本期恒空。 |

> **不含 `syncChanges`**：同步簿记不随全量迁移（未来云端迁移另配协议）。
> **legacy 兼容（D-3）**：导入检测顶层缺 `ledgers` 字段 → 视为旧版（Taro）JSON，导入到默认账本（每行补默认账本 ledgerId，legacy 时间戳归一化为 `yyyy-MM-dd`）。

### 11.2 各实体条目字段

**Ledger（账本）**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `id` | string | ✔ | UUID v4 |
| `name` | string | ✔ | 账本名，非空 |
| `icon` | string | ✔ | emoji 图标 |
| `color` | string | ✔ | 十六进制色值 `#RRGGBB` |
| `isDefault` | boolean | ✔ | 是否存在默认账本 |
| `position` | number | 否 | 列表排序，缺省 0 |
| `createdAt` / `updatedAt` | string | 否 | ISO 时间戳，缺省自动补当前时间 |

**Category（分类）**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `id` | string | ✔ | UUID v4 |
| `ledgerId` | string | ✔ | 所属账本，必须存在于 `ledgers` |
| `name` | string | ✔ | 分类名，非空 |
| `icon` | string | ✔ | emoji 图标 |
| `color` | string | ✔ | 十六进制色值 |
| `type` | string | ✔ | `expense` / `income` |
| `isBuiltIn` | boolean | ✔ | 内置分类标记 |
| `order` | number | 否 | 排序权重，缺省 0 |
| `createdAt` / `updatedAt` | string | 否 | ISO 时间戳 |

**Transaction（交易）**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `id` | string | ✔ | UUID v4 |
| `ledgerId` | string | ✔ | 所属账本，必须存在于 `ledgers` |
| `type` | string | ✔ | `expense` / `income` |
| `amount` | number | ✔ | 整数分，>0 |
| `categoryId` | string | ✔ | 必须存在于 `categories` 且与之同账本 |
| `description` | string | 否 | 备注，≤200 字符 |
| `date` | string | ✔ | `yyyy-MM-dd` |
| `createdAt` / `updatedAt` | string | 否 | ISO 时间戳 |

**Debt（债务）**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `id` | string | ✔ | UUID v4 |
| `ledgerId` | string | ✔ | 所属账本 |
| `partyName` | string | ✔ | 对方姓名，非空 |
| `amount` | number | ✔ | 整数分，>0 |
| `kind` | string | ✔ | `lend` / `repay` |
| `direction` | string | ✔ | `owe_me` / `i_owe` |
| `date` | string | ✔ | `yyyy-MM-dd` |
| `remark` | string | 否 | 备注 |
| `createdAt` / `updatedAt` | string | 否 | ISO 时间戳 |

**Budget（预算，本期空）**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `id` | string | ✔ | UUID v4 |
| `ledgerId` | string | ✔ | 所属账本 |
| `month` | string | ✔ | `yyyy-MM` |
| `categoryId` | string | 否 | 分类预算=分类 id；`null`/缺省=月度总预算 |
| `amount` | number | ✔ | 整数分，≥0 |
| `createdAt` / `updatedAt` | string | 否 | ISO 时间戳 |

> 条目中不含 `syncStatus`：导入目标设备为新设备，写入时统一置为 `local`（D-5 换机走导出→导入语义）。

### 11.3 导入校验与原子性

- **严格校验**：`version` 仅支持 1；字段类型/必填校验失败 → 停止、报错、**零落库**。
- **引用完整性**成对校验：`transaction.categoryId ∈ categories`；`*.ledgerId ∈ ledgers`；金额为正整分；UUID 合法（02_architecture §10.2）。
- **全量覆盖**单事务：`transaction { 清空 transactions/debts/categories/budgets → 保留/重建 ledgers → 逐行写入 }`，任一失败整体回滚（Room `withTransaction`）。
- 导入期间 UI 锁数据入口，防并发改写快照冲突。

---

## 12. Phase 2 契约：搜索 / 报表 / 预算方法签名

> 依据：DEC-011 / DEC-012 / DEC-013 / DEC-008；配合 `03_database.md §10`、`02_architecture.md §15`。
> 原则延续 §1：金额一律 `Long`「分」；观察用 `Flow`；所有业务查询首参 `ledgerId`（仅当前账本）。

### 12.1 filter 模型扩展（DEC-013，DataSource/Repository 签名不变）

**`TransactionFilter` 由 4 字段扩为 7 字段**（`domain/model` 与 `data/datasource` 消费侧一致）：

```kotlin
data class TransactionFilter(
    val type: TransactionType? = null,       // 全部(null)/expense/income
    val categoryId: String? = null,          // null=不限分类
    val dateFrom: String? = null,            // "yyyy-MM-dd"
    val dateTo: String? = null,
    val remark: String? = null,              // Phase 2：备注模糊（LIKE %remark%）
    val amountMin: Long? = null,             // Phase 2：金额下限（分，含端点）
    val amountMax: Long? = null,             // Phase 2：金额上限（分，含端点）
)
```

**`DebtFilter` 由 2 字段扩为 3 字段**：

```kotlin
data class DebtFilter(
    val kind: DebtKind? = null,              // 全部(null)/lend/repay
    val direction: DebtDirection? = null,    // 全部(null)/owe_me/i_owe
    val partyName: String? = null,           // Phase 2：姓名模糊（LIKE %partyName%）
)
```

> `TransactionDataSource.observeByLedger(ledgerId, filter)` / `DebtDataSource.observeByLedger(ledgerId, filter)` 签名**不变**（filter 为引用透明对象）；`RoomDataSourceImpl` 转发新增字段（§3/§4「Phase 2 搜索复用同一过滤器扩展」留白至此落定）。`TransactionRepository`/`DebtRepository` 零改动。

### 12.2 TransactionDataSource / TransactionRepository 新增聚合方法（F-07 报表 + F-08 预算「已用」）

```kotlin
// 报表/预算聚合 DTO（domain/model，字段对齐 SQL 别名，§1 分口径）
data class PeriodTrend(val bucket: String, val expenseFen: Long, val incomeFen: Long)
data class CategoryAgg(val categoryId: String, val totalFen: Long)
```

```kotlin
// TransactionDataSource（§3）与 TransactionRepository（domain）同步新增：
fun observeDailyTrend(ledgerId: String, start: String, end: String): Flow<List<PeriodTrend>>
fun observeMonthlyTrend(ledgerId: String, start: String, end: String): Flow<List<PeriodTrend>>
fun observeCategoryAgg(ledgerId: String, type: TransactionType, start: String, end: String): Flow<List<CategoryAgg>>
```

| 方法 | 返回 | 语义 |
|------|------|------|
| `observeDailyTrend` | `Flow<List<PeriodTrend>>` | 月度视图按天收支趋势（`bucket=date`），`start/end` 为当月首/末日 |
| `observeMonthlyTrend` | `Flow<List<PeriodTrend>>` | 年度视图按月收支趋势（`bucket=yyyy-MM`），`start/end` 为当年首/末日 |
| `observeCategoryAgg` | `Flow<List<CategoryAgg>>` | 按分类分组金额合计（`type` 区分收入/支出，`start/end` 区间）；报表占比 + 预算「已用」（`type=expense`，当月）共用 |

### 12.3 预算进度契约（UseCase 产物，DEC-012 / DEC-008）

预算读写沿用 §7 `BudgetDataSource` 与 `BudgetRepository` 既有方法（`setTotalBudget`/`setCategoryBudget`/`clearTotalBudget`/`clearCategoryBudget`/`observeByMonth`，已接线）；「已用/剩余/超支」为 **UseCase 层 combine 产物**（跨 Transaction + Budget + Category 三源，属「跨实体聚合走专用 UseCase」，§1 原则）：

```kotlin
// domain/model/BudgetProgress.kt（Phase 2 UseCase 产物）
data class BudgetProgress(
    val month: String,                                  // 目标月份 yyyy-MM（对齐 Budget.month / TIME §9.5）
    val totalBudgetFen: Long,                           // 月度总预算（分）；未设置 = 0
    val totalUsedFen: Long,                             // 总额已用（分）= 各分类 CategoryAgg.totalFen 之和
    val categoryProgress: List<CategoryBudgetProgress>, // 分类预算进度（仅「已设分类预算且分类仍存在」的行）
) {
    val remainingFen: Long get() = totalBudgetFen - totalUsedFen                    // 总额剩余 = 预算 − 已用；可为负（超支）
    val overBudget: Boolean get() = totalBudgetFen > 0 && totalUsedFen > totalBudgetFen // 已设预算(>0)且已用>预算才算超支
    val hasOverBudget: Boolean get() = overBudget || categoryProgress.any { it.overBudget } // 总额或任一分类超支（DEC-012 入口提示触发条件）
}

data class CategoryBudgetProgress(
    val category: Category,   // 分类元信息（name/icon/color；observeByLedger 已按 order 升序，UI 无需再映射）
    val budgetFen: Long,      // 该分类当月预算（分）
    val usedFen: Long,        // 该分类当月实际支出累计（分）= CategoryAgg.totalFen
) {
    val remainingFen: Long get() = budgetFen - usedFen     // 分类剩余 = 预算 − 已用；可为负（超支）
    val overBudget: Boolean get() = usedFen > budgetFen    // 分类超支 = 已用 > 预算
}
```

- `ObserveBudgetProgressUseCase.observe(month: YearMonth)`：`combine(budgetRepository.observeByMonth(ledgerId, month), transactionRepository.observeCategoryAgg(type=expense, 当月首/末日), categoryRepository.observeByLedger(ledgerId, type=expense))` → `Flow<BudgetProgress>`；`ledgerId` 取自 `LedgerDataStore.currentLedgerId`（切账本即 `flatMapLatest` 重订阅，§8.3），`month` 经 `TimeFormatter.formatYearMonth` 转 `yyyy-MM` 存入 `BudgetProgress.month`（域模型 `month` 为 `String`，非 `YearMonth`）；总额已用 = 各分类 `totalFen` 之和（等价 `monthlySummary.expenseFen`）。
- 总额预算取 `budgets` 中 `categoryId == null` 的总预算行，未设则为 0；`overBudget` 以「已设预算(>0)」为守卫——**未设总额预算(0)不算超支**。
- 分类预算进度只收录「已设分类预算且分类仍存在」的行：**分类已删除的孤儿预算被跳过（不入 `categoryProgress`）**，`CategoryBudgetProgress.category` 恒非空、UI 无需兜底展示（P1-1 已在删分类时事务内级联 `deleteByCategoryId` 清预算；此处跳过为 observe 路径对历史/竞态孤儿的兜底）。
- 超支提醒只见 `BudgetProgress.hasOverBudget` 派生：页内标红 + 入口提示（DEC-012 App 内告警）；无通知栏、无 `POST_NOTIFICATIONS`。

### 12.4 通用约定重申

- `PeriodTrend`/`CategoryAgg`/`Budget*` 均为纯 Kotlin data class（`domain/model`，不 import Android 类，§10）。
- 金额全程 `Long` 分；「剩余」「超支」为整数减法/比较、无浮点；展示经 `MoneyFormatter`（DATABASE §9）。
- 所有新方法首参 `ledgerId`，仅作用于「当前账本」（F-11 隔离，`currentLedgerId().flatMapLatest`）。
- 报表/已用聚合为 `Flow`（随交易/预算增删自动重算，无需手动刷新）；预算写操作为 `suspend`。

---

## 13. F-12 契约：账单导入（Phase 2）

> 依据：DEC-015/016/017/018/019；配合 `03_database.md §4.1/§7.5`、`02_architecture.md §16`。
> 原则延续 §1：纯契约、金额 `Long` 分、域模型纯 Kotlin；解析栈只有在 `SafBillFilePreparer` 触碰 Android 框架类。

### 13.1 枚举扩展（`domain/model/Enums.kt`）

```kotlin
enum class TransactionSource(val value: String) {
    MANUAL("manual"),
    WECHAT_IMPORT("wechat_import"),
    ALIPAY_IMPORT("alipay_import");
    companion object { fun fromValue(v: String): TransactionSource? = entries.firstOrNull { it.value == v } }
}

enum class ImportSkipReason { EXISTS, NEUTRAL, CLOSED, PARSE_ERROR }
```

### 13.2 交易模型扩展（`Transaction`，DEC-018 四层贯通）

```kotlin
// domain/model/Transaction.kt 追加 4 字段（默认值保证既有手动记账/导入路径零改动）：
//   externalId: String? = null
//   counterparty: String? = null
//   paymentMethod: String? = null
//   source: TransactionSource = TransactionSource.MANUAL
```

- 手动记账：`externalId/counterparty/paymentMethod=null`、`source=MANUAL`（默认值即满足，`AddTransactionUseCase` 零改动）。
- 账单导入：`source=WECHAT_IMPORT/ALIPAY_IMPORT`、其余三列按字段映射填充。

### 13.3 解析中间模型

```kotlin
data class ParsedBillRow(
    val date: String,               // yyyy-MM-dd
    val type: TransactionType,      // expense/income
    val amountFen: Long,            // >0
    val counterparty: String?,
    val paymentMethod: String?,     // 空/全空白→null
    val description: String,        // 商品说明 + 备注（｜ 分隔，≤200 截断）
    val externalId: String,         // alipay:/wechat: 前缀
    val platformCategory: String?,  // 原始分类，仅 auto-cat，不落库
)

sealed interface BillParseResult {
    data class Success(val rows: List<ParsedBillRow>, val skipped: List<ParseSkippedRow>) : BillParseResult
    data class Unrecognized(val reason: String) : BillParseResult
}
data class ParseSkippedRow(val rowNumber: Int, val reason: ImportSkipReason, val detail: String)

data class BillImportResult(
    val success: Int,               // 实际新增
    val existing: Int,              // 已存在（幂等去重）
    val neutral: Int,               // 不计收支/「/」/空
    val closed: Int,                // 支付宝「交易关闭」
    val parseErrors: List<ParseSkippedRow>,  // 解析失败（行号+原因）
    val unrecognized: Boolean = false,        // 未识别格式（零落库）
)
```

### 13.4 解析器接口与工厂（DEC-016/017）

```kotlin
// domain/usecase/import/BillParser.kt
interface BillParser {
    val format: TransactionSource
    fun parse(input: BillSourceInput): BillParseResult
}

sealed interface BillSourceInput {
    data class Csv(val reader: java.io.Reader) : BillSourceInput   // 支付宝 GBK Reader
    data class Xlsx(val file: java.io.File) : BillSourceInput      // 微信 已落 cacheDir 可 seek File
}

// domain/usecase/import/BillFilePreparer.kt（域接口）
interface BillFilePreparer {
    fun prepare(uriString: String): PreparedBillFile
}
interface PreparedBillFile : AutoCloseable {
    val input: BillSourceInput
    val displayName: String?
}

// data/local/import/BillParserFactory.kt
interface BillParserFactory {
    fun resolve(displayName: String?, magicBytes: ByteArray?): BillParser?  // null = 未识别
}
```

- `SafBillFilePreparer`（数据层 impl）：`openInputStream(uri)` → XLSX 落 `cacheDir/minibook_import_<uuid>.xlsx` 再 `ZipFile`；CSV 直接 `Reader`；`close()` 关闭流 + 删 tempFile（DEC-017）。

### 13.5 DataSource / Repository / DAO 增量方法（DEC-019）

```kotlin
// TransactionDataSource / TransactionRepository 同步新增：
suspend fun insertBatch(txs: List<Transaction>): Int          // 实际插入行数（IGNORE 忽略冲突）
suspend fun getExistingExternalIds(ledgerId: String): Set<String>

// TransactionDao：
@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertAll(entities: List<TransactionEntity>): List<Long>   // -1 = 被忽略（冲突）
@Query("SELECT externalId FROM transactions WHERE ledgerId=:ledgerId AND externalId IS NOT NULL")
suspend fun getAllExternalIds(ledgerId: String): List<String>
```

- `insertBatch` 返回「真正插入」行数（IGNORE 后统计非 `-1` 的下标数）；`getExistingExternalIds` 供幂等预查。
- 自动分类解析复用既有 `CategoryDataSource.observeByLedger(ledgerId, null).first()` 快照，**不新增分类查询方法**（DEC-019）。

### 13.6 ImportBillUseCase 契约

```kotlin
class ImportBillUseCase @Inject constructor(
    private val transactionRunner: TransactionRunner,
    private val filePreparer: BillFilePreparer,
    private val parserFactory: BillParserFactory,
    private val categoryDataSource: CategoryDataSource,      // 或 CategoryRepository
    private val transactionDataSource: TransactionDataSource, // 或 TransactionRepository
) {
    suspend operator fun invoke(uriString: String, ledgerId: String): BillImportResult
}
```

编排（`withContext(Dispatchers.IO)`，对齐 ARCHITECTURE §16.9）：

1. `filePreparer.prepare(uriString).use { handle -> ... }`（`AutoCloseable` 保证 finally 清理临时文件）。
2. `parserFactory.resolve(handle.displayName, magic)` → null 则 `unrecognized=true`（零落库）。
3. `parser.parse(handle.input)` → `Success(rows, skipped)` 或 `Unrecognized`。
4. 快照分类建 `(type,name)→categoryId` map；逐行装配 `Transaction`（UUID、`source`、`externalId/counterparty/paymentMethod`、自动分类 categoryId，兜底「其他」；「其他」亦缺 → 整体 `Failure` 零落库）。
5. `transactionRunner.runInTransaction { 预查既有 externalId → 去重（文件内重复+既有相交→计 EXISTS）→ transactionDataSource.insertBatch(剩余) → 补充 -1 计数 }`；真实写入异常 → 事务回滚 → 失败（零落库）。
6. 汇总 `BillImportResult`（`success/existing/neutral/closed/parseErrors`）。

> ViewModel 侧传入 `uri.toString()`（`android.net.Uri` 的字符串化），UseCase 保持纯 Kotlin（不 import `android.net.Uri`，§1 可测试性）。

### 13.7 UI 契约（Destinations/ViewModel，02_architecture §16.8）

- `Destination.Import = "import"`（根图全屏页，顶栏返回）；入口「导入账单」在首页/收支页顶栏 + 设置数据区可选。
- `ImportUiState = Idle | Busy | Result(summary) | Error(msg)`；`ImportViewModel` 事件：`pickFile()`、`onFilePicked(uri)`、`retry()`、`done()`；SAF `ActivityResultContracts.OpenDocument`。

---

## 14. F-13 契约：邮件账单自动导入（Phase 3）

> 依据：DEC-022 / DEC-023；设计细节与实测证据见 `12_mail_bill_import.md`；UI 对齐 `05_ui.md §10`。
> 本节为**实现后回写**：全部签名从落盘源码抄录（2026-09-23），与早期设计稿的差异已如实修正（见各小节「与设计稿差异」注）。
> 原则延续 §1：域模型纯 Kotlin（`ZipCrypto`/`Cracker`/`Extractor`/`MailImportModels` 不 import `android.*`，JVM 可单测）；Android 边界只出现在 `MailCredentialCipher`（Keystore）、`MailAccountStore`（Context）、`ImapMailClient`（JavaMail）、`SafBillFilePreparer`（Context）。

### 14.1 凭据存储契约

**`data/local/security/MailCredentialCipher.kt`**（邮箱授权码专用 Keystore 加解密）：

- Keystore 别名 `MailCredentialCipher.ALIAS = "minibook_mail_credential_key"`——与数据库 master key 体系（`minibook_master_key_auth` / `minibook_master_key_plain`）**严格隔离**，绝不复用。
- 密钥语义：`AES/GCM/NoPadding`（AES-256，12 字节 IV + 128 bit tag），`setUserAuthenticationRequired(false)`（读取方是后台 IMAP 拉取任务，不能要求每次过生物识别）；懒创建（首次使用时生成，同 `KeyManager` 口径）。
- `fun encrypt(plain: String): String`——返回 `Base64.NO_WRAP(12B IV || GCM ciphertext)`。
- `fun decrypt(blob: String): String`——解密；密文损坏（Base64 非法/结构不对/tag 校验失败）或 Keystore 密钥失效抛 `IllegalStateException`，**异常 message 只含故障类别描述（中文固定文案），绝不含授权码明文**。

**`data/local/mail/MailAccountStore.kt`**（`@Singleton class MailAccountStore @Inject constructor(@ApplicationContext context: Context)`，**无需 Hilt Module**）：

```kotlin
val email: StateFlow<String?>        // 当前已配置邮箱地址；null=未配置（构造时同步读盘得出初值）
val isConfigured: StateFlow<Boolean> // 是否已完成配置（邮箱 + 授权码密文均在盘）
suspend fun save(email: String, authCode: String)   // 校验 + 覆盖写
suspend fun getAuthCode(): String?                  // 解密读出；未配置或解密失败均返回 null
suspend fun clear()                                 // 抹除邮箱与授权码（磁盘 + 内存状态）
```

- 落盘位置：`context.noBackupFilesDir`（不参与系统备份）——`minibook_mail_account.txt`（邮箱地址明文，非敏感）+ `minibook_mail_auth.bin`（授权码密文，经 `MailCredentialCipher` 加密）。
- `save` 校验：邮箱须匹配基本格式（`局部名@域名.后缀`，不含空白）；授权码 trim 后必须恰为 **16 位**（`AUTH_CODE_LENGTH = 16`，QQ 邮箱 IMAP 授权码固定长度）。非法抛 `IllegalArgumentException`（中文原因，不含凭据内容）。
- `getAuthCode` 防御口径：**解密失败（密文损坏/密钥失效）不抛出、不写日志，返回 null 视为不可用**，由上层（`MailBillImportUseCase`）折叠为 `NotConfigured` 引导重新配置。
- 安全纪律：本类不 import 任何 Log 工具；授权码与邮箱地址不进任何日志/异常信息。
- 并发：`Mutex` 串行化 save/clear/getAuthCode 的磁盘操作。

> **与设计稿差异**：原 TASK-045 计划的 `di/MailModule.kt` **未创建**——`MailAccountStore` 用 `@Singleton` + `@Inject` 构造注入即可由 Hilt 装配，无需 Module；原计划的 `email: Flow<String?>` 落地为 `StateFlow`（初值构造时同步读盘）。

### 14.2 邮件数据模型（`domain/model/MailModels.kt`）

```kotlin
enum class BillPlatform { ALIPAY, WECHAT }

data class MailBillHeader(
    val platform: BillPlatform,
    val subject: String,
    val sentDateMillis: Long,   // 优先服务端 Date 头，缺则回退收件时间；两者皆缺为 0
    val messageNumber: Int,     // IMAP 文件夹内邮件序号（仅调试定位用）
)

data class MailBillMail(
    val header: MailBillHeader,
    val attachmentFileName: String?,  // 附件原始文件名（未解码，RFC2047/RFC2231 原始串）；无附件为 null
    val attachmentBytes: ByteArray?,  // 附件字节流；无附件为 null
    val bodyText: String,             // text/plain 与 text/html 两路非空白结果以 \n 拼接；无正文为 ""
)
```

- `MailBillMail` 保留 data class 但 **`equals`/`hashCode` 手动重写**：`attachmentBytes` 用 `contentEquals`/`contentHashCode` 按数组内容比较（编译器默认实现按引用比较 `ByteArray`，会把内容相同的两封邮件判为不等）。
- `bodyText` 两路拼接（而非 plain 优先）是**微信链接不丢的必要条件**：微信账单邮件为 `multipart/ALTERNATIVE`，下载链接只存在于 HTML 的 `<a href>` 里，纯文本只有「点击下载」四字。

### 14.3 IMAP 客户端契约（`data/local/mail/ImapMailClient.kt`）

```kotlin
@Singleton class ImapMailClient @Inject constructor() {
    suspend fun fetchBillMails(email: String, authCode: String, sinceDays: Int = 30): List<MailBillMail>
}
```

连接一次 → 拉取窗口内邮件头部 → 本地过滤账单邮件 → 命中者逐封取回附件与正文。阻塞的 JavaMail 调用统一调度到 `Dispatchers.IO`。

**两条硬约束（实现与文档必须一致）**：

1. **禁用服务端 SEARCH**：真机 spike 实测 QQ 邮箱 IMAP 的 `OR(FROM "alipay")(FROM "tencent")(SUBJECT "账单")` 返回 0 封而邮件明明存在——全程**不调用任何 `folder.search(...)`**，只 `FetchProfile(ENVELOPE + FLAGS)` 拉窗口内邮件头部，由 `MailBillMatcher` 本地过滤，命中后才取正文/附件（避免全量下载正文）。
2. **全程只读**：`folder.open(Folder.READ_ONLY)` + `folder.close(false)`（不 expunge）；不写 `Flags.Flag.SEEN`、不 delete/copy/append、不移动——绝不改动邮箱状态。（只读性属真机可观察行为，待真机验证。）

其余契约：

- 服务器 `imap.qq.com:993` + SSL；连接/读超时各 30 s。
- **IMAP `ID` 命令双保险**（QQ 要求声明客户端身份否则拒绝登录）：属性 `mail.imap.id` / `mail.imaps.id`（连接协商阶段生效）+ 连接后 `(store as IMAPStore).id(map)`（`name=MiniBook, version=1.0`）；ID 调用失败被容忍（try/catch 记警告不致命）。
- 时间窗口：优先 `getSentDate()`（服务端 `Date` 头），为空回退 `getReceivedDate()`，两者皆空视为在窗口内（不丢件）。
- 异常映射（凭证只用于 `store.connect(...)`，**绝不进异常 message / 日志**）：
  - `AuthenticationFailedException` → `MailAuthException("登录失败：请确认已在 QQ 邮箱开启 IMAP/SMTP 服务，且授权码为最新")`（固定文案，不透传原异常消息）；
  - `SocketTimeoutException` / `MessagingException` / `IOException` → `MailNetworkException`（固定中文文案 + 异常类简单名，无凭据）。
- 正文/附件抽取：递归走 `Multipart`；附件 mime 命中（`application/octet-stream` / `application/zip` / `application/x-zip-compressed`）取首个，文件名**原样透传**（解码归 `MailFilenameDecoder`）；正文 plain 与 html 两路收集，HTML 经 `stripHtml` 去标签——**先保留 `href`/`src` 属性里的 URL 再去标签**（否则 `<a href="...">点击下载</a>` 整体去标签会把 URL 一起删掉），最后反转义常见实体。

> 两处 `stripHtml`（保留 href/src）与 `bodyText`（plain+html 拼接）为开发中发现并修复的「微信下载链接丢失」缺陷的修正，属正确性红线，改动须带回归验证。

### 14.4 本地过滤与取件契约

**`MailBillMatcher.platformOf(from: String?, subject: String?): BillPlatform?`**（纯 Kotlin）：

- 发件人与主题**同时**命中才返回平台（先判支付宝再判微信，均不中返回 null；均大小写不敏感、可容忍 `张三 <service@mail.alipay.com>` 显示名形态）：

| 平台 | 发件人须含 | 主题须含 |
|------|-----------|---------|
| `ALIPAY` | `service@mail.alipay.com` | `支付宝交易流水明细` |
| `WECHAT` | `wechatpay@tencent.com` | `微信支付-账单流水文件` |

**`MailFilenameDecoder.decode(raw: String?): String?` / `joinRfc2231Parts(partsByIndex: Map<Int, String>): String`**（纯 Kotlin）：

- JavaMail `getFileName()`（默认 `mail.mime.decodefilename=false`）返回**未解码原始串**。解码覆盖三类形态：① RFC 2231 续行（`name*0`/`name*1`）按序号升序**首尾直接相接**（不插分隔符——QQ 邮箱的折行会把一个 encoded-word 从中间劈开）；② RFC 2047 encoded-word（`=?charset?Q?...?=` / `=?charset?B?...?=`，Q 编码 `=XX` 为十六进制字节、按 charset（实测 GB18030）解码、`_` 为空格；**相邻 encoded-word 之间仅隔空白时空白丢弃**，RFC 2047 §6.2）；③ 折行残留（先去 `\r`/`\n`）与非法但真实存在的 `filename=GB18030?Q?...?=`（少一处 `=`）容错。
- 任何解码失败（非法 Base64/未知字符集/非法十六进制）**原样返回输入，绝不抛异常**。

**`WechatBillLinkExtractor.extract(bodyText: String): String?`**（纯 Kotlin）：

- 在正文文本上按「host **精确等于** `tenpay.wechatpay.cn`（可带端口，防 `tenpay.wechatpay.cn.evil.com` 仿冒域名）+ 固定路径 `/userroll/userbilldownload/downloadfilefromemail`」匹配账单下载链接；URL 候选在引号/尖括号/空白/中文处截断；命中后反转义 `&amp;`。
- **不用「取第一个 http 链接」**：正文里先出现的图片/帮助页链接（`imgcache.qq.com`、`wx.gtimg.com` 等）不会被误选。

**`MailBillDownloader.download(url: String): DownloadedFile`**（`data class DownloadedFile(val fileName: String?, val bytes: ByteArray)`）：

- `HttpURLConnection`（不引入新依赖）：**仅允许 https（初始 URL 与每一跳重定向都校验）**；手动跟随重定向（≤5 跳，相对 Location 解析为绝对）；连接/读超时各 30 s；带浏览器 User-Agent。
- 非 200 统一按「链接失效」报错（微信链接有效期 7 天，过期返回 403/404/410）。
- 失败抛 `BillDownloadException`（`data/local/mail` 内）：文案固定（下载超时 / 链接失效或过期 / 下载失败 / 仅支持 https / 重定向次数过多），**message 不含 URL 全文**——微信链接 query 带签名 token，视同凭证。
- 伴生纯函数 `ContentDispositionParser.parse(header: String?): String?`：`filename*=UTF-8''<percent-encoded>`（RFC 5987，自带字符集、无歧义）**优先于** `filename`；回退 `filename` 时若含 `%XX` 则按 UTF-8 percent 解码；解析不出返回 null。

### 14.5 ZipCrypto 契约（`domain/usecase/bill/zip/`，纯 Kotlin、JVM 可单测）

**加密类型判定**：

```kotlin
sealed interface ZipEncryptionInfo {
    data object ZipCrypto : ZipEncryptionInfo          // PKWARE 传统加密，可 6 位数字穷举
    data class Unsupported(val reason: String) : ZipEncryptionInfo  // AES/未加密/未知/文件非法
}
class ZipArchiveInspector @Inject constructor() {
    fun inspect(zipFile: File): ZipEncryptionInfo
}
```

判定口径：手写解析 zip 中央目录第 0 个条目（EOCD 倒查 → 中央目录 → 本地头，全部 little-endian，不依赖 `java.util.zip`）；条目 `method ∈ {0, 8}` 且 general purpose flag bit 0（0x0001）置位 → `ZipCrypto`；**WinZip AES 判定 = `method == 99` 或中央目录/本地头任一 extra field 含 header id `0x9901`** → `Unsupported`；flag bit 0 未置位（未加密）/文件不存在/中央目录非法 → `Unsupported`。

**口令穷举**：

```kotlin
class ZipPasswordCracker @Inject constructor() {
    suspend fun crack(
        zipFile: File,
        digits: IntRange = 6..6,
        charset: String = "0123456789",
        onProgress: ((tried: Long) -> Unit)? = null,   // 多 worker 并发回调，实现须线程安全
    ): String?   // 命中口令；未命中返回 null
}
```

- **两段式判定**（正确性红线，已验证口径）：
  1. **快筛**（每个候选都做）：候选口令派生密钥 → 只解密 12 字节加密头 → 取下标 11 的校验字节，**宽松接受**：等于「中央目录 CRC32 高字节」**或**「DOS 修改时间高字节」**任一**即通过（带 data descriptor 时校验字节约定会变，同时要求两者会误杀正确口令）。
  2. **权威判定**（仅快筛命中者执行）：整包解密 → `Inflater` 解压 → 对**解压后的原始内容**算 CRC32 与中央目录 CRC 比较。**绝不能拿解密后的压缩数据算 CRC**（zip 记录的是未压缩内容 CRC）。
- 性能：预计算前 `len/2` 位口令对应的密钥状态（6 位数字即前 3 位 1000 个状态）；`Dispatchers.Default` 上按 `availableProcessors()` 分片并发；每 4096 个候选检查一次协程取消并回调进度（**可取消**）。
- 实测（协调者以临时验收测试跑三份真实账单后删除该测试）：三份均判定 `ZipCrypto`、**全部命中 6 位数字口令**，单份穷举耗时 90~760 ms（多核，亚秒级）；解出字节与独立 Python oracle 完全一致。**文档不得记录具体口令与账单内容。**

**解密解压**：

```kotlin
class EncryptedZipExtractor @Inject constructor() {
    fun extract(zipFile: File, password: String, destDir: File): File
}
```

流程：解析第 0 个条目 → 整包解密（12 字节加密头 + 压缩数据，密钥连续推进）→ 校验字节宽松校验（同快筛口径，防 data descriptor 误杀）→ `method=0` 直取原文 / `method=8` 用 `Inflater(nowrap)` 解 raw deflate → 按条目名（UTF-8（flag bit 11）/ GBK（无损往返校验）/ CP437 兜底）写入 `destDir`（取基名防路径穿越），返回解出的文件。口令错误/数据损坏抛 `IllegalArgumentException`（中文固定文案）。

底层 `ZipCrypto`（`internal object`，仅供同包 Inspector/Cracker/Extractor 与单测使用，不对外暴露）：三密钥初始化 `0x12345678/0x23456789/0x34567890`、用**明文字节**推进密钥、`keyStreamByte` 由 k2 派生密钥流字节——算法为已验证口径，**不得改动**。

### 14.6 导入链路扩展（F-12 契约的增量，不改动既有行为）

**`BillFilePreparer`（域接口）新增两方法**：

```kotlin
fun prepareLocalFile(file: File, displayName: String? = null): PreparedBillFile
fun createTempFile(prefix: String, suffix: String): File   // 落 App cacheDir；文件由调用方负责删除
```

- `prepareLocalFile` 判型口径与 `prepare(uriString)` **完全一致**（`PK\x03\x04` 魔数嗅探优先、扩展名兜底），但**不接管文件生命周期**——`PreparedBillFile.close()` **绝不删除调用方的 `file`**（临时文件归调用方清理）。XLSX 直接复用调用方文件（`ZipFile` 本就需可 seek File，无需再拷副本）；CSV 开 GBK Reader。
- `SafBillFilePreparer` 实现：XLSX 路径返回 `XlsxPrepared(file, name, magicBytes, deleteOnClose = false)`——SAF 路径仍为 `deleteOnClose = true`（拷贝出的临时副本 close 即删）。

**`ImportBillUseCase` 新增本地文件入口**：

```kotlin
suspend fun importLocalFile(file: File, displayName: String?, ledgerId: String): BillImportResult
```

- 与 `invoke(uriString, ledgerId)` **共用同一 `importPrepared` 核心**（resolve → parse → assemble 事务写入，§13.6 全部口径不变）；两入口均先 `ensureBuiltInCategories(ledgerId)` 补齐内置分类；`prepareLocalFile` 异常折叠为 `unrecognized=true` 零落库（与 `invoke` 的 prepare 异常同口径）。
- **实现约束（重要）**：`displayName` 参与解析器判型——解析器工厂按扩展名分发（魔数嗅探仅覆盖 XLSX）。**邮件侧解出的支付宝附件名是 `.zip` 名**，若直接传给 `importLocalFile` 会导致每封都被判「未识别」；因此编排层（§14.7）只在邮件侧文件名本身为 `.csv`/`.xlsx` 时采用它，否则回退用 zip 条目名（解压出的真实账单文件名，扩展名可靠）。

### 14.7 编排契约（`domain/usecase/mail/MailBillImportUseCase.kt` + `domain/model/MailImportModels.kt`）

```kotlin
@Singleton class MailBillImportUseCase @Inject constructor(/* accountStore, mailClient, downloader,
    filenameDecoder, linkExtractor, zipInspector, zipCracker, zipExtractor, filePreparer,
    importBillUseCase, @IoDispatcher ioDispatcher */) {
    suspend operator fun invoke(
        ledgerId: String,
        accountIds: List<String>,          // 1 个=直接收取；多个=「全部收取」（多邮箱按序处理）
        sinceDays: Int = 30,
        onProgress: (MailImportProgress) -> Unit = {},
    ): MailImportOutcome   // NotConfigured | Completed(summary)
}
```

全流程步骤：

1. **读凭据**：`accountIds` 为空 / Store 无账号 → `MailImportOutcome.NotConfigured`（不抛异常，UI 引导去设置页）；**账号不存在或授权码不可用（密文损坏/密钥失效）→ 不静默跳过**：往 `itemLines` 追加一行 ❌ 并 `emitSkipSnapshot`（stepper 将「连接邮箱」标 `FAILED` + 原因，其余 `PENDING`，携带累积 `itemLines`），继续下一账号——跳过信息在 UI 上显式可见。邮箱地址与授权码绝不进任何异常 message、日志或 `Unexpected`。
2. **逐账号 5 步**（每个账号各走一遍，进度头部 `邮箱 k/N`）：CONNECT→FETCH→FILTER（该三步在 `fetchBillMails` 客户端调用期间**并发 RUNNING**，调用返回后各自补 DONE 摘要——服务端 SEARCH 被禁用，无法观察三步边界，见类头 KDoc）→ PROCESS（逐封，每封发一次子步骤展开快照）→ DONE（汇总快照）。多账号时已完成账号的逐封结果经 `itemLines` 跨账号累积。
3. **进度快照累积（`MailImportStepTracker`）**：所有 `onProgress` 快照都携带**完整 5 步列表**，且状态**累积**——已发出的步骤状态在后续快照中保持，未出现过的步骤为 `PENDING`（修复前每份快照把未列出的步骤一律补 `PENDING`，导致已完成的步骤被后续快照打回待执行，真机收取成功后 stepper 显示 `○○○○✓`）。每个账号一份 tracker 实例（多账号各自从头开始）。
4. **单账号失败向上抛**：`MailAuthException` / `MailNetworkException`（message 已保证不含凭据）不吞没，由 UI 映射文案。
5. **逐封处理**（对 FILTER 后经 `MailBillSelector` 保留的每封；每封一次子步骤快照：EXTRACT→DOWNLOAD（仅微信）→INSPECT→CRACK→UNZIP→IMPORT）：取 zip 字节（支付宝=附件字节；微信=提取链接 → HTTPS 下载）→ `createTempFile` 落盘（`cacheDir`）→ `ZipArchiveInspector.inspect` 判型 → 非 ZipCrypto 记失败继续 → `ZipPasswordCracker.crack`（6 位数字）→ 未命中记失败继续 → `EncryptedZipExtractor.extract` 解压到临时目录 → 按 displayName 约束选展示名 → `ImportBillUseCase.importLocalFile` 入库 → `unrecognized=true` 记 `UnrecognizedBill`。
6. **汇总**：各账号 DONE 后跨账号累加，最后返回 `MailImportOutcome.Completed(MailImportSummary)`。
7. **临时文件必清**：每封的 zip 落盘文件、解压占位文件与解压产物登记进 `tempFiles`，整批在同一 `try/finally` 中删除（成功/失败/异常/取消皆覆盖）。
8. **`totalMailCount` 口径**：各账号时间窗口内参与筛选的邮件总数之和（`fetchBillMails` 返回窗口计数），**不再恒等于 `billMailCount`**（如实，不伪造数字）。

**模型（`domain/model/MailImportModels.kt`，据实现源码）**：

```kotlin
enum class MailImportStep { CONNECT, FETCH, FILTER, PROCESS, DONE }   // 5 步，多邮箱每账号一遍
enum class MailImportStepStatus { PENDING, RUNNING, DONE, FAILED }
data class MailImportStepState(val step: MailImportStep,
    val status: MailImportStepStatus, val summary: String? = null)   // 完成/失败附中文摘要
enum class MailItemStep { EXTRACT, DOWNLOAD, INSPECT, CRACK, UNZIP, IMPORT }  // PROCESS 内单封子步骤
data class MailImportProgress(
    val steps: List<MailImportStepState>,   // 完整 5 步列表（累积口径，见 MailImportStepTracker）
    val accountEmail: String? = null,       // 多邮箱头部「邮箱 k/N · email」
    val accountIndex: Int = 1, val accountCount: Int = 1,
    val mailIndex: Int = 0, val mailCount: Int = 0,        // 第 4 步展开区
    val currentSubject: String? = null,
    val currentItemStep: MailItemStep? = null,
    val crackPercent: Int? = null,          // 口令穷举百分比（整数变化才回调）
    val itemLines: List<String> = emptyList(),  // 已完成邮件的逐封结果行（跨账号累积）
)
sealed interface MailItemFailure   // 9 分支，见下表
data class MailBillItemResult(val accountEmail: String, val platform: BillPlatform,
    val subject: String, val sentDateMillis: Long, val password: String?,
    val attachmentFileName: String?, val importResult: BillImportResult?, val failure: MailItemFailure?)
data class MailImportSummary(val totalMailCount: Int, val billMailCount: Int,
    val items: List<MailBillItemResult>) {
    val succeededCount: Int; val failedCount: Int; val totalImported: Int; val totalExisting: Int  // 派生
}
sealed interface MailImportOutcome {
    data object NotConfigured : MailImportOutcome
    data class Completed(val summary: MailImportSummary) : MailImportOutcome
}
```

- **步骤摘要口径**（`MailImportStepState.summary`）：CONNECT「已连接（IMAP/SSL）」；FETCH「窗口内共 N 封」；FILTER「命中 N 封（微信 x / 支付宝 y）」**，存在同平台旧账单时追加「，每种账单只取最近一封 → 待处理 K 封」**；PROCESS「成功 N 封 / 失败 M 封」；DONE「新导入 N 条 / 已存在 M 条」；跳过账号 → CONNECT `FAILED` + 「授权码不可用（解密失败），已跳过该邮箱」/「邮箱账号不存在（可能已被删除），已跳过该邮箱」。
- **每种账单只取最近一封（`domain/usecase/mail/MailBillSelector.kt`，纯 Kotlin、JVM 可单测）**：`BillPlatform` 即「账单种类」（支付宝交易流水明细 / 微信支付账单流水文件），按平台分组、组内保留 `sentDateMillis` 最新一封（同毫秒按主题字典序取大，保证确定性）；其余同平台旧邮件**不处理**——`itemLines` 留一行 `⏭ {平台} · {主题} — 已有更新的同类账单，跳过`，FILTER 摘要同步披露「命中 N 封 … → 待处理 K 封」；`MailImportSummary.billMailCount` 仍按**命中数**计（如实，不因跳过而缩水）。本步只做选取，**不删除任何已导入数据**。
- **展示单一事实源**：`MailItemFailure.displayLabel()`（9 分支 `when` 穷尽）+ `BillPlatform.displayLabel()` + `MailBillItemResult.toResultLine()`（成功「✅ 平台 · 主题 — 新导入 X 条 / 已存在 Y 条」/ 失败「❌ 平台 · 主题 — 原因」）——进度 `itemLines` 与结果页逐封行共用同一份文案，不漂移。
- **步骤状态累积器**：`domain/usecase/mail/MailImportStepTracker.kt`（纯 Kotlin、JVM 可单测）——`apply(vararg active)` 记录本份快照列出的步骤状态并返回按 `MailImportStep.entries` 排序的完整列表；未出现过的步骤为 `PENDING`，已出现过的保持上次状态。编排层 `processAccount` 每账号一份实例。

**失败映射表（`MailItemFailure` 9 分支 × 触发条件）**：

| 分支 | 触发条件 |
|------|---------|
| `NoAttachment` | 支付宝邮件没找到附件（`attachmentBytes == null`） |
| `NoLink` | 微信邮件正文没找到下载链接 |
| `LinkExpired` | 下载异常文案含「失效或过期」（微信链接 7 天有效期的固定文案子串） |
| `DownloadFailed` | 其余 `BillDownloadException`（超时/网络/非 https 等；不透传异常原文，可能含 URL） |
| `UnsupportedEncryption` | zip 非 ZipCrypto（WinZip AES / 未加密 / 非法 zip） |
| `PasswordNotFound` | 6 位数字穷举未命中（**当前降级方式：记录该失败并在结果页提示「未能自动解出解压口令（非 6 位数字）」；手动输入口令 UI 未实现，属后续增强**） |
| `ExtractFailed` | 解压抛异常（口令校验不过/数据损坏等） |
| `UnrecognizedBill` | 导入链路返回 `unrecognized == true` |
| `Unexpected(message)` | 其余未预期异常（仅异常类别描述，不含凭据/URL/账单内容）；单封失败不中断整批，取消异常原样上抛 |

> **与设计稿差异**：原计划拆两个 UseCase（`FetchBillFromMailUseCase` + `MailBillImportUseCase`），实际**合并为一个** `MailBillImportUseCase`（`fetchBillMails` 直连 IMAP 客户端）；原计划的「同一账期重复邮件按主题+账期去重」**未实现**——重复入库由 F-12 的 `externalId` 幂等兜底（重复邮件会计入「已存在」而非重复入账），重复下载代价可接受。

### 14.8 UI 契约

- 路由：`Destination.MailAccount = Destination("mail_account")`（根图全屏页，顶栏返回 `popBackStack`，同 `Import` 口径）；`MiniBookNavHost` 注册 `MailSettingsScreen`，并在 `ImportScreen`（未配置态「去配置」）与 `MainScreen`→`SettingsScreen`（设置入口）两处接线 `onNavigateToMailAccount`。
- `ImportUiState` 在 F-12 四态基础上新增 5 态（`ui/billimport/ImportViewModel.kt`）：

```kotlin
sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Busy : ImportUiState
    data class Result(val result: BillImportResult) : ImportUiState
    data class Error(val message: String) : ImportUiState
    // —— F-13 新增 ——
    data class MailBusy(val progress: MailImportProgress) : ImportUiState
    data class MailResult(val summary: MailImportSummary,
                          val progress: MailImportProgress?) : ImportUiState
    data class MailChooseAccount(val accounts: List<MailAccount>) : ImportUiState  // ≥2 账号选择弹窗
    data object MailNotConfigured : ImportUiState                                  // 未配置凭据 → 引导设置页
    data class MailError(val message: String,
                         val progress: MailImportProgress?) : ImportUiState        // 认证/网络/未预期
}
```

- **同页合一是硬口径**：`MailBusy` / `MailResult` / `MailError` 三分支**统一渲染同一个 `MailImportContent`**（`ui/billimport/MailImportProgressContent.kt`）——收取中推进 stepper，结束后原地追加结果 / 错误区 + 底部「重新选择 / 返回」，**不跳页、不整体替换版式**；仅 `MailNotConfigured` 与 F-12 的 SAF 文件导入各态使用各自版式。
- `ImportViewModel.onFetchMailBills()`：0 个账号 → `MailNotConfigured`；**1 个账号直接收取**；**≥2 个 → `MailChooseAccount`**（弹窗「选一个」→ `onFetchAccounts(listOf(id))`、「全部收取」→ 全部 id）；收取中 `MailBusy(progress)` 随回调直刷（进度回调记 `lastProgress`）；`Completed` → `MailResult(summary, lastProgress)`；`NotConfigured` → `MailNotConfigured`；`MailAuthException`/`MailNetworkException`/其余异常 → `MailError`（固定中文文案，不含凭据）——失败时把 `lastProgress` 中仍为 `RUNNING` 的步骤 `copy` 成 `FAILED` 后一并传入，使失败页仍保留已完成的进度。
- `ui/billimport/MailImportPresenter.kt`（纯函数、无 Android 依赖，JVM 可单测）：`failureLabel`（委托 `MailItemFailure.displayLabel()`）、`stepLabel`（5 步中文名）、`itemStepLabel`（6 子步骤中文名）、`stepLine(state)`（`label · 待执行/进行中/摘要/失败：原因`）与 `present(summary)`（计数 + 一句话 headline + 逐封失败行）。文案全集中于此（或 domain 层单一事实源），Composable 不做映射。
- `ui/mail/MailSettingsScreen.kt` + `MailSettingsViewModel.kt`（P-16 **邮箱凭据**（多账号），详见 `05_ui.md §10.2`）：列表态（账号行 + 删除 + 添加邮箱）/ 编辑态（邮箱 + 授权码 + 保存）/ 删除强确认；持久化全委托 `MailAccountStore`（`accounts: StateFlow<List<MailAccount>>` + `suspend upsert/delete/getAuthCode`）。

### 14.9 真机验证结论与已知限制（如实记录）

**已验证（真机 A1B2C3D4，2026-09-23，实测非推断）**：

- 真实邮箱端到端收取：窗口内 13 封邮件 → 本地过滤命中 5 封账单邮件（微信 2 / 支付宝 3）→ **全部成功**（`成功 5 封，失败 0 封`）；两封微信账单分别导入映射 211 / 231 条，二次收取全部落到「已存在」（`新导入 0 条，已存在 896 条`，`externalId` 幂等生效）；`totalMailCount`(13) 与 `billMailCount`(5) 已正确分离。
- 微信取链链路（正文 HTML → `tenpay.wechatpay.cn` 下载链接 → HTTPS 下载 → ZipCrypto 口令穷举 → 解压 → 入库）真机全通。
- **「每种账单只取最近一封」真机验证（2026-10-09，同设备）**：窗口内 3 封邮件 → 命中 3 封（微信 1 / 支付宝 2）→ FILTER 摘要实测显示 `命中 3 封（微信 1 / 支付宝 2），每种账单只取最近一封 → 待处理 2 封`；同平台旧邮件以 `⏭ 支付宝 · 张宏宇的支付宝交易流水明细 — 已有更新的同类账单，跳过`（1 条）显式跳过；`处理账单邮件 · 成功 2 封 / 失败 0 封`，幂等 `新导入 0 条 / 已存在 240 条`（微信 219 + 支付宝 21）。**「选中的确为最新」的判据**：被跳过的是当日 16:00 运行中报「已存在 111 条」的那封旧支付宝账单，本次保留的 ✅ 行报「已存在 21 条」（≠111）→ 保留的是刚申请的新账单、旧那封被跳过。证据：`.claude-runs/f13-latest-only-newbill.log` + `.claude-runs/f13-latest-only.png`。
  - **如实说明数字差异**：2026-09-23 那次为 13 封 → 命中 5 封（微信 2 / 支付宝 3）。今日**无法复现「5 封 → 2 封」**——那批旧账单已不在收件箱：把窗口临时放宽到 120 天后，窗口内仍只有 6 封邮件 / 2 封账单（且 `ImapMailClient` 全程只读、遍历整个 INBOX、无封数上限，不存在本方删除或截断的可能）。故本次以 3 封（微信 1 / 支付宝 2）场景验证同一行为，行为结论等价。
- 进度 UI：5 步 stepper（含每步中文摘要）、第 4 步展开区（`第 i/N 封 · 主题` + 子步骤 + 口令穷举百分比）、逐封结果行、进度与结果**同页**、钉底按钮，均按预期渲染。
- 多邮箱分支：「邮箱凭据」多账号（设置页徽标「已配置 N 个」）、「选择邮箱」弹窗（选一个 / 全部收取 / 取消）、`邮箱 k/N` 头部推进、授权码缺失账号以 `✗ 连接邮箱 · 失败：授权码不可用（解密失败），已跳过该邮箱` 显式呈现 —— 均实测通过。
- `mail_account` 路由可达、设置页入口、各态渲染 —— 实测通过。

**仍未验证 / 已知限制（不得声称已验证）**：

- IMAP 只读性为**代码级保证**（`READ_ONLY` + `close(false)` + 零服务端 `SEARCH`）+ 真机间接验证（邮箱侧未见 flag/文件夹异常）；**未逐字段核对 `\Seen` 状态**。
- `MailBillDownloader` 的重定向与**链接过期**分支（`LinkExpired` 文案）未在真机触发过；`UnsupportedEncryption`（AES zip）亦无真实样本。
- release 打包与签名（**2026-10-09 已实测**）：`:app:assembleDebug` BUILD SUCCESSFUL（APK 36.23 MB）；`:app:assembleRelease` BUILD SUCCESSFUL → **已签名** `app-release.apk`（30.98 MB；`apksigner verify` 通过，签名证书 CN=Minibook，证书 SHA-256 `e137b7e5…`）。签名走 `signingConfigs.release` ← `keystore.properties`（`storeFile`/`storePassword`/`keyAlias`/`keyPassword` 四项齐备才创建；**该文件与 `*.jks` 均被 `.gitignore` 忽略、不入版本库**；文件缺失时退回未签名产物、不阻断构建）。**如实修正**：`app/build.gradle.kts` 里 `release { isMinifyEnabled = false }`，故 release 为**非混淆、非裁剪**构建（debug 更大仅因含 Compose 调试工具与调试信息）；`proguard-rules.pro` 的 JavaMail keep 规则处于**备用状态、当前未实际生效**。
- **GitHub Release v1.0.0（2026-10-09，私有仓库）**：tag `v1.0.0` → `b8375d0`；资产 `minibook-v1.0.0-release.apk`（32,482,627 字节 / 30.98 MB，SHA-256 `5EAF6B51…`，GitHub 侧 `digest` 与本地一致 → 上传无损）。仓库 `private=true`，下载需登录有权限的账号。
- **release 包运行期仍未装机验证（如实记录）**：debug 与 release 签名不同 → 无法覆盖安装；而卸载 debug 包会一并清除本地加密库（SQLCipher 主密钥亦随卸载销毁），因此换装前须先用 F-04 导出备份 JSON。故 release 侧仅证明「可构建、已签名、可分发」，**不等于 release 运行期行为已验证**。
- 无 JVM 单测的组件：`ImapMailClient`（JavaMail 无官方假服务器，需 greenmail 等新依赖，未引入）、`MailBillDownloader`（网络）、`SafBillFilePreparer`（依赖 `android.content.Context`，项目无 Robolectric/Mockito 且未新增依赖）、`MailBillImportUseCase` 编排主体（依赖 IMAP/下载等 I/O 边界）——覆盖方式为**真机端到端**。建议后续在 androidTest（有真 Context）补：`SafBillFilePreparer.prepareLocalFile + close() 后源文件仍在`、`prepare() + close() 后临时副本已删` 两条回归。
- `XlsxPrepared(deleteOnClose=false)` 的真实实现**没有直接单测保护**（JVM 侧仅以同语义 fake 覆盖编排层不引入删除路径，见 `ImportBillUseCaseTest`「close 不删除调用方文件」用例注释）。
- 手动输入口令未实现（见 §14.7 `PasswordNotFound` 行）。

---