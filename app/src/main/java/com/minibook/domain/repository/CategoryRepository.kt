package com.minibook.domain.repository

import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow

/**
 * 分类（PRD F-03）领域聚合入口，对齐 ARCHITECTURE §4.1。
 *
 * UseCase 层唯一分类入口；实现 [CategoryRepositoryImpl] 委托 `data/datasource` 的
 * `CategoryDataSource`（迁移编排额外组合 `TransactionDataSource`），自身不感知
 * Room/SQL/SQLCipher（ARCHITECTURE §4.1 / DEC-001）。
 *
 * 分类无 `syncStatus`（不参与增量同步，走全量导入导出，DATABASE §4.3）；
 * 删除语义（D-6 / DEC-007）：内置（`isBuiltIn=true`）与默认「其他」由业务层拒绝，
 * 数据源侧另以 `isBuiltIn=0` SQL 守卫兜底。
 */
interface CategoryRepository {

    /**
     * 种子/导入批量写入（`REPLACE` 冲突策略，幂等）。
     *
     * 种子写入由 `countByLedger(ledgerId)==0` 守卫（DATABASE §8.3）；
     * 供 BootstrapUseCase（TASK-011）与 ImportAllUseCase（TASK-016）复用。
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
     * （API §5 / DEC-007）；数据源侧 `isBuiltIn=0` 守卫兜底。
     *
     * 直接删除路径：调用方须先经 [countTransactionsByCategory] 确认无关联交易
     * （0 引用走 `DeleteCategoryUseCase`）；有关联须走 [deleteCategoryWithMigration]。
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
     * 删除前关联检查：统计引用该分类的交易数；>0 则 UI 提示走迁移
     * （TASK-015 DeleteCategoryWithMigrationUseCase / UI D-15 依赖）。
     *
     * 与 [TransactionRepository.countTransactionsByCategory] 同口径。
     */
    suspend fun countTransactionsByCategory(categoryId: String): Int

    /**
     * 删除被引用分类并迁移其交易（A-1 采纳方案，TASK-015 单调用入口）。
     *
     * 编排顺序（对应 TASK-015「单事务内编排」在 Repository 侧的落地）：
     * 1. 取引用 [categoryId] 的全部交易（`TransactionFilter(categoryId=categoryId)` 快照）；
     * 2. 逐条 `update(tx.copy(categoryId = targetCategoryId))` 重挂目标分类；
     * 3. `deleteById(categoryId)` 删除旧分类。
     *
     * 前置校验（目标分类存在、同 `type`、非自身、count>0）由
     * `DeleteCategoryWithMigrationUseCase` 完成；本方法不承载业务规则
     * （ARCHITECTURE §4.1 Repository 不承载业务规则），仅保证编排顺序。
     *
     * **原子性契约（TASK-009 已闭合）**：本方法对外承诺「迁移+删除」的原子语义
     * （TASK-015 验收：第 k 条 update 失败 → 全部回滚，无悬空引用）。实现经
     * [com.minibook.data.datasource.TransactionRunner.runInTransaction] 将上述三步
     * 整体包进 DB 级事务（见 [CategoryRepositoryImpl]），事务细节隔离在数据层
     * [com.minibook.data.local.datasource.RoomTransactionRunner]，本接口仍不感知
     * Room/SQL（DEC-001）。
     */
    suspend fun deleteCategoryWithMigration(ledgerId: String, categoryId: String, targetCategoryId: String)
}
