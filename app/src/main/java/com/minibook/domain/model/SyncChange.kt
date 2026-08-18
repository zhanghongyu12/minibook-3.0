package com.minibook.domain.model

/**
 * 同步变更领域模型，对齐 DATABASE §4.6 / API §10（Phase 3 云端出口预留，本期只建表不写数据）。
 *
 * entityType ∈ transaction/debt/category；action ∈ create/update/delete；
 * timestamp 为 ISO 8601 完整时间戳；data 为变更载荷 JSON，delete 可为空。
 * 本期不使用枚举约束（TASK-006 枚举仅覆盖交易/分类/债务/同步状态四类）。
 */
data class SyncChange(
    val id: String,
    val ledgerId: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val timestamp: String,
    val data: String? = null,
)