package com.minibook.data.datasource

/**
 * 数据层事务原语（TASK-009 事务闭合方案）。
 *
 * 闭合 Repository 层多步编排的 DB 级原子性缺口：
 * - `CategoryRepository.deleteCategoryWithMigration` 的「取关联→迁移→删除」
 *   （TASK-015 迁移回滚：第 k 条失败 → 全部回滚，无悬空引用）；
 * - `BudgetRepository.setTotalBudget` 的「先删后插」（DEC-008：DELETE+REPLACE 须整体原子）。
 *
 * **本期唯一实现** [com.minibook.data.local.datasource.RoomTransactionRunner]
 * （Room 挂起事务，经 [com.minibook.di.TransactionModule] 装配）；未来云端实现可
 * mock（如远程事务/补偿逻辑），Repository 层零改动。
 *
 * 契约纯净化（DEC-001）：方法签名不泄露任何 Room/SQL 概念，仅表述「在事务内执行
 * 给定挂起块并返回其结果」；`block` 内抛异常 → 事务整体回滚、异常原样上抛。
 */
interface TransactionRunner {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}
