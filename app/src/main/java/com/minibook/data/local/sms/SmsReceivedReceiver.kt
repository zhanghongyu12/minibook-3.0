package com.minibook.data.local.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.minibook.data.local.datastore.SettingsDataStore
import com.minibook.data.local.security.AppLockController
import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.model.SmsMessageRow
import com.minibook.domain.usecase.sms.SmsBillImportUseCase
import com.minibook.domain.usecase.sms.SmsIncomingOutcome
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 新短信接收器（F-14 L1/L2；设计 §3.5）。
 *
 * 关键约束：
 * - **只处理本条投递**（`goAsync()` ≤10s，绝不触发全量扫描；完整性由 L3 水位补扫兜底）；
 * - **不抢默认短信应用角色**（`SMS_RECEIVED` 是隐式广播豁免项，静态注册的接收器在非默认短信应用
 *   下同样能收到；只读，不改通知栏、不写短信库）；
 * - **只在总开关开启时动作**；未开启/无账本/异常 → 静默返回，不打日志、不发通知；
 * - **写库需三道闸全过**：`enabled && autoImportEnabled && !appLockEnabled`（口径见
 *   `SmsSettings.autoImportUsable`）**且**后台静默开库成功；任一不满足只累计「待确认」。
 */
@AndroidEntryPoint
class SmsReceivedReceiver : BroadcastReceiver() {

    @Inject lateinit var importUseCase: SmsBillImportUseCase

    @Inject lateinit var settingsStore: SmsSettingsStore

    @Inject lateinit var settingsDataStore: SettingsDataStore

    @Inject lateinit var notificationHelper: SmsNotificationHelper

    @Inject lateinit var ledgerProvider: LedgerProvider

    @Inject lateinit var appLockController: AppLockController

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = extractMessages(intent)
        if (messages.isEmpty()) return

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                handle(messages)
            } catch (t: Throwable) {
                // 接收器绝不因业务异常崩溃（系统会杀进程）；静默降级，等 L3 补扫兜底。
                // 诊断只记异常类型（不记异常 message，避免间接带上短信内容/号码）。
                Log.w(TAG, "SMS 广播处理异常：${t.javaClass.name}")
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }

    private suspend fun handle(messages: List<SmsMessageRow>) {
        val settings = settingsStore.current()
        if (!settings.enabled || !settings.realtimeEnabled) return

        val appLockEnabled = settingsDataStore.appLockEnabled.first()
        val ledgerId = runCatching { ledgerProvider.currentLedgerId() }.getOrNull() ?: return

        // 自动记账总闸（**默认关闭**）：口径与 L3 补扫一致 ——
        // `enabled && autoImportEnabled && !appLockEnabled`（`SmsSettings.autoImportUsable`）。
        // ⚠️ 实测缺陷修复（2026-10-10）：此前只判 `!appLockEnabled`，**漏了用户显式开启的
        // 「自动记账（仅白名单发送方）」开关** —— 一旦白名单晋级，用户没开这个闸也会后台写库，
        // 属于「不记错」红线问题。现改为单一权威口径。
        val autoImportUsable = settings.autoImportUsable(appLockEnabled)

        // ⚠️ 实测缺陷修复（模拟器 + 真机，2026-10-10）：
        // `SMS_RECEIVED` 唤起的是**新进程**，没人走过 UI 的解锁流程，`DatabaseGate` 未 open；
        // 此时任何 DB 访问都会抛 `IllegalStateException`（DAO provider / RoomDataSourceImpl 的
        // `?: error("DatabaseGate 未解锁/未打开…")`），并被本类的 catch 吞掉 ——
        // 表现为「后台整条链路静默失效」，很难查。
        // 处置：**仅在自动记账可能生效时**按 DEC-002「锁关路径」静默开库（与关锁启动同一路径，
        // 含首启无 blob 的 plain 包裹生成）；应用锁开启 / 用户未开自动记账 → 不开库。
        if (autoImportUsable && appLockController.isLocked.value) {
            runCatching { appLockController.unlock(useAuth = false) }
        }
        // 写库前提：总闸可写 + 已成功开库（开库失败则降级为「待确认」，由 L3 补扫兜底）。
        val allowAutoImport = autoImportUsable && !appLockController.isLocked.value

        var pendingCount = 0
        var autoImported = 0
        var lastDescription: String? = null
        var firstPendingReason: String? = null

        for (message in messages) {
            when (val outcome = importUseCase.handleIncoming(message, ledgerId, allowAutoImport = allowAutoImport)) {
                is SmsIncomingOutcome.AutoImported -> {
                    autoImported++
                    lastDescription = outcome.row.description
                }
                is SmsIncomingOutcome.Pending -> {
                    pendingCount++
                    if (firstPendingReason == null) firstPendingReason = outcome.item.reason
                }
                SmsIncomingOutcome.NotBill -> Unit // 零提醒
            }
        }

        // 诊断口径（真机/模拟器可验证性）：只记**结果计数与策略原因**，
        // 绝不记短信正文、发送方号码、卡尾号、余额（DEC-026）。
        Log.i(
            TAG,
            "SMS 广播处理：投递 ${messages.size} 条，自动 $autoImported，待确认 $pendingCount，" +
                "可写=$allowAutoImport，原因=${firstPendingReason ?: "-"}",
        )

        notificationHelper.ensureChannels()
        if (autoImported > 0 && lastDescription != null) {
            notificationHelper.notifyAutoImported(lastDescription, settings.notifyMode)
        }
        if (pendingCount > 0) {
            notificationHelper.notifyPending(pendingCount, settings.notifyMode)
        }
    }

    /**
     * 从广播取出本条投递的短信（只读字段，不碰 `content://sms`）。
     *
     * `contentHash` 与读取层同式：`sha256(address|receivedAtMs|规范化正文)`，
     * 但广播无 `_id`（用 -1 占位）与 `sub_id`（读 `subscription` 附加字段，缺失为 null）。
     */
    private fun extractMessages(intent: Intent): List<SmsMessageRow> {
        val pdus = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return emptyList()
        return pdus.mapNotNull { sms ->
            val body = sms.displayMessageBody ?: return@mapNotNull null
            val address = sms.displayOriginatingAddress ?: return@mapNotNull null
            val receivedAtMs = sms.timestampMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
            val contentHash = SmsBillReader.contentHashOf(
                address = address,
                receivedAtMs = receivedAtMs,
                body = body,
            )
            SmsMessageRow(
                id = -1L,
                address = address,
                receivedAtMs = receivedAtMs,
                type = 1, // 广播只可能是收件箱消息
                subId = null,
                body = body,
                contentHash = contentHash,
            )
        }
    }

    companion object {
        /** 诊断日志 TAG（只记结果计数/异常类型，不记短信内容）。 */
        const val TAG = "MiniBookSms"
    }
}
