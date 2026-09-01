package com.minibook.ui.search

import com.minibook.domain.model.Category
import com.minibook.domain.model.Debt
import com.minibook.domain.model.DebtDirection
import com.minibook.domain.model.DebtFilter
import com.minibook.domain.model.DebtKind
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionFilter
import com.minibook.domain.model.TransactionType

/** 交易组字段（4 字段一级组合）。 */
internal data class TxFieldA(
    val remark: String,
    val amountMinFen: Long?,
    val amountMaxFen: Long?,
    val categoryId: String?,
)

/** 交易组字段（3 字段一级组合）。 */
internal data class TxFieldB(val dateFrom: String?, val dateTo: String?, val type: TransactionType?)

/** 交易组完整条件。 */
internal data class TxConditions(
    val remark: String,
    val amountMinFen: Long?,
    val amountMaxFen: Long?,
    val categoryId: String?,
    val dateFrom: String?,
    val dateTo: String?,
    val type: TransactionType?,
)

/** 债务组条件。 */
internal data class DebtConditions(val partyName: String, val direction: DebtDirection?)

/**
 * 全文检索条件（组合产物）：交易 + 债务两组，供两个 UseCase 构建各自 filter。
 */
internal data class SearchConditions(
    val tx: TxConditions,
    val debt: DebtConditions,
) {

    /** 金额区间 `min > max`（无效查询，PRD F-06 异常处理；`min == max` 合法）。 */
    fun amountRangeInvalid(): Boolean =
        tx.amountMinFen != null && tx.amountMaxFen != null && tx.amountMinFen > tx.amountMaxFen

    /** 是否存在活跃条件（任一字段非空）；false=完全未输入（空态区分文案口径）。 */
    fun hasActiveFilter(): Boolean =
        tx.remark.isNotBlank() ||
            tx.amountMinFen != null ||
            tx.amountMaxFen != null ||
            tx.categoryId != null ||
            tx.dateFrom != null ||
            tx.dateTo != null ||
            tx.type != null ||
            debt.partyName.isNotBlank() ||
            debt.direction != null

    /** 交易 filter：备注模糊 + 金额区间 + 分类 + 日期区间 + 类型（DEC-013，金额单位分）。 */
    fun toTransactionFilter(): TransactionFilter = TransactionFilter(
        remark = tx.remark.takeIf { it.isNotBlank() },
        amountMin = tx.amountMinFen,
        amountMax = tx.amountMaxFen,
        categoryId = tx.categoryId,
        dateFrom = tx.dateFrom,
        dateTo = tx.dateTo,
        type = tx.type,
    )

    /**
     * 债务 filter：姓名模糊 + 方向（借出/借入，kind 口径）。
     *
     * 借出/借入按 UI §8.1「(kind 口径)」映射为 kind=LEND + direction（对齐 DebtSheet 的
     * 借出=LEND×OWE_ME、借入=LEND×I_OWE），因此方向选中时附带 kind=LEND；未选方向 = 不过滤。
     */
    fun toDebtFilter(): DebtFilter = DebtFilter(
        partyName = debt.partyName.takeIf { it.isNotBlank() },
        kind = if (debt.direction != null) DebtKind.LEND else null,
        direction = debt.direction,
    )
}

/** 搜索结果中间产物：未输入条件 / 无效输入 / 有效输入的两组结果。 */
internal sealed interface SearchResultSource {

    /** 未输入任何条件：不执行查询，UI 折为「开始搜索」空态。 */
    data object IdleConditions : SearchResultSource

    /** 金额区间无效（min>max）：不执行查询，UI 折为错误态。 */
    data object InvalidConditions : SearchResultSource

    data class ConditionsResult(
        val conditions: SearchConditions,
        val transactions: List<Transaction>,
        val debts: List<Debt>,
        val categories: List<Category>,
    ) : SearchResultSource
}

/** 交易结果排序：日期倒序（组内时间倒序），同日期按 createdAt 倒序（DAO 兜底口径）。 */
internal val TRANSACTION_ORDER: Comparator<Transaction> =
    compareByDescending<Transaction> { it.date }.thenByDescending { it.createdAt }

/** 债务结果排序：日期倒序（组内时间倒序），同日期按 createdAt 倒序。 */
internal val DEBT_ORDER: Comparator<Debt> =
    compareByDescending<Debt> { it.date }.thenByDescending { it.createdAt }