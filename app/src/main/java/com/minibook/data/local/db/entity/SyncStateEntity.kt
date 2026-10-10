package com.minibook.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * 同步基线（Room v4 新表，F-15 / DEC-033，`docs/14_cloud_sync.md §4.2 / §5.3`）。
 *
 * 一行 = 一个「本地与云端已达成一致的实体版本」，只在新表里记录，**不改既有 6 张业务表**。
 *
 * 三个用途：
 * 1. **三路冲突检测的 base**：与 `local.updatedAt`、`remote.updatedAt` 比较（§4.5）。
 * 2. **删除差分（DEC-035）**：本表里有、而本地业务表已无该 id ⇒ 说明本地删过、墓碑未上行
 *    ⇒ 下次 push 补发 `deleted=true`。因此**无需**在 5 处删除路径插入写操作，
 *    且「删账本 / 按账本清空 / 导入替换」这类批量删除同样被覆盖。
 * 3. **重推抑制**：`baseUpdatedAt == 本地 updatedAt` 表示已一致，可以跳过上行（幂等无害，
 *    纯属省流量）。
 *
 * 复合主键 `(entityType, entityId)`：`entityId` 是客户端 UUID，跨类型分域存储更稳。
 * `ledgerId` 冗余存一份——墓碑要在**本地行已不存在**时构造信封，此时取不到账本号。
 */
@Entity(
    tableName = "sync_state",
    primaryKeys = ["entityType", "entityId"],
    indices = [Index(value = ["ledgerId"])],
)
data class SyncStateEntity(
    val entityType: String,
    val entityId: String,
    val ledgerId: String,
    /** 上次与服务端一致的 `updatedAt`（服务端回显的**原始 ISO 串**，不做格式归一化）。 */
    val baseUpdatedAt: String,
    /** 达成一致的时刻（本地 ISO）。 */
    val syncedAt: String,
    /** 该行最后一次同步时服务端分配的 `server_seq`（0 = 未知/未记录）。 */
    val serverSeq: Long = 0L,
)
