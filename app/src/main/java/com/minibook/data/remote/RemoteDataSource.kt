package com.minibook.data.remote

/**
 * 未来云端出口契约，对齐 API §9 / ARCHITECTURE §6.3。
 *
 * **本期仅接口占位，不实现、不接线**；无任何实现代码。
 *
 * 本地优先、无 REST 网关（API §1）。未来对接云端时业务层与其余 DataSource
 * 接口不变，演进路径（API §9）：
 * 1. 实现本接口（推送增量变更 / 增量拉取）；
 * 2. 组合成 `SyncingDataSource`（本地写、异步推送、冲突 LWW）；
 * 3. 在 `di/DataSourceModule` 的 `@Binds` **一处切换**；Repository 不动。
 *
 * 具体接入属 **Phase 3 云端同步**（云备份/多端同步），本期留出口。
 */
interface RemoteDataSource {
    // —— 本期仅契约占位，不实现 ——

    // 推送增量变更，返回对端需合并的行（冲突进入 syncStatus=conflict）
    // suspend fun push(changes: List<SyncChange>): PullResult

    // 增量拉取：返回自 lastSyncedAt 以来的远端变更
    // suspend fun pull(lastSyncedAt: String): PullResult
}
