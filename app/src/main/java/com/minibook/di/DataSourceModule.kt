package com.minibook.di

import com.minibook.data.datasource.BudgetDataSource
import com.minibook.data.datasource.CategoryDataSource
import com.minibook.data.datasource.DebtDataSource
import com.minibook.data.datasource.LedgerDataSource
import com.minibook.data.datasource.SyncChangeDataSource
import com.minibook.data.datasource.TransactionDataSource
import com.minibook.data.datasource.UserCategoryRuleDataSource
import com.minibook.data.local.datasource.LedgerDataSourceAdapter
import com.minibook.data.local.datasource.RoomDataSourceImpl
import com.minibook.domain.repository.BudgetRepository
import com.minibook.domain.repository.BudgetRepositoryImpl
import com.minibook.domain.repository.CategoryRepository
import com.minibook.domain.repository.CategoryRepositoryImpl
import com.minibook.domain.repository.LedgerRepository
import com.minibook.domain.repository.LedgerRepositoryImpl
import com.minibook.domain.repository.DebtRepository
import com.minibook.domain.repository.DebtRepositoryImpl
import com.minibook.domain.repository.TransactionRepository
import com.minibook.domain.repository.TransactionRepositoryImpl
import com.minibook.domain.repository.UserCategoryRuleRepository
import com.minibook.domain.repository.UserCategoryRuleRepositoryImpl
import com.minibook.data.local.dictionary.MerchantDictionaryStore
import com.minibook.domain.usecase.category.MerchantDictionary
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 数据源装配（ARCHITECTURE §6.3 / API §2、TASK-008）。
 *
 * 7 个 `*DataSource` 契约全部 `@Binds` 到本期 Room 实现：
 * - 6 个（Transaction/Debt/Category/Budget/SyncChange/UserCategoryRule）→ [RoomDataSourceImpl]
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

    /** Repository 层装配（TASK-012 回溯补齐：BudgetRepository 原为 Phase 2 预留无 Impl，现接线）。 */
    @Binds
    abstract fun bindBudgetRepository(impl: BudgetRepositoryImpl): BudgetRepository

    /** Repository 层装配（TASK-024/025 接线：页面 ViewModel 经 UseCase 依赖 Repository 接口）。 */
    @Binds
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    abstract fun bindDebtRepository(impl: DebtRepositoryImpl): DebtRepository

    @Binds
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    abstract fun bindLedgerRepository(impl: LedgerRepositoryImpl): LedgerRepository

    @Binds
    abstract fun bindUserCategoryRuleDataSource(impl: RoomDataSourceImpl): UserCategoryRuleDataSource

    @Binds
    abstract fun bindUserCategoryRuleRepository(impl: UserCategoryRuleRepositoryImpl): UserCategoryRuleRepository

    @Binds
    abstract fun bindSyncChangeDataSource(impl: RoomDataSourceImpl): SyncChangeDataSource

    /** 内置商户词典（docs/15 第 7 条）：data 层实现只负责装载，查询走 domain 注册表。 */
    @Binds
    abstract fun bindMerchantDictionary(impl: MerchantDictionaryStore): MerchantDictionary
}
