package com.minibook.domain.usecase.sync

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自动同步（DEC-038）的两条硬口径：**节流**与**危险计划不自动跑**。
 *
 * 两者都是纯判定（[AutoSyncManager.isDue] 与 [SyncPlan.needsConfirm]），故无需任何 mock，
 * 也不会因为「跑得慢」而在 CI 上抖动。
 */
class AutoSyncManagerTest {

    private val now = Instant.parse("2026-10-12T02:00:00Z").toEpochMilli()

    @Test
    fun `从未同步过 该跑`() {
        assertTrue(AutoSyncManager.isDue("", now))
    }

    @Test
    fun `刚同步过 不跑`() {
        val justNow = Instant.ofEpochMilli(now - 60_000).toString()
        assertFalse(AutoSyncManager.isDue(justNow, now))
    }

    @Test
    fun `超过间隔 该跑`() {
        val old = Instant.ofEpochMilli(now - AutoSyncManager.MIN_INTERVAL_MS - 1).toString()
        assertTrue(AutoSyncManager.isDue(old, now))
    }

    @Test
    fun `恰好等于间隔 该跑（边界含等号，避免永远差一点）`() {
        val edge = Instant.ofEpochMilli(now - AutoSyncManager.MIN_INTERVAL_MS).toString()
        assertTrue(AutoSyncManager.isDue(edge, now))
    }

    @Test
    fun `时间戳解析不了 该跑（宁可多跑一次，不可长期不跑）`() {
        assertTrue(AutoSyncManager.isDue("not-a-timestamp", now))
    }

    @Test
    fun `会删本地 或 有冲突 的计划 必须用户确认`() {
        val empty = SyncPlan.empty(SyncMode.BOTH)
        assertFalse("空计划不需要确认", empty.needsConfirm)

        val deleting = empty.copy(localDeletes = listOf(tombstone()))
        assertTrue("会删本地数据必须确认（自动同步不得静默删账目）", deleting.needsConfirm)

        val conflicting = empty.copy(
            conflicts = listOf(
                SyncConflictRecord(
                    type = SyncEntityType.TRANSACTION,
                    id = "t-1",
                    ledgerId = "l-1",
                    localJson = "{}",
                    remoteJson = "{}",
                    outcome = SyncPlanner.OUTCOME_LWW_REMOTE,
                    winner = ConflictWinner.REMOTE,
                ),
            ),
        )
        assertTrue("有冲突必须确认", conflicting.needsConfirm)
    }

    /** 云端墓碑（下行后本地对应行会被删）。 */
    private fun tombstone() = SyncRemoteRow(
        type = SyncEntityType.TRANSACTION,
        id = "t-1",
        ledgerId = "l-1",
        updatedAt = "2026-10-12T02:00:00.000000000Z",
        deleted = true,
        payloadJson = null,
    )
}
