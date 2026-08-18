package com.minibook.domain.repository

import com.minibook.domain.model.Debt
import com.minibook.domain.model.DebtFilter
import kotlinx.coroutines.flow.Flow

/**
 * 债务/借还（PRD F-02）领域聚合入口，对齐 ARCHITECTURE §4.1。
 *
 * UseCase 层唯一债务入口；实现 [DebtRepositoryImpl] 委托 `data/datasource` 的
 * `DebtDataSource`，自身不感知 Room/SQL/SQLCipher（ARCHITECTURE §4.1 / DEC-001）。
 *
 * 借/还平级模型：`kind × direction` 四态（DATABASE §4.2），净额口径见 [netByParty]。
 * 所有业务查询首参统一 `ledgerId`（ARCHITECTURE §8.1）。
 */
interface DebtRepository {

    /**
     * 记一笔借/还；`kind × direction` 四态平级（DATABASE §4.2）。
     *
     * @return 生成的 UUID。
     */
    suspend fun insert(debt: Debt): String

    /** 按 [Debt.id] 更新。 */
    suspend fun update(debt: Debt)

    /** 物理删除。 */
    suspend fun deleteById(id: String)

    /**
     * 明细视图：账本内按时间倒序；[filter] 为可选 `kind` / `direction` 过滤
     * （A-2 确认展开，`DebtFilter{ kind?, direction? }`，null=不过滤）。
     */
    fun observeByLedger(ledgerId: String, filter: DebtFilter): Flow<List<Debt>>

    /**
     * 按对方姓名汇总净额（分）；正值=对方欠我，负值=我欠对方，0=两清。
     *
     * 口径（PRD F-02 / DATABASE §4.2）：lend×owe_me=+、lend×i_owe=−、
     * repay×owe_me=−、repay×i_owe=+；DB 端 `CASE WHEN` 一次聚合
     * （DATABASE §6.2 observeNetByParty）。
     */
    suspend fun netByParty(ledgerId: String): Map<String, Long>
}
