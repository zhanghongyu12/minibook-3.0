package com.minibook.domain.usecase.sync

import android.content.Context
import com.minibook.data.local.db.dao.SyncConflictDao
import com.minibook.data.local.db.dao.SyncDao
import com.minibook.data.local.db.dao.SyncStateDao
import com.minibook.data.local.db.entity.SyncConflictEntity
import com.minibook.data.local.db.entity.SyncStateEntity
import com.minibook.data.local.security.DatabaseProvider
import com.minibook.data.local.sync.SyncRegistry
import com.minibook.data.remote.SyncApiException
import com.minibook.data.remote.SyncAuthStore
import com.minibook.data.remote.SyncItemDto
import com.minibook.data.remote.SyncPushRequest
import com.minibook.data.remote.SyncRemote
import com.minibook.data.remote.SyncServerItemDto
import com.minibook.data.remote.SyncTokenAuthenticator
import com.minibook.domain.usecase.export.ExportAllUseCase
import com.minibook.util.TimeFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * F-15 同步编排（DEC-033，`docs/14_cloud_sync.md §4`）。
 *
 * 职责边界：
 * - **判定**全在纯函数 [SyncPlanner]（可单测、预览 == 执行）；
 * - 本类只做「取数据 → 调 planner → 落库/发请求 → 记游标」；
 * - 「本地不丢」的五道保险在此落地：
 *   1. [writeSnapshot] 在**任何写操作之前**写 `files/sync_backup/<ts>.json`（保留最近 3 份）；
 *   2. 删除只走墓碑（[SyncPlan.tombstones]/[SyncPlan.localDeletes]），「仅下载」不删本地多余行；
 *   3. 冲突一律 [SyncConflictDao.insert] 留档；
 *   4. 失败**不推进游标**（`server_cursor` / 落地基线与上传时间只在成功后写）；
 *   5. [preview] 即 dry-run，UI 拿它渲染「上传 N / 下载 M / 冲突 K / 删除 D」后再确认。
 *
 * 幂等性：下行是 `INSERT OR REPLACE` + 基线 upsert；上行是「与基线比」判定，
 * 重复推送服务端按 `updated_at` no-op。故中断后重跑不会重复、不会跳过。
 */
class SyncUseCase @Inject constructor(
    private val store: SyncAuthStore,
    private val authenticator: SyncTokenAuthenticator,
    private val remote: SyncRemote,
    private val databaseProvider: DatabaseProvider,
    private val exportAll: ExportAllUseCase,
    private val stateDao: SyncStateDao,
    private val conflictDao: SyncConflictDao,
    private val syncDao: SyncDao,
    private val json: Json,
    @ApplicationContext private val appContext: Context,
) {

    private val registry by lazy { SyncRegistry(syncDao, json) }

    /**
     * dry-run：拉取云端增量 + 组装计划，**不写任何本地数据、不推进游标**。
     *
     * 「仅上传」也在本方法里拉取（只读）——为的是能提前告诉用户「冲突 K 条」，
     * 而不是先推上去才发现冲突。
     */
    suspend fun preview(mode: SyncMode): SyncPreview = withContext(Dispatchers.IO) {
        val guard = guardReady()
        if (guard != null) return@withContext SyncPreview.Failed(guard)

        try {
            val now = TimeFormatter.nowIso()
            val local = registry.allLocalRows()
            val bases = allBases()
            val (remoteRows, nextSeq) = authenticator.withToken { session ->
                pullAll(session.baseUrl, session.accessToken)
            }
            val plan = SyncPlanner.plan(mode, local, bases, remoteRows, now)
            SyncPreview.Ready(plan, nextCursor = nextSeq)
        } catch (e: SyncApiException) {
            SyncPreview.Failed(e.userMessage())
        } catch (e: Exception) {
            SyncPreview.Failed("同步预演失败：${e.message ?: e::class.simpleName}")
        }
    }

    /**
     * 执行 [plan]（必须来自 [preview]，保证「预览 == 执行」）。
     *
     * 顺序：快照 → 上行（含墓碑）→ 下行落地 → 冲突留档 → 游标/状态。
     * 任一步抛异常即中止并**不**推进游标；已成功批次是幂等的，重试即可。
     */
    suspend fun execute(plan: SyncPlan, nextCursor: Long): SyncRunResult = withContext(Dispatchers.IO) {
        val guard = guardReady()
        if (guard != null) return@withContext SyncRunResult.failed(plan.mode, guard)

        val now = TimeFormatter.nowIso()
        var snapshotPath: String? = null
        var uploaded = 0
        var tombstoned = 0
        var downloaded = 0
        var deletedLocal = 0
        var conflicts = 0
        val archivedKeys = mutableSetOf<String>()

        try {
            // 1) 五道保险之一：任何写操作前先落本地快照（明文 JSON，复用 F-04 导出）。
            snapshotPath = writeSnapshot()

            // 2) 上行：普通项 + 墓碑，按批发送（服务端整批原子）。
            val pushItems = plan.uploads + plan.tombstones
            if (plan.mode.uploads && pushItems.isNotEmpty()) {
                authenticator.withToken { session ->
                    for (batch in pushItems.chunked(PUSH_BATCH)) {
                        val response = remote.push(
                            session.baseUrl,
                            session.accessToken,
                            SyncPushRequest(
                                deviceId = store.deviceId(),
                                items = batch.map { it.toDto() },
                            ),
                        )
                        // 服务端判定的冲突同样要留档（此时云端胜，本地那份是输家）。
                        archivedKeys += applyPushOutcome(
                            batch = batch,
                            serverWinners = response.conflicts.mapNotNull { it.server.toRowOrNull() },
                            now = now,
                        )
                        uploaded += batch.count { !it.deleted }
                        tombstoned += batch.count { it.deleted }
                    }
                }
            }

            // 3) 下行：落地 + 云端墓碑删除（仅下载模式同样执行「删」——那是用户自己的删除意图）。
            if (plan.mode.downloads) {
                for (row in plan.downloads) {
                    val payload = row.payloadJson ?: continue
                    registry.forType(row.type).applyPayload(row.id, row.ledgerId, payload)
                    stateDao.upsert(
                        SyncStateEntity(
                            entityType = row.type.wire,
                            entityId = row.id,
                            ledgerId = row.ledgerId,
                            baseUpdatedAt = row.updatedAt,
                            syncedAt = now,
                            serverSeq = row.serverSeq,
                        ),
                    )
                    downloaded++
                }
                for (row in plan.localDeletes) {
                    registry.forType(row.type).deleteById(row.id)
                    stateDao.delete(row.type.wire, listOf(row.id))
                    deletedLocal++
                }
            }

            // 4) 冲突一律留档（输家副本不丢）。预演阶段已判定的冲突与上行阶段服务端
            //    判定的冲突都进同一张表；同一条只留一份（按类型+id 去重，避免"冲突数"翻倍）。
            for (c in plan.conflicts) {
                if (!archivedKeys.add(rowKey(c.type, c.id))) continue
                conflictDao.insert(
                    SyncConflictEntity(
                        id = UUID.randomUUID().toString(),
                        entityType = c.type.wire,
                        entityId = c.id,
                        ledgerId = c.ledgerId,
                        localJson = c.localJson,
                        remoteJson = c.remoteJson,
                        detectedAt = now,
                        resolvedAt = null,
                        resolution = c.outcome,
                    ),
                )
                conflicts++
            }
            conflicts += archivedKeys.size - conflicts

            // 5) 全部成功后才推进游标与账号态（失败不推进 ⇒ 下次可重试）。
            if (plan.mode.downloads && nextCursor > store.serverCursor()) {
                store.setServerCursor(nextCursor)
            }
            if (plan.mode.uploads) {
                store.setLastPushedAt(now)
            }
            val unresolved = conflictDao.unresolvedCount()
            store.setLastSyncResult(atIso = now, mode = plan.mode.label, conflictCount = unresolved)
            store.setConflictCount(unresolved)

            SyncRunResult(
                mode = plan.mode,
                uploaded = uploaded,
                tombstoned = tombstoned,
                downloaded = downloaded,
                deletedLocal = deletedLocal,
                conflicts = conflicts,
                snapshotPath = snapshotPath,
                error = null,
                retryable = false,
            )
        } catch (e: SyncApiException) {
            SyncRunResult.failed(plan.mode, e.userMessage(), retryable = e.isRetryable, snapshotPath = snapshotPath)
        } catch (e: Exception) {
            SyncRunResult.failed(
                plan.mode,
                e.message ?: (e::class.simpleName ?: "未知错误"),
                snapshotPath = snapshotPath,
            )
        }
    }

    /** 冲突数（未处理口径）+ 账号态，供 UI 状态行。 */
    suspend fun statusLine(): CloudStatusLine {
        val state = store.current()
        return CloudStatusLine(
            loggedIn = state.isLoggedIn,
            baseUrl = state.baseUrl,
            username = state.username,
            lastSyncAt = state.lastSyncAt,
            lastSyncMode = state.lastSyncMode,
            conflictCount = conflictDao.unresolvedCount(),
            serverCursor = state.serverCursor,
        )
    }

    // ---------- 内部 ----------

    /** 前置条件：已登录 + 数据库已解锁；否则给出可直接展示的中文原因。 */
    private suspend fun guardReady(): String? {
        if (!store.current().hasServer) return "请先填写服务器地址"
        if (!store.isLoggedIn()) return "请先登录云端账号"
        if (databaseProvider.database == null) return "本地数据库未解锁，无法同步"
        return null
    }

    private suspend fun allBases(): List<SyncBaseRow> =
        SyncEntityType.entries.flatMap { type ->
            stateDao.byType(type.wire).map {
                SyncBaseRow(
                    type = type,
                    id = it.entityId,
                    ledgerId = it.ledgerId,
                    baseUpdatedAt = it.baseUpdatedAt,
                )
            }
        }

    /** 拉全量增量（循环到 `has_more=false`）。只读，不推进游标。 */
    private suspend fun pullAll(
        baseUrl: String,
        accessToken: String,
    ): Pair<List<SyncRemoteRow>, Long> {
        val rows = mutableListOf<SyncRemoteRow>()
        var cursor = store.serverCursor()
        var pages = 0
        while (true) {
            val response = remote.pull(baseUrl, accessToken, cursor, PULL_LIMIT)
            response.items.mapNotNullTo(rows) { it.toRowOrNull() }
            cursor = response.nextSeq
            pages++
            if (!response.hasMore) break
            if (pages >= MAX_PULL_PAGES) {
                // 上限保护：宁可让用户分批同步，也不静默只同步一半还宣称成功。
                throw IllegalStateException("云端增量超过 ${MAX_PULL_PAGES * PULL_LIMIT} 条，请先分批同步")
            }
        }
        return rows to cursor
    }

    /**
     * 处理一次 push 的结果，返回**本次因服务端更新而留档的冲突键**。
     *
     * - 非冲突项：服务端按我们的 `updated_at` 原样落库（§12.2 的 `updated_at_raw`），
     *   故基线直接用本地值，无需服务端回显；
     * - 墓碑：成功即清基线（两端一致认为该行不存在）；
     * - 冲突项：服务端版本更新 ⇒ **云端胜**，落地云端版 + 基线取服务端值 + 本地那份留档。
     */
    private suspend fun applyPushOutcome(
        batch: List<SyncPushItem>,
        serverWinners: List<SyncRemoteRow>,
        now: String,
    ): Set<String> {
        val winners = serverWinners.associateBy { rowKey(it.type, it.id) }
        val states = mutableListOf<SyncStateEntity>()
        val cleared = mutableListOf<Pair<SyncEntityType, String>>()
        val archived = mutableSetOf<String>()

        for (item in batch) {
            val winner = winners[item.key]
            if (winner != null) {
                val payload = winner.payloadJson
                if (payload != null) {
                    registry.forType(winner.type).applyPayload(winner.id, winner.ledgerId, payload)
                }
                states += SyncStateEntity(
                    entityType = winner.type.wire,
                    entityId = winner.id,
                    ledgerId = winner.ledgerId,
                    baseUpdatedAt = winner.updatedAt,
                    syncedAt = now,
                    serverSeq = winner.serverSeq,
                )
                conflictDao.insert(
                    SyncConflictEntity(
                        id = UUID.randomUUID().toString(),
                        entityType = item.type.wire,
                        entityId = item.id,
                        ledgerId = item.ledgerId,
                        localJson = item.payloadJson ?: SyncPlanner.DELETED_MARKER,
                        remoteJson = payload ?: SyncPlanner.DELETED_MARKER,
                        detectedAt = now,
                        resolvedAt = null,
                        resolution = SyncPlanner.OUTCOME_LWW_REMOTE,
                    ),
                )
                archived += item.key
            } else if (item.deleted) {
                cleared += item.type to item.id
            } else {
                states += SyncStateEntity(
                    entityType = item.type.wire,
                    entityId = item.id,
                    ledgerId = item.ledgerId,
                    baseUpdatedAt = item.updatedAt,
                    syncedAt = now,
                )
            }
        }
        if (states.isNotEmpty()) stateDao.upsertAll(states)
        cleared.groupBy({ it.first }, { it.second }).forEach { (type, ids) ->
            stateDao.delete(type.wire, ids)
        }
        return archived
    }

    /**
     * 同步前快照（五道保险之一）：复用 F-04 的明文 JSON 备份，落到
     * `filesDir/sync_backup/<原名>.json`，**只保留最近 3 份**。
     *
     * 快照写失败**不阻断**同步（例如磁盘满）：只在结果里不带路径，由 UI 如实展示。
     */
    private suspend fun writeSnapshot(): String? = try {
        val source = exportAll()
        val dir = File(appContext.filesDir, SNAPSHOT_DIR).apply { mkdirs() }
        val target = File(dir, source.name)
        source.copyTo(target, overwrite = true)
        dir.listFiles()
            ?.sortedByDescending { it.name }
            ?.drop(SNAPSHOT_KEEP)
            ?.forEach { it.delete() }
        target.absolutePath
    } catch (e: Exception) {
        null
    }

    private fun SyncPushItem.toDto(): SyncItemDto = SyncItemDto(
        entityType = type.wire,
        entityId = id,
        ledgerId = ledgerId,
        updatedAt = updatedAt,
        deleted = deleted,
        payload = payloadJson?.let { json.parseToJsonElement(it).jsonObject },
    )

    /** 未知实体类型（服务端将来新增）直接跳过，保证**前向兼容**而不是整批失败。 */
    private fun SyncServerItemDto.toRowOrNull(): SyncRemoteRow? {
        val entityType = SyncEntityType.fromWire(entityType) ?: return null
        return SyncRemoteRow(
            type = entityType,
            id = entityId,
            ledgerId = ledgerId,
            updatedAt = updatedAt,
            deleted = deleted,
            payloadJson = payload?.toString(),
            serverSeq = serverSeq,
        )
    }

    private companion object {
        const val PUSH_BATCH = 200
        const val PULL_LIMIT = 200
        const val MAX_PULL_PAGES = 100
        const val SNAPSHOT_DIR = "sync_backup"
        const val SNAPSHOT_KEEP = 3
    }
}

/** [SyncUseCase.preview] 的结果。 */
sealed interface SyncPreview {
    data class Ready(val plan: SyncPlan, val nextCursor: Long) : SyncPreview

    data class Failed(val message: String) : SyncPreview
}

/** 一次同步的执行结果（UI 结果卡用）。 */
data class SyncRunResult(
    val mode: SyncMode,
    val uploaded: Int,
    val tombstoned: Int,
    val downloaded: Int,
    val deletedLocal: Int,
    val conflicts: Int,
    val snapshotPath: String?,
    val error: String?,
    val retryable: Boolean,
) {
    val ok: Boolean get() = error == null

    companion object {
        fun failed(
            mode: SyncMode,
            message: String,
            retryable: Boolean = false,
            snapshotPath: String? = null,
        ) = SyncRunResult(mode, 0, 0, 0, 0, 0, snapshotPath, message, retryable)
    }
}

/** UI 状态行数据（**只有计数与时间，不含账目明细**，沿用 F-14 隐私口径）。 */
data class CloudStatusLine(
    val loggedIn: Boolean,
    val baseUrl: String,
    val username: String,
    val lastSyncAt: String,
    val lastSyncMode: String,
    val conflictCount: Int,
    val serverCursor: Long,
)
