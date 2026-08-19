package com.minibook.domain.usecase.transaction

import com.minibook.domain.common.CategoryRequired
import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionType
import com.minibook.domain.repository.TransactionRepository
import com.minibook.util.TimeFormatter
import com.minibook.util.Validators
import java.util.UUID
import javax.inject.Inject

/**
 * 记一笔交易（TASK-013，PRD F-01 / ARCHITECTURE §4.1），对齐 DATABASE §4.1 与 §9.4。
 *
 * **入参口径（TASK-013 裁定）**：`amount: Long` 直接以「分」传入（DATABASE §9 杜绝浮点），
 * **UI 负责「元→分」转换**（含 `Validators.validateDecimalPlaces` 第 3 位小数拦截与
 * `MoneyFormatter.toFen` 精确换算）——本类只校验「分」值本身是否合法（>0 且不超上限），
 * 不感知元/分换算，单测可直接覆盖 `12.34` 元 = `1234` 分后的金额校验。
 *
 * **校验清单（顺序执行，任一失败抛 [CategoryRequired] 或 `IllegalArgumentException`）**：
 * 1. **金额 > 0 且不超上限**：`require(Validators.validateAmount(amount))`（拒绝 0/负数/
 *    超 [Validators.MAX_AMOUNT_FEN]，DATABASE §9.4）；
 * 2. **分类必填**：`categoryId` 非空非纯空白 → [CategoryRequired]（DATABASE §4.1 NOT NULL）；
 * 3. **备注 ≤ 200**：`require(Validators.validateNoteLength(description.orEmpty()))`（可空
 *    字段空串视为合法，DATABASE §4.1）。
 *
 * 记入「当前账本」：经 [LedgerProvider.currentLedgerId] 取当前账本 ID（ARCHITECTURE §8.2/§8.5），
 * 本类不感知 DataStore/Room（DEC-001）。`createdAt/updatedAt` 统一 [TimeFormatter.nowIso]，
 * `id` 离线生成 UUID（API §1；数据层 insert 重生成并以其返回值为准，同 AddDebtUseCase）。
 */
class AddTransactionUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val ledgerProvider: LedgerProvider,
) {

    /**
     * 记一笔交易，返回落库 id。
     *
     * @param amount 金额（**分**，>0 且 ≤ [Validators.MAX_AMOUNT_FEN]；UI 负责元→分）。
     * @param type 收支类型（expense/income）。
     * @param categoryId 关联分类 id（必填，[CategoryRequired]）。
     * @param date 发生日期 `yyyy-MM-dd`（DATABASE §9.5 口径）。
     * @param description 备注（≤ 200 字符，可为空）。
     * @return 落库交易 id（数据层 insert 生成 UUID）。
     * @throws CategoryRequired 分类为空或纯空白。
     * @throws IllegalArgumentException 金额 ≤ 0 或超上限 / 备注超长（`require` 抛出）。
     * @throws com.minibook.domain.common.LedgerNotFound 当前账本未初始化（§8.5）。
     */
    suspend operator fun invoke(
        amount: Long,
        type: TransactionType,
        categoryId: String,
        date: String,
        description: String? = null,
    ): String {
        // 录入校验（DATABASE §9.4）：金额 >0 且不超上限。
        require(Validators.validateAmount(amount)) {
            "金额必须大于 0 且不超过 ${Validators.MAX_AMOUNT_FEN} 分"
        }
        // 分类必填（DATABASE §4.1 NOT NULL）。
        if (categoryId.isBlank()) throw CategoryRequired()
        // 备注 ≤ 200 字（可空字段空串视为合法）。
        require(Validators.validateNoteLength(description.orEmpty())) {
            "备注不能超过 ${Validators.MAX_NOTE_LENGTH} 字"
        }

        val ledgerId = ledgerProvider.currentLedgerId()
        val now = TimeFormatter.nowIso()
        return transactionRepository.insert(
            Transaction(
                id = UUID.randomUUID().toString(), // 数据层 insert 重生成 UUID，此处占位
                ledgerId = ledgerId,
                type = type,
                amount = amount,
                categoryId = categoryId,
                description = description,
                date = date,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}
