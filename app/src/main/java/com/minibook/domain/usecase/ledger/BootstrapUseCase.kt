package com.minibook.domain.usecase.ledger

import com.minibook.data.datasource.TransactionRunner
import com.minibook.data.local.datastore.LedgerDataStore
import com.minibook.domain.model.Ledger
import com.minibook.domain.repository.LedgerRepository
import com.minibook.domain.usecase.category.CloneBuiltInCategoriesUseCase
import com.minibook.util.TimeFormatter
import java.util.UUID
import javax.inject.Inject

/**
 * 首次打开（DB 首次连接成功）幂等引导（TASK-011），对齐 ARCHITECTURE §8.4。
 *
 * 流程：`ledgers` 空（[LedgerRepository.getDefault] == null）→ 插入默认账本
 * （`isDefault=true`，name="默认账本"，内置 icon/color，position=0）→ 为该账本克隆
 * 24 内置分类（[CloneBuiltInCategoriesUseCase]）→ `LedgerDataStore.setCurrentLedgerId`
 * 写入默认账本 id。
 *
 * **事务原子性（TASK-013 评审 W3）**：插入默认账本 + 克隆分类 + 写当前账本三步整体包进
 * [TransactionRunner.runInTransaction]（与 [CreateLedgerUseCase] 同口径）——任一失败 →
 * 账本插入一并回滚，杜绝「账本已建但分类未克隆/当前账本未写」的半初始化态被幂等守卫
 * （默认账本已存在）永久跳过。
 *
 * **幂等守卫**：默认账本已存在则直接返回，不重复插入（重装/恢复 KeyStore 后不重复刷）。
 * 分类克隆的幂等由 [CloneBuiltInCategoriesUseCase] 内部 `countByLedger==0` 守卫兜底。
 */
class BootstrapUseCase @Inject constructor(
    private val ledgerRepository: LedgerRepository,
    private val cloneBuiltInCategories: CloneBuiltInCategoriesUseCase,
    private val ledgerDataStore: LedgerDataStore,
    private val transactionRunner: TransactionRunner,
) {

    /**
     * 执行首启引导（幂等）；重复调用时默认账本已存在则直接返回。
     */
    suspend operator fun invoke() {
        // 幂等守卫：默认账本已存在 → 直接返回（ARCHITECTURE §8.4 步骤 1「空则插入」）。
        if (ledgerRepository.getDefault() != null) return

        transactionRunner.runInTransaction {
            // 插入默认账本。数据层 insert 会重新生成 UUID 与 createdAt/updatedAt
            // （LedgerDataSourceAdapter），故此处 id/时间戳仅作完整对象占位；
            // 下游一律使用返回的落库 id（克隆分类 + 写当前账本）。
            val now = TimeFormatter.nowIso()
            val defaultLedgerId = ledgerRepository.insert(
                Ledger(
                    id = UUID.randomUUID().toString(),
                    name = DEFAULT_NAME,
                    icon = DEFAULT_ICON,
                    color = DEFAULT_COLOR,
                    isDefault = true,
                    position = DEFAULT_POSITION,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            // 为该默认账本克隆 24 内置分类（16 支出 + 8 收入，内部幂等守卫 countByLedger==0）。
            cloneBuiltInCategories(defaultLedgerId)

            // 写入当前账本 id（ARCHITECTURE §8.4 步骤 3；上游幂等守卫保证此时尚无默认账本）。
            ledgerDataStore.setCurrentLedgerId(defaultLedgerId)
        }
    }

    /** 默认账本内置值（ARCHITECTURE §8.4 指定「内置值」，具体取值本类锁定）。 */
    private companion object {
        const val DEFAULT_NAME: String = "默认账本"

        /**
         * 2026-10-13 起为 [com.minibook.ui.components.LedgerIcons.BOOK] 的 key（原为 emoji `📒`）。
         * 这里写字面量以免 domain 层依赖 ui 层；老数据/老备份里的 emoji 由 `LedgerIcons.LEGACY_EMOJI` 兜底。
         */
        const val DEFAULT_ICON: String = "book"
        const val DEFAULT_COLOR: String = "#5D6D7E"
        const val DEFAULT_POSITION: Int = 0
    }
}
