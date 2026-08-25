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
import com.minibook.data.local.db.dao.TransactionDao
import com.minibook.data.local.db.dao.UserCategoryRuleDao
import com.minibook.data.local.db.entity.BudgetEntity
import com.minibook.data.local.db.entity.CategoryEntity
import com.minibook.data.local.db.entity.DebtEntity
import com.minibook.data.local.db.entity.LedgerEntity
import com.minibook.data.local.db.entity.SyncChangeEntity
import com.minibook.data.local.db.entity.TransactionEntity
import com.minibook.data.local.db.entity.UserCategoryRuleEntity

/**
 * MiniBook 房间数据库（version=3），对齐 DATABASE §2/§4/§7。
 *
 * - version = 3：F-12 分类自学习新增 `user_category_rules` 表（含唯一索引
 *   `UNIQUE(ledgerId, source, keyword, categoryId)`），显式 [MIGRATION_2_3] 落地。
 * - version = 2：首个破坏性 schema 变更（F-12 账单导入，DEC-018）——`transactions` 加 4 列 +
 *   唯一索引 `UNIQUE(ledgerId, externalId)`（§7.5）。显式 [MIGRATION_1_2] 落地，
 *   `fallbackToDestructiveMigration(false)` 不变（§7.3 显式禁止破坏迁移）。
 * - exportSchema = true：schema JSON 导出至 `app/schemas/` 入 VCS（`3.json` 由编译
 *   重新生成），供 `MigrationTestHelper` 写迁移测试（TASK-001 ksp arg `room.schemaLocation`）。
 * - 注册 [Converter]（Boolean↔Int，§9.6）；枚举/日期以 String 列存，无需额外转码。
 *
 * **迁移策略（§7.3，显式禁止破坏迁移）**：本库构建时必须搭配
 * `.fallbackToDestructiveMigration(false)` 且 `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`，
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
    ],
    version = 3,
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
    }
}