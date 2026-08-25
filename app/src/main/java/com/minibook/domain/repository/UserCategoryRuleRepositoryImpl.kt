package com.minibook.domain.repository

import com.minibook.data.datasource.UserCategoryRuleDataSource
import com.minibook.domain.model.UserCategoryRule
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户自学习归类规则 Repository 实现（F-12 分类自学习）。
 *
 * 纯委托 [UserCategoryRuleDataSource]：逐方法转发、无业务逻辑（ARCHITECTURE §4.1
 * Repository 为领域聚合入口 / 组合 DataSource 接口）。
 */
@Singleton
class UserCategoryRuleRepositoryImpl @Inject constructor(
    private val dataSource: UserCategoryRuleDataSource,
) : UserCategoryRuleRepository {

    override suspend fun upsertRule(rule: UserCategoryRule) = dataSource.upsertRule(rule)

    override suspend fun getRules(ledgerId: String): List<UserCategoryRule> =
        dataSource.getRules(ledgerId)

    override suspend fun deleteByLedger(ledgerId: String) = dataSource.deleteByLedger(ledgerId)
}