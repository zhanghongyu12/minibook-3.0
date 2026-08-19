package com.minibook.domain.common

/**
 * 业务校验异常基类（TASK-013）。
 *
 * 业务层（UseCase）校验失败统一抛本基类子类；UI 层集中捕获 `IllegalStateException`
 * 族做错误提示（与 [LedgerNotFound] 同族）。
 *
 * 注：[LedgerNotFound] 定义于 `LedgerProvider.kt`（TASK-012 已 commit），本文件
 * **不重复定义**，仅补齐交易相关校验异常。
 */
open class BusinessError(message: String) : IllegalStateException(message)

/**
 * 交易输入非法或目标不存在（TASK-013）。
 *
 * 触发点：
 * - 金额 ≤ 0 / 超上限、备注 > 200、分类为空（Add/Update 校验层拒绝保存）；
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
