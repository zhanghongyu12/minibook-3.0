package com.minibook.data.datasource

import com.minibook.domain.model.SyncChange

/**
 * 云同步变更簿记数据源契约，逐条对齐 API §8 + DATABASE §6.5。
 *
 * **Phase 3 预留**：本期只建表不写数据，契约落盘保证 Phase 3 零迁移接入。
 *
 * 签名以 DATABASE §6.5 `SyncChangeDao` 为准（`pendingChanges` 带 `ledgerId`
 * 维度），比 ARCHITECTURE §6.1 初稿更精确（API §8 注）。
 */
interface SyncChangeDataSource {

    /**
     * 记录一次变更；`entityType` ∈ transaction/debt/category，
     * `action` ∈ create/update/delete，`data` 为变更载荷 JSON（delete 可为空）。
     */
    suspend fun enqueue(entityType: String, entityId: String, action: String, data: String?)

    /** 取未同步变更（按 `timestamp` 升序），供云端推送。 */
    suspend fun pendingChanges(ledgerId: String): List<SyncChange>

    /** 推送成功后批量标记已同步。 */
    suspend fun markSynced(ids: List<String>)
}
