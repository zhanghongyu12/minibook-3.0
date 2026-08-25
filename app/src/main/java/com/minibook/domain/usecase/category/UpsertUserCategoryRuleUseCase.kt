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
 * 时调用，本 UseCase 只负责组装（keyword=交易对方全名、categoryId=新分类、source=交易来源）并 upsert。
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
        userCategoryRuleRepository.upsertRule(
            UserCategoryRule(
                id = UUID.randomUUID().toString(),
                ledgerId = ledgerId,
                source = source,
                keyword = keyword,
                categoryId = categoryId,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}