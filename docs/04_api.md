# MiniBook Android 重构版 - API 契约设计（数据访问抽象层）

> 状态：**已确认**
> 维护者：Architect
> 最后更新：2026-08-18
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

（本文件为设计文档，随实现对细节的差分由后续修订更新；版本历史由协调者统一管理。）