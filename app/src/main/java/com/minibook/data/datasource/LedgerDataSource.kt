package com.minibook.data.datasource

import com.minibook.domain.model.Ledger
import kotlinx.coroutines.flow.Flow

/**
 * 账本（PRD F-11）数据源契约，逐条对齐 API §6。
 *
 * 纯接口、无实现；本期唯一实现为 RoomDataSourceImpl（TASK-008）。
 *
 * 注意：「当前账本」由 `LedgerDataStore` 提供，不属本数据源；切换为纯状态
 * 变更（ARCHITECTURE §8.3）。账本无 `ledgerId` 维度，查询天然全库。
 */
interface LedgerDataSource {

    /**
     * 新增账本；新账本创建流程在事务内联动克隆内置分类
     * （ARCHITECTURE §8.4，TASK-012 CreateLedgerUseCase 编排）。
     *
     * @return 生成的 UUID。
     */
    suspend fun insert(ledger: Ledger): String

    /** 更新后回读并返回最新值。 */
    suspend fun update(ledger: Ledger): Ledger

    /** 按 [id] 删除账本行（删账本时最后一步，DATABASE §4.4）。 */
    suspend fun deleteById(id: String)

    /** 单账本，不存在返回 `null`。 */
    suspend fun getById(id: String): Ledger?

    /** 取默认账本（`isDefault=1`），不存在返回 `null`（首启动引导）。 */
    suspend fun getDefault(): Ledger?

    /** 全部账本，按 `position` 排序。 */
    fun observeAll(): Flow<List<Ledger>>
}
