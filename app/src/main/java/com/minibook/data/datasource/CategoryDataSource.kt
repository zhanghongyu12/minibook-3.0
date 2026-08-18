package com.minibook.data.datasource

import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow

/**
 * 分类（PRD F-03）数据源契约，逐条对齐 API §5。
 *
 * 纯接口、无实现；本期唯一实现为 RoomDataSourceImpl（TASK-008）。
 * 所有业务查询首参统一 `ledgerId`（ARCHITECTURE §8.1）；分类无 `syncStatus`
 * （不参与增量同步，走全量导入导出，DATABASE §4.3）。
 */
interface CategoryDataSource {

    /**
     * 种子/导入批量写入（`REPLACE` 冲突策略）。
     *
     * 幂等：种子写入由 `countByLedger(ledgerId)==0` 守卫（DATABASE §8.3）。
     */
    suspend fun upsertList(categories: List<Category>)

    /**
     * 新增自定义分类。
     *
     * @return 生成的 UUID。
     */
    suspend fun insert(category: Category): String

    /**
     * 改 `name/icon/color/order`；**不可改 `type`/`isBuiltIn`**（D-6 最终口径），
     * 「其他」兜底分类保持默认名（DATABASE §8.3 / DEC-007）。
     */
    suspend fun update(category: Category)

    /**
     * **仅能删自定义分类**；内置（`isBuiltIn=true`）与默认「其他」由业务层拒绝
     * （API §5 / DEC-007）。
     */
    suspend fun deleteById(id: String)

    /** 账本分类数；种子幂等判断用（==0 才 clone，DATABASE §8.3）。 */
    suspend fun countByLedger(ledgerId: String): Long

    /**
     * 账本内分类流，可按收支类型过滤（null=全部），按 `order` 升序
     * （DATABASE §6.3 observeByType）。
     */
    fun observeByLedger(ledgerId: String, type: CategoryType?): Flow<List<Category>>

    /**
     * 删除前关联检查：统计引用该分类的交易数；>0 则提示走迁移
     * （TASK-015 DeleteCategoryWithMigrationUseCase / UI D-15 依赖）。
     */
    suspend fun countTransactionsByCategory(categoryId: String): Int
}
