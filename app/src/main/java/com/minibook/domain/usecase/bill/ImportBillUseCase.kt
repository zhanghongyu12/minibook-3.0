package com.minibook.domain.usecase.bill

import com.minibook.data.datasource.CategoryDataSource
import com.minibook.data.datasource.TransactionDataSource
import com.minibook.data.datasource.TransactionRunner
import com.minibook.data.local.bill.BillParserFactory
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
import com.minibook.util.TimeFormatter
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
 */
class ImportBillUseCase @Inject constructor(
    private val transactionRunner: TransactionRunner,
    private val filePreparer: BillFilePreparer,
    private val parserFactory: BillParserFactory,
    private val categoryDataSource: CategoryDataSource,
    private val transactionDataSource: TransactionDataSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(uriString: String, ledgerId: String): BillImportResult =
        withContext(ioDispatcher) {
            val prepared = try {
                filePreparer.prepare(uriString)
            } catch (e: Exception) {
                // 文件损坏/无法读取 → 未识别（零落库），与损坏文件同口径（PRD §F-12 异常处理）。
                return@withContext BillImportResult(unrecognized = true)
            }

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
        }

    /** 跳过聚合 + 快照分类 + 单事务写入（第 4~6 步）。 */
    private suspend fun assemble(
        parseResult: BillParseResult.Success,
        source: TransactionSource,
        ledgerId: String,
    ): BillImportResult {
        // 行级过滤计数（DEC-015：NEUTRAL/CLOSED 计数、PARSE_ERROR 保留明细）。
        var neutral = 0
        var closed = 0
        val parseErrors = mutableListOf<ParseSkippedRow>()
        for (skip in parseResult.skipped) {
            when (skip.reason) {
                ImportSkipReason.NEUTRAL -> neutral++
                ImportSkipReason.CLOSED -> closed++
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

                val categoryId = resolveCategoryId(row, source, byTypeAndName, othersByName)
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
            parseErrors = parseErrors,
            unrecognized = false,
        )
    }

    /** 平台分类 → 目标内置分类名 → (type,name) 精确解析 categoryId；查不到回退「其他」锚点。 */
    private fun resolveCategoryId(
        row: ParsedBillRow,
        source: TransactionSource,
        byTypeAndName: Map<Pair<CategoryType, String>, String>,
        othersByName: Map<CategoryType, String>,
    ): String? {
        val type = row.type.toCategoryType()
        val mappedName = BillCategoryMapping.resolve(source, row.platformCategory, row.type)
        val resolved = if (mappedName != null) byTypeAndName[type to mappedName] else null
        return resolved ?: othersByName[type]
    }

    private fun TransactionType.toCategoryType(): CategoryType = when (this) {
        TransactionType.EXPENSE -> CategoryType.EXPENSE
        TransactionType.INCOME -> CategoryType.INCOME
    }
}