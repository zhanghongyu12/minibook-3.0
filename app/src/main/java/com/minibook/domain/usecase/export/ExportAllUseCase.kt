package com.minibook.domain.usecase.export

import android.content.Context
import com.minibook.domain.model.DebtFilter
import com.minibook.domain.model.TransactionFilter
import com.minibook.domain.repository.CategoryRepository
import com.minibook.domain.repository.DebtRepository
import com.minibook.domain.repository.LedgerRepository
import com.minibook.domain.repository.TransactionRepository
import com.minibook.util.BackupJsonMapper
import com.minibook.util.BackupV1
import com.minibook.util.BackupJsonMapper.toDto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 全量导出（TASK-016，ARCHITECTURE §10.1 / API §11 / DEC-004 / DEC-005 / DEC-006）。
 *
 * 全量读 5 表（ledgers/categories/transactions/debts/budgets；budgets 本期恒空）→
 * version 1 明文 JSON（`scope="all"`，**不含 syncChanges**，API §11.1）→ 写私有文件目录
 * `minibook_backup_yyyyMMdd_HHmmss.json`（明文，DEC-005 人工可 inspect；`filesDir` 属
 * `allowBackup=false` 排除范围，DEC-006 换机不走系统备份、走「导出→导入」路径）。
 *
 * **跨账本全量读的组合方式**：Repository 接口对 categories/transactions/debts 仅按账本读
 * （`observeByLedger`，observeAll 仅 Ledger 有）；本 UseCase 以 `observeAll()` 取全部账本、
 * 逐账本 `observeByLedger`（null/全空 filter = 不过滤）组合出全库快照，字段与 API §11 逐项
 * 一致。预算表 Phase 2 预留无全量读接口，本期恒空数组（API §11.1 注）。
 *
 * **调度（ARCHITECTURE §5.2）**：导入导出文件 I/O + JSON 序列化显式切
 * `withContext(Dispatchers.IO)`；`@IoDispatcher` 属 TASK-018 DispatcherModule 收口后提供，
 * 本期以 `Dispatchers.IO` 直用并在 TASK-018 替换为 `@IoDispatcher` 限定符注入。
 */
class ExportAllUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ledgerRepository: LedgerRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val debtRepository: DebtRepository,
) {

    /**
     * 执行全量导出。
     *
     * 产出文件路径：`{context.filesDir}/minibook_backup_yyyyMMdd_HHmmss.json`
     * （私有 file 目录；DEC-006 已排除系统备份，换机迁移由 UI 经 SAF 分享/另存）。
     *
     * @return 已写入的备份 [File]（调用方据此展示路径 / 分享）。
     */
    suspend operator fun invoke(): File = withContext(Dispatchers.IO) {
        val backup = buildBackup()
        val json = BackupJsonMapper.toJson(backup)
        val fileName = FILENAME_PREFIX + LocalDateTime.now().format(FILE_TIMESTAMP) + FILE_EXT
        File(context.filesDir, fileName).apply { writeText(json, Charsets.UTF_8) }
    }

    /** 全量读 5 表并组装 version 1 BackupV1（budgets 本期恒空）。 */
    private suspend fun buildBackup(): BackupV1 {
        val ledgers = ledgerRepository.observeAll().first()
        // 逐账本读全部业务行：null type / 全空 filter = 不过滤（API §3/§4、DATABASE §6）。
        val categories = ledgers.flatMap { categoryRepository.observeByLedger(it.id, null).first() }
        val transactions = ledgers.flatMap { transactionRepository.observeByLedger(it.id, TransactionFilter()).first() }
        val debts = ledgers.flatMap { debtRepository.observeByLedger(it.id, DebtFilter()).first() }
        return BackupV1(
            version = 1,
            scope = "all",
            ledgers = ledgers.map { it.toDto() },
            categories = categories.map { it.toDto() },
            transactions = transactions.map { it.toDto() },
            debts = debts.map { it.toDto() },
            budgets = emptyList(), // 本期恒空（API §11.1；Phase 2 落库后导出）
        )
    }

    private companion object {
        const val FILENAME_PREFIX: String = "minibook_backup_"
        const val FILE_EXT: String = ".json"
        val FILE_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }
}
