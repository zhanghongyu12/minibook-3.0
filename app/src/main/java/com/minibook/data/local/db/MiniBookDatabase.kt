package com.minibook.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.minibook.data.local.db.dao.BudgetDao
import com.minibook.data.local.db.dao.CategoryDao
import com.minibook.data.local.db.dao.DebtDao
import com.minibook.data.local.db.dao.LedgerDao
import com.minibook.data.local.db.dao.SyncChangeDao
import com.minibook.data.local.db.dao.SyncConflictDao
import com.minibook.data.local.db.dao.SyncDao
import com.minibook.data.local.db.dao.SyncStateDao
import com.minibook.data.local.db.dao.TransactionDao
import com.minibook.data.local.db.dao.UserCategoryRuleDao
import com.minibook.data.local.db.entity.BudgetEntity
import com.minibook.data.local.db.entity.CategoryEntity
import com.minibook.data.local.db.entity.DebtEntity
import com.minibook.data.local.db.entity.LedgerEntity
import com.minibook.data.local.db.entity.SyncChangeEntity
import com.minibook.data.local.db.entity.SyncConflictEntity
import com.minibook.data.local.db.entity.SyncStateEntity
import com.minibook.data.local.db.entity.TransactionEntity
import com.minibook.data.local.db.entity.UserCategoryRuleEntity

/**
 * MiniBook 房间数据库（version=5），对齐 DATABASE §2/§4/§7。
 *
 * - version = 5：账单转债务关联（DEC-056）——`transactions` 加可空列 `debtId`，显式
 *   [MIGRATION_4_5] 落地；统计口径改由聚合 SQL 的 `debtId IS NULL` 承担（见实体注释）。
 * - version = 4：F-15 云端同步（DEC-033）**只加两张新表**（`sync_state` / `sync_conflicts`），
 *   **既有 6 张业务表零改动**（含 `sync_changes`，本轮不接墓碑写入——墓碑改由
 *   `sync_state` 差分补发，见 DEC-035 与 `SyncStateEntity` 注释）。显式 [MIGRATION_3_4] 落地。
 * - version = 3：F-12 分类自学习新增 `user_category_rules` 表（含唯一索引
 *   `UNIQUE(ledgerId, source, keyword, categoryId)`），显式 [MIGRATION_2_3] 落地。
 * - version = 2：首个破坏性 schema 变更（F-12 账单导入，DEC-018）——`transactions` 加 4 列 +
 *   唯一索引 `UNIQUE(ledgerId, externalId)`（§7.5）。显式 [MIGRATION_1_2] 落地，
 *   `fallbackToDestructiveMigration(false)` 不变（§7.3 显式禁止破坏迁移）。
 * - exportSchema = true：schema JSON 导出至 `app/schemas/` 入 VCS（`4.json` 由编译
 *   重新生成），供 `MigrationTestHelper` 写迁移测试（TASK-001 ksp arg `room.schemaLocation`）。
 * - 注册 [Converter]（Boolean↔Int，§9.6）；枚举/日期以 String 列存，无需额外转码。
 *
 * **迁移策略（§7.3，显式禁止破坏迁移）**：本库构建时必须搭配
 * `.fallbackToDestructiveMigration(false)` 且 `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)`，
 * 即任何版本升级都不允许整库重建丢弃数据。该 builder 调用在
 * [com.minibook.data.local.security.DatabaseGate.open]（TASK-004，SQLCipher 接线）落地；
 * 此处声明迁移常量，确保实现不越权关闭该保护。
 */
@Database(
    entities = [
        TransactionEntity::class,
        DebtEntity::class,
        CategoryEntity::class,
        LedgerEntity::class,
        BudgetEntity::class,
        SyncChangeEntity::class,
        UserCategoryRuleEntity::class,
        SyncStateEntity::class,
        SyncConflictEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converter::class)
abstract class MiniBookDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    abstract fun debtDao(): DebtDao

    abstract fun categoryDao(): CategoryDao

    abstract fun ledgerDao(): LedgerDao

    abstract fun budgetDao(): BudgetDao

    abstract fun syncChangeDao(): SyncChangeDao

    abstract fun userCategoryRuleDao(): UserCategoryRuleDao

    abstract fun syncStateDao(): SyncStateDao

    abstract fun syncConflictDao(): SyncConflictDao

    abstract fun syncDao(): SyncDao

    companion object {
        /**
         * 迁移 1 → 2（F-12 transactions 加列 + 唯一索引，DATABASE §7.5 / DEC-018）。
         *
         * 顺序：先 4 条 `ADD COLUMN`，再 `CREATE UNIQUE INDEX`，不可颠倒。
         * - `ADD COLUMN ... NOT NULL DEFAULT 'manual'`：SQLite 允许对既有行加 NOT NULL 列，
         *   前提是提供非空默认（`'manual'` 满足）；既有手动记录迁移后 `source='manual'`、
         *   其余三列 NULL，语义正确。
         * - 唯一索引名 `index_transactions_ledgerId_externalId` 与 Entity 侧
         *   `@Index(..., name=...)` **逐字一致**，否则 Room 校验报「索引名失配」。
         * - NULL 互异（幂等关键）：UNIQUE 索引把 NULL 视为互异 ⇒ 手动记录（externalId=NULL）
         *   不参与冲突，无需部分索引（§7.5）。
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN externalId TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN counterparty TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN paymentMethod TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN source TEXT NOT NULL DEFAULT 'manual'")
                db.execSQL(
                    "CREATE UNIQUE INDEX index_transactions_ledgerId_externalId " +
                        "ON transactions(ledgerId, externalId)",
                )
            }
        }

        /**
         * 迁移 2 → 3（F-12 分类自学习，新增 user_category_rules 表）。
         *
         * 顺序：先 `CREATE TABLE`，再 `CREATE UNIQUE INDEX`（唯一索引名与 Entity 侧
         * `@Index(name=...)` **逐字一致** `index_user_category_rules_ledgerId_source_keyword_categoryId`，
         * 否则 Room 校验报「索引名失配」）。
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS user_category_rules (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "ledgerId TEXT NOT NULL, " +
                        "source TEXT NOT NULL, " +
                        "keyword TEXT NOT NULL, " +
                        "categoryId TEXT NOT NULL, " +
                        "createdAt TEXT NOT NULL, " +
                        "updatedAt TEXT NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_user_category_rules_ledgerId_source_keyword_categoryId " +
                        "ON user_category_rules(ledgerId, source, keyword, categoryId)",
                )
            }
        }

        /**
         * 迁移 3 → 4（F-15 云端同步，只加两张新表，`docs/14_cloud_sync.md §5.3`）。
         *
         * **既有 6 张业务表零改动**（含 `sync_changes` —— 本轮墓碑改由 `sync_state` 差分补发，
         * 见 DEC-035），因此本迁移只有 `CREATE TABLE` + `CREATE INDEX`，无 `ALTER`。
         *
         * DDL 必须与 Room 从 `SyncStateEntity`/`SyncConflictEntity` 期望的 schema **结构逐项一致**
         * （列名/类型/NOT NULL/主键/索引名），否则 `MigrationTestHelper.runMigrationsAndValidate`
         * 与真机升级都会在校验阶段失败：
         * - `sync_state`：**复合主键 `(entityType, entityId)`**，`serverSeq` 是 `INTEGER NOT NULL`
         *   （Kotlin `Long = 0L` 有默认值，但**列本身非空**）；
         * - `sync_conflicts`：`id` 单列主键，`resolvedAt`/`resolution` 可空（二期人工处理预留）；
         * - 索引名与 Entity 侧 `@Index` 自动生成名逐字一致：`index_sync_state_ledgerId`、
         *   `index_sync_conflicts_entityType_entityId`、`index_sync_conflicts_ledgerId`。
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_state (" +
                        "entityType TEXT NOT NULL, " +
                        "entityId TEXT NOT NULL, " +
                        "ledgerId TEXT NOT NULL, " +
                        "baseUpdatedAt TEXT NOT NULL, " +
                        "syncedAt TEXT NOT NULL, " +
                        "serverSeq INTEGER NOT NULL, " +
                        "PRIMARY KEY(entityType, entityId))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sync_state_ledgerId ON sync_state(ledgerId)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_conflicts (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "entityType TEXT NOT NULL, " +
                        "entityId TEXT NOT NULL, " +
                        "ledgerId TEXT NOT NULL, " +
                        "localJson TEXT NOT NULL, " +
                        "remoteJson TEXT NOT NULL, " +
                        "detectedAt TEXT NOT NULL, " +
                        "resolvedAt TEXT, " +
                        "resolution TEXT)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sync_conflicts_entityType_entityId " +
                        "ON sync_conflicts(entityType, entityId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sync_conflicts_ledgerId " +
                        "ON sync_conflicts(ledgerId)",
                )
            }
        }

        /**
         * 迁移 4 → 5（账单转债务关联，DEC-056）。
         *
         * 只有一条 `ALTER TABLE ... ADD COLUMN`：`transactions.debtId TEXT`（**可空、无默认值**）。
         * - 可空 + 无默认值 ⇒ 存量行自动为 NULL（= 未转债务），无需回填；
         * - **不建索引**：关联查询只有两种——按 `id` 置位/清位（主键命中）与按 `debtId`
         *   反查债务来源（账本内债务数量级小，`(ledgerId, ...)` 范围扫描足够）；
         * - 统计口径「已转债务不进统计」落在聚合 SQL 的 `debtId IS NULL`，不是索引问题。
         *
         * 列类型 `TEXT` 与实体 `String?` 对齐（Room schema 校验按亲和类型比较）。
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN debtId TEXT")
            }
        }
    }
}