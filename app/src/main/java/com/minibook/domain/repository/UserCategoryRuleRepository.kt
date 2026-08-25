package com.minibook.domain.repository

import com.minibook.domain.model.UserCategoryRule

/**
 * 用户自学习归类规则领域聚合入口（F-12 分类自学习）。
 *
 * UseCase 层（学习 UpsertUserCategoryRuleUseCase / 导入匹配 ImportBillUseCase）唯一入口；
 * 实现 [UserCategoryRuleRepositoryImpl] 委托 `data/datasource` 的 [UserCategoryRuleDataSource]，
 * 自身不感知 Room/SQL/SQLCipher。
 */
interface UserCategoryRuleRepository {

    /** 学习/覆写一条规则（REPLACE 冲突策略，幂等）。 */
    suspend fun upsertRule(rule: UserCategoryRule)

    /** 取账本内全部规则（导入侧一次查出后内存按 source 过滤匹配，不逐行查库）。 */
    suspend fun getRules(ledgerId: String): List<UserCategoryRule>

    /** 按 ledgerId 批删该账本全部规则（删账本随账本清空，C5）。 */
    suspend fun deleteByLedger(ledgerId: String)
}