package com.minibook.domain.usecase.category

import com.minibook.data.local.datastore.LedgerDataStore
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import com.minibook.domain.repository.CategoryRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * 观察当前账本分类流（TASK-015，PRD F-03 / ARCHITECTURE §8.3）。
 *
 * 账本维度 + 可选 `type` 过滤（null=全部）：`ledgerDataStore.currentLedgerId`
 * 逐次发射 → [flatMapLatest] 重订阅到当前账本的分类流（ARCHITECTURE §8.3：
 * 切账本自动重订阅，分类/收支/统计口径不混账）。
 *
 * **observe 特殊性**：与一次性 UseCase（Create/Update/Delete 经
 * [com.minibook.domain.common.LedgerProvider.currentLedgerId] 取当前账本）不同，
 * 本类须**响应账本切换**，故直接注入 [LedgerDataStore] 以 `currentLedgerId` 流
 * 做 `flatMapLatest`（同 TASK-013/014 observe 方案）。
 *
 * 当前账本未初始化（空串，TASK-011 Bootstrap 尚未写入）时发射空列表而非抛错，
 * 避免冷流 collector 直接崩；Bootstrap 写入后 `flatMapLatest` 自动重订阅到真实数据。
 */
class ObserveCategoriesUseCase @Inject constructor(
    private val ledgerDataStore: LedgerDataStore,
    private val categoryRepository: CategoryRepository,
) {

    /**
     * 当前账本分类流（按 `order` 升序，DATABASE §6.3；「其他」order=99 恒末尾）。
     *
     * @param type 收支类型过滤；null=全部。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(type: CategoryType? = null): Flow<List<Category>> =
        ledgerDataStore.currentLedgerId.flatMapLatest { ledgerId ->
            if (ledgerId.isBlank()) {
                // 首启前无当前账本：发空列表而非抛错，待 Bootstrap 写入后自动重订阅。
                flowOf(emptyList())
            } else {
                categoryRepository.observeByLedger(ledgerId, type)
            }
        }
}
