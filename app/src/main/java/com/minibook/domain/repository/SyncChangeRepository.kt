package com.minibook.domain.repository

import com.minibook.domain.model.SyncChange

/**
 * 云同步变更簿记领域聚合入口。
 *
 * **Phase 2 预留接口：只定义契约，本期不实现不接线**（对应 `data/datasource` 的
 * `SyncChangeDataSource` Phase 3 预留，本期只建表不写数据，ARCHITECTURE §3.2）。
 * 未来 Impl 将注入 `SyncChangeDataSource` 并逐方法委托（ARCHITECTURE §4.1）；
 * 签名以 DATABASE §6.5 `SyncChangeDao` 为准（`pendingChanges` 带 `ledgerId` 维度）。
 */
interface SyncChangeRepository {

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
