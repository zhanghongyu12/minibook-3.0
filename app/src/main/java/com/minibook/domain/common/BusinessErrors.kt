package com.minibook.domain.common

/**
 * 业务校验异常基类（TASK-013）。
 *
 * 业务层（UseCase）校验失败统一抛本基类子类；UI 层集中捕获 `IllegalStateException`
 * 族做错误提示（与 [LedgerNotFound] 同族）。
 *
 * 注：[LedgerNotFound] 定义于 `LedgerProvider.kt`（TASK-012 已 commit），本文件
 * **不重复定义**，仅补齐业务校验异常。
 */
open class BusinessError(message: String) : IllegalStateException(message)

/**
 * 通用输入非法（债务/分类等非交易域的校验失败，TASK-013 补齐）。
 *
 * 触发点：
 * - 债务：`partyName` 为空、金额 ≤ 0 或超上限（Add/Update 校验层拒绝保存）；
 * - 分类：名称空、排序越界、目标不存在、内置禁删、「其他」改名、引用计数 > 0 应走迁移等。
 */
class InvalidInput(message: String) : BusinessError(message)

/**
 * 交易输入非法或目标不存在（TASK-013）。
 *
 * 触发点：
 * - 金额 ≤ 0 / 超上限、备注 > 200（Add/Update 校验层拒绝保存）；
 * - Delete/Update 目标交易不存在（API §3「id 不存在为空操作」由 Repository 兜底，
 *   UseCase 显式校验以便 UI 反馈「该笔已删除」）。
 */
class InvalidTransactionInput(message: String) : BusinessError(message)

/**
 * 交易缺少分类（TASK-013）。
 *
 * 触发点：`categoryId` 为空或纯空白——DATABASE §4.1 `categoryId` 为 NOT NULL，分类必填。
 */
class CategoryRequired(message: String = "交易必须关联分类") : BusinessError(message)
