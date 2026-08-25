package com.minibook.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.minibook.data.local.db.entity.UserCategoryRuleEntity

/**
 * 用户自学习归类规则 DAO（F-12 分类自学习，DB version 3）。
 *
 * - [upsert]：`@Insert(REPLACE)`，唯一索引 `(ledgerId, source, keyword, categoryId)` 作冲突键，
 *   同四元组重复学习幂等覆盖（id 每次新 UUID，REPLACE 删旧插新）；
 * - [queryByLedgerAndSource]：按 (ledgerId, source) 批量查（导入匹配用，一次查内存匹配，不逐行查库）；
 * - [deleteByLedger]：删账本时随账本一并清空（C5）。
 */
@Dao
interface UserCategoryRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: UserCategoryRuleEntity)

    @Query("SELECT * FROM user_category_rules WHERE ledgerId=:ledgerId AND source=:source")
    suspend fun queryByLedgerAndSource(ledgerId: String, source: String): List<UserCategoryRuleEntity>

    @Query("SELECT * FROM user_category_rules WHERE ledgerId=:ledgerId")
    suspend fun queryByLedger(ledgerId: String): List<UserCategoryRuleEntity>

    @Query("DELETE FROM user_category_rules WHERE ledgerId=:ledgerId")
    suspend fun deleteByLedger(ledgerId: String)
}