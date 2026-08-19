package com.minibook.domain.repository

import com.minibook.domain.model.Ledger
import kotlinx.coroutines.flow.Flow

/**
 * 账本（PRD F-11）领域聚合入口，对齐 ARCHITECTURE §4.1。
 *
 * UseCase 层唯一账本入口；实现 [LedgerRepositoryImpl] 委托 `data/datasource` 的
 * `LedgerDataSource`，自身不感知 Room/SQL/SQLCipher（ARCHITECTURE §4.1 / DEC-001）。
 *
 * 注意：「当前账本」由 `LedgerDataStore` 提供，不属本接口（切换为纯状态变更，
 * ARCHITECTURE §8.3）。账本无 `ledgerId` 维度，查询天然全库。
 */
interface LedgerRepository {

    /**
     * 新增账本；新账本创建流程在事务内联动克隆内置分类
     * （ARCHITECTURE §8.4，TASK-012 CreateLedgerUseCase 编排）。
     *
     * @return 生成的 UUID。
     */
    suspend fun insert(ledger: Ledger): String

    /** 更新后回读并返回最新值。 */
    suspend fun update(ledger: Ledger): Ledger

    /** 按 [id] 删除账本行（删账本时最后一步，TASK-012 DeleteLedgerUseCase 编排；DATABASE §4.4）。 */
    suspend fun deleteById(id: String)

    /** 单账本，不存在返回 `null`。 */
    suspend fun getById(id: String): Ledger?

    /** 取默认账本（`isDefault=1`），不存在返回 `null`（首启动引导，TASK-011）。 */
    suspend fun getDefault(): Ledger?

    /** 全部账本，按 `position` 排序（TASK-029 账本管理页）。 */
    fun observeAll(): Flow<List<Ledger>>
}
