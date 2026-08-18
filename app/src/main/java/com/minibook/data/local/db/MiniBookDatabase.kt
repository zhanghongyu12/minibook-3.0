package com.minibook.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.minibook.data.local.db.dao.BudgetDao
import com.minibook.data.local.db.dao.CategoryDao
import com.minibook.data.local.db.dao.DebtDao
import com.minibook.data.local.db.dao.LedgerDao
import com.minibook.data.local.db.dao.SyncChangeDao
import com.minibook.data.local.db.dao.TransactionDao
import com.minibook.data.local.db.entity.BudgetEntity
import com.minibook.data.local.db.entity.CategoryEntity
import com.minibook.data.local.db.entity.DebtEntity
import com.minibook.data.local.db.entity.LedgerEntity
import com.minibook.data.local.db.entity.SyncChangeEntity
import com.minibook.data.local.db.entity.TransactionEntity

/**
 * MiniBook 房间数据库（version=1），对齐 DATABASE §2/§4/§7。
 *
 * - version = 1：本期即含全部 6 表（ledgers/categories/transactions/debts/budgets/sync_changes），
 *   不设「先无后加」的破坏迁移路径（§7.1）。
 * - exportSchema = true：schema JSON 导出至 `app/schemas/` 入 VCS，供
 *   `MigrationTestHelper` 写迁移测试（TASK-001 ksp arg `room.schemaLocation`）。
 * - 注册 [Converter]（Boolean↔Int，§9.6）；枚举/日期以 String 列存，无需额外转码。
 *
 * **迁移策略（§7.3，显式禁止破坏迁移）**：本库构建时必须搭配
 * `.fallbackToDestructiveMigration(false)`，即任何版本升级都不允许整库重建丢弃数据。
 * 该 builder 调用在 `di/DatabaseModule`（TASK-004，SQLCipher 接线）落地；
 * 此处以文档口径声明，确保实现不越权关闭该保护。
 */
@Database(
    entities = [
        TransactionEntity::class,
        DebtEntity::class,
        CategoryEntity::class,
        LedgerEntity::class,
        BudgetEntity::class,
        SyncChangeEntity::class,
    ],
    version = 1,
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
}