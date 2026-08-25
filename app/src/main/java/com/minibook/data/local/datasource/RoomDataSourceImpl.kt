package com.minibook.data.local.datasource

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.minibook.data.datasource.BudgetDataSource
import com.minibook.data.datasource.CategoryDataSource
import com.minibook.data.datasource.DebtDataSource
import com.minibook.data.datasource.SyncChangeDataSource
import com.minibook.data.datasource.TransactionDataSource
import com.minibook.data.datasource.TransactionRunner
import com.minibook.data.datasource.UserCategoryRuleDataSource
import com.minibook.data.local.db.CategoryAggRow
import com.minibook.data.local.db.PeriodTrendRow
import com.minibook.data.local.db.dao.BudgetDao
import com.minibook.data.local.db.dao.CategoryDao
import com.minibook.data.local.db.dao.DebtDao
import com.minibook.data.local.db.dao.SyncChangeDao
import com.minibook.data.local.db.dao.TransactionDao
import com.minibook.data.local.db.dao.UserCategoryRuleDao
import com.minibook.data.local.db.entity.BudgetEntity
import com.minibook.data.local.db.entity.TransactionEntity
import com.minibook.data.local.security.DatabaseProvider
import com.minibook.data.mapper.BudgetMapper
import com.minibook.data.mapper.CategoryMapper
import com.minibook.data.mapper.DebtMapper
import com.minibook.data.mapper.SyncChangeMapper
import com.minibook.data.mapper.TransactionMapper
import com.minibook.data.mapper.UserCategoryRuleMapper
import com.minibook.domain.model.Budget
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryAgg
import com.minibook.domain.model.CategoryType
import com.minibook.domain.model.Debt
import com.minibook.domain.model.DebtFilter
import com.minibook.domain.model.MonthSummary
import com.minibook.domain.model.PeriodTrend
import com.minibook.domain.model.SyncChange
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionFilter
import com.minibook.domain.model.TransactionType
import com.minibook.domain.model.UserCategoryRule
import java.time.Instant
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Room 数据源唯一实现（TASK-008），聚合 6 DAO 直接实现 6 个 `*DataSource` 接口。
 *
 * 结构说明：`TransactionDataSource/DebtDataSource/CategoryDataSource/BudgetDataSource/
 * SyncChangeDataSource/UserCategoryRuleDataSource` 直接实现；`LedgerDataSource` 因 `getById` 签名冲突（见 [RoomDataSource]）
 * 由独立 [LedgerDataSourceAdapter] 实现，经 `DataSourceModule` 的 `@Binds` 装配。
 *
 * 锁周期安全（ARCHITECTURE §7.5 / DatabaseModule 注释）：本实现**不缓存 DAO 引用**，
 * 构造注入 [DatabaseProvider]，每方法经 `gate.database?.xxxDao()` 取「当前已打开数据库」的 DAO；
 * 未解锁时 `gate.database` 为 null → 统一 `error(...)` 硬失败。重锁→再解锁后
 * 自动取到新建 DB 的 DAO，绝不持有已关闭实例。
 *
 * 映射口径（DEC-001 / DATABASE §9）：
 * - `amount` 保持 `Long` 分，不转换不丢精度；`date`/`createdAt`/`updatedAt` ISO 字符串原样透传。
 * - 枚举列（type/kind/direction/syncStatus）经各 `*Mapper` 在 entity( String ) ↔ domain( enum ) 间互转。
 * - insert 生成 UUID + `createdAt`；update 刷新 `updatedAt`（API §1/§3 语义）。
 *
 * 少数组件缺口与取舍（本层已自行闭合，见各方法注释）：
 * - DAO 的 delete 一律带 `ledgerId` 维度（DATABASE §6「所有查询带 ledgerId」），而 `*DataSource`
 *   契约的 `deleteById(id)` 为 id 单参（API §3~§5）；`deleteById` 同签名被 Transaction/Debt/
 *   Category 三接口共享（无法在单实现内按接口分发到不同表），故对三表各发等价 DELETE——
 *   UUID 全局唯一，实际只会命中 id 所属那一行（其余为空操作），分类保留 `isBuiltIn=0`
 *   防误删守卫（DEC-007）。方法内部经 [TransactionRunner] 事务包裹，**自身原子**
 *   （TASK-013 评审 W4，不依赖调用方闭合事务）。
 * - TASK-012 回溯补齐的 `deleteByLedger(ledgerId)` 亦同签名被 Transaction/Debt/Category/
 *   Budget/UserCategoryRule 五接口共享（无法在单实现内按接口分发到不同表），单方法对五表各发等价 DELETE
 *   ——ledgerId 为各表外键维度，五表全部命中（删账本场景，DATABASE §4.4）；分类无
 *   `isBuiltIn=0` 守卫（删账本时内置分类一并清空），预算无「先删后插」语义，
 *   user_category_rules 随账本一并清空（F-12 分类自学习规则，C5）。方法内部
 *   同样经 [TransactionRunner] 事务包裹，**自身原子**（TASK-013 评审 W4）。
 * - 无 ledgerId 入参的 `getById(id)` 经 `gate.database.openHelper.writableDatabase` 直接查询
 *   （Room 官方逃生舱）；`Category.observeByLedger(type=null)` 由 expense+income 双流 [combine]。
 * - `SyncChangeDataSource.enqueue` 签名缺 `ledgerId` 而表结构要求该列，本期属 Phase 3
 *   预留「只建表不写数据」，故以显式 `UnsupportedOperationException` 落桩。
 */
@Singleton
class RoomDataSourceImpl @Inject constructor(
    private val gate: DatabaseProvider,
    private val transactionRunner: TransactionRunner,
) : RoomDataSource,
    TransactionDataSource,
    DebtDataSource,
    CategoryDataSource,
    BudgetDataSource,
    SyncChangeDataSource,
    UserCategoryRuleDataSource {

    // ===== TransactionDataSource =====

    override suspend fun insert(tx: Transaction): String {
        val now = Instant.now().toString()
        val id = UUID.randomUUID().toString()
        transactionDao().insert(
            TransactionMapper.toEntity(tx.copy(id = id, createdAt = now, updatedAt = now)),
        )
        return id
    }

    /**
     * 批量插入（F-12 账单导入，DEC-019 / API §13.5）。
     *
     * 与单条 [insert] 不同：**不重生成 id/createdAt/updatedAt**——导入编排方
     * （ImportBillUseCase）已按 §13.6 为每行装配 UUID + 时间戳，此处原样透传
     * （`@Insert(IGNORE)` 的冲突键是 `UNIQUE(ledgerId, externalId)`，与主键无关）。
     *
     * @return 实际插入行数（统计 `insertAll` 返回中非 `-1` 的下标数）。
     */
    override suspend fun insertBatch(txs: List<Transaction>): Int {
        if (txs.isEmpty()) return 0
        val results = transactionDao().insertAll(txs.map(TransactionMapper::toEntity))
        return results.count { it != -1L }
    }

    override suspend fun getExistingExternalIds(ledgerId: String): Set<String> =
        transactionDao().getAllExternalIds(ledgerId).toSet()

    override suspend fun update(tx: Transaction) {
        transactionDao().update(
            id = tx.id,
            ledgerId = tx.ledgerId,
            type = tx.type.value,
            amount = tx.amount,
            categoryId = tx.categoryId,
            description = tx.description,
            date = tx.date,
            updatedAt = Instant.now().toString(),
        )
    }

    /**
     * 同签名三接口共享（Transaction/Debt/Category 均「物理删除」）：见类注释取舍说明。
     *
     * **自身原子（TASK-013 评审 W4）**：三表 DELETE 整体包进
     * [TransactionRunner.runInTransaction]——方法自身闭合事务，不依赖调用方；
     * 若调用方已在事务内（如 [com.minibook.domain.repository.CategoryRepositoryImpl.deleteCategoryWithMigration]），
     * Room `withTransaction` 可重入，直接并入外层事务，不产生嵌套事务。
     */
    override suspend fun deleteById(id: String) {
        transactionRunner.runInTransaction {
            val db = writableDatabase()
            db.delete("transactions", "id = ?", arrayOf(id))
            db.delete("debts", "id = ?", arrayOf(id))
            // 保留 DAO deleteCustom 的 `isBuiltIn=0` 守卫（DEC-007 内置禁删下沉到 SQL）。
            db.delete("categories", "id = ? AND isBuiltIn = 0", arrayOf(id))
        }
    }

    /**
     * 同签名多接口共享（Transaction/Debt/Category/Budget/UserCategoryRule 均「按 ledgerId 批删」）：
     * 见类注释取舍说明。DAO 均无 `deleteByLedger` → 各表各发等价 DELETE（原始 SQL，
     * 与 deleteById 三表 DELETE 同一逃生舱）。user_category_rules 为分类自学习规则
     * （F-12，C5「删账本随账本清空」），同批删除。
     *
     * **自身原子（TASK-013 评审 W4）**：各表 DELETE 整体包进
     * [TransactionRunner.runInTransaction]——方法自身闭合事务，不依赖调用方；
     * 调用方 [com.minibook.domain.usecase.ledger.DeleteLedgerUseCase] 外层已包事务时
     * （Room `withTransaction` 可重入）并入外层事务，不产生嵌套事务。
     *
     * 语义差异说明：
     * - categories 删账本场景**内置分类一并清空，无 `isBuiltIn=0` 守卫**（与单条
     *   deleteById 的 DEC-007 守卫不同）；
     * - budgets 无「先删后插」语义（DEC-008 只约束月度总预算唯一性）。
     */
    override suspend fun deleteByLedger(ledgerId: String) {
        transactionRunner.runInTransaction {
            val db = writableDatabase()
            db.delete("transactions", "ledgerId = ?", arrayOf(ledgerId))
            db.delete("debts", "ledgerId = ?", arrayOf(ledgerId))
            db.delete("categories", "ledgerId = ?", arrayOf(ledgerId))
            db.delete("budgets", "ledgerId = ?", arrayOf(ledgerId))
            db.delete("user_category_rules", "ledgerId = ?", arrayOf(ledgerId))
        }
    }

    override fun observeByLedger(ledgerId: String, filter: TransactionFilter): Flow<List<Transaction>> =
        transactionDao().observeFiltered(
            ledgerId = ledgerId,
            type = filter.type?.value,
            categoryId = filter.categoryId,
            dateFrom = filter.dateFrom,
            dateTo = filter.dateTo,
            remark = filter.remark,
            amountMin = filter.amountMin,
            amountMax = filter.amountMax,
        ).map { list -> list.map(TransactionMapper::toDomain) }

    override fun observeDailyTrend(ledgerId: String, start: String, end: String): Flow<List<PeriodTrend>> =
        transactionDao().observeDailyTrend(ledgerId, start, end)
            .map { list -> list.map { it.toDomain() } }

    override fun observeMonthlyTrend(ledgerId: String, start: String, end: String): Flow<List<PeriodTrend>> =
        transactionDao().observeMonthlyTrend(ledgerId, start, end)
            .map { list -> list.map { it.toDomain() } }

    override fun observeCategoryAgg(
        ledgerId: String,
        type: TransactionType,
        start: String,
        end: String,
    ): Flow<List<CategoryAgg>> =
        transactionDao().observeCategoryAgg(ledgerId, type.value, start, end)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun monthlySummary(ledgerId: String, month: YearMonth): MonthSummary {
        val row = transactionDao().monthSummary(
            ledgerId = ledgerId,
            monthStart = month.atDay(1).toString(),
            monthEnd = month.atEndOfMonth().toString(),
        )
        return MonthSummary(
            incomeFen = row.incomeFen,
            expenseFen = row.expenseFen,
            balanceFen = row.balance,
        )
    }

    override suspend fun getById(id: String): Transaction? {
        writableDatabase().query("SELECT * FROM transactions WHERE id = ?", arrayOf(id)).use { cursor ->
            return if (cursor.moveToFirst()) TransactionMapper.toDomain(cursor.toTransactionEntity()) else null
        }
    }

    override suspend fun countTransactionsByCategory(categoryId: String): Int =
        // 与 CategoryDataSource 同口径（API §3 注）：categoryId 为全局唯一 UUID，
        // 复用 CategoryDao.countRef（`WHERE categoryId=?` 无 ledgerId，DATABASE §6.3）。
        categoryDao().countRef(categoryId)

    // ===== DebtDataSource =====

    override suspend fun insert(debt: Debt): String {
        val now = Instant.now().toString()
        val id = UUID.randomUUID().toString()
        debtDao().insert(DebtMapper.toEntity(debt.copy(id = id, createdAt = now, updatedAt = now)))
        return id
    }

    override suspend fun update(debt: Debt) {
        debtDao().update(
            id = debt.id,
            ledgerId = debt.ledgerId,
            partyName = debt.partyName,
            amount = debt.amount,
            kind = debt.kind.value,
            direction = debt.direction.value,
            date = debt.date,
            remark = debt.remark,
            updatedAt = Instant.now().toString(),
        )
    }

    override fun observeByLedger(ledgerId: String, filter: DebtFilter): Flow<List<Debt>> =
        debtDao().observeFiltered(
            ledgerId = ledgerId,
            kind = filter.kind?.value,
            direction = filter.direction?.value,
            partyName = filter.partyName,
        ).map { list -> list.map(DebtMapper::toDomain) }

    override suspend fun netByParty(ledgerId: String): Map<String, Long> =
        debtDao().observeNetByParty(ledgerId).first().associate { it.partyName to it.netFen }

    // ===== CategoryDataSource =====

    override suspend fun upsertList(categories: List<Category>) {
        categoryDao().upsertList(categories.map(CategoryMapper::toEntity))
    }

    override suspend fun insert(category: Category): String {
        val now = Instant.now().toString()
        val id = UUID.randomUUID().toString()
        categoryDao().insert(CategoryMapper.toEntity(category.copy(id = id, createdAt = now, updatedAt = now)))
        return id
    }

    override suspend fun update(category: Category) {
        categoryDao().update(
            id = category.id,
            ledgerId = category.ledgerId,
            name = category.name,
            icon = category.icon,
            color = category.color,
            order = category.order,
            updatedAt = Instant.now().toString(),
        )
    }

    override suspend fun countByLedger(ledgerId: String): Long =
        categoryDao().countByLedger(ledgerId)

    override fun observeByLedger(ledgerId: String, type: CategoryType?): Flow<List<Category>> =
        when (type) {
            // DAO observeByType 的 type 参数为非空（DATABASE §6.3），null=全部由 expense+income 双流合并。
            null -> combine(
                categoryDao().observeByType(ledgerId, CategoryType.EXPENSE.value),
                categoryDao().observeByType(ledgerId, CategoryType.INCOME.value),
            ) { expense, income -> expense + income }
                .map { list -> list.map(CategoryMapper::toDomain) }

            else -> categoryDao().observeByType(ledgerId, type.value)
                .map { list -> list.map(CategoryMapper::toDomain) }
        }

    // ===== UserCategoryRuleDataSource（F-12 分类自学习，DB version 3） =====

    override suspend fun upsertRule(rule: UserCategoryRule) {
        val now = Instant.now().toString()
        userCategoryRuleDao().upsert(
            UserCategoryRuleMapper.toEntity(
                rule.copy(
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
    }

    override suspend fun getRules(ledgerId: String): List<UserCategoryRule> =
        userCategoryRuleDao().queryByLedger(ledgerId).map(UserCategoryRuleMapper::toDomain)

    // ===== BudgetDataSource（Phase 2 预留，本期骨架） =====

    override suspend fun upsertTotal(ledgerId: String, month: YearMonth, amount: Long) {
        // 月度总预算（categoryId=null）唯一由 Repository 层「先删后插」保证（D-7 / DEC-008），
        // 本层只落 upsert。
        upsertBudget(ledgerId = ledgerId, month = month, categoryId = null, amount = amount)
    }

    override suspend fun upsertCategory(ledgerId: String, month: YearMonth, categoryId: String, amount: Long) {
        // 分类预算由 DB UNIQUE(ledgerId, month, categoryId) 约束保证唯一（REPLACE）。
        upsertBudget(ledgerId = ledgerId, month = month, categoryId = categoryId, amount = amount)
    }

    override suspend fun deleteTotal(ledgerId: String, month: YearMonth) {
        budgetDao().deleteTotal(ledgerId, month.toString())
    }

    override suspend fun deleteByCategory(ledgerId: String, month: YearMonth, categoryId: String) {
        // BudgetDao 仅 deleteTotal（categoryId IS NULL），分类预算删除按表列等价 SQL 补齐。
        writableDatabase().delete(
            "budgets",
            "ledgerId = ? AND month = ? AND categoryId = ?",
            arrayOf(ledgerId, month.toString(), categoryId),
        )
    }

    override suspend fun deleteByCategoryId(categoryId: String) {
        // P1-1：删分类级联清其预算；调用方（DeleteCategory*UseCase）外层事务已闭合，本方法纯委托。
        budgetDao().deleteByCategoryId(categoryId)
    }

    override fun observeByMonth(ledgerId: String, month: YearMonth): Flow<List<Budget>> =
        budgetDao().observeByMonth(ledgerId, month.toString())
            .map { list -> list.map(BudgetMapper::toDomain) }

    // ===== SyncChangeDataSource（Phase 3 预留，本期只建表不写数据） =====

    override suspend fun enqueue(entityType: String, entityId: String, action: String, data: String?) {
        // 接口签名缺 ledgerId（API §8），而 sync_changes 表要求 ledgerId 列（DATABASE §4.6）；
        // 本期明确「只建表不写数据」，以显式抛错落桩，避免静默丢数据。
        throw UnsupportedOperationException(
            "SyncChangeDataSource.enqueue 为 Phase 3 预留扩展点，本期不写入（API §8）；" +
                "签名缺 ledgerId 入参而表结构要求该列，待 Phase 3 同步实现补齐调用方后落地。",
        )
    }

    override suspend fun pendingChanges(ledgerId: String): List<SyncChange> =
        syncChangeDao().pendingChanges(ledgerId).map(SyncChangeMapper::toDomain)

    override suspend fun markSynced(ids: List<String>) {
        syncChangeDao().markSynced(ids)
    }

    // ===== 内部工具 =====

    private suspend fun upsertBudget(ledgerId: String, month: YearMonth, categoryId: String?, amount: Long) {
        val now = Instant.now().toString()
        budgetDao().upsert(
            BudgetEntity(
                id = UUID.randomUUID().toString(),
                ledgerId = ledgerId,
                month = month.toString(),
                categoryId = categoryId,
                amount = amount,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /** 从 [DatabaseProvider.database] 取当前已打开库的 [TransactionDao]；未解锁即硬失败。 */
    private fun transactionDao(): TransactionDao = gate.database?.transactionDao()
        ?: error("DatabaseGate 未解锁/未打开，无法提供 TransactionDao")

    private fun debtDao(): DebtDao = gate.database?.debtDao()
        ?: error("DatabaseGate 未解锁/未打开，无法提供 DebtDao")

    private fun categoryDao(): CategoryDao = gate.database?.categoryDao()
        ?: error("DatabaseGate 未解锁/未打开，无法提供 CategoryDao")

    private fun budgetDao(): BudgetDao = gate.database?.budgetDao()
        ?: error("DatabaseGate 未解锁/未打开，无法提供 BudgetDao")

    private fun syncChangeDao(): SyncChangeDao = gate.database?.syncChangeDao()
        ?: error("DatabaseGate 未解锁/未打开，无法提供 SyncChangeDao")

    private fun userCategoryRuleDao(): UserCategoryRuleDao = gate.database?.userCategoryRuleDao()
        ?: error("DatabaseGate 未解锁/未打开，无法提供 UserCategoryRuleDao")

    /** 当前打开库的可写连接（Room 官方逃生舱，供无 DAO 的等价 SQL 操作使用）。 */
    private fun writableDatabase(): SupportSQLiteDatabase = gate.database?.openHelper?.writableDatabase
        ?: error("DatabaseGate 未解锁/未打开，无法访问数据库")

    /** DAO 查询 DTO [PeriodTrendRow] → domain [PeriodTrend]（字段透传，金额 Long 分不变）。 */
    private fun PeriodTrendRow.toDomain(): PeriodTrend =
        PeriodTrend(bucket = bucket, expenseFen = expenseFen, incomeFen = incomeFen)

    /** DAO 查询 DTO [CategoryAggRow] → domain [CategoryAgg]（字段透传，金额 Long 分不变）。 */
    private fun CategoryAggRow.toDomain(): CategoryAgg =
        CategoryAgg(categoryId = categoryId, totalFen = totalFen)

    /** Cursor → [TransactionEntity]（`getById` 用，列名与 Room 默认列名一致，DATABASE §4.1）。 */
    private fun Cursor.toTransactionEntity(): TransactionEntity {
        val descriptionIndex = getColumnIndexOrThrow("description")
        val externalIdIndex = getColumnIndexOrThrow("externalId")
        val counterpartyIndex = getColumnIndexOrThrow("counterparty")
        val paymentMethodIndex = getColumnIndexOrThrow("paymentMethod")
        return TransactionEntity(
            id = getString(getColumnIndexOrThrow("id")),
            ledgerId = getString(getColumnIndexOrThrow("ledgerId")),
            type = getString(getColumnIndexOrThrow("type")),
            amount = getLong(getColumnIndexOrThrow("amount")),
            categoryId = getString(getColumnIndexOrThrow("categoryId")),
            description = if (isNull(descriptionIndex)) null else getString(descriptionIndex),
            date = getString(getColumnIndexOrThrow("date")),
            createdAt = getString(getColumnIndexOrThrow("createdAt")),
            updatedAt = getString(getColumnIndexOrThrow("updatedAt")),
            syncStatus = getString(getColumnIndexOrThrow("syncStatus")),
            externalId = if (isNull(externalIdIndex)) null else getString(externalIdIndex),
            counterparty = if (isNull(counterpartyIndex)) null else getString(counterpartyIndex),
            paymentMethod = if (isNull(paymentMethodIndex)) null else getString(paymentMethodIndex),
            source = getString(getColumnIndexOrThrow("source")),
        )
    }
}
