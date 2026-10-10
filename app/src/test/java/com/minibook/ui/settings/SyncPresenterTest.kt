package com.minibook.ui.settings

import com.minibook.data.remote.SyncAccountState
import com.minibook.domain.usecase.sync.CloudStatusLine
import com.minibook.domain.usecase.sync.ConflictWinner
import com.minibook.domain.usecase.sync.SyncConflictRecord
import com.minibook.domain.usecase.sync.SyncEntityType
import com.minibook.domain.usecase.sync.SyncMode
import com.minibook.domain.usecase.sync.SyncPlan
import com.minibook.domain.usecase.sync.SyncPushItem
import com.minibook.domain.usecase.sync.SyncRemoteRow
import com.minibook.domain.usecase.sync.SyncRunResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SyncPresenter] 纯文案层单测（F-15 / TASK-067）。
 *
 * 这些字符串是用户判断「同步到底做了什么」的唯一渠道，所以逐条断言：
 * 计数不能漏项、危险操作必须能看出危险、失败必须说清「游标未推进（可重试）」。
 */
class SyncPresenterTest {

    @Test
    fun `预览主句逐项给出四个计数`() {
        val plan = SyncPlan(
            mode = SyncMode.BOTH,
            uploads = listOf(pushItem("u1"), pushItem("u2")),
            downloads = listOf(remoteRow("d1"), remoteRow("d2"), remoteRow("d3")),
            conflicts = listOf(conflict("c1")),
            localDeletes = listOf(remoteRow("x1", deleted = true, payload = null)),
            tombstones = listOf(pushItem("t1", deleted = true, payload = null)),
        )

        assertEquals(
            "上传 2 条改动 + 1 条删除 · 下载 3 条 · 删除本地 1 条 · 冲突 1 条",
            SyncPresenter.previewText(plan),
        )
    }

    @Test
    fun `空计划预览计数全为零`() {
        val text = SyncPresenter.previewText(SyncPlan.empty(SyncMode.BOTH))

        assertEquals("上传 0 条改动 + 0 条删除 · 下载 0 条 · 删除本地 0 条 · 冲突 0 条", text)
    }

    @Test
    fun `仅上传模式的明细不谈下行`() {
        val details = SyncPresenter.previewDetails(SyncMode.UPLOAD_ONLY, planOf(mode = SyncMode.UPLOAD_ONLY))

        assertTrue("应说明上行条数", details.any { it.startsWith("上行") })
        assertFalse("仅上传不该出现下行说明（会让用户以为会下载）", details.any { it.startsWith("下行") })
    }

    @Test
    fun `仅下载模式的明细不谈上行`() {
        val plan = planOf(
            mode = SyncMode.DOWNLOAD_ONLY,
            uploads = listOf(pushItem("u1")),
            tombstones = listOf(pushItem("t1", deleted = true, payload = null)),
        )
        val details = SyncPresenter.previewDetails(SyncMode.DOWNLOAD_ONLY, plan)

        assertFalse("仅下载不该出现上行说明", details.any { it.startsWith("上行") })
        assertTrue(details.any { it.startsWith("下行") })
    }

    @Test
    fun `会删本地时必须显式警告`() {
        val plan = planOf(localDeletes = listOf(remoteRow("x1", deleted = true, payload = null)))
        val details = SyncPresenter.previewDetails(SyncMode.DOWNLOAD_ONLY, plan)

        assertTrue("删本地数据必须有警告行", details.any { it.contains("⚠️") && it.contains("删除本地") })
    }

    @Test
    fun `有冲突时说明处置口径`() {
        val plan = planOf(conflicts = listOf(conflict("c1")))
        val details = SyncPresenter.previewDetails(SyncMode.BOTH, plan)

        assertTrue(details.any { it.contains("冲突") && it.contains("留档") })
    }

    @Test
    fun `危险确认判定 只认删数据与冲突`() {
        assertFalse("无待办不必弹危险确认", SyncPresenter.needsDangerConfirm(SyncPlan.empty(SyncMode.BOTH)))
        assertFalse(
            "纯上传/下载不算危险",
            SyncPresenter.needsDangerConfirm(
                planOf(uploads = listOf(pushItem("u1")), downloads = listOf(remoteRow("d1"))),
            ),
        )
        assertTrue(
            "会删本地要危险确认",
            SyncPresenter.needsDangerConfirm(planOf(localDeletes = listOf(remoteRow("x", deleted = true, payload = null)))),
        )
        assertTrue("有冲突要危险确认", SyncPresenter.needsDangerConfirm(planOf(conflicts = listOf(conflict("c")))))
    }

    @Test
    fun `危险确认文案点明会改动本地且已有备份`() {
        val plan = planOf(localDeletes = listOf(remoteRow("x", deleted = true, payload = null)))
        val message = SyncPresenter.confirmMessage(SyncMode.DOWNLOAD_ONLY, plan)

        assertTrue(message.contains("删除本地 1 条"))
        assertTrue("必须告诉用户已留备份", message.contains("备份"))
    }

    @Test
    fun `空计划提示按模式区分且不弹确认`() {
        val both = SyncPresenter.emptyPlanMessage(SyncMode.BOTH)
        val upload = SyncPresenter.emptyPlanMessage(SyncMode.UPLOAD_ONLY)
        val download = SyncPresenter.emptyPlanMessage(SyncMode.DOWNLOAD_ONLY)

        assertEquals("本地与云端已经一致，无需同步。", both)
        assertTrue(upload.contains("上传"))
        assertTrue(download.contains("下载"))
        assertNotEquals(both, upload)
    }

    @Test
    fun `成功结果卡给出四个计数`() {
        val result = SyncRunResult(
            mode = SyncMode.BOTH,
            uploaded = 2,
            tombstoned = 1,
            downloaded = 3,
            deletedLocal = 4,
            conflicts = 1,
            snapshotPath = "/data/data/com.minibook/files/sync_backup/minibook_backup_20261010_101010.json",
            error = null,
            retryable = false,
        )

        val text = SyncPresenter.resultText(result)
        assertTrue(text.contains("上传 2 条"))
        assertTrue(text.contains("云端删除 1 条"))
        assertTrue(text.contains("下载 3 条"))
        assertTrue(text.contains("删除本地 4 条"))
        assertTrue(text.contains("冲突 1 条"))
        assertEquals("minibook_backup_20261010_101010.json", SyncPresenter.snapshotLabel(result.snapshotPath))
    }

    @Test
    fun `失败结果必须说明游标未推进且可重试`() {
        val result = SyncRunResult.failed(SyncMode.BOTH, "网络不可用", retryable = true)

        val text = SyncPresenter.resultText(result)
        assertTrue(text.contains("同步失败"))
        assertTrue(text.contains("网络不可用"))
        assertTrue("必须说明可重试", text.contains("重试"))
        assertTrue("必须说明没漏数据", text.contains("游标未推进"))
    }

    @Test
    fun `不可重试的失败不提可重试`() {
        val result = SyncRunResult.failed(SyncMode.BOTH, "服务器地址无效", retryable = false)

        assertFalse(SyncPresenter.resultText(result).contains("重试"))
    }

    @Test
    fun `未登录状态说清本地不受影响`() {
        val text = SyncPresenter.statusText(SyncAccountState())

        assertTrue(text.startsWith("未登录"))
        assertTrue("要让用户放心离线记账", text.contains("本地数据不受影响"))
    }

    @Test
    fun `已登录但未同步过时的状态行`() {
        val state = SyncAccountState(username = "alice", userId = "u-1", hasTokens = true)

        val text = SyncPresenter.statusText(state)
        assertTrue(text.contains("已登录：alice"))
        assertTrue(text.contains("尚未同步过"))
    }

    @Test
    fun `已登录同步过的状态行含时间与模式`() {
        val state = SyncAccountState(
            username = "alice",
            userId = "u-1",
            hasTokens = true,
            lastSyncAt = "2026-10-10T07:44:46Z",
            lastSyncMode = "双向同步",
            conflictCount = 3,
        )

        val text = SyncPresenter.statusText(state)
        assertTrue(text.contains("已登录：alice"))
        assertTrue(text.contains("待处理冲突 3 条"))
        assertTrue(text.contains("上次同步"))
        assertTrue(text.contains("双向同步"))
    }

    @Test
    fun `CloudStatusLine 与账号态同口径`() {
        val line = CloudStatusLine(
            loggedIn = true,
            baseUrl = "http://192.168.1.5:8000",
            username = "bob",
            lastSyncAt = "2026-10-10T07:44:46Z",
            lastSyncMode = "仅上传",
            conflictCount = 0,
            serverCursor = 42L,
        )

        val text = SyncPresenter.statusText(line)
        assertTrue(text.contains("已登录：bob"))
        assertTrue(text.contains("仅上传"))
        assertFalse(text.contains("待处理冲突"))
    }

    @Test
    fun `时间格式化 合法 ISO 变本地短格式 非法原样返回`() {
        val formatted = SyncPresenter.humanTime("2026-10-10T07:44:46Z")

        // 不断言具体时区偏移（跑测环境时区不定），只断言形状与"确实解析过"。
        assertTrue("应格式化为 MM-dd HH:mm，实际 $formatted", Regex("""\d{2}-\d{2} \d{2}:\d{2}""").matches(formatted))
        assertEquals("解析不了就原样返回，不显示错误时间", "not-a-time", SyncPresenter.humanTime("not-a-time"))
        assertEquals("—", SyncPresenter.humanTime(""))
    }

    @Test
    fun `冲突处置口径三种映射与未知透传`() {
        assertEquals("本地版本较新，已保留本地（云端旧版留档）", SyncPresenter.outcomeText("lww:local"))
        assertEquals("云端版本较新，已采用云端（本地旧版留档）", SyncPresenter.outcomeText("lww:remote"))
        assertTrue(SyncPresenter.outcomeText("delete-vs-edit:local").contains("删除"))
        assertEquals("未知口径应原样透传，不假装认得", "未来新增的口径", SyncPresenter.outcomeText("未来新增的口径"))
    }

    @Test
    fun `冲突行文案含类型与短 id`() {
        val conflict = SyncConflictRecord(
            type = SyncEntityType.TRANSACTION,
            id = "12345678-1234-1234-1234-123456789abc",
            ledgerId = "l-1",
            localJson = """{"amount":1}""",
            remoteJson = """{"amount":2}""",
            outcome = "lww:remote",
            winner = ConflictWinner.REMOTE,
        )

        val text = SyncPresenter.conflictText(conflict)
        assertTrue(text.contains(SyncEntityType.TRANSACTION.label))
        assertTrue(text.contains("12345678"))
        assertFalse("不该把整串 UUID 铺满一行", text.contains("123456789abc"))
        assertEquals("本地", SyncPresenter.winnerLabel(ConflictWinner.LOCAL))
        assertEquals("云端", SyncPresenter.winnerLabel(ConflictWinner.REMOTE))
    }

    @Test
    fun `三种模式的副标题各不相同且非空`() {
        val subtitles = SyncMode.entries.map { SyncPresenter.modeSubtitle(it) }

        assertTrue(subtitles.all { it.isNotBlank() })
        assertEquals("三种模式文案应互不相同", 3, subtitles.toSet().size)
    }

    @Test
    fun `短 id 与快照名边界`() {
        assertEquals("abc", SyncPresenter.shortId("abc"))
        assertEquals("12345678", SyncPresenter.shortId("123456789"))
        assertNull(SyncPresenter.snapshotLabel(null))
        assertNull(SyncPresenter.snapshotLabel(""))
        assertEquals("a.json", SyncPresenter.snapshotLabel("/x/y/a.json"))
    }

    // ---------- 夹具 ----------

    private fun planOf(
        mode: SyncMode = SyncMode.BOTH,
        uploads: List<SyncPushItem> = emptyList(),
        downloads: List<SyncRemoteRow> = emptyList(),
        conflicts: List<SyncConflictRecord> = emptyList(),
        localDeletes: List<SyncRemoteRow> = emptyList(),
        tombstones: List<SyncPushItem> = emptyList(),
    ) = SyncPlan(mode, uploads, downloads, conflicts, localDeletes, tombstones)

    private fun pushItem(
        id: String,
        deleted: Boolean = false,
        payload: String? = """{"id":"$id"}""",
    ) = SyncPushItem(SyncEntityType.TRANSACTION, id, "l-1", "2026-10-10T07:00:00Z", deleted, payload)

    private fun remoteRow(
        id: String,
        deleted: Boolean = false,
        payload: String? = """{"id":"$id"}""",
    ) = SyncRemoteRow(SyncEntityType.TRANSACTION, id, "l-1", "2026-10-10T07:00:00Z", deleted, payload, serverSeq = 1)

    private fun conflict(id: String) = SyncConflictRecord(
        type = SyncEntityType.TRANSACTION,
        id = id,
        ledgerId = "l-1",
        localJson = """{"amount":1}""",
        remoteJson = """{"amount":2}""",
        outcome = "lww:remote",
        winner = ConflictWinner.REMOTE,
    )
}
