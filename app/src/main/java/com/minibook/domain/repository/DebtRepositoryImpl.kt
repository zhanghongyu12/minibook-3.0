package com.minibook.domain.repository

import com.minibook.data.datasource.DebtDataSource
import com.minibook.domain.model.Debt
import com.minibook.domain.model.DebtFilter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * 债务 Repository 实现（TASK-009）。
 *
 * 纯委托 [DebtDataSource]：逐方法转发、无业务逻辑重写（ARCHITECTURE §4.1）。
 * 只依赖 `data/datasource` 接口，不感知 Room/SQL/SQLCipher。
 */
@Singleton
class DebtRepositoryImpl @Inject constructor(
    private val dataSource: DebtDataSource,
) : DebtRepository {

    override suspend fun insert(debt: Debt): String = dataSource.insert(debt)

    override suspend fun update(debt: Debt) = dataSource.update(debt)

    override suspend fun deleteById(id: String) = dataSource.deleteById(id)

    override fun observeByLedger(ledgerId: String, filter: DebtFilter): Flow<List<Debt>> =
        dataSource.observeByLedger(ledgerId, filter)

    override suspend fun netByParty(ledgerId: String): Map<String, Long> =
        dataSource.netByParty(ledgerId)
}
