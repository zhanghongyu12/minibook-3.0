package com.minibook.domain.repository

import com.minibook.data.datasource.LedgerDataSource
import com.minibook.domain.model.Ledger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * 账本 Repository 实现（TASK-009）。
 *
 * 纯委托 [LedgerDataSource]：逐方法转发、无业务逻辑重写（ARCHITECTURE §4.1）。
 * 只依赖 `data/datasource` 接口，不感知 Room/SQL/SQLCipher。
 */
@Singleton
class LedgerRepositoryImpl @Inject constructor(
    private val dataSource: LedgerDataSource,
) : LedgerRepository {

    override suspend fun insert(ledger: Ledger): String = dataSource.insert(ledger)

    override suspend fun update(ledger: Ledger): Ledger = dataSource.update(ledger)

    override suspend fun deleteById(id: String) = dataSource.deleteById(id)

    override suspend fun getById(id: String): Ledger? = dataSource.getById(id)

    override suspend fun getDefault(): Ledger? = dataSource.getDefault()

    override fun observeAll(): Flow<List<Ledger>> = dataSource.observeAll()
}
