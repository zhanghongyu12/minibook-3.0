package com.minibook.data.datasource

import com.minibook.domain.model.UserCategoryRule

/**
 * 用户自学习归类规则数据源契约（F-12 分类自学习）。
 *
 * 纯接口、无实现；本期唯一实现 [com.minibook.data.local.datasource.RoomDataSourceImpl]。
 * 规则仅在「学习」与「导入匹配」两个时机读写，均为单值/批量、非观察（无 [kotlinx.coroutines.flow.Flow]）。
 */
interface UserCategoryRuleDataSource {

    /** 学习/覆写一条规则（REPLACE 冲突策略，幂等）。 */
    suspend fun upsertRule(rule: UserCategoryRule)

    /** 取账本内全部规则（不含 source 过滤，供导入侧内存按 source 过滤）。 */
    suspend fun getRules(ledgerId: String): List<UserCategoryRule>

    /** 按 ledgerId 批删该账本全部规则（删账本时随账本一并清空，C5）。 */
    suspend fun deleteByLedger(ledgerId: String)
}