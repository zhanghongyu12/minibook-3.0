package com.minibook.data.remote

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minibook.data.local.db.MiniBookDatabase
import com.minibook.data.local.db.entity.CategoryEntity
import com.minibook.data.local.db.entity.LedgerEntity
import com.minibook.data.local.db.entity.TransactionEntity
import com.minibook.data.local.security.DatabaseProvider
import com.minibook.data.local.security.KeystoreSecretCipher
import com.minibook.domain.usecase.export.ExportAllUseCase
import com.minibook.domain.usecase.sync.SyncMode
import com.minibook.domain.usecase.sync.SyncPreview
import com.minibook.domain.usecase.sync.SyncRunResult
import com.minibook.domain.usecase.sync.SyncUseCase
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **设备端真实端到端**（TASK-068 的设备段）：跑在模拟器/真机上，用**生产编排** [SyncUseCase]
 * ——真 Room（内存库）、真 Keystore（令牌包裹）、真 OkHttp、真 API、真 MySQL。
 *
 * 为什么用 `DatabaseProvider` 替身喂内存库：该接口（`DatabaseProvider.kt` 注释）本就是为
 * androidTest 内存库替身设计的；生产加密门 SQLCipher/KeyManager 与「未解锁」判断不受影响。
 *
 * 覆盖 TASK-068 的三条设备端验收：
 * 1. 「另一侧仅下载」→ 条数与金额合计一致；
 * 2. 两端各改一条 → 双向同步 → 冲突留档可见、两端最终一致；
 * 3. 断网 → 本地数据不变、游标不推进 → 恢复后重试成功。
 *
 * 前置：宿主机 API 需在跑（模拟器经 `10.0.2.2` 访问宿主机回环）。不可达时**如实跳过**。
 * 副作用：在开发库留一个 `dev_*` 账号（服务端无删除 API），收口时用 SQL 清理。
 */
@RunWith(AndroidJUnit4::class)
class SyncDeviceE2ETest {

    private val hostApi = "http://10.0.2.2:8000"

    @Test
    fun 设备端端到端_仅下载一致_双向冲突留档_断网不推进游标() = runBlocking {
        Assume.assumeTrue("API 不可达（$hostApi），跳过设备端端到端", apiReachable())

        val context = ApplicationProvider.getApplicationContext<Context>()
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }
        val remote = SyncApi(OkHttpClient(), json, Dispatchers.IO)
        val exportAll = EntryPointAccessors
            .fromApplication(context, SyncDebugEntryPoint::class.java)
            .exportAllUseCase()

        // ---- 账号（真注册 + 真登录）----
        val deviceId = "emu-" + UUID.randomUUID().toString().take(8)
        val username = "dev_" + UUID.randomUUID().toString().replace("-", "").take(10)
        remote.register(
            hostApi,
            SyncRegisterRequest(username, PASSWORD, deviceId = deviceId, deviceName = "emulator"),
        )
        val tokens = remote.login(
            hostApi,
            SyncLoginRequest(
                username = username,
                password = PASSWORD,
                deviceId = deviceId,
                deviceName = "emulator",
                platform = "android",
            ),
        )

        // ---- 两台「设备」：各自内存库 + 各自（真 Keystore 加密的）账号存储 ----
        val dbA = newDb(context)
        val dbB = newDb(context)
        val storeA = newStore(context, "e2e-device-a")
        val storeB = newStore(context, "e2e-device-b")
        storeA.saveSession(hostApi, tokens.user.userId, username, tokens.accessToken, tokens.refreshToken, 3600L)
        storeB.saveSession(hostApi, tokens.user.userId, username, tokens.accessToken, tokens.refreshToken, 3600L)
        storeB.setServerCursor(0L)

        val useA = newUseCase(context, storeA, dbA, remote, json, exportAll)
        val useB = newUseCase(context, storeB, dbB, remote, json, exportAll)

        // ---- 设备 A 造 1 账本 + 1 分类 + 3 笔交易 ----
        val ledgerId = UUID.randomUUID().toString()
        val categoryId = UUID.randomUUID().toString()
        val t1 = UUID.randomUUID().toString()
        val t2 = UUID.randomUUID().toString()
        val t3 = UUID.randomUUID().toString()
        val seedAt = "2026-10-10T09:00:00.000000000Z"

        dbA.ledgerDao().insert(
            LedgerEntity(
                id = ledgerId,
                name = "设备端账本",
                icon = "wallet",
                color = "#3B82F6",
                isDefault = true,
                position = 0,
                createdAt = seedAt,
                updatedAt = seedAt,
            ),
        )
        dbA.categoryDao().insert(
            CategoryEntity(
                id = categoryId,
                ledgerId = ledgerId,
                name = "餐饮",
                icon = "restaurant",
                color = "#4CAF50",
                type = "expense",
                isBuiltIn = false,
                order = 1,
                createdAt = seedAt,
                updatedAt = seedAt,
            ),
        )
        dbA.transactionDao().insertAll(
            listOf(
                tx(t1, ledgerId, categoryId, 1200L, "午饭", seedAt),
                tx(t2, ledgerId, categoryId, 3450L, "晚饭", seedAt),
                tx(t3, ledgerId, categoryId, 5000L, "退款", seedAt),
            ),
        )

        // ---- ① A 首次双向：5 条上行 ----
        val firstA = expectReady("A 首次预演", useA.preview(SyncMode.BOTH))
        assertEquals("上行应含 账本+分类+3 笔交易", 5, firstA.plan.uploads.size)
        assertTrue("首次同步不该有本地删除", firstA.plan.localDeletes.isEmpty())
        assertTrue("首次同步不该有冲突", firstA.plan.conflicts.isEmpty())
        val runA = useA.execute(firstA.plan, firstA.nextCursor)
        assertTrue("A 首次同步应成功，实际：${runA.error}", runA.ok)
        assertEquals(5, runA.uploaded)
        assertEquals(0, runA.downloaded)
        // 实测口径（别写成「游标应 >0」）：首次双向的预览发生在推送之前，云端当时为空
        // ⇒ nextCursor = 0 ⇒ 游标**不前进**，这是安全设计：游标只由 pull 推进，绝不按 push
        // 的 server_seq 前跳——否则并发设备在该窗口推的行会被永久跳过（静默丢数据）。
        // 代价仅是下次同步把刚推的记录再拉一遍（幂等、无害）。
        assertEquals("推送后应写入 5 条基线（下次才不会重复上传）", 5, dbA.syncStateDao().count())

        // ---- ② 设备 B「仅下载」：条数与金额合计必须与 A 一致 ----
        val firstB = expectReady("B 仅下载预演", useB.preview(SyncMode.DOWNLOAD_ONLY))
        assertEquals("应拉到 5 条", 5, firstB.plan.downloads.size)
        assertTrue("仅下载不得删本地多余行", firstB.plan.localDeletes.isEmpty())
        val runB = useB.execute(firstB.plan, firstB.nextCursor)
        assertTrue("B 仅下载应成功，实际：${runB.error}", runB.ok)
        assertEquals(5, runB.downloaded)
        assertTrue("B 是拉到了真实记录，游标应推进", storeB.serverCursor() > 0)

        val aTx = dbA.syncDao().transactionsSince("")
        val bTx = dbB.syncDao().transactionsSince("")
        assertEquals("两侧条数一致", aTx.size, bTx.size)
        assertEquals("两侧金额合计一致", aTx.sumOf { it.amount }, bTx.sumOf { it.amount })
        assertEquals("账本也应落到 B", 1, dbB.syncDao().ledgersSince("").size)
        assertEquals("分类也应落到 B", 1, dbB.syncDao().categoriesSince("").size)

        // ---- ③ 冲突：两端各改同一笔（B 的更新），A 先同步，B 后同步 ----
        val aEditAt = "2026-10-10T10:00:00.000000000Z"
        val bEditAt = "2026-10-10T11:00:00.000000000Z"
        dbA.syncDao().upsertTransaction(tx(t1, ledgerId, categoryId, 2000L, "午饭-A改", aEditAt))
        dbB.syncDao().upsertTransaction(tx(t1, ledgerId, categoryId, 3000L, "午饭-B改", bEditAt))

        val secondA = expectReady("A 第二次预演", useA.preview(SyncMode.BOTH))
        assertEquals("只该上传被改的那 1 条（差分推送生效）", 1, secondA.plan.uploads.size)
        assertTrue(useA.execute(secondA.plan, secondA.nextCursor).ok)

        val conflictB = expectReady("B 冲突预演", useB.preview(SyncMode.BOTH))
        assertTrue("预演应判定出冲突", conflictB.plan.conflicts.isNotEmpty())
        val runB2 = useB.execute(conflictB.plan, conflictB.nextCursor)
        assertTrue("B 同步应成功，实际：${runB2.error}", runB2.ok)
        assertTrue("冲突应留档（输家副本不丢）", dbB.syncConflictDao().unresolvedCount() >= 1)
        val archived = dbB.syncConflictDao().recent(5).first()
        assertEquals("留档应指向被改的那条", t1, archived.entityId)
        assertTrue("留档应含两份副本原文", archived.localJson.isNotEmpty() && archived.remoteJson.isNotEmpty())

        // ---- ④ A 再同步一次 → 两端最终一致（更新的一方胜出）----
        val thirdA = expectReady("A 第三次预演", useA.preview(SyncMode.BOTH))
        val runA3 = useA.execute(thirdA.plan, thirdA.nextCursor)
        assertTrue("A 再同步应成功，实际：${runA3.error}", runA3.ok)
        val amountA = dbA.syncDao().transactionsSince("").first { it.id == t1 }.amount
        val amountB = dbB.syncDao().transactionsSince("").first { it.id == t1 }.amount
        assertEquals("两端最终一致", amountB, amountA)
        assertEquals("更新的一方（B，3000）应胜出", 3000L, amountA)

        // ---- ⑤ 断网：游标与本地数据都不许动；恢复后重试成功 ----
        val cursorBefore = storeA.serverCursor()
        val txBefore = dbA.syncDao().transactionsSince("")
        storeA.setBaseUrl("http://10.0.2.2:9") // 无服务端口 → 连接被拒（快速失败）
        val offline = useA.preview(SyncMode.BOTH)
        assertTrue("断网应给出可读失败（实际：$offline）", offline is SyncPreview.Failed)
        val failed = offline as SyncPreview.Failed
        assertTrue("失败原因应可读（实际：${failed.message}）", failed.message.isNotBlank())
        assertEquals("断网不得推进游标", cursorBefore, storeA.serverCursor())
        val txAfter = dbA.syncDao().transactionsSince("")
        assertEquals("断网不得改本地条数", txBefore.size, txAfter.size)
        assertEquals("断网不得改本地金额", txBefore.sumOf { it.amount }, txAfter.sumOf { it.amount })

        storeA.setBaseUrl(hostApi)
        val retry = expectReady("恢复后预演", useA.preview(SyncMode.BOTH))
        val runRetry = useA.execute(retry.plan, retry.nextCursor)
        assertTrue("恢复后重试应成功，实际：${runRetry.error}", runRetry.ok)

        dbA.close()
        dbB.close()
    }

    // ---------- 装配 ----------

    private fun newDb(context: Context): MiniBookDatabase =
        Room.inMemoryDatabaseBuilder(context, MiniBookDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    /** 真 Keystore 包裹 + 独立 DataStore 文件（不碰 App 自己的同步状态）。 */
    private fun newStore(context: Context, name: String): SyncAuthStore = SyncAuthStore(
        PreferenceDataStoreFactory.create(
            produceFile = { File(context.filesDir, "$name.preferences_pb") },
        ),
        KeystoreSecretCipher(),
    )

    /** 生产 [SyncUseCase]，只把「本地库」换成内存库替身（`DatabaseProvider` 接口的本意）。 */
    private fun newUseCase(
        context: Context,
        store: SyncAuthStore,
        db: MiniBookDatabase,
        remote: SyncApi,
        json: Json,
        exportAll: ExportAllUseCase,
    ): SyncUseCase = SyncUseCase(
        store = store,
        authenticator = SyncTokenAuthenticator(store) { baseUrl, request -> remote.refresh(baseUrl, request) },
        remote = remote,
        databaseProvider = object : DatabaseProvider {
            override val database: MiniBookDatabase = db
        },
        exportAll = exportAll,
        stateDao = db.syncStateDao(),
        conflictDao = db.syncConflictDao(),
        syncDao = db.syncDao(),
        json = json,
        appContext = context,
    )

    private fun tx(
        id: String,
        ledgerId: String,
        categoryId: String,
        amount: Long,
        description: String,
        at: String,
    ): TransactionEntity = TransactionEntity(
        id = id,
        ledgerId = ledgerId,
        type = "expense",
        amount = amount,
        categoryId = categoryId,
        description = description,
        date = "2026-10-10",
        createdAt = at,
        updatedAt = at,
    )

    private fun expectReady(what: String, preview: SyncPreview): SyncPreview.Ready {
        assertTrue("$what 应成功，实际：$preview", preview is SyncPreview.Ready)
        return preview as SyncPreview.Ready
    }

    private fun apiReachable(): Boolean = runCatching {
        OkHttpClient().newCall(Request.Builder().url("$hostApi/healthz").get().build())
            .execute()
            .use { it.isSuccessful }
    }.getOrDefault(false)

    private companion object {
        const val PASSWORD = "E2e!Passw0rd"
    }
}
