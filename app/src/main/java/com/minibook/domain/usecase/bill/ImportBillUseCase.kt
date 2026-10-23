package com.minibook.domain.usecase.bill

import com.minibook.data.datasource.CategoryDataSource
import com.minibook.data.datasource.TransactionDataSource
import com.minibook.data.datasource.BillParserFactory
import com.minibook.data.datasource.TransactionRunner
import com.minibook.di.IoDispatcher
import com.minibook.domain.model.BillCategoryMapping
import com.minibook.domain.model.BillImportResult
import com.minibook.domain.model.BillParseResult
import com.minibook.domain.model.CategoryType
import com.minibook.domain.model.ImportSkipReason
import com.minibook.domain.model.ParseSkippedRow
import com.minibook.domain.model.ParsedBillRow
import com.minibook.domain.model.SyncStatus
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionSource
import com.minibook.domain.model.TransactionType
import com.minibook.domain.model.UserCategoryRule
import com.minibook.domain.repository.UserCategoryRuleRepository
import com.minibook.domain.usecase.category.CategoryRuleNormalizer
import com.minibook.domain.usecase.category.EnsureBuiltInCategoriesUseCase
import com.minibook.domain.usecase.category.MerchantDictionary
import com.minibook.util.TimeFormatter
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 账单导入编排（F-12，DEC-015/019，API §13.6 / ARCHITECTURE §16.9）。
 *
 * **「逐行过滤 + 单事务整批写入」编排**：
 * 1. `filePreparer.prepare(uriString).use { }`（`AutoCloseable` finally 清理临时文件/流）；
 * 2. `parserFactory.resolve(displayName, magicBytes)` → null 则 `unrecognized=true` 零落库；
 * 3. `parser.parse(handle.input)` → `Success(rows, skipped)` / `Unrecognized`；
 * 4. 快照分类建 `(type,name)→categoryId` map（千条级**一次查询**，§16.7 避免逐条查库）；
 *    逐行装配 `Transaction`（UUID、`source`、`externalId/counterparty/paymentMethod`、
 *    自动分类 categoryId，兜底「其他」；「其他」亦缺 → 抛异常整体 Failure 零落库）；
 * 5. `transactionRunner.runInTransaction { 预查既有 externalId → 去重（文件内重复 + 既有相交
 *    → 计 EXISTS）→ insertBatch(IGNORE) → 补 -1 计数 }`；真实写入异常 → 事务回滚 → 抛异常零落库；
 * 6. 汇总 [BillImportResult]（success/existing/neutral/closed/parseErrors）。
 *
 * 纯 Kotlin（不 import `android.net.Uri`，§1/§13.6 可测试性）；文件 I/O/解析在
 * [@IoDispatcher] 上执行（ARCHITECTURE §5.2 切线程经限定符注入，便于测试替换）。
 *
 * 两个入口：`invoke(uriString, …)`（SAF content URI，F-12）与 [importLocalFile]
 * （已落地本地文件，F-13 邮件账单导入）——prepare 之后共用同一 [importPrepared] 核心。
 */
class ImportBillUseCase @Inject constructor(
    private val transactionRunner: TransactionRunner,
    private val filePreparer: BillFilePreparer,
    private val parserFactory: BillParserFactory,
    private val categoryDataSource: CategoryDataSource,
    private val transactionDataSource: TransactionDataSource,
    private val ensureBuiltInCategories: EnsureBuiltInCategoriesUseCase,
    private val userCategoryRuleRepository: UserCategoryRuleRepository,
    private val merchantDictionary: MerchantDictionary,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(uriString: String, ledgerId: String): BillImportResult =
        withContext(ioDispatcher) {
            // 导入前先补齐该账本缺失的内置分类（B2 增量补种：美团/转账/缴费/收款/退款）。
            // 选型见 EnsureBuiltInCategoriesUseCase 注释——Bootstrap 幂等早退，无法覆盖已有账本，
            // 此处 ensure 保证「导入前分类齐全」，稳态下幂等零写入。
            ensureBuiltInCategories(ledgerId)

            val prepared = try {
                filePreparer.prepare(uriString)
            } catch (e: Exception) {
                // 文件损坏/无法读取 → 未识别（零落库），与损坏文件同口径（PRD §F-12 异常处理）。
                return@withContext BillImportResult(unrecognized = true)
            }

            importPrepared(prepared, ledgerId)
        }

    /**
     * 本地文件导入入口（F-13 邮件账单导入）：与 [invoke] 共用同一套解析+落库核心，
     * 唯一差别是输入来源为已落地的 [file]（不删除该文件，调用方负责）。
     *
     * @param displayName 原始文件名（可为 null）
     */
    suspend fun importLocalFile(file: File, displayName: String?, ledgerId: String): BillImportResult =
        withContext(ioDispatcher) {
            // 与 invoke 同口径：导入前补齐内置分类，保证「导入前分类齐全」。
            ensureBuiltInCategories(ledgerId)

            val prepared = try {
                filePreparer.prepareLocalFile(file, displayName)
            } catch (e: Exception) {
                // 文件损坏/无法读取 → 未识别（零落库），与 invoke 的 prepare 异常同口径。
                return@withContext BillImportResult(unrecognized = true)
            }

            importPrepared(prepared, ledgerId)
        }

    /**
     * 已解析行导入入口（F-14 短信账单）：与 [invoke] / [importLocalFile] **共用同一 [assemble] 核心**
     * （快照分类 + 单事务 + `externalId` 幂等 + `IGNORE` 兜底），**不新建第二条写库路径**（DEC-025）。
     *
     * @param rows 已解析的有效行（短信解析器产出，`externalId` 已带 `sms:` 前缀）
     * @param source 来源（F-14 固定 [TransactionSource.SMS_IMPORT]）
     * @param allowWeakRuleMatch 是否允许「需要猜」的弱匹配规则参与分类（默认 false）。
     *   **只允许在用户看得见、改得动的路径上传 true**（短信 L0/L3 预览确认）；L2 到达即记账恒为 false
     *   ——写错不静默进账本（优先级①，见 [CategoryRuleNormalizer]）。
     */
    suspend fun importParsedRows(
        rows: List<ParsedBillRow>,
        source: TransactionSource,
        ledgerId: String,
        allowWeakRuleMatch: Boolean = false,
    ): BillImportResult =
        withContext(ioDispatcher) {
            ensureBuiltInCategories(ledgerId)
            assemble(
                BillParseResult.Success(rows = rows, skipped = emptyList()),
                source,
                ledgerId,
                allowWeakRuleMatch,
            )
        }

    /** 两入口共用的解析+落库核心：resolve → parse → assemble（[PreparedBillFile] 由 `use` 保证清理）。 */
    private suspend fun importPrepared(
        prepared: PreparedBillFile,
        ledgerId: String,
    ): BillImportResult =
        prepared.use { handle ->
            val parser = parserFactory.resolve(handle.displayName, handle.magicBytes)
            if (parser == null) {
                BillImportResult(unrecognized = true)
            } else {
                when (val result = parser.parse(handle.input)) {
                    is BillParseResult.Unrecognized -> BillImportResult(unrecognized = true)
                    is BillParseResult.Success -> assemble(result, parser.format, ledgerId)
                }
            }
        }

    /** 跳过聚合 + 快照分类 + 单事务写入（第 4~6 步）。 */
    private suspend fun assemble(
        parseResult: BillParseResult.Success,
        source: TransactionSource,
        ledgerId: String,
        allowWeakRuleMatch: Boolean = false,
    ): BillImportResult {
        // 行级过滤计数（DEC-015：NEUTRAL/CLOSED/ZERO_AMOUNT 计数、PARSE_ERROR 保留明细）。
        var neutral = 0
        var closed = 0
        var zeroAmount = 0
        val parseErrors = mutableListOf<ParseSkippedRow>()
        for (skip in parseResult.skipped) {
            when (skip.reason) {
                ImportSkipReason.NEUTRAL -> neutral++
                ImportSkipReason.CLOSED -> closed++
                ImportSkipReason.ZERO_AMOUNT -> zeroAmount++
                ImportSkipReason.PARSE_ERROR -> parseErrors += skip
                ImportSkipReason.EXISTS -> Unit // 解析器不产出 EXISTS（防御分支）
            }
        }

        // 快照分类一次查询（§16.7 / §13.6 第 4 步）；「其他」为内置锚点（DEC-007 锁定默认名）。
        val categories = categoryDataSource.observeByLedger(ledgerId, null).first()
        val byTypeAndName: Map<Pair<CategoryType, String>, String> =
            categories.associate { (it.type to it.name) to it.id }
        val othersByName: Map<CategoryType, String> = categories
            .filter { it.name == BillCategoryMapping.OTHERS_CATEGORY_NAME }
            .associate { it.type to it.id }
        val validCategoryIds: Set<String> = categories.map { it.id }.toSet()

        // 自学习规则：按 ledgerId 一次查出，循环内按 source 内存子串匹配（C4「不逐行查库」）。
        val rules: List<UserCategoryRule> = userCategoryRuleRepository.getRules(ledgerId)

        // 内置商户词典（docs/15 第 7 条）：只在「用户看得见、改得动」的预览路径上装载与使用
        // （allowWeakRuleMatch=true）；L2 到达即记账不查词典，防止全局词典静默写错账。
        if (allowWeakRuleMatch) {
            merchantDictionary.ensureLoaded()
        }

        val now = TimeFormatter.nowIso()

        // 单事务整批写入（DEC-015 原子性口径）：任一真实写入异常 → 整体回滚零落库。
        val (success, existing) = transactionRunner.runInTransaction {
            val existingIds = transactionDataSource.getExistingExternalIds(ledgerId).toMutableSet()
            val seenInFile = mutableSetOf<String>()
            val toInsert = mutableListOf<Transaction>()
            var existingCount = 0

            for (row in parseResult.rows) {
                // 文件内重复 → EXISTS。
                if (!seenInFile.add(row.externalId)) {
                    existingCount++
                    continue
                }
                // 既有 externalId 相交 → EXISTS。
                if (row.externalId in existingIds) {
                    existingCount++
                    continue
                }

                val categoryId = resolveCategoryId(
                    row,
                    source,
                    byTypeAndName,
                    othersByName,
                    validCategoryIds,
                    rules,
                    allowWeakRuleMatch,
                )
                    ?: error("账本 $ledgerId 缺少 ${row.type.value} 的「${BillCategoryMapping.OTHERS_CATEGORY_NAME}」兜底分类")

                toInsert += Transaction(
                    id = UUID.randomUUID().toString(),
                    ledgerId = ledgerId,
                    type = row.type,
                    amount = row.amountFen,
                    categoryId = categoryId,
                    description = row.description,
                    date = row.date,
                    createdAt = now,
                    updatedAt = now,
                    syncStatus = SyncStatus.LOCAL,
                    externalId = row.externalId,
                    counterparty = row.counterparty,
                    paymentMethod = row.paymentMethod,
                    source = source,
                )
            }

            val inserted = transactionDataSource.insertBatch(toInsert)
            // IGNORE 兜底：批次内理论上已去重，-1 为并发/最后防线，计「已存在」（DEC-019）。
            existingCount += (toInsert.size - inserted)
            inserted to existingCount
        }

        return BillImportResult(
            success = success,
            existing = existing,
            neutral = neutral,
            closed = closed,
            zeroAmount = zeroAmount,
            parseErrors = parseErrors,
            unrecognized = false,
        )
    }

    /**
     * 分类决策（F-12 自动分类 + 分类校准 + 自学习，B4/C4 优先级逐条落地）：
     * 1. 用户自学习规则：经 [CategoryRuleNormalizer] 归一化后匹配（原始串包含 / 归一化相等 /
     *    归一化核心词被包含 = 强匹配；归一化公共前缀 ≥ 2 字 = 弱匹配）。强匹配恒可用；弱匹配仅在
     *    `allowWeakRuleMatch=true`（用户看得见的预览路径）时可用。命中分类须仍属当前账本
     *    （`rule.categoryId in validCategoryIds`），否则视为失效、继续下探。
     * 1.5 内置商户词典（[MerchantDictionary]，docs/15 第 7 条）：同样只在预览路径可用，
     *    命中即给建议分类；L2 自动记账恒跳过本步。
     * 2. 内置类型映射 [BillCategoryMapping.resolve]（红包/转账/群收款/退款/收款 命中即用；
     *    商户消费/扫码付款等无类型映射 → null）。
     * 3. 仅当 2 未命中且 source=wechat：[BillCategoryMapping.resolveByCounterparty] 对手方关键词。
     * 4. 仍未命中且微信「扫二维码付款」→ 目标分类名「付款」；其余（含商户消费未命中）→「其他」兜底。
     * 支付宝：1 → 2 →「其他」，**不走** counterparty 关键词与「扫二维码付款」分支。
     *
     * @param allowWeakRuleMatch 是否允许弱匹配规则（默认 false = 只认强匹配，L2 自动记账用）。
     */
    private fun resolveCategoryId(
        row: ParsedBillRow,
        source: TransactionSource,
        byTypeAndName: Map<Pair<CategoryType, String>, String>,
        othersByName: Map<CategoryType, String>,
        validCategoryIds: Set<String>,
        rules: List<UserCategoryRule>,
        allowWeakRuleMatch: Boolean = false,
    ): String? {
        val type = row.type.toCategoryType()

        // 第 1 步：用户自学习规则（TASK-084：归一化「核心词」匹配，跨写法/跨门店也能对上）。
        val counterparty = row.counterparty?.trim().orEmpty()
        if (counterparty.isNotEmpty()) {
            val rule = rules.asSequence()
                .filter { it.source == source }
                .map { it to CategoryRuleNormalizer.matchStrength(it.keyword, counterparty) }
                .filter { (_, strength) ->
                    strength == CategoryRuleNormalizer.MatchStrength.STRONG ||
                        (allowWeakRuleMatch && strength == CategoryRuleNormalizer.MatchStrength.WEAK)
                }
                // 多条命中：强匹配优先、再取核心词最长者（更具体）、等长按 updatedAt 最新（ISO 字典序==时间序）。
                .sortedWith(
                    compareByDescending<Pair<UserCategoryRule, CategoryRuleNormalizer.MatchStrength>> {
                        it.second == CategoryRuleNormalizer.MatchStrength.STRONG
                    }
                        .thenByDescending { CategoryRuleNormalizer.coreLength(it.first.keyword) }
                        .thenByDescending { it.first.updatedAt },
                )
                .map { it.first }
                .firstOrNull()
            if (rule != null && rule.categoryId in validCategoryIds) {
                return rule.categoryId
            }
        }

        // 第 1.5 步：内置商户词典（docs/15 第 7 条；2026-10-13 用户定案）——
        // 只作**手动导入预览**的建议（与弱匹配规则同一路径），L2 到达即记账不查词典。
        // 命中分类名须存在于本账本（内置分类一般都在），否则继续下探。
        if (allowWeakRuleMatch && counterparty.isNotEmpty()) {
            merchantDictionary.suggest(counterparty)?.let { suggested ->
                byTypeAndName[type to suggested]?.let { return it }
            }
        }

        // 第 2 步：内置类型映射。
        val mappedName = BillCategoryMapping.resolve(source, row.platformCategory, row.type)
        if (mappedName != null) {
            return byTypeAndName[type to mappedName] ?: othersByName[type]
        }

        // 第 3 步：仅微信与 F-14 短信走对手方关键词（支付宝不走，避免误分类；短信无平台分类，只有户名）。
        if (source == TransactionSource.WECHAT_IMPORT || source == TransactionSource.SMS_IMPORT) {
            val keywordName = BillCategoryMapping.resolveByCounterparty(source, row.counterparty, row.type)
            if (keywordName != null) {
                return byTypeAndName[type to keywordName] ?: othersByName[type]
            }
        }

        // 第 4 步：微信「扫二维码付款」→「付款」；其余 →「其他」。
        if (source == TransactionSource.WECHAT_IMPORT && row.platformCategory?.contains("扫二维码付款") == true) {
            return byTypeAndName[type to "付款"] ?: othersByName[type]
        }

        return othersByName[type]
    }

    private fun TransactionType.toCategoryType(): CategoryType = when (this) {
        TransactionType.EXPENSE -> CategoryType.EXPENSE
        TransactionType.INCOME -> CategoryType.INCOME
    }
}