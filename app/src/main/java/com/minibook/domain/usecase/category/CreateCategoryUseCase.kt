package com.minibook.domain.usecase.category

import com.minibook.domain.common.InvalidInput
import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.model.BuiltInCategories
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import com.minibook.domain.repository.CategoryRepository
import com.minibook.util.TimeFormatter
import com.minibook.util.Validators
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * 新增自定义分类（TASK-015，PRD F-03 / DEC-007）。
 *
 * 规则（对齐 DATABASE §8.3）：
 * - `isBuiltIn=false`：自定义分类（与内置种子区分，D-6 判断一律看 `isBuiltIn`）；
 * - `order` 在 **1~98** 分配（不与「其他」99 冲突）：取当前账本同 `type` 已有分类的
 *   最小空缺排序值（删除/乱序后自动复用空位，`1 until OTHERS_ORDER`）；
 * - `type` 必填（expense/income，UI 选择）；
 * - `name` 非空（[Validators.validatePartyName]）。
 *
 * 生成 UUID + 时间戳 → [CategoryRepository.insert]。数据层 insert 会重生成真实 UUID
 * 与 `createdAt`/`updatedAt`（RoomDataSourceImpl，与 CloneBuiltInCategoriesUseCase
 * 同口径），返回值即落库 id，下游一律使用该 id。
 */
class CreateCategoryUseCase @Inject constructor(
    private val ledgerProvider: LedgerProvider,
    private val categoryRepository: CategoryRepository,
) {

    /**
     * 新增自定义分类到当前账本。
     *
     * @param name 分类名（非空）。
     * @param icon 图标 emoji。
     * @param color 主题色（如 `#FF6B6B`）。
     * @param type 收支类型（必填）。
     * @return 落库分类 id（数据层重生成，非入参占位）。
     * @throws InvalidInput name 为空 / 该类型分类数已达上限（98）。
     */
    suspend operator fun invoke(
        name: String,
        icon: String,
        color: String,
        type: CategoryType,
    ): String {
        if (!Validators.validatePartyName(name)) throw InvalidInput("分类名不能为空")

        val ledgerId = ledgerProvider.currentLedgerId()
        val order = nextOrder(ledgerId, type)
        val now = TimeFormatter.nowIso()

        // id/createdAt/updatedAt 为占位；数据层 insert 重生成真实 UUID 与时间戳。
        return categoryRepository.insert(
            Category(
                id = UUID.randomUUID().toString(),
                ledgerId = ledgerId,
                name = name,
                icon = icon,
                color = color,
                type = type,
                isBuiltIn = false,
                order = order,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /**
     * 分配同 `type` 下 1~98 的最小空缺 order（「其他」order=99 保留，DATABASE §8.3）。
     */
    private suspend fun nextOrder(ledgerId: String, type: CategoryType): Int {
        val usedOrders = categoryRepository.observeByLedger(ledgerId, type).first()
            .map { it.order }
            .toSet()
        return (1 until BuiltInCategories.OTHERS_ORDER).firstOrNull { it !in usedOrders }
            ?: throw InvalidInput("该类型分类数量已达上限（98），请先删除无用分类")
    }
}
