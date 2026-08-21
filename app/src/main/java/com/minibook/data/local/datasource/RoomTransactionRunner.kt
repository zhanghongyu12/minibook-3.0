package com.minibook.data.local.datasource

import androidx.room.withTransaction
import com.minibook.data.datasource.TransactionRunner
import com.minibook.data.local.security.DatabaseProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TransactionRunner] 的 Room 实现（TASK-009 事务闭合方案）。
 *
 * 经 `androidx.room.withTransaction`（Room ktx 挂起事务，Room 2.8.4 已含 room-ktx）
 * 在 [DatabaseProvider] 当前打开的数据库上执行；`block` 内抛异常 → 整体回滚、异常上抛。
 *
 * 锁周期安全（ARCHITECTURE §7.5）：不缓存连接，每调用经 [gate.database] 取当前已打开库；
 * 未解锁（null）→ `error(...)` 硬失败，与 [RoomDataSourceImpl] 各 DAO 取用口径一致。
 *
 * SQLCipher 兼容性：`withTransaction` 是对 `SupportSQLiteDatabase` 事务包装的挂起封装，
 * 与 `SupportOpenHelperFactory` 无冲突（同 [RoomDataSourceImpl] 的 DAO 写路径）。
 */
@Singleton
class RoomTransactionRunner @Inject constructor(
    private val gate: DatabaseProvider,
) : TransactionRunner {

    override suspend fun <T> runInTransaction(block: suspend () -> T): T =
        gate.database?.withTransaction { block() }
            ?: error("DatabaseGate 未解锁/未打开")
}
