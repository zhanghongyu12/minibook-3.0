package com.minibook.domain.model

/**
 * 分类领域模型，对齐 DATABASE §4.3 / API §10。
 *
 * isBuiltIn 为 Boolean（true=内置禁删）；「其他」分类 order 恒 99 置末尾，自定义占 1~98。
 * type 为 `expense`/`income`。分类无 syncStatus（不参与增量同步，走全量导入导出）。
 */
data class Category(
    val id: String,
    val ledgerId: String,
    val name: String,
    val icon: String,
    val color: String,
    val type: CategoryType,
    val isBuiltIn: Boolean,
    val order: Int,
    val createdAt: String,
    val updatedAt: String,
)