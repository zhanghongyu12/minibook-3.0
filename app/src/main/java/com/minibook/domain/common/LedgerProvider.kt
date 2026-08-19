package com.minibook.domain.common

import com.minibook.data.local.datastore.LedgerDataStore
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * 当前账本轻量门面（ARCHITECTURE §8.2 / §8.5），UseCase 获取当前账本 ID 的唯一入口。
 *
 * 背景：进程级「当前账本」单例状态存 Preferences DataStore（[LedgerDataStore]，
 * ARCHITECTURE §5.3 / §8.2）。本类收敛读取逻辑，UseCase 一律经 [currentLedgerId] 取，
 * **禁止硬编码 ledgerId 或直接感知 DataStore**（§8.5）。
 *
 * 实现说明（TASK-012）：任务权限仅允许新增本 5 文件、不得改 di 模块，故实现为
 * **具体类**而非接口——`@Inject constructor` 由 Hilt 隐式提供（与 TASK-011
 * BootstrapUseCase 直注 [LedgerDataStore] 同一口径）；未来拆模块时可接口化
 * （ARCHITECTURE §8.2「轻接口」）。
 *
 * 首启由 TASK-011 BootstrapUseCase 写入默认账本 ID；未写入（空串）时抛
 * [LedgerNotFound]，UI 引导回账本管理（§8.5）。
 */
class LedgerProvider @Inject constructor(
    private val ledgerDataStore: LedgerDataStore,
) {

    /**
     * 当前账本 ID；未初始化（空串，BootstrapUseCase 尚未写入）→ 抛 [LedgerNotFound]。
     */
    suspend fun currentLedgerId(): String =
        ledgerDataStore.currentLedgerId.first().takeIf { it.isNotBlank() }
            ?: throw LedgerNotFound()
}

/**
 * 账本不存在或当前账本未初始化（ARCHITECTURE §8.5）。
 *
 * 触发点：
 * - [LedgerProvider.currentLedgerId]：当前账本为空（首启引导前）；
 * - SwitchLedgerUseCase / DeleteLedgerUseCase：UI 传入 id 不存在。
 */
class LedgerNotFound(message: String = "账本不存在或当前账本未初始化") : IllegalStateException(message)
