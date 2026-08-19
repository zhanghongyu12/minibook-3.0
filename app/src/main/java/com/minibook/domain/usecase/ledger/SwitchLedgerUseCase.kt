package com.minibook.domain.usecase.ledger

import com.minibook.data.local.datastore.LedgerDataStore
import com.minibook.domain.common.LedgerNotFound
import com.minibook.domain.repository.LedgerRepository
import javax.inject.Inject

/**
 * 切换当前账本（TASK-012，ARCHITECTURE §8.3）。
 *
 * **纯状态变更**：`LedgerDataStore.setCurrentLedgerId` 写 Preferences DataStore，
 * **无任何数据搬移**（§8.3）；写后全部 `flatMapLatest` Flow 自动重订阅到新账本
 * （首页/收支/债务/分类/预算同步切换，统计口径不混账）。
 *
 * 目标账本存在性校验（§8.5）：UI 传入 id 不存在 → 抛 [LedgerNotFound]，
 * UI 引导回账本管理；通过 [LedgerProvider] 取当前账本的场景见
 * `domain/common/LedgerProvider`（本类只写不读）。
 */
class SwitchLedgerUseCase @Inject constructor(
    private val ledgerRepository: LedgerRepository,
    private val ledgerDataStore: LedgerDataStore,
) {

    /**
     * 切换到 [ledgerId]。
     *
     * @throws LedgerNotFound 目标账本不存在。
     */
    suspend operator fun invoke(ledgerId: String) {
        val exists = ledgerRepository.getById(ledgerId) != null
        if (!exists) throw LedgerNotFound("账本不存在：$ledgerId")
        ledgerDataStore.setCurrentLedgerId(ledgerId)
    }
}
