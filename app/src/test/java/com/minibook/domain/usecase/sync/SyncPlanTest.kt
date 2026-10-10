package com.minibook.domain.usecase.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SyncPlanner] 纯函数单测（F-15 / DEC-033，`docs/14_cloud_sync.md §4.3~§4.7`）。
 *
 * 这层是同步正确性的**唯一判定处**（UseCase 只负责取数与落库），所以三模式、三路比较、
 * LWW、墓碑差分、「仅下载不删本地」「删了不复活」全部在这里断言。
 */
class SyncPlanTest {

    // ---------- 基础 ----------

    @Test
    fun `空输入不产生任何待办`() {
        val plan = SyncPlanner.plan(SyncMode.BOTH, emptyList(), emptyList(), emptyList(), NOW)

        assertTrue("无数据时计划应为空", plan.isEmpty)
        assertEquals(0, plan.uploadCount)
        assertEquals(0, plan.downloadCount)
        assertEquals(0, plan.conflictCount)
        assertEquals(0, plan.deleteCount)
    }

    @Test
    fun `首次同步（无基线）全部上行`() {
        val plan = SyncPlanner.plan(
            SyncMode.BOTH,
            local = listOf(local("t1"), local("t2")),
            bases = emptyList(),
            remote = emptyList(),
            nowIso = NOW,
        )

        assertEquals(2, plan.uploadCount)
        assertEquals(setOf("t1", "t2"), plan.uploads.map { it.id }.toSet())
        assertTrue("首次上行不应带墓碑", plan.tombstones.isEmpty())
        assertTrue(plan.uploads.none { it.deleted })
    }

    @Test
    fun `基线与本地一致时不上行`() {
        val plan = SyncPlanner.plan(
            SyncMode.BOTH,
            local = listOf(local("t1", updatedAt = T2)),
            bases = listOf(base("t1", baseUpdatedAt = T2)),
            remote = emptyList(),
            nowIso = NOW,
        )

        assertTrue("已一致的行不应重复上行", plan.uploads.isEmpty())
    }

    @Test
    fun `本地修改（updatedAt 与基线不同）时上行该行`() {
        val plan = SyncPlanner.plan(
            SyncMode.BOTH,
            local = listOf(local("t1", updatedAt = T3)),
            bases = listOf(base("t1", baseUpdatedAt = T2)),
            remote = emptyList(),
            nowIso = NOW,
        )

        assertEquals(listOf("t1"), plan.uploads.map { it.id })
        assertEquals(T3, plan.uploads.single().updatedAt)
    }

    // ---------- 墓碑（DEC-035：基线差分） ----------

    @Test
    fun `基线有而本地已无时补发墓碑`() {
        val plan = SyncPlanner.plan(
            SyncMode.BOTH,
            local = emptyList(),
            bases = listOf(base("gone")),
            remote = emptyList(),
            nowIso = NOW,
        )

        assertEquals(1, plan.tombstones.size)
        val tombstone = plan.tombstones.single()
        assertEquals("gone", tombstone.id)
        assertTrue("墓碑必须 deleted=true", tombstone.deleted)
        assertEquals("墓碑不带 payload", null, tombstone.payloadJson)
        assertEquals(1, plan.uploadCount)
    }

    @Test
    fun `仅下载模式不产生上行也不产生墓碑`() {
        val plan = SyncPlanner.plan(
            SyncMode.DOWNLOAD_ONLY,
            local = emptyList(),
            bases = listOf(base("gone")),
            remote = emptyList(),
            nowIso = NOW,
        )

        assertTrue(plan.uploads.isEmpty())
        assertTrue("仅下载不做任何上行（含墓碑）", plan.tombstones.isEmpty())
    }

    // ---------- 下行 ----------

    @Test
    fun `云端新行（无基线）直接落地`() {
        val plan = SyncPlanner.plan(
            SyncMode.BOTH,
            local = emptyList(),
            bases = emptyList(),
            remote = listOf(remote("r1")),
            nowIso = NOW,
        )

        assertEquals(listOf("r1"), plan.downloads.map { it.id })
        assertTrue(plan.conflicts.isEmpty())
        assertTrue(plan.localDeletes.isEmpty())
    }

    @Test
    fun `本地未改时快进到云端版本`() {
        val plan = SyncPlanner.plan(
            SyncMode.BOTH,
            local = listOf(local("t1", updatedAt = T2)),
            bases = listOf(base("t1", baseUpdatedAt = T2)),
            remote = listOf(remote("t1", updatedAt = T3)),
            nowIso = NOW,
        )

        assertEquals(listOf("t1"), plan.downloads.map { it.id })
        assertTrue("快进不算冲突", plan.conflicts.isEmpty())
    }

    @Test
    fun `本地改而云端未改时保留本地等待上行`() {
        val plan = SyncPlanner.plan(
            SyncMode.BOTH,
            local = listOf(local("t1", updatedAt = T3)),
            bases = listOf(base("t1", baseUpdatedAt = T2)),
            remote = listOf(remote("t1", updatedAt = T2)),
            nowIso = NOW,
        )

        assertTrue("云端没改就不该覆盖本地", plan.downloads.isEmpty())
        assertEquals("本地改动要上行", listOf("t1"), plan.uploads.map { it.id })
        assertTrue(plan.conflicts.isEmpty())
    }

    @Test
    fun `云端墓碑删除本地对应行`() {
        val plan = SyncPlanner.plan(
            SyncMode.BOTH,
            local = listOf(local("t1", updatedAt = T2)),
            bases = listOf(base("t1", baseUpdatedAt = T2)),
            remote = listOf(remote("t1", updatedAt = T3, deleted = true, payload = null)),
            nowIso = NOW,
        )

        assertEquals(listOf("t1"), plan.localDeletes.map { it.id })
        assertEquals(1, plan.deleteCount)
        assertTrue("墓碑不是下载项", plan.downloads.isEmpty())
        assertTrue(plan.conflicts.isEmpty())
    }

    @Test
    fun `仅下载模式不会因本地多余行而删除本地`() {
        // 本地有一条云端根本没有的行（例如云端只存了部分账本）：仅下载绝不能删它。
        val plan = SyncPlanner.plan(
            SyncMode.DOWNLOAD_ONLY,
            local = listOf(local("only-local", updatedAt = T2)),
            bases = emptyList(),
            remote = listOf(remote("cloud-only")),
            nowIso = NOW,
        )

        assertTrue("仅下载不删本地多余行", plan.localDeletes.isEmpty())
        assertEquals(listOf("cloud-only"), plan.downloads.map { it.id })
        assertTrue("仅下载不上行（不含墓碑）", plan.tombstones.isEmpty())
    }

    // ---------- 冲突（LWW + 留档） ----------

    @Test
    fun `双方都改且云端更新时云端胜并留档`() {
        val plan = SyncPlanner.plan(
            SyncMode.BOTH,
            local = listOf(local("t1", updatedAt = T2, payload = LOCAL_JSON)),
            bases = listOf(base("t1", baseUpdatedAt = T1)),
            remote = listOf(remote("t1", updatedAt = T3, payload = REMOTE_JSON)),
            nowIso = NOW,
        )

        assertEquals("云端胜 ⇒ 要落地云端版", listOf("t1"), plan.downloads.map { it.id })
        val conflict = plan.conflicts.single()
        assertEquals(ConflictWinner.REMOTE, conflict.winner)
        assertEquals(SyncPlanner.OUTCOME_LWW_REMOTE, conflict.outcome)
        assertEquals("输家副本必须留档", LOCAL_JSON, conflict.localJson)
        assertEquals(REMOTE_JSON, conflict.remoteJson)
    }

    @Test
    fun `双方都改且本地更新时本地胜并留档`() {
        val plan = SyncPlanner.plan(
            SyncMode.BOTH,
            local = listOf(local("t1", updatedAt = T3, payload = LOCAL_JSON)),
            bases = listOf(base("t1", baseUpdatedAt = T1)),
            remote = listOf(remote("t1", updatedAt = T2, payload = REMOTE_JSON)),
            nowIso = NOW,
        )

        assertTrue("本地胜 ⇒ 不覆盖本地", plan.downloads.isEmpty())
        val conflict = plan.conflicts.single()
        assertEquals(ConflictWinner.LOCAL, conflict.winner)
        assertEquals(SyncPlanner.OUTCOME_LWW_LOCAL, conflict.outcome)
        assertEquals("云端那份也要留档", REMOTE_JSON, conflict.remoteJson)
    }

    @Test
    fun `云端墓碑遇上本地修改时本地胜且不删本地`() {
        val plan = SyncPlanner.plan(
            SyncMode.BOTH,
            local = listOf(local("t1", updatedAt = T3, payload = LOCAL_JSON)),
            bases = listOf(base("t1", baseUpdatedAt = T2)),
            remote = listOf(remote("t1", updatedAt = T4, deleted = true, payload = null)),
            nowIso = NOW,
        )

        assertTrue("本地改过就不能被墓碑删掉", plan.localDeletes.isEmpty())
        val conflict = plan.conflicts.single()
        assertEquals(ConflictWinner.LOCAL, conflict.winner)
        assertEquals(SyncPlanner.OUTCOME_DELETE_VS_EDIT, conflict.outcome)
        assertEquals("在已删除的一侧用占位 JSON", SyncPlanner.DELETED_MARKER, conflict.remoteJson)
    }

    @Test
    fun `本地已删而云端改过时不复活且留档`() {
        // 场景：本地删了某条但还没上行；期间另一台设备改了它并在云端成了新版本。
        val plan = SyncPlanner.plan(
            SyncMode.BOTH,
            local = emptyList(),
            bases = listOf(base("t1", baseUpdatedAt = T1)),
            remote = listOf(remote("t1", updatedAt = T2, payload = REMOTE_JSON)),
            nowIso = NOW,
        )

        assertTrue("绝不把用户删掉的账目拉回来", plan.downloads.isEmpty())
        assertEquals("本地删除意图要上行成墓碑", listOf("t1"), plan.tombstones.map { it.id })
        val conflict = plan.conflicts.single()
        assertEquals(ConflictWinner.LOCAL, conflict.winner)
        assertEquals(SyncPlanner.OUTCOME_DELETE_VS_EDIT, conflict.outcome)
        assertEquals(LOCAL_DELETED_MARKER, conflict.localJson)
    }

    @Test
    fun `本地已删且云端未改时不产生冲突`() {
        val plan = SyncPlanner.plan(
            SyncMode.BOTH,
            local = emptyList(),
            bases = listOf(base("t1", baseUpdatedAt = T2)),
            remote = listOf(remote("t1", updatedAt = T2)),
            nowIso = NOW,
        )

        assertTrue("云端自基线以来没变，无冲突", plan.conflicts.isEmpty())
        assertTrue("也不会复活", plan.downloads.isEmpty())
        assertEquals(listOf("t1"), plan.tombstones.map { it.id })
    }

    // ---------- 多类型 / 计数 ----------

    @Test
    fun `跨类型互不干扰且计数口径正确`() {
        val plan = SyncPlanner.plan(
            SyncMode.BOTH,
            local = listOf(
                local("tx-1", type = SyncEntityType.TRANSACTION, updatedAt = T3),
                local("debt-1", type = SyncEntityType.DEBT, updatedAt = T2),
            ),
            bases = listOf(
                base("tx-1", type = SyncEntityType.TRANSACTION, baseUpdatedAt = T2),
                base("debt-1", type = SyncEntityType.DEBT, baseUpdatedAt = T2),
                base("cat-gone", type = SyncEntityType.CATEGORY, baseUpdatedAt = T2),
            ),
            remote = listOf(
                remote("budget-1", type = SyncEntityType.BUDGET, updatedAt = T3),
                remote("ledger-gone", type = SyncEntityType.LEDGER, updatedAt = T3, deleted = true, payload = null),
            ),
            nowIso = NOW,
        )

        assertEquals("tx-1 改了要上行；debt-1 没改不上行", listOf("tx-1"), plan.uploads.map { it.id })
        assertEquals("cat-gone 本地已无 ⇒ 墓碑", listOf("cat-gone"), plan.tombstones.map { it.id })
        assertEquals("budget-1 是新行 ⇒ 下载", listOf("budget-1"), plan.downloads.map { it.id })
        assertEquals("ledger-gone 是云端墓碑 ⇒ 但本地没有该行，无事可做", 0, plan.deleteCount)
        assertEquals(2, plan.uploadCount)
        assertEquals(1, plan.downloadCount)
        assertEquals(0, plan.conflictCount)
    }

    @Test
    fun `同 id 不同类型互不串味`() {
        val plan = SyncPlanner.plan(
            SyncMode.BOTH,
            local = listOf(
                local("a", type = SyncEntityType.TRANSACTION, updatedAt = T3),
                local("a", type = SyncEntityType.DEBT, updatedAt = T2),
            ),
            bases = listOf(
                base("a", type = SyncEntityType.TRANSACTION, baseUpdatedAt = T2),
                base("a", type = SyncEntityType.DEBT, baseUpdatedAt = T2),
            ),
            remote = emptyList(),
            nowIso = NOW,
        )

        assertEquals("只有 transaction 的 a 改了", 1, plan.uploadCount)
        assertEquals(SyncEntityType.TRANSACTION, plan.uploads.single().type)
    }

    @Test
    fun `未知实体类型不会被误判`() {
        assertEquals(SyncEntityType.TRANSACTION, SyncEntityType.fromWire("transaction"))
        assertEquals(null, SyncEntityType.fromWire("not_a_type"))
        assertFalse(SyncEntityType.entries.any { it.wire.isEmpty() })
    }

    // ---------- 夹具 ----------

    private fun local(
        id: String,
        type: SyncEntityType = SyncEntityType.TRANSACTION,
        ledgerId: String = LEDGER,
        updatedAt: String = T2,
        payload: String = LOCAL_JSON,
    ) = SyncLocalRow(type, id, ledgerId, updatedAt, payload)

    private fun base(
        id: String,
        type: SyncEntityType = SyncEntityType.TRANSACTION,
        ledgerId: String = LEDGER,
        baseUpdatedAt: String = T1,
    ) = SyncBaseRow(type, id, ledgerId, baseUpdatedAt)

    private fun remote(
        id: String,
        type: SyncEntityType = SyncEntityType.TRANSACTION,
        ledgerId: String = LEDGER,
        updatedAt: String = T3,
        deleted: Boolean = false,
        payload: String? = REMOTE_JSON,
    ) = SyncRemoteRow(type, id, ledgerId, updatedAt, deleted, payload, serverSeq = 7)

    private companion object {
        const val LEDGER = "ledger-1"
        const val T1 = "2026-10-10T01:00:00.000000000Z"
        const val T2 = "2026-10-10T02:00:00.000000000Z"
        const val T3 = "2026-10-10T03:00:00.000000000Z"
        const val T4 = "2026-10-10T04:00:00.000000000Z"
        const val NOW = "2026-10-10T05:00:00.000000000Z"
        const val LOCAL_JSON = """{"id":"t1","amount":100}"""
        const val REMOTE_JSON = """{"id":"t1","amount":200}"""
        const val LOCAL_DELETED_MARKER = SyncPlanner.DELETED_MARKER
    }
}
