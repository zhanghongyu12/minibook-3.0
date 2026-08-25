package com.minibook.domain.usecase.ledger

import com.minibook.data.datasource.TransactionRunner
import com.minibook.data.local.datastore.LedgerDataStore
import com.minibook.domain.model.Ledger
import com.minibook.domain.repository.LedgerRepository
import com.minibook.domain.usecase.category.CloneBuiltInCategoriesUseCase
import com.minibook.util.TimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * 新建账本（TASK-012，ARCHITECTURE §8.4 新账本创建流程）。
 *
 * 流程：插入 Ledger 行 → **同一 DB 事务**（[TransactionRunner.runInTransaction]）内
 * 克隆 24 内置分类（[CloneBuiltInCategoriesUseCase]，内部幂等 `countByLedger==0` 守卫）
 * → 切换为当前账本（[LedgerDataStore.setCurrentLedgerId]）→ 返回新账本 id。
 *
 * **事务原子性（TASK-012 验收）**：`insert` + `clone` 整体包进事务；克隆任一失败 →
 * 账本插入一并回滚（DEC-001 不感知 Room/SQL，事务细节经 [TransactionRunner] 原语
 * 隔离在数据层）。`setCurrentLedgerId` 亦置于事务块内：DataStore 写异常 → 事务回滚，
 * 避免「账本已建但未选中」的『无当前账本』态（验收「新建后自动切为当前」）。
 *
 * 与 TASK-011 BootstrapUseCase 同口径：`insert` 返回值即落库 id
 * （LedgerDataSourceAdapter 重生成 UUID），下游一律用该 id（克隆分类 + 写当前账本）。
 */
class CreateLedgerUseCase @Inject constructor(
    private val ledgerRepository: LedgerRepository,
    private val cloneBuiltInCategories: CloneBuiltInCategoriesUseCase,
    private val ledgerDataStore: LedgerDataStore,
    private val transactionRunner: TransactionRunner,
) {

    /**
     * 新建账本并自动切换为当前账本。
     *
     * @param name 账本名（非空校验归 UI / TASK-010 Validators，本类不承载）。
     * @param icon 账本图标 emoji。
     * @param color 账本主题色（如 `#5D6D7E`）。
     * @return 新账本落库 id（数据层重生成，非入参占位）。
     */
    suspend operator fun invoke(name: String, icon: String, color: String): String =
        transactionRunner.runInTransaction {
            val now = TimeFormatter.nowIso()
            // position 取当前账本数（0 基），新账本恒排末尾（observeAll 按 position 升序）。
            val nextPosition = ledgerRepository.observeAll().first().size
            val ledgerId = ledgerRepository.insert(
                Ledger(
                    id = UUID.randomUUID().toString(), // 占位；数据层 insert 重生成 UUID
                    name = name,
                    icon = icon,
                    color = color,
                    isDefault = false,
                    position = nextPosition,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            cloneBuiltInCategories(ledgerId)
            ledgerDataStore.setCurrentLedgerId(ledgerId)
            ledgerId
        }
}
