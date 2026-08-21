# MiniBook Android 重构版 - 数据库设计文档

> 状态：**已确认**
> 维护者：Architect
> 最后更新：2026-08-18
> 依据：`docs/01_prd.md`（已确认）；字段语义参考旧项目 `minibook/docs/DATA_MODEL.md`（技术栈已废弃，仅参考字段口径）。
> 配套：`docs/02_architecture.md`（加密/导出/多账本如何落地见架构文档）。

---

## 目录

- [1. 总体设计原则](#1-总体设计原则)
- [2. 数据库总览](#2-数据库总览)
- [3. ER 关系图](#3-er-关系图)
- [4. 实体定义（Room Entity）](#4-实体定义room-entity)
  - [4.1 Transaction（交易）](#41-transaction交易)
  - [4.2 Debt（债务/借还）](#42-debt债务借还)
  - [4.3 Category（分类）](#43-category分类)
  - [4.4 Ledger（账本）](#44-ledger账本)
  - [4.5 Budget（预算，Phase 2 建表）](#45-budget预算phase-2-建表)
  - [4.6 SyncChange（同步变更）](#46-syncchange同步变更)
- [5. 索引设计](#5-索引设计)
- [6. DAO 与核心查询](#6-dao-与核心查询)
- [7. 数据库版本号与迁移策略](#7-数据库版本号与迁移策略)
- [8. 内置分类种子数据](#8-内置分类种子数据)
- [9. 金额分处理规范与转换约定](#9-金额分处理规范与转换约定)

---

## 1. 总体设计原则

| 原则 | 说明 |
|------|------|
| **金额整数化** | 所有金额以「分」为单位存 `INTEGER`（Kotlin `Long`），彻底避免浮点误差（`12.34 元 → 1234`） |
| **UUID 主键** | 所有实体主键 `TEXT` 存 UUID v4，离线生成、无自增依赖，为未来多端同步预留 |
| **时间 ISO 化** | 时间字段统一 ISO 8601 字符串；「发生日期」用 `yyyy-MM-dd`，「审计时间」用完整时间戳（见 §4.1 字段页） |
| **账本自第一天隔离** | 所有业务实体带 `ledgerId` 外键；所有查询以 `WHERE ledgerId=?` 打头；复合索引以 `ledgerId` 为第一列 |
| **外键不设级联** | 复用旧版经验：删除分类前业务层做关联检查并提示迁移，DB 层不设 `ON DELETE CASCADE` |
| **加密整体** | 整个 DB 文件由 SQLCipher 加密，无「敏感表加密」分域的概念 |
| **软删除/同步预留** | 业务实体物理删除 + `syncStatus` 状态机 + `SyncChange` 变更表，为 Phase 3 云端同步预留 |

---

## 2. 数据库总览

| 属性 | 值 |
|------|-----|
| 库名 | `minibook-encrypted.db` |
| 当前版本 | **1** |
| 加密 | SQLCipher（全库），密钥见 `02_architecture.md §7` |
| 表数量 | 6（ledgers / categories / transactions / debts / budgets / sync_changes） |
| ORM | Room，`exportSchema = true`（schema JSON 入 VCS，用于迁移测试） |

---

## 3. ER 关系图

```
┌─────────────┐        ┌─────────────┐
│   Ledger    │ 1    N │  Category   │
│─────────────│────────│─────────────│
│ id (PK)     │        │ id (PK)     │
│ name        │        │ ledgerId(FK)│
│ isDefault   │        │ type        │
└─────────────┘        │ name/icon/  │
                       │ color/order │
┌─────────────┐        └─────────────┘
│ Transaction │  N:1  ┌─────────────┐
│─────────────│──────▶│  Category   │
│ id (PK)     │ N:1   │  (同账本内)  │
│ ledgerId(FK)│───▶   └─────────────┘
│ categoryId  │
│ amount      │
│ syncStatus  │
└─────────────┘

┌─────────────┐
│    Debt     │  独立实体（不关联分类，自成体系）
│─────────────│
│ id (PK)     │
│ ledgerId(FK)│
│ partyName   │
│ kind/dir    │
│ syncStatus  │
└─────────────┘

┌─────────────┐        ┌─────────────┐
│   Budget    │        │ SyncChange  │
│─────────────│        │─────────────│
│ id (PK)     │        │ id (PK)     │
│ ledgerId(FK)│        │ ledgerId(FK)│
│ month       │        │ entityType  │
│ categoryId? │        │ entityId    │
│ amount      │        │ action/data │
└─────────────┘        └─────────────┘
```

关系说明：

- `Transaction.categoryId → Category.id`：仅同账本内引用有效。DB 层不设强外键（防删级联），由业务层校验 + `countTransactionsByCategory` 兜底。
- `SyncChange.entityId` 引用 Transaction/Debt/Category 的 id，通过 `entityType` 区分（符合云端出口预留）。
- `Debt` 不关联分类/账本外，另有 ledgerId。借/还平级模型见 §4.2。

---

## 4. 实体定义（Room Entity）

> Kotlin 类型：金额 `Long` / 枚举以 `TEXT` 存（`TypeConverter` 统一转码），布尔以 `INTEGER(0/1)` 存。
>
> 「索引」列：`[a,b]` 表示复合索引为 `@Index(value = ["a","b"])`（`columns(a,b)`），`UNIQUE` 另标注。

---

### 4.1 Transaction（交易）

对应 domain `Transaction`。

| 字段 | Kotlin 类型 | SQL 类型 | 约束/缺省 | 说明 |
|------|------------|----------|-----------|------|
| `id` | String | TEXT | PK，UUID v4 | 主键，存储前生成 |
| `ledgerId` | String | TEXT | NOT NULL | **账本作用域（第一列过滤）** |
| `type` | String | TEXT | NOT NULL | `expense` / `income`（TypeConverter 转枚举） |
| `amount` | Long | INTEGER | NOT NULL, >0 | 金额，单位分（Long 上限远够） |
| `categoryId` | String | TEXT | NOT NULL | 关联同账本 Category.id（业务层校验） |
| `description` | String? | TEXT | NULL，≤200 字符 | 备注（输入框限长 + 校验） |
| `date` | String | TEXT | NOT NULL | 发生日期，`yyyy-MM-dd`（本地日，无时区换算） |
| `createdAt` | String | TEXT | NOT NULL | 创建时间，`yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`，不可改 |
| `updatedAt` | String | TEXT | NOT NULL | 修改时间，每次修改刷新 |
| `syncStatus` | String | TEXT | NOT NULL 默认 `'local'` | `local/syncing/synced/conflict` |

索引：`[ledgerId, date]`、`[ledgerId, type]`、`[ledgerId, categoryId]`、`[ledgerId, syncStatus]`

```kotlin
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["ledgerId", "date"]),
        Index(value = ["ledgerId", "type"]),
        Index(value = ["ledgerId", "categoryId"]),
        Index(value = ["ledgerId", "syncStatus"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val ledgerId: String,
    val type: String,
    val amount: Long,
    val categoryId: String,
    val description: String?,
    val date: String,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: String = "local",
)
```

> `date` 存 `yyyy-MM-dd` 的原因：按「月」分组用 `BETWEEN '2026-08-01' AND '2026-08-31'` 即命中 `[ledgerId,date]` 索引；字符串字典序即时间序，天然可排序。legacy 全时间戳在导入时归一化（见 §9）。

---

### 4.2 Debt（债务/借还）

对应 domain `Debt`。**借/还平级模型**：欠款与还款都是独立条目，`kind × direction` 四态：

| `kind` | `direction` | 业务术语 | 对净额贡献 |
|--------|------------|---------|-----------|
| `lend` | `owe_me` | 借出（我借给别人） | `+amount` |
| `lend` | `i_owe` | 借入（我向别人借） | `-amount` |
| `repay` | `owe_me` | 收到还款 | `-amount` |
| `repay` | `i_owe` | 我还款 | `+amount` |

净额口径（正=对方欠我，负=我欠对方）：`sum(contribution)` 按 `partyName` 分组得出；用 SQL `CASE WHEN` 一次算（§6 Q-D2）。

| 字段 | Kotlin 类型 | SQL 类型 | 约束/缺省 | 说明 |
|------|------------|----------|-----------|------|
| `id` | String | TEXT | PK，UUID v4 | 主键 |
| `ledgerId` | String | TEXT | NOT NULL | 账本作用域 |
| `partyName` | String | TEXT | NOT NULL 非空 | 对方姓名（输入校验拒绝空） |
| `amount` | Long | INTEGER | NOT NULL, >0 | 金额（分） |
| `kind` | String | TEXT | NOT NULL | `lend`/`repay` |
| `direction` | String | TEXT | NOT NULL | `owe_me`/`i_owe` |
| `date` | String | TEXT | NOT NULL | 发生日期 `yyyy-MM-dd` |
| `remark` | String? | TEXT | NULL | 备注 |
| `createdAt` | String | TEXT | NOT NULL | 创建时间（完整时间戳） |
| `updatedAt` | String | TEXT | NOT NULL | 修改时间 |
| `syncStatus` | String | TEXT | NOT NULL 默认 `'local'` | 同上 |

索引：`[ledgerId, date]`、`[ledgerId, partyName]`、`[ledgerId, kind]`、`[ledgerId, direction]`、`[ledgerId, syncStatus]`

```kotlin
@Entity(
    tableName = "debts",
    indices = [
        Index(value = ["ledgerId", "date"]),
        Index(value = ["ledgerId", "partyName"]),
        Index(value = ["ledgerId", "kind"]),
        Index(value = ["ledgerId", "direction"]),
        Index(value = ["ledgerId", "syncStatus"]),
    ],
)
data class DebtEntity(
    @PrimaryKey val id: String,
    val ledgerId: String,
    val partyName: String,
    val amount: Long,
    val kind: String,        // lend / repay
    val direction: String,   // owe_me / i_owe
    val date: String,
    val remark: String?,
    val createdAt: String,
    val updatedAt: String,
    val syncStatus: String = "local",
)
```

> 设计说明：不做「主欠款 + 嵌套 repayments」模型。还款独立成行、出现在明细列表，符合「先记欠款、再记还款」的录入习惯（沿用旧版已定模型）。

---

### 4.3 Category（分类）

| 字段 | Kotlin 类型 | SQL 类型 | 约束/缺省 | 说明 |
|------|------------|----------|-----------|------|
| `id` | String | TEXT | PK，UUID v4 | 主键 |
| `ledgerId` | String | TEXT | NOT NULL | **账本作用域** |
| `name` | String | TEXT | NOT NULL 非空 | 分类名 |
| `icon` | String | TEXT | NOT NULL | emoji 图标（如 `🍜`） |
| `color` | String | TEXT | NOT NULL | 十六进制色值（如 `#FF6B6B`） |
| `type` | String | TEXT | NOT NULL | `expense`/`income`（与交易类型对应） |
| `isBuiltIn` | Boolean | INTEGER | NOT NULL | true=内置（禁删）；false=自定义 |
| `order` | Int | INTEGER | NOT NULL | 排序权重，越小越靠前；「其他」=99 恒末尾 |
| `createdAt` | String | TEXT | NOT NULL | 创建时间 |
| `updatedAt` | String | TEXT | NOT NULL | 修改时间 |

索引：`[ledgerId, type]`、`[ledgerId, order]`

```kotlin
@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["ledgerId", "type"]),
        Index(value = ["ledgerId", "order"]),
    ],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val ledgerId: String,
    val name: String,
    val icon: String,
    val color: String,
    val type: String,
    val isBuiltIn: Boolean,
    val order: Int,
    val createdAt: String,
    val updatedAt: String,
)
```

> - 主键 `(id)` 全局唯一；适用于未来凭证共享分类：账本隔离靠 `ledgerId`，不做「同 name 去重」假设。
> - 无 `syncStatus`：沿用旧版「分类不参与增量同步，走全量导出/导入」；Phase 3 若需同步分类，用 Room 迁移加列解决（§7）。
> - 删除保护在业务层：`isBuiltIn` 禁删；删除自定义分类前 `countTransactionsByCategory`，有关联则提示迁移。

---

### 4.4 Ledger（账本）

| 字段 | Kotlin 类型 | SQL 类型 | 约束/缺省 | 说明 |
|------|------------|----------|-----------|------|
| `id` | String | TEXT | PK，UUID v4 | 主键 |
| `name` | String | TEXT | NOT NULL 非空 | 账本名 |
| `icon` | String | TEXT | NOT NULL | 账本图标（emoji） |
| `color` | String | TEXT | NOT NULL | 账本色值 |
| `isDefault` | Boolean | INTEGER | NOT NULL 默认 false | 默认账本标记（至少一个） |
| `position` | Int | INTEGER | NOT NULL 默认 0 | 列表排序 |
| `createdAt` | String | TEXT | NOT NULL | 创建时间 |
| `updatedAt` | String | TEXT | NOT NULL | 修改时间 |

索引：`[position]`、`[isDefault]`

```kotlin
@Entity(
    tableName = "ledgers",
    indices = [Index(value = ["position"]), Index(value = ["isDefault"])],
)
data class LedgerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val isDefault: Boolean = false,
    val position: Int = 0,
    val createdAt: String,
    val updatedAt: String,
)
```

> - 默认账本在首次 DB 打开时引导创建（`02_architecture.md §8.4`）。
> - 默认账本禁止删除；删除非默认账本 = 连同其全部业务行删除（`withTransaction` 内按 `ledgerId` 批量删 4 表）。

---

### 4.5 Budget（预算，Phase 2 建表）

Phase 2（F-08）才启用功能，但**表结构现在定**，version 1 即建表。

| 字段 | Kotlin 类型 | SQL 类型 | 约束/缺省 | 说明 |
|------|------------|----------|-----------|------|
| `id` | String | TEXT | PK，UUID v4 | 主键 |
| `ledgerId` | String | TEXT | NOT NULL | 账本作用域 |
| `month` | String | TEXT | NOT NULL | `yyyy-MM` 月份 |
| `categoryId` | String? | TEXT | NULL | 分类预算=对应分类 id；**月度总预算=NULL** |
| `amount` | Long | INTEGER | NOT NULL, ≥0 | 预算额度（分） |
| `createdAt` | String | TEXT | NOT NULL | 创建时间 |
| `updatedAt` | String | TEXT | NOT NULL | 修改时间 |

索引：`[ledgerId, month]`、`[ledgerId, month, categoryId]`（后随说明）
唯一约束：`[ledgerId, month, categoryId] unique`

```kotlin
@Entity(
    tableName = "budgets",
    indices = [
        Index(value = ["ledgerId", "month"]),
        Index(value = ["ledgerId", "month", "categoryId"], unique = true),
    ],
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    val ledgerId: String,
    val month: String,          // "yyyy-MM"
    val categoryId: String?,    // null = 月度总预算
    val amount: Long,
    val createdAt: String,
    val updatedAt: String,
)
```

> **UNIQUE 语义补充（对应 `02_architecture.md` D-7）**：SQLite 中 `(ledgerId, month, NULL)` 的 NULL 彼此视为不同，月度总预算（categoryId=NULL）理论上可插多行。解决：Repository 层对「总预算」走「先删后插」保证业务唯一，并配代码注释；DB 不加触发器。分类预算天然被 UNIQUE 约束保护。

---

### 4.6 SyncChange（同步变更）

云端出口预留（本期只建表不写数据）。相比旧版增加 `ledgerId`（变更亦有账本维度）。

| 字段 | Kotlin 类型 | SQL 类型 | 约束/缺省 | 说明 |
|------|------------|----------|-----------|------|
| `id` | String | TEXT | PK，UUID v4 | 变更记录 ID |
| `ledgerId` | String | TEXT | NOT NULL | 所属账本 |
| `entityType` | String | TEXT | NOT NULL | `transaction`/`debt`/`category` |
| `entityId` | String | TEXT | NOT NULL | 关联实体 id |
| `action` | String | TEXT | NOT NULL | `create`/`update`/`delete` |
| `timestamp` | String | TEXT | NOT NULL | 变更发生时间（完整时间戳） |
| `data` | String? | TEXT | NULL | 变更载荷 JSON；delete 可为空 |

索引：`[ledgerId, entityType]`、`[ledgerId, timestamp]`、`[entityId]`

```kotlin
@Entity(
    tableName = "sync_changes",
    indices = [
        Index(value = ["ledgerId", "entityType"]),
        Index(value = ["ledgerId", "timestamp"]),
        Index(value = ["entityId"]),
    ],
)
data class SyncChangeEntity(
    @PrimaryKey val id: String,
    val ledgerId: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val timestamp: String,
    val data: String?,
)
```

---

## 5. 索引设计

### 5.1 概览

| 表 | 索引 | 类型 | 用途 |
|----|------|------|------|
| transactions | `(ledgerId, date)` | 复合 | 账本内按日期范围/排序（首页、月结余） |
| transactions | `(ledgerId, type)` | 复合 | 账本内按支出/收入筛选 |
| transactions | `(ledgerId, categoryId)` | 复合 | 账本内按分类筛选（含删除前统计） |
| transactions | `(ledgerId, syncStatus)` | 复合 | Phase 3 查询未同步 |
| debts | `(ledgerId, date)` | 复合 | 账本内按时间排序/明细视图 |
| debts | `(ledgerId, partyName)` | 复合 | 账本内按对象汇总（净额） |
| debts | `(ledgerId, kind)` | 复合 | 帐本内欠款/还款筛选 |
| debts | `(ledgerId, direction)` | 复合 | 账本内借出/借入筛选 |
| debts | `(ledgerId, syncStatus)` | 复合 | Phase 3 |
| categories | `(ledgerId, type)` | 复合 | 账本内按收支类型取分类 |
| categories | `(ledgerId, order)` | 复合 | 账本内排序展示 |
| ledgers | `(position)` | 单列 | 账本列表排序 |
| ledgers | `(isDefault)` | 单列 | 找默认账本 |
| budgets | `(ledgerId, month)` | 复合 | 账本+月份取月度预算 |
| budgets | `(ledgerId, month, categoryId)` | 复合 **UNIQUE** | 分类预算唯一性 |
| sync_changes | `(ledgerId, entityType)` | 复合 | 按账本+实体取变更 |
| sync_changes | `(ledgerId, timestamp)` | 复合 | 增量时间线 |
| sync_changes | `(entityId)` | 单列 | 按实体反查 |

### 5.2 索引设计说明

- **所有业务复合索引第一列均为 `ledgerId`**：天然满足「每次查询先按账本切分」，账本间互不干扰，且同账本内再走二级列范围扫描。
- 数据量判断：千条级 → 复合索引 + DB 端聚合已足够，不需要冗余「month」列或函数索引。按月统计使用 `date BETWEEN 'yyyy-MM-01' AND 'yyyy-MM-31'` 命中 `(ledgerId, date)`。
- `syncStatus` 不参与本期查询（未接同步），放复合索引为 Phase 3 零迁移接入。

---

## 6. DAO 与核心查询

DAO 命名 `*Dao`，全部 `@Singleton @Inject`。**所有查询都必须带 `ledgerId` 过滤**；列表/汇总返回 `Flow`（或 `Flow<List<...>>`），CRUD 用 `suspend`。

### 6.1 TransactionDao

```kotlin
@Dao
interface TransactionDao {
    @Insert suspend fun insert(tx: TransactionEntity)
    @Query("UPDATE transactions SET type=:type, amount=:amount, categoryId=:categoryId, description=:description, date=:date, updatedAt=:updatedAt WHERE id=:id AND ledgerId=:ledgerId")
    suspend fun update(...)

    @Query("DELETE FROM transactions WHERE id=:id AND ledgerId=:ledgerId")
    suspend fun deleteById(id: String, ledgerId: String)

    // 账本内按日期分组（首页/列表）
    @Query("SELECT * FROM transactions WHERE ledgerId=:ledgerId ORDER BY date DESC, createdAt DESC")
    fun observeAll(ledgerId: String): Flow<List<TransactionEntity>>

    // 筛选：全部/支出/收入 + （Phase2 搜索复用同一参数化查询）
    @Query("""
        SELECT * FROM transactions
        WHERE ledgerId=:ledgerId
          AND (type = :type OR :type IS NULL)
          AND (categoryId = :categoryId OR :categoryId IS NULL)
          AND (date >= COALESCE(:dateFrom, date))
          AND (date <= COALESCE(:dateTo, date))
        ORDER BY date DESC, createdAt DESC
    """)
    fun observeFiltered(ledgerId: String, type: String?, categoryId: String?, dateFrom: String?, dateTo: String?): Flow<List<TransactionEntity>>

    // 月结余：DP-1 首页「本月结余/收支合计」
    @Query("""
        SELECT
          COALESCE(SUM(CASE WHEN type='expense' THEN amount ELSE 0 END), 0) AS expenseFen,
          COALESCE(SUM(CASE WHEN type='income'  THEN amount ELSE 0 END), 0) AS incomeFen
        FROM transactions
        WHERE ledgerId=:ledgerId AND date BETWEEN :monthStart AND :monthEnd
    """)
    suspend fun monthSummary(ledgerId: String, monthStart: String, monthEnd: String): SummaryRow

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId=:categoryId AND ledgerId=:ledgerId")
    suspend fun countByCategory(categoryId: String, ledgerId: String): Int   // 分类删除前检查
}
```

### 6.2 DebtDao

```kotlin
@Dao
interface DebtDao {
    @Insert suspend fun insert(debt: DebtEntity)
    @Query("UPDATE debts SET partyName=:partyName, amount=:amount, kind=:kind, direction=:direction, date=:date, remark=:remark, updatedAt=:updatedAt WHERE id=:id AND ledgerId=:ledgerId")
    suspend fun update(...)
    @Query("DELETE FROM debts WHERE id=:id AND ledgerId=:ledgerId")
    suspend fun deleteById(id: String, ledgerId: String)

    // 明细视图：账本内按时间排序
    @Query("SELECT * FROM debts WHERE ledgerId=:ledgerId ORDER BY date DESC, createdAt DESC")
    fun observeAll(ledgerId: String): Flow<List<DebtEntity>>

    // 按对象视图：净额汇总（group by partyName，含借出/借入/还款四态符号）
    @Query("""
        SELECT partyName,
               COALESCE(SUM(CASE
                 WHEN (kind='lend'  AND direction='owe_me') THEN  amount
                 WHEN (kind='lend'  AND direction='i_owe')  THEN -amount
                 WHEN (kind='repay' AND direction='owe_me') THEN -amount
                 WHEN (kind='repay' AND direction='i_owe')  THEN  amount
                 ELSE 0 END), 0) AS netFen
        FROM debts
        WHERE ledgerId=:ledgerId
        GROUP BY partyName
        ORDER BY netFen DESC
    """)
    fun observeNetByParty(ledgerId: String): Flow<List<NetByPartyRow>>

    @Query("SELECT * FROM debts WHERE ledgerId=:ledgerId AND (:kind IS NULL OR kind=:kind) AND (:direction IS NULL OR direction=:direction) ORDER BY date DESC")
    fun observeFiltered(ledgerId: String, kind: String?, direction: String?): Flow<List<DebtEntity>>
}
```

> `netFen` 计算即「借出+、借入−、收到还款−、我还款+」，与 PRD F-02 口径逐项对应；字段长整型累加不溢出。

### 6.3 CategoryDao

```kotlin
@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertList(categories: List<CategoryEntity>)   // 种子/导入
    @Insert suspend fun insert(category: CategoryEntity)
    @Query("UPDATE categories SET name=:name, icon=:icon, color=:color, order=:order, updatedAt=:updatedAt WHERE id=:id AND ledgerId=:ledgerId")
    suspend fun update(...)
    @Query("DELETE FROM categories WHERE id=:id AND ledgerId=:ledgerId AND isBuiltIn=0")
    suspend fun deleteCustom(id: String, ledgerId: String)              // 防误删内置

    @Query("SELECT * FROM categories WHERE ledgerId=:ledgerId AND type=:type ORDER BY `order` ASC, name ASC")
    fun observeByType(ledgerId: String, type: String): Flow<List<CategoryEntity>>
    @Query("SELECT COUNT(*) FROM categories WHERE ledgerId=:ledgerId")
    suspend fun countByLedger(ledgerId: String): Long
    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId=:categoryId")
    suspend fun countRef(in categoryId: String): Int                    // 删除前关联检查
}
```

### 6.4 LedgerDao

```kotlin
@Dao
interface LedgerDao {
    @Insert suspend fun insert(ledger: LedgerEntity)
    @Query("SELECT * FROM ledgers ORDER BY position ASC, createdAt ASC")
    fun observeAll(): Flow<List<LedgerEntity>>
    @Query("SELECT * FROM ledgers WHERE isDefault=1 LIMIT 1")
    suspend fun getDefault(): LedgerEntity?
    @Query("SELECT * FROM ledgers WHERE id=:id")
    suspend fun getById(id: String): LedgerEntity?
    @Query("SELECT COUNT(*) FROM ledgers")
    suspend fun count(): Long
}
```

### 6.5 BudgetDao / SyncChangeDao（本期建表，DAO 骨架）

```kotlin
@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(budget: BudgetEntity)
    @Query("SELECT * FROM budgets WHERE ledgerId=:ledgerId AND month=:month")
    fun observeByMonth(ledgerId: String, month: String): Flow<List<BudgetEntity>>
    @Query("DELETE FROM budgets WHERE ledgerId=:ledgerId AND categoryId IS NULL AND month=:month")
    suspend fun deleteTotal(ledgerId: String, month: String)   // 先删后插保障 D-7
}

@Dao
interface SyncChangeDao {
    @Insert suspend fun insert(change: SyncChangeEntity)
    @Query("SELECT * FROM sync_changes WHERE ledgerId=:ledgerId ORDER BY timestamp ASC")
    suspend fun pendingChanges(ledgerId: String): List<SyncChangeEntity>
    @Query("DELETE FROM sync_changes WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}
```

### 6.6 常用查询 → 索引命中对照

| 查询场景 | 命中索引 |
|----------|----------|
| 首页本月结余（BETWEEN 范围） | `(ledgerId, date)` |
| 列表按日期 DESC | `(ledgerId, date)` |
| 按类型筛选 | `(ledgerId, type)` |
| 按分类筛选 / 删除前计数 | `(ledgerId, categoryId)`、`(ledgerId, type)` |
| 债务按对象净额（GROUP BY partyName） | `(ledgerId, partyName)` |
| 债务按 kind / direction 筛选 | `(ledgerId, kind)`、`(ledgerId, direction)` |
| 找默认账本 | `(isDefault)` |
| 账本列表排序 | `(position)` |
| 预算按月取数 | `(ledgerId, month)` |
| 未同步变更（Phase 3） | `(ledgerId, syncStatus)` / `(ledgerId, timestamp)` |

---

## 7. 数据库版本号与迁移策略

### 7.1 版本基线

- **版本 1** 即含全部 6 张表、多账本字段、`syncStatus`、`ledgers`、`budgets`（Phase 2 预留）、`sync_changes`（Phase 3 预留）。
- 不存在「先无账本后加账本」「先明文后加密」的升级路径：**从 1 起就是加密+多账本设计**。
- `exportSchema = true`：Room 把 schema 以 JSON 签入 VCS（`app/schemas/`），配套 `MigrationTestHelper` 写迁移测试。

### 7.2 加密与迁移的关系

SQLCipher 在版本升级时使用 `PRAGMA cipher_version` / `PRAGMA key` 在同一加密上下文内执行 `Room` 的 `Migration`，**不改密、不显明文**。任何版本只在 SQLCipher 打开成功后执行 schema 迁移。

### 7.3 迁移策略

| 情况 | 策略 |
|------|------|
| 纯粹加表（新实体，如 Phase 2 未建现已建的例外） | `RoomDatabase.Callback` + `@Database` 内递增版本：**首选 `AutoMigration`（`@RenameColumn/@DeleteColumn` 注解）**；复杂变更写显式 `Migration(1→2)` |
| 加列（如给 categories 补 syncStatus） | `ALTER TABLE ... ADD COLUMN` + 显式 Migration，列允许 NULL 或带 default，避免瞬时重写 |
| 改语义（如枚举扩展） | 仅加新成员值，不破坏写前缀；若需数据清洗则 Migration 内 `UPDATE` 语句迁移测试覆盖 |
| schema 在新版本脚本自动迁移 | 每个 migration 使用 SQLCipher 同一 helper，配置 `room.schemaLocation`；`fallbackToDestructiveMigration(false)`（**显式禁止**） |
| 测试 | 每个 `MIGRATION_1_2` 配 `MigrationTestHelper` 单测：旧库可打开、新查询可跑 |

### 7.4 已知 Phase 3 可能的迁移

- `categories` 增加 `syncStatus` 列（如分类纳入增量同步）。
- `transactions` / `debts` 若引入软删除，加 `deletedAt` 列并调整 `syncStatus` 语义。
- 统一走 §7.3 的显式 Migration + 测试，不破坏已加密库。

---

## 8. 内置分类种子数据

首次打开每个账本时克隆内置集（`BootstrapUseCase` / 新账本创建流程，`02_architecture.md §8.4`）。`order` 为展示顺序，「其他」固定 99 恒末尾；`isBuiltIn=true`。

### 8.1 内置支出分类（12）

| order | icon | name | color | 说明 |
|:-----:|------|------|-------|------|
| 1 | 🍜 | 餐饮 | `#FF6B6B` | 日常餐饮消费 |
| 2 | 🚗 | 交通 | `#4ECDC4` | 公交、地铁、打车等 |
| 3 | 🛒 | 购物 | `#45B7D1` | 日用百货、杂货 |
| 4 | 🎬 | 娱乐 | `#96CEB4` | 电影、游戏等 |
| 5 | 🏠 | 住房 | `#FFEAA7` | 房租、水电物业 |
| 6 | 💊 | 医疗 | `#DDA0DD` | 门诊、药品 |
| 7 | 📚 | 教育 | `#98D8C8` | 学习、培训 |
| 8 | 👔 | 服饰 | `#F7DC6F` | 服装鞋帽 |
| 9 | 📱 | 通讯 | `#BB8FCE` | 话费、网费 |
| 10 | 🎁 | 人情 | `#F0B27A` | 礼金、请客 |
| 11 | ✈️ | 旅行 | `#85C1E9` | 出行旅游 |
| 12 | 🔧 | 其他 | `#AEB6BF` | 其他支出兜底（order=99） |

### 8.2 内置收入分类（6）

| order | icon | name | color | 说明 |
|:-----:|------|------|-------|------|
| 1 | 💰 | 工资 | `#2ECC71` | 薪资收入 |
| 2 | 🎯 | 奖金 | `#F39C12` | 奖金、提成 |
| 3 | 📈 | 投资 | `#3498DB` | 理财收益 |
| 4 | 💼 | 兼职 | `#9B59B6` | 副业、兼职收入 |
| 5 | 🧧 | 礼金 | `#E74C3C` | 红包、礼金 |
| 6 | 💵 | 其他 | `#1ABC9C` | 其他收入兜底（order=99） |

### 8.3 种子数据规则

- 种子 id 固定生成（UUID），但**不是全局常量**：因为每个账本 clone 一份，不同账本的同名内置分类 id 不同。判断「是否内置」一律看 `isBuiltIn`。
- 内置分类**允许改 `icon`/`color`/`name`**，**不可删、不可改 `type` 与 `isBuiltIn`**（D-6 最终口径）；「其他」兜底分类**保持默认名**。
- 「其他」类 order 恒 99，保证始终兜底在末尾；用户自定义分类 order 从 1~98 范围分配，避免与 99 冲突。
- 种子写入幂等：`countByLedger(ledgerId)==0` 才 clone（重装/恢复 KeyStore 后不重复刷）。

---

## 9. 金额分处理规范与转换约定

### 9.1 存储与展示

```
用户输入   "12.34"（元，字符串）
   │  util/MoneyFormatter.toFen()
   ▼
存储       Long 1234（分，INTEGER）
   │  util/MoneyFormatter.toYuan()
   ▼
展示       "¥12.34"（本地化格式，NumberFormat 固定 2 位小数）
```

- 存储一律 `Long`；统计一律 DB 端整数 SUM/累加，杜绝中间态浮点。
- UI 金额输入组件与格式化集中在 `util/MoneyFormatter.kt`，禁止在业务代码手写 `* 100` 或 `/ 100f`。

### 9.2 元 → 分（存储）

| 方法 | 原则 | 示例 |
|------|------|------|
| 推荐 | `BigDecimal(inputWithScale2).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()` | `"12.345" → 1235`（半舍） |
| 禁止 | `Double * 100` | 大数/小数字面量有浮点误差 |

- 输入限定最多 2 位小数（输入法/校验层拒绝第 3 位）；对超长整数使用 `longValueExact()` 捕获溢出并报错。

### 9.3 分 → 元（展示）

- `NumberFormat`/`String.format("%.2f", fen / 100.0)` 因除法引入浮点，仅用于渲染；正确做法：

```
fun formatFen(fen: Long, withSymbol: Boolean = true): String {
    val yuanSign = fen / 100          // 整数部分
    val fenPart  = (fen % 100).toInt().coerceIn(0, 99)
    return "${if (withSymbol) "¥" else ""}${yuanSign}.%02d".format(fenPart)
}
```

- 千分位可选用 `DecimalFormat` 的 `15.00` 分组，仍走整数拆分，不经过 Float/Double。

### 9.4 边界与校验（统一 `util/Validators.kt`）

| 场景 | 规则 |
|------|------|
| 金额为正 | `fen > 0`，否则拒绝保存（PRD F-01/F-02 异常处理） |
| 最大金额 | 建议上限 `99_999_999_99` 分（≈ 9.99 亿），Long 远未溢出；超限提示 |
| 金额为空 | 表单必填 |
| 两可 | `0` 与负数一律拒绝（不存零金额行） |
| 小数位数 | 输入限 2 位小数，输入层即拦截 |

### 9.5 时间与日期的转换约定

| 字段 | 存储格式 | 备注 |
|------|---------|------|
| `date`（发生日期） | `yyyy-MM-dd` | 本地日；字符串字典序=时间序 |
| `createdAt/updatedAt/timestamp` | `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`（UTC） | 审计用UTC，展示本地化 |
| `month`（预算） | `yyyy-MM` | 与 `substr(date,1,7)` 约定一致 |
| legacy 导入 | 时间戳/日期归一化为 `yyyy-MM-dd` | 见 `02_architecture.md §10.2` |

生成时间统一用 `DateTimeFormatter`（所依赖的 `java.time.*` 在 minSdk 26 已可用），不重复造轮子。

### 9.6 TypeConverter

`enums ↔ String`、`Boolean ↔ Int`、`LocalDate?`（如需要）全部经 Room `TypeConverter` 转码；`amount` 直接 `Long`，**不做**浮点类型。
`SummaryRow` / `NetByPartyRow` 为 DAO 查询 DTO（data class，字段名与 SQL 别名对齐）。

---

## 10. Phase 2 数据设计：搜索过滤 / 报表聚合 / 预算口径

> 依据：DEC-011 / DEC-012 / DEC-013 / DEC-008；配合 `02_architecture.md §15`。

### 10.1 结论：无新表 / 无新索引

- **不新增表**：Phase 2 所需预算表 `budgets` 已随 version 1 建表（§4.5，6 表之一）；搜索/报表/预算均不引入新实体。
- **不新增索引**：搜索/报表/预算全部复用既有复合索引——`(ledgerId, date)`、`(ledgerId, categoryId)`、`(ledgerId, partyName)`、`(ledgerId, month)`（§5.1）。
- **只扩 DAO SQL**：改动集中在 `TransactionDao.observeFiltered` 加参（搜索）、`DebtDao.observeFiltered` 加参（搜索）、`TransactionDao` 新增三条聚合查询（报表 + 预算「已用」）。
- 数据库版本维持 **1**，无迁移（§7.1：version 1 即含全部 6 表，`fallbackToDestructiveMigration(false)` 不变）。

### 10.2 搜索过滤 SQL（DEC-013 扩展 observeFiltered）

**transactions**（`TransactionDao.observeFiltered` 由 5 参扩为 8 参）：

```sql
SELECT * FROM transactions
WHERE ledgerId=:ledgerId
  AND (type = :type OR :type IS NULL)
  AND (categoryId = :categoryId OR :categoryId IS NULL)
  AND (date >= COALESCE(:dateFrom, date))
  AND (date <= COALESCE(:dateTo, date))
  AND (:remark IS NULL OR description LIKE '%' || :remark || '%')
  AND (:amountMin IS NULL OR amount >= :amountMin)
  AND (:amountMax IS NULL OR amount <= :amountMax)
ORDER BY date DESC, createdAt DESC
```

**debts**（`DebtDao.observeFiltered` 由 3 参扩为 4 参）：

```sql
SELECT * FROM debts
WHERE ledgerId=:ledgerId
  AND (:kind IS NULL OR kind=:kind)
  AND (:direction IS NULL OR direction=:direction)
  AND (:partyName IS NULL OR partyName LIKE '%' || :partyName || '%')
ORDER BY date DESC
```

> 备注/姓名模糊为 `LIKE '%kw%'`（前置通配），无法走 B-tree 索引；但查询先按 `ledgerId` 首滤 + 范围谓词收窄，千条级规模下余集扫描可接受，**不需为「模糊」与「金额区间」单独建索引**（§5.2 数据量判断）。金额区间 `amountMin/amountMax` 单位「分」、含端点；`min > max` 由 ViewModel/UseCase 层拒绝或视为无效（PRD F-06 异常处理）。

### 10.3 报表聚合 SQL（F-07）

**收支趋势 / 结余走势（按桶）**——DTO `PeriodTrend(bucket, expenseFen, incomeFen)`：

```sql
-- 月度视图：按天（GROUP BY date）
SELECT date AS bucket,
       COALESCE(SUM(CASE WHEN type='expense' THEN amount ELSE 0 END), 0) AS expenseFen,
       COALESCE(SUM(CASE WHEN type='income'  THEN amount ELSE 0 END), 0) AS incomeFen
FROM transactions
WHERE ledgerId=:ledgerId AND date BETWEEN :start AND :end
GROUP BY date
ORDER BY bucket ASC

-- 年度视图：按月（GROUP BY substr(date,1,7)，与 month 'yyyy-MM' 口径一致，§9.5）
SELECT substr(date,1,7) AS bucket,
       COALESCE(SUM(CASE WHEN type='expense' THEN amount ELSE 0 END), 0) AS expenseFen,
       COALESCE(SUM(CASE WHEN type='income'  THEN amount ELSE 0 END), 0) AS incomeFen
FROM transactions
WHERE ledgerId=:ledgerId AND date BETWEEN :start AND :end
GROUP BY substr(date,1,7)
ORDER BY bucket ASC
```

> 结余走势不另写 SQL：每桶 `balanceFen = incomeFen - expenseFen`，累计结余做 domain 层整数跑表（桶数 ≤ 366/年、31/月，非全量内存聚合，§13 可测试性成立）。

**分类占比（收入/支出分开）**——DTO `CategoryAgg(categoryId, totalFen)`：

```sql
SELECT categoryId,
       COALESCE(SUM(amount), 0) AS totalFen
FROM transactions
WHERE ledgerId=:ledgerId AND type=:type AND date BETWEEN :start AND :end
GROUP BY categoryId
ORDER BY totalFen DESC
```

> `type` 参数区分收入/支出（占比分开展示，PRD F-07）；分类名/图标/色由 UI 经既有分类流做 id→元信息映射，**不在 SQL 内 JOIN**（与首页分类联表同径，避免跨表聚合复杂度）。

### 10.4 预算「已用 / 剩余 / 超支」口径 SQL（F-08 + DEC-008）

- **已用（按分类）**：复用 §10.3 的 `observeCategoryAgg(type='expense', start=当月首日, end=当月末日)`：

```sql
SELECT categoryId, COALESCE(SUM(amount), 0) AS totalFen
FROM transactions
WHERE ledgerId=:ledgerId AND type='expense' AND date BETWEEN :start AND :end
GROUP BY categoryId
```

- **已用（总额）**：`= COALESCE(SUM(各分类 totalFen), 0)` —— 等价复用 §6.1 `monthSummary.expenseFen`（因 `categoryId` 列 `NOT NULL`，每笔支出必属某分类，分组求和 == 全量求和）。
- **预算额度**：`BudgetDao.observeByMonth(ledgerId, month)` 取 `budgets` 行（总预算 `categoryId=NULL` + 分类预算），命中 `(ledgerId, month)` 索引。
- **逐行口径（domain 层算，非 SQL）**：
  - `剩余 = budget.amount − spentFen`
  - `超支 = spentFen > budget.amount`（即 `剩余 < 0`）
- 总额口径与 DEC-008 完全一致：总额唯一靠 Repository「先删后插」（`deleteTotal` + `upsert` 事务内成对），分类唯一靠 `UNIQUE(ledgerId, month, categoryId)`；「已用」只读 `transactions`，预算写操作不触碰交易表。

### 10.5 索引命中对照（Phase 2 增量）

| 场景 | 命中索引 |
|------|----------|
| 搜索 transactions（remark/金额区间/分类/日期/类型组合） | `(ledgerId, date)` / `(ledgerId, categoryId)` / `(ledgerId, type)`；remark、amount 为非索引谓词（首滤 ledgerId 后扫描可接受） |
| 搜索 debts（partyName 模糊 + kind/direction） | 前置通配 `partyName` LIKE 不用索引；其余走 `(ledgerId, kind)` / `(ledgerId, direction)` |
| 报表趋势（按天/按月 GROUP BY date/substr） | `(ledgerId, date)` 范围扫描 |
| 分类占比（GROUP BY categoryId） | `(ledgerId, date)`（范围）+ 结果分组计数走 `(ledgerId, categoryId)` |
| 预算已用（GROUP BY categoryId 当月支出） | `(ledgerId, date)`（范围）+ `(ledgerId, categoryId)` |
| 预算额度（按 ledgerId+month） | `(ledgerId, month)` |

---

（本文件为设计文档，随实现对细节的差分由后续修订更新；版本历史由协调者统一管理。）