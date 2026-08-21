package com.minibook.di

import android.content.Context
import com.minibook.data.local.db.MiniBookDatabase
import com.minibook.data.local.db.dao.BudgetDao
import com.minibook.data.local.db.dao.CategoryDao
import com.minibook.data.local.db.dao.DebtDao
import com.minibook.data.local.db.dao.LedgerDao
import com.minibook.data.local.db.dao.SyncChangeDao
import com.minibook.data.local.db.dao.TransactionDao
import com.minibook.data.local.security.DatabaseGate
import com.minibook.data.local.security.DatabaseProvider
import com.minibook.data.local.security.KeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * SQLCipher Room 数据库装配（ARCHITECTURE §5.1/§7.1、TASK-004）。
 *
 * 循环依赖防护：**本模块不直接 @Provides passphrase**——passphrase 由
 * [DatabaseGate] 内部经 [KeyManager] 获取（构造注入），DAO/DB 一律经「解密门」暴露，
 * 未解锁时不产生裸 Room 实例（ARCHITECTURE §7.1「模块不直接产生裸实例」）。
 *
 * DAO 绑定说明：
 * - DAO 均**无 @Singleton 作用域**（默认 unscoped），每次注入都从
 *   `DatabaseGate.database` 当前实例取。这样在「解锁 → 打开 DB → 注入 DAO」与
 *   「重锁 → 关闭 DB → 再解锁 → 重建 DB」的完整锁周期中，DAO 始终绑定到当前
 *   已打开的数据库，不会缓存已关闭实例（ARCHITECTURE §7.5 重锁时序）。
 * - 若 DAO 被 @Singleton 消费者持有（如 TASK-008 RoomDataSourceImpl），由该层在
 *   锁周期内负责重新获取，本模块只保证「取到的永远是当前 DB 的 DAO」。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** 解密门单例：构建时不触碰 DB，直到 [DatabaseGate.open] 才真正打开 SQLCipher 库。 */
    @Provides
    @Singleton
    fun provideDatabaseGate(
        @ApplicationContext context: Context,
        keyManager: KeyManager,
    ): DatabaseGate = DatabaseGate(context, keyManager)

    /**
     * 解密门以 [DatabaseProvider] 接口对外暴露（可测性改造，TASK-009/015）。
     *
     * 本模块为 Kotlin `object`（仅承载 @Provides，无法放置 abstract @Binds），故以
     * @Provides 直接返回与 [provideDatabaseGate] 同一 @Singleton [DatabaseGate] 实例的
     * 接口视图。DatabaseGate 仍是唯一单例、open/close/锁状态/SQLCipher/KeyManager
     * 语义完全不变，仅多一个接口绑定供 [com.minibook.data.local.datasource.RoomTransactionRunner]
     * 与 [com.minibook.data.local.datasource.RoomDataSourceImpl] 构造注入。
     */
    @Provides
    @Singleton
    fun provideDatabaseProvider(gate: DatabaseGate): DatabaseProvider = gate

    @Provides
    fun provideTransactionDao(gate: DatabaseGate): TransactionDao =
        gate.database?.transactionDao()
            ?: error("DatabaseGate 未解锁/未打开，无法提供 TransactionDao")

    @Provides
    fun provideDebtDao(gate: DatabaseGate): DebtDao =
        gate.database?.debtDao()
            ?: error("DatabaseGate 未解锁/未打开，无法提供 DebtDao")

    @Provides
    fun provideCategoryDao(gate: DatabaseGate): CategoryDao =
        gate.database?.categoryDao()
            ?: error("DatabaseGate 未解锁/未打开，无法提供 CategoryDao")

    @Provides
    fun provideLedgerDao(gate: DatabaseGate): LedgerDao =
        gate.database?.ledgerDao()
            ?: error("DatabaseGate 未解锁/未打开，无法提供 LedgerDao")

    @Provides
    fun provideBudgetDao(gate: DatabaseGate): BudgetDao =
        gate.database?.budgetDao()
            ?: error("DatabaseGate 未解锁/未打开，无法提供 BudgetDao")

    @Provides
    fun provideSyncChangeDao(gate: DatabaseGate): SyncChangeDao =
        gate.database?.syncChangeDao()
            ?: error("DatabaseGate 未解锁/未打开，无法提供 SyncChangeDao")
}
