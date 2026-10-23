package com.minibook.data.local.security

import com.minibook.data.local.db.MiniBookDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 数据库提供者接口（可测性改造，TASK-009/015 testability）。
 *
 * 从 [DatabaseGate] 加密「解密门」提炼出的最小视图：只暴露当前已打开（已解锁）的
 * [MiniBookDatabase]；未解锁（未 [DatabaseGate.open]）时为 null，不提供任何可用连接。
 *
 * 存在目的：解除 [RoomTransactionRunner] 与 [RoomDataSourceImpl] 对具体类 [DatabaseGate]
 * 的构造依赖，使 androidTest 能以内存库替身取代真实 SQLCipher/KeyManager 加密门，
 * 从而可测事务原子性（无需 hilt-testing、无设备锁/生物识别环境）。
 *
 * 加密语义不变：本接口不引入任何新行为，[DatabaseGate] 的
 * open/close/KeyManager/SQLCipher/锁状态逻辑原样保留，仅额外实现本接口。
 */
interface DatabaseProvider {
    /** 当前打开的 [MiniBookDatabase]；未解锁（未 open）时为 null，调用方不得强解包。 */
    val database: MiniBookDatabase?

    /**
     * 「已打开数据库」流（F-16i，2026-10-13）：未解锁/重锁后不发射 `null` 之外的值，
     * 解锁后发射当前实例、重锁后回到 `null`。给**返回 Flow 的数据源方法**用：
     * 这类方法在解密门关闭期间必须「不发射」，而不是同步抛错（真机崩溃根因见
     * [com.minibook.data.local.datasource.gatedFlow]）。
     *
     * 默认实现只发当前快照（够测试替身用）；[DatabaseGate] 覆写为随 open/close 变化的 StateFlow。
     */
    val openState: Flow<MiniBookDatabase?>
        get() = flowOf(database)
}