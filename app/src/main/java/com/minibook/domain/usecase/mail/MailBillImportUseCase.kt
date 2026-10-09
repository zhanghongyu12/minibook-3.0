package com.minibook.domain.usecase.mail

import com.minibook.data.local.mail.ImapMailClient
import com.minibook.data.local.mail.MailAccountStore
import com.minibook.di.IoDispatcher
import com.minibook.domain.model.BillPlatform
import com.minibook.domain.model.MailAccount
import com.minibook.domain.model.MailBillItemResult
import com.minibook.domain.model.MailImportOutcome
import com.minibook.domain.model.MailImportProgress
import com.minibook.domain.model.MailImportStep
import com.minibook.domain.model.MailImportStepState
import com.minibook.domain.model.MailImportStepStatus
import com.minibook.domain.model.MailImportSummary
import com.minibook.domain.model.displayLabel
import com.minibook.domain.model.toResultLine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * 「一键收取」邮件账单编排（F-13 编排层，多账号 + 分步骤进度增强）。
 *
 * 一次调用 = 逐账号读凭据 → IMAP 拉取 → 本地过滤账单邮件 → 逐封处理（单封链路委托
 * [MailItemProcessor]：取件 → ZipCrypto 6 位数字口令穷举 → 解压 → 送入导入链路）→ 汇总。
 * 每个账号各走一遍 5 步（CONNECT→FETCH→FILTER→PROCESS→DONE），每步经 [onProgress] 发
 * **完整快照**（[MailImportProgress]，UI 直接渲染 stepper）；步骤状态**累积**
 * （[MailImportStepTracker]：已发出的步骤状态在后续快照中保持，未出现过的步骤为
 * PENDING，不会把已完成步骤打回待执行）；已完成账号的逐封结果累积进快照的
 * `itemLines`。
 *
 * 编排纪律：
 * 1. **凭据不可用不抛异常**：`accountIds` 为空 / store 无账号 → 返回
 *    [MailImportOutcome.NotConfigured]，由 UI 引导去设置页；**授权码绝不出现在任何
 *    异常 message、日志或失败信息中**。
 * 2. **单账号解析失败不静默跳过**：`accountIds` 中的 id 在 store 里找不到 / 授权码解
 *    不出（`getAuthCode` 返回 null）→ 在 `itemLines` 留一行 ❌ 说明并继续下一账号。
 *    跳过账号同样发快照（连接步 FAILED + 原因），保证 UI 可见。
 * 3. **连接/网络异常向上抛**：[com.minibook.data.local.mail.MailAuthException] /
 *    [com.minibook.data.local.mail.MailNetworkException]（message 已保证不含凭据）不做
 *    吞没，由 UI 映射文案。
 * 4. **单封失败不中断整批**：[MailItemProcessor.process] 内兜底为
 *    [com.minibook.domain.model.MailItemFailure.Unexpected]，继续处理下一封。
 * 5. **临时文件必清**：每账号的 zip 落盘文件与解压产物登记进 `tempFiles`，在同一个
 *    `try/finally` 中删除（成功/失败/异常/取消皆覆盖）。
 * 6. **写账本发生在第 4 步**：第 5 步（DONE）只发汇总快照，不再有任何写操作。
 * 7. 不新增网络请求（仅既有的微信链接下载，见 [MailItemProcessor]）。
 * 8. **每种账单只取最近一封**：[MailBillSelector] 在 FILTER 后按平台各保留
 *    `sentDateMillis` 最新的一封，同平台的旧邮件不处理并在 `itemLines` 留 ⏭ 行说明
 *    （FILTER 摘要同时披露「命中 N 封 … → 待处理 K 封」）。本步只做选取，
 *    **不删除任何已导入数据**。
 *
 * 步骤口径说明：`ImapMailClient.fetchBillMails` 一次调用贯通「连接 + 拉取 + 本地过滤 +
 * 命中取件」（服务端 SEARCH 被禁用，见其类 KDoc），无法观察三步边界，故 CONNECT/FETCH/
 * FILTER 三步在客户端调用期间**并发展示**（三步同时 RUNNING），调用返回后各自补 DONE
 * 摘要（连接方式 / 窗口内邮件数 / 命中数分平台计数）。
 *
 * 文件 I/O 在 [@IoDispatcher] 上执行；穷举器内部另用 Default 并发分片，CRACK 百分比
 * 在 [MailItemProcessor] 内经原子 CAS 节流（整数变化才回调，避免高频刷 UI）。
 */
@Singleton
class MailBillImportUseCase @Inject constructor(
    private val accountStore: MailAccountStore,
    private val mailClient: ImapMailClient,
    private val itemProcessor: MailItemProcessor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * 执行一轮「一键收取」（可跨多个邮箱，按 [accountIds] 顺序逐账号处理）。
     *
     * @param ledgerId 目标账本 id（导入链路用）
     * @param accountIds 要收取的账号 id 列表（UI 侧「全部收取」= 全部 id 一次传入）
     * @param sinceDays 拉取时间窗口（天），默认 30
     * @param onProgress 进度回调：每步/每封/每个子步骤都发完整 [MailImportProgress] 快照
     *   （步骤状态累积：已发出的步骤状态保持，未出现过的步骤为 PENDING）
     * @return [MailImportOutcome.NotConfigured]（未配置任何账号 / accountIds 为空）或
     *   [MailImportOutcome.Completed]（含跨账号汇总的逐封结果）
     * @throws com.minibook.data.local.mail.MailAuthException 认证失败（向上透传）
     * @throws com.minibook.data.local.mail.MailNetworkException 网络/超时/协议异常（向上透传）
     */
    suspend operator fun invoke(
        ledgerId: String,
        accountIds: List<String>,
        sinceDays: Int = 30,
        onProgress: (MailImportProgress) -> Unit = {},
    ): MailImportOutcome = withContext(ioDispatcher) {
        val configured = accountStore.accounts.value
        if (configured.isEmpty() || accountIds.isEmpty()) {
            return@withContext MailImportOutcome.NotConfigured
        }

        val itemLines = mutableListOf<String>()
        val allItems = mutableListOf<MailBillItemResult>()
        var totalWindowCount = 0
        var totalBillCount = 0
        var cumulativeImported = 0
        var cumulativeExisting = 0

        accountIds.forEachIndexed { index, accountId ->
            val account = configured.firstOrNull { it.id == accountId }
            if (account == null) {
                itemLines += "❌ 邮箱账号不存在（可能已被删除）— 已跳过该邮箱"
                emitSkipSnapshot(
                    onProgress = onProgress,
                    accountIndex = index + 1,
                    accountCount = accountIds.size,
                    email = null,
                    reason = "邮箱账号不存在（可能已被删除），已跳过该邮箱",
                    itemLines = itemLines,
                )
                return@forEachIndexed
            }
            val authCode = accountStore.getAuthCode(accountId)
            if (authCode == null) {
                itemLines += "❌ 邮箱 ${account.email} — 授权码不可用（解密失败），已跳过该邮箱"
                emitSkipSnapshot(
                    onProgress = onProgress,
                    accountIndex = index + 1,
                    accountCount = accountIds.size,
                    email = account.email,
                    reason = "授权码不可用（解密失败），已跳过该邮箱",
                    itemLines = itemLines,
                )
                return@forEachIndexed
            }
            val stats = processAccount(
                ledgerId = ledgerId,
                account = account,
                authCode = authCode,
                sinceDays = sinceDays,
                accountIndex = index + 1,
                accountCount = accountIds.size,
                cumulativeImported = cumulativeImported,
                cumulativeExisting = cumulativeExisting,
                itemLines = itemLines,
                itemsOut = allItems,
                onProgress = onProgress,
            )
            totalWindowCount += stats.windowMailCount
            totalBillCount += stats.billMailCount
            cumulativeImported += stats.imported
            cumulativeExisting += stats.existing
        }

        MailImportOutcome.Completed(
            MailImportSummary(
                totalMailCount = totalWindowCount,
                billMailCount = totalBillCount,
                items = allItems.toList(),
            ),
        )
    }

    /**
     * 被跳过账号的进度快照：stepper 将「连接邮箱」标为 FAILED 并附原因（该账号未能开始收取），
     * 其余步骤 PENDING；同时把累积 [itemLines] 带到 UI，避免跳过信息静默丢失。
     */
    private fun emitSkipSnapshot(
        onProgress: (MailImportProgress) -> Unit,
        accountIndex: Int,
        accountCount: Int,
        email: String?,
        reason: String,
        itemLines: List<String>,
    ) {
        onProgress(
            MailImportProgress(
                steps = MailImportStep.entries.map { step ->
                    if (step == MailImportStep.CONNECT) {
                        MailImportStepState(step, MailImportStepStatus.FAILED, reason)
                    } else {
                        MailImportStepState(step, MailImportStepStatus.PENDING)
                    }
                },
                accountEmail = email,
                accountIndex = accountIndex,
                accountCount = accountCount,
                itemLines = itemLines.toList(),
            ),
        )
    }

    // ------------------------------------------------------------------ 单账号编排

    /** 单账号一轮处理的产出计数（供跨账号汇总累加）。 */
    private data class AccountStats(
        val windowMailCount: Int,
        val billMailCount: Int,
        val imported: Int,
        val existing: Int,
    )

    /**
     * 单账号走完 5 步（CONNECT→FETCH→FILTER→PROCESS→DONE），逐封处理并累积
     * [MailBillItemResult]；[com.minibook.data.local.mail.MailAuthException] /
     * [com.minibook.data.local.mail.MailNetworkException] 向上抛。
     */
    private suspend fun processAccount(
        ledgerId: String,
        account: MailAccount,
        authCode: String,
        sinceDays: Int,
        accountIndex: Int,
        accountCount: Int,
        cumulativeImported: Int,
        cumulativeExisting: Int,
        itemLines: MutableList<String>,
        itemsOut: MutableList<MailBillItemResult>,
        onProgress: (MailImportProgress) -> Unit,
    ): AccountStats {
        val email = account.email

        // 步骤状态累加器：每账号一份（本函数体内创建，多账号时每账号 stepper 从头
        // PENDING，沿用既有口径）。已发出的步骤状态在后续快照中保持，未出现过的
        // 步骤为 PENDING——修复「成功后 stepper 显示 ○○○○✓」的回退缺陷。
        val stepTracker = MailImportStepTracker()

        /** 发一份完整快照（步骤状态累积：已发出的保持上次状态，未出现过的为 PENDING）。 */
        fun snap(
            vararg active: MailImportStepState,
            mailIndex: Int = 0,
            mailCount: Int = 0,
            subject: String? = null,
            itemStep: com.minibook.domain.model.MailItemStep? = null,
            crackPercent: Int? = null,
        ) {
            onProgress(
                MailImportProgress(
                    steps = stepTracker.apply(*active),
                    accountEmail = email,
                    accountIndex = accountIndex,
                    accountCount = accountCount,
                    mailIndex = mailIndex,
                    mailCount = mailCount,
                    currentSubject = subject,
                    currentItemStep = itemStep,
                    crackPercent = crackPercent,
                    itemLines = itemLines.toList(),
                ),
            )
        }

        // ---- 步骤 1~3：客户端一次调用贯通（见类 KDoc 步骤口径），三步并发展示 ----
        snap(MailImportStepState(MailImportStep.CONNECT, MailImportStepStatus.RUNNING))
        snap(
            MailImportStepState(MailImportStep.CONNECT, MailImportStepStatus.RUNNING),
            MailImportStepState(MailImportStep.FETCH, MailImportStepStatus.RUNNING),
            MailImportStepState(MailImportStep.FILTER, MailImportStepStatus.RUNNING),
        )
        // MailAuthException / MailNetworkException 向上抛（message 不含凭据）。
        val fetch = mailClient.fetchBillMails(email, authCode, sinceDays)
        // 每种账单（平台）只取最近一封：同平台旧邮件不处理，避免区间重叠的重复导入与
        // 多一次口令穷举耗时；本步只做选取，不删除任何已导入数据。
        val selection = MailBillSelector.select(fetch.billMails)
        val mails = selection.kept
        val alipayCount = fetch.billMails.count { it.header.platform == BillPlatform.ALIPAY }
        val wechatCount = fetch.billMails.size - alipayCount
        selection.skipped.forEach { skippedMail ->
            itemLines += "⏭ ${skippedMail.header.platform.displayLabel()} · " +
                "${skippedMail.header.subject} — 已有更新的同类账单，跳过"
        }
        val filterSummary = if (selection.skipped.isEmpty()) {
            "命中 ${selection.hitCount} 封（微信 $wechatCount / 支付宝 $alipayCount）"
        } else {
            "命中 ${selection.hitCount} 封（微信 $wechatCount / 支付宝 $alipayCount）" +
                "，每种账单只取最近一封 → 待处理 ${mails.size} 封"
        }
        snap(
            MailImportStepState(MailImportStep.CONNECT, MailImportStepStatus.DONE, "已连接（IMAP/SSL）"),
            MailImportStepState(MailImportStep.FETCH, MailImportStepStatus.DONE, "窗口内共 ${fetch.windowMailCount} 封"),
            MailImportStepState(MailImportStep.FILTER, MailImportStepStatus.DONE, filterSummary),
        )

        // ---- 步骤 4：逐封处理（写账本实际发生在这里，每封一个子步骤链）----
        snap(MailImportStepState(MailImportStep.PROCESS, MailImportStepStatus.RUNNING))
        val tempFiles = mutableListOf<File>()
        var succeeded = 0
        var failed = 0
        var imported = 0
        var existing = 0
        try {
            mails.forEachIndexed { index, mail ->
                val result = itemProcessor.process(
                    mail = mail,
                    accountEmail = account.email,
                    ledgerId = ledgerId,
                    tempFiles = tempFiles,
                ) { step, percent ->
                    snap(
                        MailImportStepState(MailImportStep.PROCESS, MailImportStepStatus.RUNNING),
                        mailIndex = index + 1,
                        mailCount = mails.size,
                        subject = mail.header.subject,
                        itemStep = step,
                        crackPercent = percent,
                    )
                }
                itemsOut += result
                itemLines += result.toResultLine()
                if (result.failure == null) {
                    succeeded++
                    imported += result.importResult?.success ?: 0
                    existing += result.importResult?.existing ?: 0
                } else {
                    failed++
                }
            }
        } finally {
            tempFiles.forEach { file -> runCatching { file.delete() } }
        }
        snap(
            MailImportStepState(MailImportStep.PROCESS, MailImportStepStatus.DONE, "成功 $succeeded 封 / 失败 $failed 封"),
            mailIndex = mails.size,
            mailCount = mails.size,
        )

        // ---- 步骤 5：完成汇总（只发快照，无任何写操作）----
        snap(
            MailImportStepState(
                MailImportStep.DONE,
                MailImportStepStatus.DONE,
                "新导入 ${cumulativeImported + imported} 条 / 已存在 ${cumulativeExisting + existing} 条",
            ),
        )
        return AccountStats(
            windowMailCount = fetch.windowMailCount,
            billMailCount = selection.hitCount,
            imported = imported,
            existing = existing,
        )
    }
}
