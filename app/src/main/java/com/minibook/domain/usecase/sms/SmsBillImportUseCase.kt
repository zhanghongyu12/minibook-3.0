package com.minibook.domain.usecase.sms

import com.minibook.data.datasource.TransactionDataSource
import com.minibook.data.local.datastore.SettingsDataStore
import com.minibook.data.local.sms.SmsBillReader
import com.minibook.data.local.sms.SmsSettingsStore
import com.minibook.data.local.sms.SmsWhitelistStore
import com.minibook.data.local.security.DatabaseProvider
import com.minibook.di.IoDispatcher
import com.minibook.domain.model.BillImportResult
import com.minibook.domain.model.SmsAmbiguousHit
import com.minibook.domain.model.SmsDuplicateHit
import com.minibook.domain.model.SmsImportSummary
import com.minibook.domain.model.SmsMessageRow
import com.minibook.domain.model.SmsParseOutcome
import com.minibook.domain.model.SmsPendingItem
import com.minibook.domain.model.SmsScanMode
import com.minibook.domain.model.SmsScanPreview
import com.minibook.domain.model.SmsSenderSummary
import com.minibook.domain.model.SmsWhitelistEntry
import com.minibook.domain.model.TransactionSource
import com.minibook.domain.usecase.bill.ImportBillUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 短信账单导入编排（F-14，设计 §3.1 / §3.3；DEC-025）。
 *
 * 三段职责，全部复用既有链路：
 * 1. **扫描（零写入）**：读短信 → 模板匹配 → 解析 → 同源幂等判定 → 跨源判重 → 余额链审计
 *    → [SmsScanPreview]（UI 干跑预览）；
 * 2. **确认入库**：把预览中「可导入」的行交给 `ImportBillUseCase.importParsedRows`
 *    （单事务 + `IGNORE` 幂等，**不新建第二条写库路径**），并回填 [SmsImportSummary]（计数闭合）；
 * 3. **实时（L1/L2）**：单条短信 → 解析 → 白名单四条件 → 自动写入或进「待确认」。
 *
 * 计数闭合不变量由 [SmsImportSummary] / [SmsScanPreview.assertClosed] 强制（设计 G1）。
 */
@Singleton
class SmsBillImportUseCase @Inject constructor(
    private val reader: SmsBillReader,
    private val matcher: SmsBillMatcher,
    private val importBillUseCase: ImportBillUseCase,
    private val transactionDataSource: TransactionDataSource,
    private val whitelistStore: SmsWhitelistStore,
    private val settingsStore: SmsSettingsStore,
    private val settingsDataStore: SettingsDataStore,
    private val databaseProvider: DatabaseProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * 干跑扫描（**零写入账单**）。两种模式（2026-10-10 用户要求，见 [SmsScanMode]）：
     *
     * - [SmsScanMode.FULL]（L0 全量）：读全部历史；**不推进水位** ⇒ 历史永远可被全量重见；
     * - [SmsScanMode.INCREMENTAL]（L0 增量）：只读水位**之后**的短信（[SmsBillReader.readAfter]，
     *   严格不重叠）；**读完即推进水位** —— 这是「增量」的语义前提（下次不再把看过的翻出来）。
     *   首次（无水位）只立基线（水位 = 库内最新一条）并返回空扫描，与 L3 补扫首次口径一致。
     *
     * 不遗漏的口径不变：增量跳过的一切，仍可由全量扫描列出（含折叠区里的「已看过」）。
     */
    suspend fun scan(ledgerId: String, mode: SmsScanMode = SmsScanMode.FULL): SmsScanPreview =
        withContext(ioDispatcher) {
            val incremental = mode == SmsScanMode.INCREMENTAL
            // **时间节点 = 本次扫描的墙钟时间**（2026-10-10 用户口径：增量起点应是「扫描的时间」，不是
            // 最后一条短信的时间 —— 后者会显示成一条短信的日期，且**空库时永远立不起水位**）。
            // 在读之前取：读完到写水位之间到达的短信（时间戳晚于本节点）下次仍会被读到，不丢件。
            val scanAtMs = if (incremental) System.currentTimeMillis() else 0L
            val watermark = if (incremental) settingsStore.current().lastWatermarkMs else null
            val messages = when {
                !incremental -> reader.readAll()
                watermark == null -> {
                    // 首次增量：以「本次扫描时间」为起点，不回灌历史（历史请走全量扫描）
                    settingsStore.advanceWatermark(scanAtMs)
                    emptyList()
                }
                else -> reader.readAfter(watermark)
            }
            // 增量：读完把节点推进到**本次扫描时间**（不是最后一条短信的时间）；全量不动水位。
            if (incremental && watermark != null) {
                settingsStore.advanceWatermark(scanAtMs)
            }
            val existingIds = transactionDataSource.getExistingExternalIds(ledgerId)
            // 「已看过」水位（2026-10-10 用户要求）：早于该时刻的未识别短信 = 上次已展示过 → 折叠展示。
            val ackMs = settingsStore.unrecognizedAckMs()

            val importable = mutableListOf<SmsParseOutcome.Parsed>()
            val existing = mutableListOf<SmsParseOutcome.Parsed>()
            val duplicates = mutableListOf<SmsDuplicateHit>()
            val ambiguous = mutableListOf<SmsAmbiguousHit>()
            val unrecognized = mutableListOf<SmsPendingItem>()
            val notBillReasons = mutableListOf<String>()
            val parsedAll = mutableListOf<SmsParseOutcome.Parsed>()
            // 导入来源（docs/15 第 2 条，2026-10-13）：「允许导入」被关掉的发送方，其短信一律
            // **不解析、不计入账单**，只往「非账单」计数里记一条理由（计数闭合不变、不静默消失）。
            val noImport = whitelistStore.entries().filterNot { it.importEnabled }
                .mapTo(mutableSetOf()) { it.sender }
            val senderAgg = linkedMapOf<String, SmsSenderAggregate>()

            for (message in messages) {
                val agg = senderAgg.getOrPut(message.address) { SmsSenderAggregate() }
                agg.totalCount++
                agg.lastAtMs = maxOf(agg.lastAtMs, message.receivedAtMs)
                if (agg.institution == null) {
                    agg.institution = SmsSenderNamer.name(message.address, message.body)
                }
                if (message.address in noImport) {
                    notBillReasons += "来源未勾选（${message.address}）"
                    continue
                }
                val outcome = parseMessage(message)
                if (outcome is SmsParseOutcome.Parsed) agg.billCount++
                when (outcome) {
                    is SmsParseOutcome.NotBill -> notBillReasons += outcome.reason
                    is SmsParseOutcome.Unrecognized -> unrecognized += SmsPendingItem(
                        id = message.id,
                        sender = message.address,
                        receivedAtMs = message.receivedAtMs,
                        preview = SmsBillParser.previewOf(message),
                        reason = outcome.reason,
                        seen = message.receivedAtMs <= ackMs,
                    )
                    is SmsParseOutcome.Parsed -> {
                        parsedAll += outcome
                        if (outcome.row.externalId in existingIds) {
                            existing += outcome
                        } else {
                            when (val decision = matcher.decide(ledgerId, outcome.row, TransactionSource.SMS_IMPORT)) {
                                is SmsMatchDecision.Duplicate -> duplicates += SmsDuplicateHit(
                                    incoming = outcome.row,
                                    matchedTransactionId = decision.matched.id,
                                    matchedSource = decision.matched.source,
                                    matchedDate = decision.matched.date,
                                    reason = decision.reason,
                                )
                                is SmsMatchDecision.Ambiguous -> ambiguous += SmsAmbiguousHit(
                                    incoming = outcome.row,
                                    candidates = decision.candidates,
                                )
                                SmsMatchDecision.New -> importable += outcome
                            }
                        }
                    }
                }
            }

            // 来源清单（docs/15 第 2 条）：把**真出过账单**的发送方登记进来源配置（默认允许导入），
            // 只新增不覆盖；纯通知/营销号不登记，避免把来源列表撑成垃圾堆。
            whitelistStore.rememberSenders(senderAgg.filterValues { it.billCount > 0 }.keys)

            val preview = SmsScanPreview(
                scanTotal = messages.size,
                importable = importable,
                existing = existing,
                duplicates = duplicates,
                ambiguous = ambiguous,
                unrecognized = unrecognized,
                notBillReasons = notBillReasons,
                balanceGaps = BalanceChainAuditor.audit(parsedAll),
                scanMode = mode,
                sinceMs = if (incremental) watermark ?: 0L else 0L,
                scannedAtMs = scanAtMs,
                senders = SmsSenderSummaryBuilder.build(senderAgg, noImport),
            )
            preview.assertClosed()
            preview
        }

    /** 解析单条（模板匹配 + 解析）。 */
    fun parseMessage(message: SmsMessageRow): SmsParseOutcome {
        val template = SmsTemplateRegistry.match(message.address, message.body)
        return SmsBillParser.parse(message, template)
    }

    /**
     * 用户确认后入库（L0/L3）：只写「可导入」行；其余分类原样回填进汇总（计数闭合）。
     *
     * 副作用：对本次涉及的发送方各记一次「手动确认」（白名单晋级依据；**每次导入动作每个发送方
     * 至多 +1**，避免一次批量导入把发送方直接顶上自动档——自主决定，见 `.claude-runs/F14-PROGRESS.md`）。
     */
    suspend fun importConfirmed(preview: SmsScanPreview, ledgerId: String): SmsImportSummary =
        withContext(ioDispatcher) {
            val rows = preview.importable.map { it.row }
            val result: BillImportResult =
                importBillUseCase.importParsedRows(
                    rows,
                    TransactionSource.SMS_IMPORT,
                    ledgerId,
                    // L0/L3 全量预览确认：用户看得见每一行，允许归一化弱匹配规则出建议（TASK-084）。
                    allowWeakRuleMatch = true,
                )

            recordManualConfirmations(preview.importable)

            SmsImportSummary(
                scanTotal = preview.scanTotal,
                parsed = preview.parsedTotal,
                imported = result.success,
                existing = preview.existing.size + result.existing,
                duplicates = preview.duplicates.size,
                ambiguous = preview.ambiguous.size,
                unrecognized = preview.unrecognized.size,
                notBill = preview.notBillReasons.size,
                balanceGaps = preview.balanceGaps,
            )
        }

    /**
     * 实时单条（L1/L2）：解析 → 同源幂等 → 跨源判重 → 白名单四条件
     * → 自动写入（[SmsIncomingOutcome.AutoImported]）或进「待确认」。
     *
     * @param allowAutoImport 应用锁开启时必须传 `false`（后台无法解密开库，写库必失败且会丢件）；
     *   此时白名单命中也会降级为「待确认」，由用户解锁后确认。
     */
    suspend fun handleIncoming(
        message: SmsMessageRow,
        ledgerId: String,
        allowAutoImport: Boolean = true,
    ): SmsIncomingOutcome =
        withContext(ioDispatcher) {
            val parsed = when (val outcome = parseMessage(message)) {
                is SmsParseOutcome.NotBill -> return@withContext SmsIncomingOutcome.NotBill
                is SmsParseOutcome.Unrecognized -> return@withContext SmsIncomingOutcome.Pending(
                    pendingItem(message, outcome.reason),
                )
                is SmsParseOutcome.Parsed -> outcome
            }

            // ⚠️ 兜底（实测缺陷，2026-10-10）：DB 未就绪（后台新进程未开库 / 开库失败 /
            // 应用锁开启）时，**绝不触碰 DB**（DAO provider 与 RoomDataSourceImpl 在门未开时
            // 直接 `error(...)` 抛 IllegalStateException）→ 一律降级「待确认」。
            // 语义：不写错、不崩溃、不丢件 —— 用户下次打开 App 经 L0/L3 扫描仍能看到它。
            if (databaseProvider.database?.isOpen != true) {
                return@withContext SmsIncomingOutcome.Pending(
                    pendingItem(message, "数据库未解锁（待下次打开 App 补扫）"),
                )
            }

            val existingIds = transactionDataSource.getExistingExternalIds(ledgerId)
            if (parsed.row.externalId in existingIds) {
                return@withContext SmsIncomingOutcome.Pending(pendingItem(message, "已存在（同源幂等）"))
            }

            val decision = matcher.decide(ledgerId, parsed.row, TransactionSource.SMS_IMPORT)
            val entry = whitelistStore.find(parsed.sender)
            when (val auto = SmsWhitelistPolicy.evaluate(entry, parsed, decision)) {
                is SmsWhitelistPolicy.AutoDecision.Pending ->
                    SmsIncomingOutcome.Pending(pendingItem(message, auto.reason))
                SmsWhitelistPolicy.AutoDecision.AutoImport -> {
                    if (!allowAutoImport) {
                        return@withContext SmsIncomingOutcome.Pending(
                            pendingItem(message, "应用锁开启：后台不写入，解锁后确认"),
                        )
                    }
                    val result = importBillUseCase.importParsedRows(
                        listOf(parsed.row),
                        TransactionSource.SMS_IMPORT,
                        ledgerId,
                    )
                    if (result.success > 0) {
                        SmsIncomingOutcome.AutoImported(parsed.row)
                    } else {
                        SmsIncomingOutcome.Pending(pendingItem(message, "写入未生效（可能已存在）"))
                    }
                }
            }
        }

    /**
     * 用户在「待确认」里手工记一笔：入库 + 记一次手动确认（白名单晋级路径）。
     */
    suspend fun confirmManual(
        ledgerId: String,
        parsed: SmsParseOutcome.Parsed,
    ): BillImportResult = withContext(ioDispatcher) {
        val result = importBillUseCase.importParsedRows(
            listOf(parsed.row),
            TransactionSource.SMS_IMPORT,
            ledgerId,
            // L1「待确认」是用户看得见、改得动的路径：允许归一化弱匹配规则出建议（TASK-084）。
            allowWeakRuleMatch = true,
        )
        if (result.success > 0) {
            whitelistStore.upsert(SmsWhitelistPolicy.afterManualConfirm(whitelistStore.find(parsed.sender))
                .copy(sender = parsed.sender))
        }
        result
    }

    /**
     * **L3 增量补扫**（进入导入页时自动触发）：按水位读增量 → 逐条走 L1/L2 判定 → 推进水位。
     *
     * 存在意义（不遗漏）：`SMS_RECEIVED` 广播在关机 / 后台被系统清理 / 被安全软件拦截时会丢，
     * 补扫把这段时间的短信补齐。
     *
     * 三个已定口径：
     * 1. **首次启用只立基线、不回灌历史**：否则历史短信会一次性涌进「待确认」，违背「不烦人」；
     *    历史账单请走 L0「读取短信账单」全量干跑 + 用户确认。
     * 2. **不做开机/常驻调度**：应用锁开启时数据库在解锁前无法打开（Keystore auth 包裹），
     *    故补扫只在**数据库已打开**的界面路径触发，宁可晚一点，也不冒写库失败丢件的风险。
     * 3. 自动写入仍受四条件与应用锁门控（`handleIncoming` 内既有逻辑）。
     * 4. **本方法只兜「广播丢了」这一段增量**（读到即推进水位，不区分是否写成功）；更早的历史或
     *    未处理短信一律由手动导入（L0 全量干跑）兜底，因此**不做任何「待确认/已读」状态** ——
     *    用户 2026-10-10 决策：不做已读未读，三层兜底 = ①到达即记账 → ②打开 App 补扫 → ③手动导入。
     */
    suspend fun catchUp(ledgerId: String): SmsCatchUpResult = withContext(ioDispatcher) {
        val settings = settingsStore.current()
        if (!settings.enabled || !settings.autoScanEnabled) {
            return@withContext SmsCatchUpResult(skippedReason = "总开关或自动补扫未开启")
        }
        val watermark = settings.lastWatermarkMs
        if (watermark == null) {
            val baseline = reader.suggestedWatermarkMs() ?: System.currentTimeMillis()
            settingsStore.advanceWatermark(baseline)
            return@withContext SmsCatchUpResult(
                skippedReason = "首次启用：已建立水位基线（历史账单请用「读取短信账单」全量确认）",
            )
        }

        // 上方已处理 null（首次启用分支）；此处再取一次非空局部量，避免可空类型扩散到水位比较。
        val allowAutoImport = settings.autoImportUsable(settingsDataStore.appLockEnabled.first())
        // 节点 = 本次补扫的墙钟时间（在读之前取；口径见方法尾注释与 DEC-030 修订）。
        val scanAtMs = System.currentTimeMillis()
        val messages = reader.readSince(watermark)
        if (messages.isEmpty()) return@withContext SmsCatchUpResult()

        var autoImported = 0
        var pending = 0
        for (message in messages) {
            when (handleIncoming(message, ledgerId, allowAutoImport)) {
                is SmsIncomingOutcome.AutoImported -> autoImported++
                is SmsIncomingOutcome.Pending -> pending++
                SmsIncomingOutcome.NotBill -> Unit
            }
        }
        // 水位推进（2026-10-10 用户决策口径）：补扫只兜「广播丢了」的**增量**，读到的整段一律推进。
        // 被水位越过的历史/未处理短信由**手动导入（L0 全量干跑）**兜底 —— 三层模型见 DEC-028 修订：
        // ①到达即记账（L1/L2）→ ②打开 App 补扫（L3）→ ③手动导入（L0）。
        // **推进到「本次补扫的墙钟时间」而非「读到的最新一条短信时间」**（2026-10-10 用户口径统一，
        // 见 DEC-030 修订）：两者语义都是「这之前的算处理过」，但墙上时间才符合用户对「扫描时间节点」
        // 的预期，且空库/长期无短信时不会把节点卡在一条旧短信的日期上。补扫读法是 3 天重叠回扫，
        // 故推进到读之前的墙钟时间也不会漏（重叠窗口会兜住读完瞬间到达的短信）。
        settingsStore.advanceWatermark(scanAtMs)
        SmsCatchUpResult(scanned = messages.size, autoImported = autoImported, pending = pending)
    }

    /** 纠错信号（用户删除/修改了某笔短信来源记录）→ 该发送方立即降级。 */
    suspend fun reportUserCorrection(sender: String) = withContext(ioDispatcher) {
        SmsWhitelistPolicy.afterUserCorrection(whitelistStore.find(sender))?.let {
            whitelistStore.upsert(it)
        }
        Unit
    }

    /** 白名单（设置页展示与逐项开关）。 */
    suspend fun whitelist(): List<SmsWhitelistEntry> = whitelistStore.entries()

    suspend fun setAutoEnabled(sender: String, enabled: Boolean) =
        whitelistStore.setAutoEnabled(sender, enabled)

    /** 「允许导入」来源开关（docs/15 第 2 条）：关掉后该发送方的短信不计入账单。 */
    suspend fun setSenderImportEnabled(sender: String, enabled: Boolean) =
        whitelistStore.setImportEnabled(sender, enabled)

    /** 库内短信总数（与外部 oracle 对齐，设计 §3.10）。 */
    suspend fun countInProvider(): Int = reader.countAll()

    /** 建议水位（下次补扫起点）。 */
    suspend fun suggestedWatermarkMs(): Long? = reader.suggestedWatermarkMs()

    /**
     * 「已看过」标记（2026-10-10 用户要求：扫描过的做标记，重扫不再重复啰嗦）。
     *
     * 清单页（L0 干跑预览 / 待确认清单）**实际展示过**扫描结果后调用：把「未识别已看水位」
     * 推进到本次展示的未识别条目中最新一条的时间戳。此后重扫时，这些条目 `seen = true`，
     * UI 折叠进「此前已看过 N 条」，不再与新增项混在一起。
     *
     * 口径三条（缺一不可）：
     * 1. **不丢件**：只影响展示分组，不删除、不过滤任何条目；未识别条目本身就是从系统收件箱
     *    全量扫描派生的，随时可再看到、可「手工记一笔」；
     * 2. **不动计数**：`未识别 N` 仍按全量计（`SmsScanPreview` 闭合断言不变）；
     * 3. **不留隐私**：只落一个时间戳，不存短信内容 / 号码 / 发送方（DEC-026）。
     *
     * @param shown 本次清单里展示的未识别条目（空列表 = 无操作）。
     */
    suspend fun acknowledgeUnrecognized(shown: List<SmsPendingItem>) = withContext(ioDispatcher) {
        shown.maxOfOrNull { it.receivedAtMs }?.let { settingsStore.acknowledgeUnrecognized(it) }
    }

    private suspend fun recordManualConfirmations(parsed: List<SmsParseOutcome.Parsed>) {
        parsed.map { it.sender }.distinct().forEach { sender ->
            val current = whitelistStore.find(sender)
            val updated = SmsWhitelistPolicy.afterManualConfirm(current).copy(sender = sender)
            whitelistStore.upsert(updated)
        }
    }

    private fun pendingItem(message: SmsMessageRow, reason: String) = SmsPendingItem(
        id = message.id,
        sender = message.address,
        receivedAtMs = message.receivedAtMs,
        preview = SmsBillParser.previewOf(message),
        reason = reason,
    )
}

/** 实时单条处理结果（L1/L2）。 */
sealed interface SmsIncomingOutcome {
    /** 白名单四条件成立 → 已自动写入。 */
    data class AutoImported(val row: com.minibook.domain.model.ParsedBillRow) : SmsIncomingOutcome

    /** 进「待确认」（不写库；默认静默聚合，不弹通知）。 */
    data class Pending(val item: SmsPendingItem) : SmsIncomingOutcome

    /** 判定非账单（验证码/营销/快递…）→ **零提醒**。 */
    data object NotBill : SmsIncomingOutcome
}

/**
 * L3 增量补扫结果（[SmsBillImportUseCase.catchUp]）。
 *
 * @param scanned 本次补扫覆盖的短信条数（含已存在/非账单，口径 = 读取条数）
 * @param autoImported 其中自动写入的条数
 * @param pending 进「待确认」的条数
 * @param skippedReason 未执行的原因（开关关闭 / 首次启用只立基线）；null = 已执行
 */
data class SmsCatchUpResult(
    val scanned: Int = 0,
    val autoImported: Int = 0,
    val pending: Int = 0,
    val skippedReason: String? = null,
)

/** 扫描期发送方聚合与「导入来源」清单构造见 [SmsSenderSummaryBuilder]（抽出去是为了可 JVM 单测）。 */
