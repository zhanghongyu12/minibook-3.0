package com.minibook.domain.model

/**
 * 内置分类种子常量（TASK-011），对齐 DATABASE §8.1 / §8.2 两张表逐行一致。
 *
 * 24 条模板（16 支出 + 8 收入）作为**不可变常量**：`id`/`ledgerId`/`createdAt`/`updatedAt`
 * 以空串占位（此时**不含具体 UUID**），克隆时由
 * [com.minibook.domain.usecase.category.CloneBuiltInCategoriesUseCase] 为**每个账本独立**
 * 生成 UUID + 账本 ID + 实际时间戳（DATABASE §8.3：不同账本的同名内置分类 id 不同）。
 * 判断「是否内置」一律看 `isBuiltIn`（D-6）。
 *
 * 口径（DATABASE §8 / DEC-007）：
 * - 「其他」兜底分类 `order=99` 恒末尾、默认名锁定（不可改名）；其余内置分类允许改
 *   icon/color/name，但不可删、不可改 type/isBuiltIn；
 * - `type` 与交易类型对应（expense/income），克隆时原样透传。
 * - `icon` 自 2026-10-12 起是 [com.minibook.ui.components.CategoryIcons] 的**稳定 key**
 *   （如 `"food"`），不再是 emoji；渲染时经 [com.minibook.ui.components.CategoryIcons.resolve]
 *   解析为矢量图标，历史 emoji 取值由该对象内部的 `LEGACY_EMOJI` 表兜底映射（不做数据迁移）。
 */
object BuiltInCategories {

    /** 「其他」兜底分类固定 order（DATABASE §8.3 / DEC-007），恒排末尾。 */
    const val OTHERS_ORDER: Int = 99

    /** 模板占位 id：ledgerId 在克隆时填充，此刻不含具体 UUID（DATABASE §8.3）。 */
    private const val PLACEHOLDER_ID: String = ""

    /** 模板占位时间戳：createdAt/updatedAt 在克隆时填实际值（DATABASE §9.5 ISO-8601）。 */
    private const val PLACEHOLDER_TIMESTAMP: String = ""

    /** 内置支出分类模板（16），顺序即 DATABASE §8.1 展示顺序；「其他」order=99。 */
    val EXPENSE: List<Category> = listOf(
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "餐饮",
            icon = "food",
            color = "#FF6B6B",
            type = CategoryType.EXPENSE,
            isBuiltIn = true,
            order = 1,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "交通",
            icon = "transport",
            color = "#4ECDC4",
            type = CategoryType.EXPENSE,
            isBuiltIn = true,
            order = 2,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "购物",
            icon = "shopping",
            color = "#45B7D1",
            type = CategoryType.EXPENSE,
            isBuiltIn = true,
            order = 3,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "娱乐",
            icon = "entertainment",
            color = "#96CEB4",
            type = CategoryType.EXPENSE,
            isBuiltIn = true,
            order = 4,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "住房",
            icon = "housing",
            color = "#FFEAA7",
            type = CategoryType.EXPENSE,
            isBuiltIn = true,
            order = 5,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "医疗",
            icon = "medical",
            color = "#DDA0DD",
            type = CategoryType.EXPENSE,
            isBuiltIn = true,
            order = 6,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "教育",
            icon = "education",
            color = "#98D8C8",
            type = CategoryType.EXPENSE,
            isBuiltIn = true,
            order = 7,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "服饰",
            icon = "clothing",
            color = "#F7DC6F",
            type = CategoryType.EXPENSE,
            isBuiltIn = true,
            order = 8,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "通讯",
            icon = "communication",
            color = "#BB8FCE",
            type = CategoryType.EXPENSE,
            isBuiltIn = true,
            order = 9,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "人情",
            icon = "gift",
            color = "#F0B27A",
            type = CategoryType.EXPENSE,
            isBuiltIn = true,
            order = 10,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "旅行",
            icon = "travel",
            color = "#85C1E9",
            type = CategoryType.EXPENSE,
            isBuiltIn = true,
            order = 11,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "美团",
            icon = "takeout",
            color = "#FFB300",
            type = CategoryType.EXPENSE,
            isBuiltIn = true,
            order = 20,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "转账",
            icon = "transfer",
            color = "#7F8C8D",
            type = CategoryType.EXPENSE,
            isBuiltIn = true,
            order = 21,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "缴费",
            icon = "utilities",
            color = "#F4D03F",
            type = CategoryType.EXPENSE,
            isBuiltIn = true,
            order = 22,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "付款",
            icon = "payment",
            color = "#EC7063",
            type = CategoryType.EXPENSE,
            isBuiltIn = true,
            order = 23,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "其他",
            icon = "repair",
            color = "#AEB6BF",
            type = CategoryType.EXPENSE,
            isBuiltIn = true,
            order = OTHERS_ORDER,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
    )

    /** 内置收入分类模板（8），顺序即 DATABASE §8.2 展示顺序；「其他」order=99。 */
    val INCOME: List<Category> = listOf(
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "工资",
            icon = "salary",
            color = "#2ECC71",
            type = CategoryType.INCOME,
            isBuiltIn = true,
            order = 1,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "奖金",
            icon = "bonus",
            color = "#F39C12",
            type = CategoryType.INCOME,
            isBuiltIn = true,
            order = 2,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "投资",
            icon = "investment",
            color = "#3498DB",
            type = CategoryType.INCOME,
            isBuiltIn = true,
            order = 3,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "兼职",
            icon = "parttime",
            color = "#9B59B6",
            type = CategoryType.INCOME,
            isBuiltIn = true,
            order = 4,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "礼金",
            icon = "giftmoney",
            color = "#E74C3C",
            type = CategoryType.INCOME,
            isBuiltIn = true,
            order = 5,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "收款",
            icon = "receive",
            color = "#5DADE2",
            type = CategoryType.INCOME,
            isBuiltIn = true,
            order = 20,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "退款",
            icon = "refund",
            color = "#58D68D",
            type = CategoryType.INCOME,
            isBuiltIn = true,
            order = 21,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
        Category(
            id = PLACEHOLDER_ID,
            ledgerId = PLACEHOLDER_ID,
            name = "其他",
            icon = "cash",
            color = "#1ABC9C",
            type = CategoryType.INCOME,
            isBuiltIn = true,
            order = OTHERS_ORDER,
            createdAt = PLACEHOLDER_TIMESTAMP,
            updatedAt = PLACEHOLDER_TIMESTAMP,
        ),
    )

    /** 全量内置模板（24）：支出在前、收入在后；克隆/校验共用（DATABASE §8 两表总和）。 */
    val ALL: List<Category> = EXPENSE + INCOME
}