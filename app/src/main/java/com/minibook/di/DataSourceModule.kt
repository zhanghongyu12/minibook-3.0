package com.minibook.di

import com.minibook.data.datasource.BudgetDataSource
import com.minibook.data.datasource.CategoryDataSource
import com.minibook.data.datasource.DebtDataSource
import com.minibook.data.datasource.LedgerDataSource
import com.minibook.data.datasource.SyncChangeDataSource
import com.minibook.data.datasource.TransactionDataSource
import com.minibook.data.local.datasource.LedgerDataSourceAdapter
import com.minibook.data.local.datasource.RoomDataSourceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 数据源装配（ARCHITECTURE §6.3 / API §2、TASK-008）。
 *
 * 6 个 `*DataSource` 契约全部 `@Binds` 到本期 Room 实现：
 * - 5 个（Transaction/Debt/Category/Budget/SyncChange）→ [RoomDataSourceImpl]
 *   （@Singleton，构造注入 DatabaseGate，见 [RoomDataSourceImpl] 类注释）；
 * - [LedgerDataSource] → [LedgerDataSourceAdapter]（因 `getById` 签名冲突独立成类，
 *   见 [RoomDataSource] 注释，二者共享同一 [com.minibook.data.local.security.DatabaseGate]）。
 *
 * **未来接云端只改本模块 @Binds 一处**（ARCHITECTURE §6.3）：新增 `RemoteDataSource`
 * / `SyncingDataSource` 实现后，替换对应接口的绑定即可，业务层与其余接口零改动。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {

    @Binds
    abstract fun bindTransactionDataSource(impl: RoomDataSourceImpl): TransactionDataSource

    @Binds
    abstract fun bindDebtDataSource(impl: RoomDataSourceImpl): DebtDataSource

    @Binds
    abstract fun bindCategoryDataSource(impl: RoomDataSourceImpl): CategoryDataSource

    @Binds
    abstract fun bindLedgerDataSource(impl: LedgerDataSourceAdapter): LedgerDataSource

    @Binds
    abstract fun bindBudgetDataSource(impl: RoomDataSourceImpl): BudgetDataSource

    @Binds
    abstract fun bindSyncChangeDataSource(impl: RoomDataSourceImpl): SyncChangeDataSource
}
