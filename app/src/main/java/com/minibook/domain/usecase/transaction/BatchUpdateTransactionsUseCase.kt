package com.minibook.domain.usecase.transaction

import com.minibook.domain.common.InvalidTransactionInput
import com.minibook.domain.model.CategoryType
import com.minibook.domain.model.TransactionSource
import com.minibook.domain.model.TransactionType
import com.minibook.domain.repository.CategoryRepository
import com.minibook.domain.repository.TransactionRepository
import com.minibook.domain.usecase.category.UpsertUserCategoryRuleUseCase
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * 批量改分类 / 批量改支出·收入类型（记账页批量管理，复用任务 3 批量模式骨架）。
 *
 * 两者均为「一次性改多笔」的编排，空 [ids] 直接短路（不触库）；数据层的
 * [TransactionRepository.updateCategoryByIds] / [updateTypeByIds] 已在 Repository 侧单事务
 * 闭合，本类不承载事务细节（DEC-001）。
 *
 * - [updateCategory]：改分类 + **触发自学习**（对齐单笔 [UpdateTransactionUseCase] C3 语义——
 *   仅对 `source ∈ {wechat_import, alipay_import}` 且 counterparty 非空的交易逐条
 *   upsert 规则，keyword=交易对方、categoryId=新分类、source=交易来源；失败尽力而为不阻断）。
 * - [updateType]：改类型**不触发自学习**；改类型后旧分类与新类型不一致，须归位到新 type 的
 *   「其他」兜底分类——先按 `(CategoryType, "其他")` 查目标 categoryId，**缺失即抛错整体失败
 *   零写入**（不产生「收入交易挂着支出分类」的脏数据），命中则先改 type 再改 categoryId 归位。
 */
class BatchUpdateTransactionsUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val upsertUserCategoryRule: UpsertUserCategoryRuleUseCase,
) {

    /**
     * 批量改分类：把 [ids] 全部交易归到 [categoryId]，并对其中「导入 + 有交易对方」的交易
     * 触发自学习（失败不阻断）。
     */
    suspend fun updateCategory(ledgerId: String, ids: List<String>, categoryId: String) {
        if (ids.isEmpty()) return
        transactionRepository.updateCategoryByIds(ids, categoryId)
        learnCategory(ledgerId, ids, categoryId)
    }

    /**
     * 批量改类型：把 [ids] 全部交易改为 [type]，并将其分类归位到新 type 的「其他」兜底。
     *
     * @throws InvalidTransactionInput 新 type 下无「其他」兜底分类（此时未发生任何写入，整体失败）。
     */
    suspend fun updateType(ledgerId: String, ids: List<String>, type: TransactionType) {
        if (ids.isEmpty()) return
        // 先查新 type 的「其他」兜底；缺失即抛错，保证「零写入」而非留下 type/category 不一致的脏数据。
        val fallbackCategoryId = categoryRepository.observeByLedger(ledgerId, type.toCategoryType())
            .first()
            .firstOrNull { it.name == "其他" }
            ?.id
            ?: throw InvalidTransactionInput("该类型缺少「其他」兜底分类，无法批量改类型")
        // 先改 type（Repository 侧单事务），再把分类归位到「其他」（同样单事务）。
        // 改类型不触发自学习（与 updateCategory 明确区分，C3 只学「手动改分类」动作）。
        transactionRepository.updateTypeByIds(ids, type)
        transactionRepository.updateCategoryByIds(ids, fallbackCategoryId)
    }

    /**
     * 自学习归类（C3，批量版）：逐条 getById（无批量查询面），对「导入交易且 counterparty 非空」
     * 的交易 upsert 规则（keyword=交易对方全名、categoryId=新分类、source=交易来源）。
     * 规则写入为尽力而为副作用，失败不阻断已完成的分类更新（CancellationException 照常上抛）。
     */
    private suspend fun learnCategory(ledgerId: String, ids: List<String>, categoryId: String) {
        ids.forEach { id ->
            val tx = transactionRepository.getById(id) ?: return@forEach
            if (tx.source != TransactionSource.WECHAT_IMPORT && tx.source != TransactionSource.ALIPAY_IMPORT) {
                return@forEach
            }
            val keyword = tx.counterparty?.trim().orEmpty()
            if (keyword.isEmpty()) return@forEach
            try {
                upsertUserCategoryRule(ledgerId, tx.source, keyword, categoryId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 自学习为尽力而为副作用：规则写入失败不阻断分类变更结果（已落库）。
            }
        }
    }

    /** [TransactionType] → [CategoryType] 映射（二者 value 同构：expense/income）。 */
    private fun TransactionType.toCategoryType(): CategoryType = when (this) {
        TransactionType.EXPENSE -> CategoryType.EXPENSE
        TransactionType.INCOME -> CategoryType.INCOME
    }
}
