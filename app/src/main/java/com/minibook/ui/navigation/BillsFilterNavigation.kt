package com.minibook.ui.navigation

import com.minibook.domain.model.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 账单筛选跳转请求（报表·分类占比点击 → 跳「记账」Tab 并按分类/类型/周期筛明细）。
 *
 * @property categoryId 分类 id（null=不限分类；跳转后 [com.minibook.ui.transactions.TransactionsViewModel] 以其筛明细）。
 * @property categoryName 分类名（null=分类已删或未按分类筛选）。
 * @property type 收入/支出口径（null=不限类型；与报表图 2 当前口径一致）。
 * @property dateFrom 区间起点 `yyyy-MM-dd`（含）。
 * @property dateTo 区间终点 `yyyy-MM-dd`（含）。
 * @property periodLabel 周期文案（月如「2026年8月」、年如「2026年」）。
 */
data class BillsFilterRequest(
    val categoryId: String?,
    val categoryName: String?,
    val type: TransactionType?,
    val dateFrom: String,
    val dateTo: String,
    val periodLabel: String,
)

/**
 * 账单筛选跳转总线（进程内单例 StateFlow）。
 *
 * 报表页 [post] 请求 → [MainScreen] 观察后跳「记账」Tab → 账单 VM 消费后 [clear]。
 * StateFlow 保证消费方可读当前值；post 覆盖旧请求、clear 幂等清空。
 */
object BillsFilterBus {

    private val _request = MutableStateFlow<BillsFilterRequest?>(null)

    /** 待消费的筛选跳转请求；null=无待处理请求。 */
    val request: StateFlow<BillsFilterRequest?> = _request.asStateFlow()

    /** 投递一条跳转请求（覆盖旧请求）。 */
    fun post(req: BillsFilterRequest) {
        _request.value = req
    }

    /** 清空待处理请求（消费方应用筛选后调用）。 */
    fun clear() {
        _request.value = null
    }
}