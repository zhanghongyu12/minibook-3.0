package com.minibook.domain.usecase.category

import com.minibook.domain.common.LedgerProvider
import com.minibook.domain.model.BuiltInCategories
import com.minibook.domain.repository.CategoryRepository
import com.minibook.util.Validators
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * 改分类名/图标/颜色/排序（TASK-015，DEC-007）。
 *
 * **锁定字段（业务拒绝改）**：
 * - `type` / `isBuiltIn` 不可改——本类仅透传新 `name/icon/color/order`，其余字段从原值
 *   `copy`，天然锁定（数据层 update SQL 亦不写这两列，双保险）；
 * - 「其他」兜底分类 `name` **只读**（拒绝改名；判断 `order==OTHERS_ORDER(99) && isBuiltIn`
 *   或 `name=="其他"`），其 icon/color/order 仍可改（DEC-007）。
 *
 * 先按 id 取原值校验再 update（防篡改 type/isBuiltIn/「其他」名）。Repository 未暴露
 * `getById`，此处以 `observeByLedger(ledgerId, null)` 快照按 id 定位原值（等价读）。
 */
class UpdateCategoryUseCase @Inject constructor(
    private val ledgerProvider: LedgerProvider,
    private val categoryRepository: CategoryRepository,
) {

    /**
     * 更新当前账本下 [categoryId] 分类。
     *
     * @param categoryId 目标分类 id。
     * @param name 新分类名（非空；「其他」锁定只读，拒绝改名）。
     * @param icon 新图标 emoji。
     * @param color 新主题色（如 `#FF6B6B`）。
     * @param order 新排序值（1~98；99 为「其他」保留）。
     * @throws IllegalArgumentException 分类不存在 / name 为空 / 「其他」改名 / order 越界。
     */
    suspend operator fun invoke(
        categoryId: String,
        name: String,
        icon: String,
        color: String,
        order: Int,
    ) {
        if (!Validators.validatePartyName(name)) throw IllegalArgumentException("分类名不能为空")
        if (order !in 1 until BuiltInCategories.OTHERS_ORDER) {
            throw IllegalArgumentException("分类排序值须在 1~98（99 为「其他」保留）")
        }

        val ledgerId = ledgerProvider.currentLedgerId()
        val original = categoryRepository.observeByLedger(ledgerId, null).first()
            .firstOrNull { it.id == categoryId }
            ?: throw IllegalArgumentException("分类不存在：$categoryId")

        // 「其他」name 只读（DEC-007）：仅当其名被实际改动才拒绝（同名无操作放行）。
        val isOthers = (original.isBuiltIn && original.order == BuiltInCategories.OTHERS_ORDER) ||
            original.name == "其他"
        if (isOthers && name != original.name) {
            throw IllegalArgumentException("「其他」分类默认名不可修改")
        }

        // type/isBuiltIn 从原值 copy，锁定不可改（数据层 update 亦不写这两列，双保险）。
        categoryRepository.update(
            original.copy(
                name = name,
                icon = icon,
                color = color,
                order = order,
            ),
        )
    }
}
