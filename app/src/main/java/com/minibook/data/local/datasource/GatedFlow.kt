package com.minibook.data.local.datasource

import com.minibook.data.local.db.MiniBookDatabase
import com.minibook.data.local.security.DatabaseProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * 门控 Flow 助手（F-16i，2026-10-13 真机崩溃修复）。
 *
 * ## 缺陷现象（真机 crash buffer 实录）
 * ```
 * FATAL EXCEPTION: main  Process: com.minibook
 * java.lang.IllegalStateException: DatabaseGate 未解锁/未打开，无法提供 LedgerDao
 *   at LedgerDataSourceAdapter.ledgerDao(RoomDataSource.kt:91)
 *   at LedgerDataSourceAdapter.observeAll(RoomDataSource.kt:88)
 *   at LedgerRepositoryImpl.observeAll(LedgerRepositoryImpl.kt:30)
 *   at ObserveLedgersUseCase.observe(ObserveLedgersUseCase.kt:19)
 *   at HomeViewModel$special$$inlined$flatMapLatest$1.invokeSuspend(Merge.kt:196)
 *   ...
 *   at androidx.lifecycle.compose.FlowExtKt$collectAsStateWithLifecycle$1$1$1
 * ```
 *
 * ## 根因（时序，非逻辑）
 * 1. 重锁路径 [com.minibook.data.local.security.AppLockController.lock] **先关库、后**翻 `isLocked`
 *    （`databaseGate.close()` → `_isLocked.value = true`）；
 * 2. UI 生命周期恢复（回前台/首帧）会让 `collectAsStateWithLifecycle` 立刻重启上游，
 *    而 `stateIn(WhileSubscribed)` 期间上游已被取消 ⇒ 此时**重新调用**
 *    `observeLedgers.observe()`；
 * 3. 旧实现里 `observeAll()` 在**调用点**就 `ledgerDao()`（`gate.database?.ledgerDao() ?: error(...)`），
 *    于是异常在 `flatMapLatest` 的协程里同步抛出，`Dispatchers.Main.immediate` 上无人捕获 ⇒ 主线程崩溃。
 *
 * 同类方法（交易/债务/分类/预算/账本等所有 `observe*`）都走同一 DAO 取用模式，只修一处只是把
 * 崩溃推到下一个 Flow，故在此做**统一门控**。
 *
 * ## 语义
 * - 解密门关闭：**不发射任何值**（`StateFlow` 保留旧值，不重置成空，避免闪一下空列表）、**不抛异常**；
 * - 解锁：切到新库的 DAO 流（重锁→再解锁后自动拿到新建 DB 的 DAO，绝不持有已关闭实例）；
 * - 重锁：上游随 `null` 切换而取消，回到「不发射」。
 *
 * 注意：**一次性 `suspend` 查询方法仍保持硬失败**（`?: error(...)`）——它们只应在解锁后被调用，
 * 静默返回空会把「没解锁」误报成「没有数据」（优先级：不记错 > 不崩）。
 */
/**
 * 门控：解密门关闭（上游发射 `null`）时**取消正在订阅旧库的流**并切到空流（不发射任何值），
 * 解锁（发射实例）时启动该库的 DAO 流。重锁→再解锁自动拿到新建 DB 的 DAO。
 *
 * 为什么不能 `filterNotNull().flatMapLatest(...)`：`null` 被过滤后到不了 `flatMapLatest`，
 * 于是「重锁」事件不会取消旧库流——旧流会继续挂在已关闭的 [MiniBookDatabase] 上，
 * 这正是 F-16i 回归测试抓到的点。必须把 `null` 换成空流，让上游每次变化都触发切换。
 */
internal fun <D : Any, T> Flow<D?>.gatedFlowBy(block: (D) -> Flow<T>): Flow<T> =
    flatMapLatest { value -> if (value == null) emptyFlow() else block(value) }

/** [DatabaseProvider.openState] 的门控包装：见 [gatedFlowBy]。 */
internal fun <T> DatabaseProvider.gatedFlow(block: (MiniBookDatabase) -> Flow<T>): Flow<T> =
    openState.gatedFlowBy(block)
