package com.minibook.ui.search

import com.minibook.domain.model.Category
import com.minibook.domain.model.Debt
import com.minibook.domain.model.Transaction

/** 搜索页条件组：交易 / 债务（UI §8.1，「交易|债务」SegmentedButton）。 */
enum class SearchTab {
    TRANSACTION,
    DEBT,
}

/**
 * 搜索页 UI 四态（TASK-033，UI §8.1，对齐 TransactionsViewModel 的四态风格）。
 *
 * @property hasActiveFilter 空态下是否处于筛选状态（true 展示「清除条件」动作）。
 */
sealed interface SearchUiState {

    /** 流未首发射 / 首启加载中：展示骨架屏。 */
    data object Loading : SearchUiState

    /** 空列表：[hasActiveFilter]=true 提示清除条件；false 提示输入关键词/设置条件。 */
    data class Empty(val hasActiveFilter: Boolean) : SearchUiState

    /** 错误态：金额区间无效（min>max）或观察流异常（账本未初始化/数据库异常）。 */
    data class Error(val message: String) : SearchUiState

    /** 非空结果：交易 + 债务两组（各自时间倒序）+ 分类 id→[Category] 映射（联表展示用）。 */
    data class Success(
        val transactions: List<Transaction>,
        val debts: List<Debt>,
        val categoriesById: Map<String, Category>,
    ) : SearchUiState
}