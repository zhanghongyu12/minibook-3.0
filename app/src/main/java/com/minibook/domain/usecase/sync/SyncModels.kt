package com.minibook.domain.usecase.sync

/**
 * F-15 同步核心的**纯数据模型**（无 Android 依赖，可 JVM 单测）。
 *
 * 术语对齐 `docs/14_cloud_sync.md §4`：
 * - **local**：本地业务表的行（同步视角的最小投影 + payload JSON）
 * - **base**：`sync_state` 里「上次与云端一致的版本」（三路比较的基准）
 * - **remote**：`GET /sync/pull` 下发的行（含墓碑）
 *
 * 约定：`updatedAt` 一律用**客户端原始 ISO 串**（服务端 `updated_at_raw` 原样回显）。
 * 同源同格式、定宽 9 位小数，故可直接按字符串比较先后（§12.2）。
 */

/** 三种手动同步模式（§4.1）。 */
enum class SyncMode(val label: String) {
    /** 双向：上行本地变更 + 下行云端变更。 */
    BOTH("双向同步"),

    /** 仅上传：只把本地变更推到云端，**不下载、不删本地**。 */
    UPLOAD_ONLY("仅上传"),

    /** 仅下载：只把云端变更拉到本地；**不删本地多余行**（云端墓碑除外）。 */
    DOWNLOAD_ONLY("仅下载"),
    ;

    /** 本模式是否执行上行。 */
    val uploads: Boolean get() = this != DOWNLOAD_ONLY

    /** 本模式是否执行下行。 */
    val downloads: Boolean get() = this != UPLOAD_ONLY
}

/**
 * 参与同步的 6 类实体，`wire` 与服务端 `entity_spec.py` 的 `ENTITY_TYPES` **逐字一致**。
 */
enum class SyncEntityType(val wire: String, val label: String) {
    LEDGER("ledger", "账本"),
    CATEGORY("category", "分类"),
    TRANSACTION("transaction", "交易"),
    DEBT("debt", "债务"),
    BUDGET("budget", "预算"),
    USER_CATEGORY_RULE("user_category_rule", "归类规则"),
    ;

    companion object {
        fun fromWire(wire: String): SyncEntityType? = entries.firstOrNull { it.wire == wire }
    }
}

/** 行身份键：同一 UUID 跨类型可能重名，故按类型分域。 */
internal fun rowKey(type: SyncEntityType, id: String): String = type.wire + "\u0000" + id

/** 本地业务行（同步视角投影）。[payloadJson] = 该行的 camelCase JSON（与实体 `@Serializable` 一致）。 */
data class SyncLocalRow(
    val type: SyncEntityType,
    val id: String,
    val ledgerId: String,
    val updatedAt: String,
    val payloadJson: String,
) {
    internal val key: String get() = rowKey(type, id)
}

/** 云端行（pull 结果）。[payloadJson] 在墓碑（`deleted=true`）时为 null。 */
data class SyncRemoteRow(
    val type: SyncEntityType,
    val id: String,
    val ledgerId: String,
    val updatedAt: String,
    val deleted: Boolean,
    val payloadJson: String?,
    val serverSeq: Long = 0L,
) {
    internal val key: String get() = rowKey(type, id)
}

/** 同步基线（`sync_state` 行）。[baseUpdatedAt] = 上次与云端一致的版本。 */
data class SyncBaseRow(
    val type: SyncEntityType,
    val id: String,
    val ledgerId: String,
    val baseUpdatedAt: String,
) {
    internal val key: String get() = rowKey(type, id)
}

/** 待上行一项；`deleted=true` 即墓碑（[payloadJson] 为 null，服务端要求墓碑省略 payload）。 */
data class SyncPushItem(
    val type: SyncEntityType,
    val id: String,
    val ledgerId: String,
    val updatedAt: String,
    val deleted: Boolean,
    val payloadJson: String?,
) {
    internal val key: String get() = rowKey(type, id)
}

/** 冲突留档一项（**绝不静默丢**：两份 JSON 原样保留）。 */
data class SyncConflictRecord(
    val type: SyncEntityType,
    val id: String,
    val ledgerId: String,
    val localJson: String,
    val remoteJson: String,
    /** 系统按 LWW 给出的处置（写进 `sync_conflicts.resolution`，如 `lww:remote`）。 */
    val outcome: String,
    /** 本轮同步后本地最终采用的是哪一份（决定要不要把云端版写进本地）。 */
    val winner: ConflictWinner,
)

/** 冲突处置结果。 */
enum class ConflictWinner {
    /** 本地胜：保留本地行，等下次 push 上行（云端墓碑场景下 push 会把 `deleted` 复位）。 */
    LOCAL,

    /** 云端胜：把云端版写进本地，本地那份进 `sync_conflicts`。 */
    REMOTE,
}

/**
 * 一次同步的执行计划（dry-run 与真实执行共用同一份计算结果，保证「预览 == 执行」）。
 *
 * 计数口径（§4.7 第 5 条预览卡）：
 * - [uploadCount] = 待上行条数（含墓碑）
 * - [downloadCount] = 待写入本地的云端条数
 * - [conflictCount] = 冲突留档条数
 * - [deleteCount] = **本地将被删除**的条数（仅来自云端墓碑）
 */
data class SyncPlan(
    val mode: SyncMode,
    val uploads: List<SyncPushItem>,
    val downloads: List<SyncRemoteRow>,
    val conflicts: List<SyncConflictRecord>,
    val localDeletes: List<SyncRemoteRow>,
    val tombstones: List<SyncPushItem>,
) {
    val uploadCount: Int get() = uploads.size + tombstones.size

    val downloadCount: Int get() = downloads.size

    val conflictCount: Int get() = conflicts.size

    val deleteCount: Int get() = localDeletes.size

    /** 无任何待办：UI 可提示「已是最新」而不是弹确认卡。 */
    val isEmpty: Boolean
        get() = uploadCount == 0 && downloadCount == 0 && conflictCount == 0 && deleteCount == 0

    companion object {
        fun empty(mode: SyncMode) = SyncPlan(mode, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}
