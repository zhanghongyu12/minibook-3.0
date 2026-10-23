package com.minibook.domain.usecase.category

import com.minibook.domain.model.TransactionSource
import com.minibook.domain.model.UserCategoryRule
import com.minibook.domain.repository.UserCategoryRuleRepository
import com.minibook.util.TimeFormatter
import java.util.UUID
import javax.inject.Inject

/**
 * 学习/覆写一条用户归类规则（F-12 分类自学习，C2）。
 *
 * 学习时机统一在 [com.minibook.domain.usecase.transaction.UpdateTransactionUseCase]：
 * 仅当「导入交易（wechat_import/alipay_import）的分类被手动改且确实变了、且 counterparty 非空」
 * 时调用，本 UseCase 只负责组装（keyword=对方全名**归一化后的核心词**、categoryId=新分类、
 * source=交易来源）并 upsert。
 *
 * **为什么存核心词而不是全名**（TASK-084，2026-10-13 用户需求①）：全名里带着门店/公司后缀，
 * 下次换个门店（`罗森…朝阳门店` → `罗森…房山店`）就匹配不上。存归一化核心词后，
 * 同商户的不同写法与分店都能对上（见 [CategoryRuleNormalizer]）。
 *
 * 幂等：规则表唯一索引 `(ledgerId, source, keyword, categoryId)` + `@Insert(REPLACE)`，重复学习覆盖。
 */
class UpsertUserCategoryRuleUseCase @Inject constructor(
    private val userCategoryRuleRepository: UserCategoryRuleRepository,
) {

    suspend operator fun invoke(
        ledgerId: String,
        source: TransactionSource,
        keyword: String,
        categoryId: String,
    ) {
        val now = TimeFormatter.nowIso()
        // 归一化核心词；剥得只剩 1 个字（或剥空）时退回原始全名，避免把规则词弄成没法匹配的碎片。
        val coreKeyword = CategoryRuleNormalizer.normalize(keyword).takeIf { it.length >= 2 }
            ?: keyword.trim()
        userCategoryRuleRepository.upsertRule(
            UserCategoryRule(
                id = UUID.randomUUID().toString(),
                ledgerId = ledgerId,
                source = source,
                keyword = coreKeyword,
                categoryId = categoryId,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}