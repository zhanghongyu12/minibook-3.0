package com.minibook.domain.usecase.debt

import com.minibook.domain.common.InvalidInput
import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.model.Debt
import com.minibook.domain.model.DebtDirection
import com.minibook.domain.model.DebtKind
import com.minibook.domain.repository.DebtRepository
import com.minibook.util.TimeFormatter
import com.minibook.util.Validators
import java.util.UUID
import javax.inject.Inject

/**
 * 记一笔借/还（TASK-014，PRD F-02），对齐 DATABASE §4.2 四态平级模型。
 *
 * **四态字典（净额口径，DATABASE §4.2 / PRD F-02）**：
 * - `lend × owe_me`：借出，对净额 `+amount`（对方欠我）；
 * - `lend × i_owe`：借入，对净额 `-amount`（我欠对方）；
 * - `repay × owe_me`：收到还款，对净额 `-amount`；
 * - `repay × i_owe`：我还款，对净额 `+amount`。
 *
 * 四态校验：`kind`（lend/repay）与 `direction`（owe_me/i_owe）均为强类型枚举，
 * 取值恒合法，**无非法组合、四态全部允许**——录入校验仅需拒绝「姓名空」与
 * 「金额 ≤ 0 / 超上限」（[Validators]），枚举组合由类型系统闭合。
 *
 * 还款金额可大于当前净额（业务允许，UI 提示不拦截，P-08 边界）：本类不查当前净额、
 * 不拦截超还，金额仅做正数/上限校验后落库（净额实时重算见 [NetByPartyUseCase]）。
 *
 * 记入「当前账本」：经 [LedgerProvider] 取当前账本 ID（ARCHITECTURE §8.2 / §8.5），
 * 本类不感知 DataStore/Room（DEC-001）。`createdAt/updatedAt` 统一 [TimeFormatter.nowIso]。
 */
class AddDebtUseCase @Inject constructor(
    private val debtRepository: DebtRepository,
    private val ledgerProvider: LedgerProvider,
) {

    /**
     * 记一笔借/还，返回落库 id。
     *
     * @param partyName 对方姓名（非空，[Validators.validatePartyName]）。
     * @param amount 金额（分，>0 且 ≤ [Validators.MAX_AMOUNT_FEN]）。
     * @param kind 借/还（lend/repay）。
     * @param direction 方向（owe_me/i_owe）；与 [kind] 组成四态，全部合法。
     * @param date 发生日期 `yyyy-MM-dd`（DATABASE §9.5 口径）。
     * @param remark 备注（≤ 200 字符，可为空）。
     * @return 落库债务 id（数据层 insert 生成 UUID）。
     * @throws InvalidInput 姓名空 / 金额 ≤ 0 或超上限。
     * @throws com.minibook.domain.common.LedgerNotFound 当前账本未初始化（§8.5）。
     */
    suspend operator fun invoke(
        partyName: String,
        amount: Long,
        kind: DebtKind,
        direction: DebtDirection,
        date: String,
        remark: String? = null,
    ): String {
        // 录入校验（TASK-010 Validators，DATABASE §9.4）：姓名空 / 金额 ≤0 或超上限拒绝。
        if (!Validators.validatePartyName(partyName)) throw InvalidInput("对方姓名不能为空")
        if (!Validators.validateAmount(amount)) throw InvalidInput("金额必须大于 0 且不超过上限")
        // 四态校验：kind/direction 强类型枚举取值恒合法，无非法组合（见类 KDoc 字典）。
        val ledgerId = ledgerProvider.currentLedgerId()
        val now = TimeFormatter.nowIso()
        return debtRepository.insert(
            Debt(
                id = UUID.randomUUID().toString(), // 数据层 insert 重生成 UUID，此处占位
                ledgerId = ledgerId,
                partyName = partyName,
                amount = amount,
                kind = kind,
                direction = direction,
                date = date,
                remark = remark,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}
