package com.minibook.domain.usecase.export

import com.minibook.data.datasource.TransactionRunner
import com.minibook.domain.model.Budget
import com.minibook.domain.model.Category
import com.minibook.domain.model.CategoryType
import com.minibook.domain.model.Debt
import com.minibook.domain.model.DebtDirection
import com.minibook.domain.model.DebtKind
import com.minibook.domain.model.Ledger
import com.minibook.domain.model.Transaction
import com.minibook.domain.model.TransactionSource
import com.minibook.domain.model.TransactionType
import com.minibook.domain.repository.BudgetRepository
import com.minibook.domain.repository.CategoryRepository
import com.minibook.domain.repository.DebtRepository
import com.minibook.domain.repository.LedgerRepository
import com.minibook.domain.repository.TransactionRepository
import com.minibook.util.BackupJsonMapper
import com.minibook.util.BackupV1
import com.minibook.util.TimeFormatter
import com.minibook.util.Validators
import com.minibook.util.BackupJsonMapper.toDomain
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 全量导入（TASK-016，ARCHITECTURE §10.2 / API §11.3 / DEC-004 / DEC-005 / DEC-006）。
 *
 * 语义：**全量覆盖**——单事务内清空 4 数据表（transactions/debts/categories/budgets）、
 * 保留/重建 ledgers，再逐行写入导入内容；任一失败整体回滚（API §11.3 / ARCHITECTURE §10.2，
 * Room `withTransaction` 经 [TransactionRunner] 原语，DEC-001 不感知 Room/SQL）。
 *
 * 流程（严格校验零落库）：
 * 1. 解析 [BackupV1]（[BackupJsonMapper.fromJson]，类型/必填失败 → Failure，零落库）；
 * 2. 事务外全量校验（[validate]）：`version==1`；字段类型/必填/引用完整性
 *    （`transaction.categoryId ∈ categories` 且同账本、`*.ledgerId ∈ ledgers`、金额正整分、
 *    UUID 合法）→ 任一失败 Failure，**零落库**（API §11.3）；
 * 3. 前置转换 DTO → domain（[toDomain] 枚举/日期非法在此抛出 → Failure，零落库）；
 * 4. 单事务全量覆盖写入（[importFull] / [importLegacy]）。
 *
 * **legacy 分支（DEC-004 / D-3）**：缺 `ledgers` 字段（null）→ 导入到默认账本
 * （每行补默认账本 ledgerId，legacy 时间戳归一化为 `yyyy-MM-dd`）。
 *
 * **写入统一 `syncStatus='local'`**（API §11 尾部说明：条目不含 syncStatus，目标设备为新设备）。
 *
 * **调度（ARCHITECTURE §5.2）**：文件/JSON 处理显式 `withContext(Dispatchers.IO)`；
 * `@IoDispatcher` 属 TASK-018 收口后提供，本期以 `Dispatchers.IO` 直用并注记替换点。
 *
 * 已知约束：
 * 1. 数据层 insert 重生成 id/审计时间（TASK-008 已 commit）：非 legacy 导入后
 *    ledgers/transactions/debts 的 `id` 与 `createdAt/updatedAt` 由数据层重新生成
 *    （LedgerDataSourceAdapter / RoomDataSourceImpl）；categories 经 `upsertList`（REPLACE）
 *    保留 id。业务字段（金额/日期/分类/名称/对方等）与引用完整性完整保留。
 * 2. 非 legacy 重建 ledgers 后，「当前账本」（LedgerDataStore，Preferences）可能指向已被
 *    重建的旧账本 id（悬空）；本 UseCase 不触碰 DataStore（账本切换为纯状态变更，
 *    ARCHITECTURE §8.3），刷新由 UI 层（TASK-027 导入后强制刷新/重定位当前账本）配合。
 */
class ImportAllUseCase @Inject constructor(
    private val transactionRunner: TransactionRunner,
    private val ledgerRepository: LedgerRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val debtRepository: DebtRepository,
    private val budgetRepository: BudgetRepository,
) {

    /**
     * 执行全量导入。
     *
     * @param json 备份 JSON 明文内容（文件读取由 UI/调用方负责）。
     * @return [Success]（各表落库行数）/ [Failure]（reason，零落库）。
     */
    suspend operator fun invoke(json: String): ImportResult = withContext(Dispatchers.IO) {
        try {
            val backup = BackupJsonMapper.fromJson(json)
            validate(backup)?.let { return@withContext ImportResult.Failure(it) }

            // 前置转换：枚举/日期非法在此抛出 → 零落库（严格校验第三步）。
            val ledgers = backup.ledgers?.map { it.toDomain() }
            val categories = backup.categories.map { it.toDomain() }
            val transactions = backup.transactions.map { it.toDomain() }
            val debts = backup.debts.map { it.toDomain() }
            val budgets = backup.budgets.map { it.toDomain() }

            if (ledgers == null) {
                importLegacy(categories, transactions, debts, budgets)
            } else {
                importFull(ledgers, categories, transactions, debts, budgets)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ImportResult.Failure(e.message ?: "导入失败：${e::class.simpleName}")
        }
    }

    // ===== 事务外严格校验（零落库） =====

    /**
     * 严格校验（API §11.3）：返回 null=通过，非 null=错误原因。
     *
     * 覆盖：`version==1`；ledgers 非空且含默认账本；各条目 UUID 合法；
     * 引用完整性成对校验（`*.ledgerId ∈ ledgers`、`transaction.categoryId ∈ categories`
     * 且同账本、预算分类同账本）；金额正整分（预算 ≥0）；必填非空（账本名/分类名/对方姓名）；
     * 枚举取值（type/kind/direction）与月份格式（yyyy-MM）。日期合法性由
     * [BackupJsonMapper.toDomain] 前置转换拦截。
     *
     * legacy（ledgers==null）：跳过 `ledgerId ∈ ledgers` 与「同账本」校验——全部重挂默认账本；
     * 仍校验分类归属（`transaction.categoryId ∈ categories`）。
     */
    private fun validate(backup: BackupV1): String? {
        if (backup.version != 1) return "不支持的备份版本：${backup.version}（仅支持 version=1）"

        // 各表主键全局唯一（引用完整性的前提：悬空/歧义引用零容忍）。
        if (backup.ledgers?.map { it.id }.hasDuplicates()) return "ledgers 存在重复 id"
        if (backup.categories.map { it.id }.hasDuplicates()) return "categories 存在重复 id"
        if (backup.transactions.map { it.id }.hasDuplicates()) return "transactions 存在重复 id"
        if (backup.debts.map { it.id }.hasDuplicates()) return "debts 存在重复 id"
        if (backup.budgets.map { it.id }.hasDuplicates()) return "budgets 存在重复 id"

        val isLegacy = backup.ledgers == null
        val ledgerIds = backup.ledgers?.map { it.id }?.toSet() ?: emptySet()

        if (!isLegacy) {
            val ledgers = backup.ledgers ?: return "ledgers 不能为空"
            if (ledgers.isEmpty()) return "ledgers 不能为空"
            for (l in ledgers) {
                checkUuid(l.id, "账本 id")?.let { return it }
                if (l.name.isBlank()) return "账本名不能为空：${l.id}"
            }
            // 领域不变量（Ledger 模型：至少一个默认账本；DeleteLedgerUseCase 同口径）。
            if (ledgers.none { it.isDefault }) return "备份缺少默认账本（isDefault=true）"
        }

        val categoryIds = backup.categories.map { it.id }.toSet()
        val categoryLedgerById = backup.categories.associate { it.id to it.ledgerId }

        for (c in backup.categories) {
            checkUuid(c.id, "分类 id")?.let { return it }
            if (!isLegacy) {
                checkLedgerRef(c.ledgerId, "分类 ${c.id}", ledgerIds)?.let { return it }
            }
            if (c.name.isBlank()) return "分类名不能为空：${c.id}"
            if (CategoryType.fromValue(c.type) == null) return "非法分类类型：${c.type}"
        }
        for (t in backup.transactions) {
            checkUuid(t.id, "交易 id")?.let { return it }
            if (!isLegacy) {
                checkLedgerRef(t.ledgerId, "交易 ${t.id}", ledgerIds)?.let { return it }
            }
            if (TransactionType.fromValue(t.type) == null) return "非法交易类型：${t.type}"
            // source 可空（旧备份缺省），但非空且非法时严格拒绝（对齐 type 校验口径，零落库）。
            if (t.source != null && TransactionSource.fromValue(t.source) == null) {
                return "非法交易来源：${t.source}"
            }
            if (!Validators.validateAmount(t.amount)) {
                return "交易金额非法（须正整分且≤${Validators.MAX_AMOUNT_FEN}）：${t.amount}"
            }
            val catLedgerId = categoryLedgerById[t.categoryId]
            if (catLedgerId == null) return "交易 ${t.id} 的分类不存在：${t.categoryId}"
            if (!isLegacy && catLedgerId != t.ledgerId) {
                return "交易 ${t.id} 与分类 ${t.categoryId} 不属于同一账本"
            }
        }
        for (d in backup.debts) {
            checkUuid(d.id, "债务 id")?.let { return it }
            if (!isLegacy) {
                checkLedgerRef(d.ledgerId, "债务 ${d.id}", ledgerIds)?.let { return it }
            }
            if (!Validators.validatePartyName(d.partyName)) return "对方姓名不能为空：${d.id}"
            if (!Validators.validateAmount(d.amount)) {
                return "债务金额非法（须正整分且≤${Validators.MAX_AMOUNT_FEN}）：${d.amount}"
            }
            if (DebtKind.fromValue(d.kind) == null) return "非法债务 kind：${d.kind}"
            if (DebtDirection.fromValue(d.direction) == null) return "非法债务 direction：${d.direction}"
        }
        for (b in backup.budgets) {
            checkUuid(b.id, "预算 id")?.let { return it }
            if (!isLegacy) {
                checkLedgerRef(b.ledgerId, "预算 ${b.id}", ledgerIds)?.let { return it }
            }
            if (b.amount < 0) return "预算金额不能为负：${b.amount}"
            if (!isValidYearMonth(b.month)) return "预算月份非法：${b.month}"
            val catLedgerId = b.categoryId?.let { categoryLedgerById[it] }
            if (b.categoryId != null && catLedgerId == null) return "预算 ${b.id} 的分类不存在：${b.categoryId}"
            if (b.categoryId != null && !isLegacy && catLedgerId != b.ledgerId) {
                return "预算 ${b.id} 与分类 ${b.categoryId} 不属于同一账本"
            }
        }
        return null
    }

    private fun checkUuid(id: String, label: String): String? = try {
        UUID.fromString(id)
        null
    } catch (e: IllegalArgumentException) {
        "$label 非法 UUID：$id"
    }

    private fun checkLedgerRef(ledgerId: String, label: String, ledgerIds: Set<String>): String? =
        if (ledgerId !in ledgerIds) "$label 的 ledgerId 不存在于 ledgers：$ledgerId" else null

    private fun List<String>?.hasDuplicates(): Boolean = this != null && distinct().size != size

    private fun isValidYearMonth(month: String): Boolean = try {
        YearMonth.parse(month)
        true
    } catch (e: Exception) {
        false
    }

    // ===== 非 legacy：单事务全量重建 =====

    /**
     * 非 legacy 全量覆盖（API §11.3）：清空 4 数据表 → 删除现有 ledgers → 重建导入 ledgers
     * （数据层 insert 重生成 id，记录 old→new 映射）→ 逐行写入 categories/transactions/debts/budgets。
     * 任一失败 [TransactionRunner] 整体回滚（原子性，ARCHITECTURE §10.2）。
     */
    private suspend fun importFull(
        ledgers: List<Ledger>,
        categories: List<Category>,
        transactions: List<Transaction>,
        debts: List<Debt>,
        budgets: List<Budget>,
    ): ImportResult = transactionRunner.runInTransaction {
        val existingLedgers = ledgerRepository.observeAll().first()
        existingLedgers.forEach { clearData(it.id) }
        existingLedgers.forEach { ledgerRepository.deleteById(it.id) }

        // 重建 ledgers：insert 返回重生成的落库 id，old id → new id 映射用于子表重挂。
        val ledgerIdMap = ledgers.associate { it.id to ledgerRepository.insert(it) }

        // 分类 upsertList（REPLACE，保留 id）；交易/债务 insert（重生成 id）。
        categoryRepository.upsertList(categories.map { it.copy(ledgerId = ledgerIdMap.getValue(it.ledgerId)) })
        transactions.forEach { transactionRepository.insert(it.copy(ledgerId = ledgerIdMap.getValue(it.ledgerId))) }
        debts.forEach { debtRepository.insert(it.copy(ledgerId = ledgerIdMap.getValue(it.ledgerId))) }
        writeBudgets(budgets) { ledgerIdMap.getValue(it) }

        ImportResult.Success(
            ledgers = ledgers.size,
            categories = categories.size,
            transactions = transactions.size,
            debts = debts.size,
            budgets = budgets.size,
        )
    }

    // ===== legacy：导入默认账本 + 时间归一化（DEC-004） =====

    /**
     * legacy（缺 ledgers）分支：清空 4 数据表 → 保留现有 ledgers，确保存在默认账本
     * （无则新建 `isDefault=true`）→ 全部行重挂默认账本 ledgerId 后逐行写入。
     * 日期归一化已由 [BackupJsonMapper.toDomain]（[BackupJsonMapper.normalizeDate]）完成。
     */
    private suspend fun importLegacy(
        categories: List<Category>,
        transactions: List<Transaction>,
        debts: List<Debt>,
        budgets: List<Budget>,
    ): ImportResult = transactionRunner.runInTransaction {
        val existingLedgers = ledgerRepository.observeAll().first()
        existingLedgers.forEach { clearData(it.id) }

        val existingDefault = ledgerRepository.getDefault()
        val defaultLedgerId = existingDefault?.id ?: ledgerRepository.insert(newDefaultLedger())
        val ledgerCount = if (existingDefault == null) existingLedgers.size + 1 else existingLedgers.size

        categoryRepository.upsertList(categories.map { it.copy(ledgerId = defaultLedgerId) })
        transactions.forEach { transactionRepository.insert(it.copy(ledgerId = defaultLedgerId)) }
        debts.forEach { debtRepository.insert(it.copy(ledgerId = defaultLedgerId)) }
        writeBudgets(budgets) { defaultLedgerId }

        ImportResult.Success(
            ledgers = ledgerCount,
            categories = categories.size,
            transactions = transactions.size,
            debts = debts.size,
            budgets = budgets.size,
        )
    }

    /** 缺省默认账本（legacy 且库中无默认账本时创建；取值与 BootstrapUseCase 内置默认一致）。 */
    private fun newDefaultLedger(): Ledger {
        val now = TimeFormatter.nowIso()
        return Ledger(
            id = UUID.randomUUID().toString(), // 占位；数据层 insert 重生成
            name = DEFAULT_NAME,
            icon = DEFAULT_ICON,
            color = DEFAULT_COLOR,
            isDefault = true,
            position = DEFAULT_POSITION,
            createdAt = now,
            updatedAt = now,
        )
    }

    /** 清空某账本全部业务数据（transactions/debts/categories/budgets，四接口等价 DELETE；
     *  经 RoomDataSourceImpl 共享 `deleteByLedger` 同时清空 user_category_rules，F-12 全量替换语义）。 */
    private suspend fun clearData(ledgerId: String) {
        transactionRepository.deleteByLedger(ledgerId)
        debtRepository.deleteByLedger(ledgerId)
        categoryRepository.deleteByLedger(ledgerId)
        budgetRepository.deleteByLedger(ledgerId)
    }

    /** 逐行写入预算：categoryId==null 走月度总预算「先删后插」（DEC-008），否则分类预算。 */
    private suspend fun writeBudgets(budgets: List<Budget>, ledgerIdResolver: (String) -> String) {
        budgets.forEach { budget ->
            val ledgerId = ledgerIdResolver(budget.ledgerId)
            val month = YearMonth.parse(budget.month)
            if (budget.categoryId == null) {
                budgetRepository.setTotalBudget(ledgerId, month, budget.amount)
            } else {
                budgetRepository.setCategoryBudget(ledgerId, month, budget.categoryId, budget.amount)
            }
        }
    }

    private companion object {
        const val DEFAULT_NAME: String = "默认账本"
        const val DEFAULT_ICON: String = "📒"
        const val DEFAULT_COLOR: String = "#5D6D7E"
        const val DEFAULT_POSITION: Int = 0
    }
}

/** 导入结果（TASK-016）：[Success] 携带各表落库行数；[Failure] 携带原因（零落库）。 */
sealed interface ImportResult {
    /** 导入成功；各字段为落库行数（ledgers 为 legacy 下保留+新建的现有账本数）。 */
    data class Success(
        val ledgers: Int,
        val categories: Int,
        val transactions: Int,
        val debts: Int,
        val budgets: Int,
    ) : ImportResult

    /** 导入失败；[reason] 为可读原因，且**零落库**（API §11.3 严格校验前置 / 事务回滚）。 */
    data class Failure(val reason: String) : ImportResult
}
