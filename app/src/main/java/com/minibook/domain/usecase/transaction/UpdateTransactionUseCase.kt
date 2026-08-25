package com.minibook.domain.usecase.transaction

import com.minibook.domain.common.CategoryRequired
import com.minibook.domain.common.InvalidTransactionInput
import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionFilter
import com.minibook.domain.model.TransactionSource
import com.minibook.domain.repository.TransactionRepository
import com.minibook.domain.usecase.category.UpsertUserCategoryRuleUseCase
import com.minibook.util.TimeFormatter
import com.minibook.util.Validators
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * 编辑交易（TASK-013，PRD F-01 / API §3）。
 *
 * 语义（API §3）：`type/amount/categoryId/description/date` 可改，`createdAt` **不可改**
 * ——入参 [Transaction] 由调用方按编辑表单构建，本类取库内原值并强制以原 `createdAt` 覆盖，
 * `updatedAt` 刷新为当前时间（[TimeFormatter.nowIso]）；`id/ledgerId/syncStatus` 一并保留
 * 入参原值。
 *
 * **输入校验（TASK-013 评审 W2）**：与 Add 侧同套口径（金额 >0 且不超上限、分类必填、
 * 备注 ≤ 200），更新路径同样拒绝非法输入——领域不变量不因路径分叉而空洞。
 *
 * 流程：录入校验（同 [AddTransactionUseCase]）→ 经当前账本视图校验目标存在
 * （[LedgerProvider.currentLedgerId] + `observeByLedger`，仅对当前账本内的交易操作，
 * TASK-013 评审 W5；跨账本 id 视为不存在，防御性拒绝）→
 * `update(tx.copy(ledgerId = existing.ledgerId, createdAt = existing.createdAt, updatedAt = nowIso()))`。
 */
class UpdateTransactionUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val ledgerProvider: LedgerProvider,
    private val upsertUserCategoryRule: UpsertUserCategoryRuleUseCase,
) {

    /**
     * 编辑 [tx.id] 交易。
     *
     * @param tx 编辑后的交易值对象（id 必须存在且属当前账本；createdAt 以库内原值为准，入参值被覆盖）。
     * @throws InvalidTransactionInput 金额 ≤ 0 或超上限 / 备注超长 / 目标交易不存在。
     * @throws CategoryRequired 分类为空或纯空白。
     * @throws com.minibook.domain.common.LedgerNotFound 当前账本未初始化（§8.5）。
     */
    suspend operator fun invoke(tx: Transaction) {
        // 录入校验（镜像 AddTransactionUseCase）：金额 / 分类 / 备注（DATABASE §9.4 / §4.1）。
        if (!Validators.validateAmount(tx.amount)) {
            throw InvalidTransactionInput("金额必须大于 0 且不超过 ${Validators.MAX_AMOUNT_FEN} 分")
        }
        if (tx.categoryId.isBlank()) throw CategoryRequired()
        if (!Validators.validateNoteLength(tx.description.orEmpty())) {
            throw InvalidTransactionInput("备注不能超过 ${Validators.MAX_NOTE_LENGTH} 字")
        }

        // 经当前账本视图校验存在（对齐 DeleteDebtUseCase 账本维度统一；跨账本 id 视为不存在）。
        val ledgerId = ledgerProvider.currentLedgerId()
        val existing = transactionRepository.observeByLedger(ledgerId, TransactionFilter())
            .first()
            .firstOrNull { it.id == tx.id }
            ?: throw InvalidTransactionInput("交易不存在：${tx.id}")
        transactionRepository.update(
            tx.copy(
                ledgerId = existing.ledgerId, // 锁定为当前账本（防入参 ledgerId 不一致导致 update WHERE 不命中）
                createdAt = existing.createdAt,
                updatedAt = TimeFormatter.nowIso(),
            ),
        )

        learnCategoryIfChanged(existing, tx, ledgerId)
    }

    /**
     * 自学习归类（C3）：仅当「导入交易（source ∈ {wechat_import, alipay_import}）的分类被手动改
     * 且**确实变了**、且该交易 counterparty 非空」时，upsert 一条规则
     * （keyword=交易对方全名、categoryId=新分类、source=交易 source）。
     *
     * 只学「改分类」这个动作：以 `existing.categoryId != updated.categoryId` 为唯一判据，
     * 改金额/备注/日期等其它字段（分类未变）不触发；无 counterparty 的交易不学（C5）。
     * 规则写入为尽力而为副作用，失败不阻断已完成的编辑保存（CancellationException 照常上抛）。
     */
    private suspend fun learnCategoryIfChanged(existing: Transaction, updated: Transaction, ledgerId: String) {
        if (existing.categoryId == updated.categoryId) return
        val source = existing.source
        if (source != TransactionSource.WECHAT_IMPORT && source != TransactionSource.ALIPAY_IMPORT) return
        val keyword = existing.counterparty?.trim().orEmpty()
        if (keyword.isEmpty()) return
        try {
            upsertUserCategoryRule(ledgerId, source, keyword, updated.categoryId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 自学习为尽力而为副作用：规则写入失败不阻断分类变更结果（已落库）。
        }
    }
}
