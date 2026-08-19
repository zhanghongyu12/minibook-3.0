package com.minibook.domain.repository

import com.minibook.data.datasource.TransactionDataSource
import com.minibook.domain.model.MonthSummary
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionFilter
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * 交易 Repository 实现（TASK-009）。
 *
 * 纯委托 [TransactionDataSource]：逐方法转发、无业务逻辑重写（ARCHITECTURE §4.1
 * Repository 为领域聚合入口 / 组合 DataSource 接口）。只依赖 `data/datasource`
 * 接口，不感知 Room/SQL/SQLCipher；未来接云端只改 `DataSourceModule` 绑定。
 */
@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val dataSource: TransactionDataSource,
) : TransactionRepository {

    override suspend fun insert(tx: Transaction): String = dataSource.insert(tx)

    override suspend fun update(tx: Transaction) = dataSource.update(tx)

    override suspend fun deleteById(id: String) = dataSource.deleteById(id)

    override suspend fun deleteByLedger(ledgerId: String) = dataSource.deleteByLedger(ledgerId)

    override fun observeByLedger(ledgerId: String, filter: TransactionFilter): Flow<List<Transaction>> =
        dataSource.observeByLedger(ledgerId, filter)

    override suspend fun monthlySummary(ledgerId: String, month: YearMonth): MonthSummary =
        dataSource.monthlySummary(ledgerId, month)

    override suspend fun getById(id: String): Transaction? = dataSource.getById(id)

    override suspend fun countTransactionsByCategory(categoryId: String): Int =
        dataSource.countTransactionsByCategory(categoryId)
}
