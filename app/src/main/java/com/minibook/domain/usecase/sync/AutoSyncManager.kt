package com.minibook.domain.usecase.sync

import com.minibook.data.remote.SyncAuthStore
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex

/**
 * 「自动云同步」编排（DEC-038）：**打开 / 回到 App 时**静默跑一次双向同步。
 *
 * 为什么不做后台定时：用户要的是「不用手动点」，不是「关着 App 也在跑」；WorkManager 周期任务
 * 最短 15 分钟且要新增依赖与常驻 worker，故本轮按最小可用面落地（`docs/14_cloud_sync.md §4.8`）。
 *
 * **安全边界（「本地不能丢」优先于「自动到底」）**：
 * - 只在「开关开 + 已登录 + 距上次同步超过 [MIN_INTERVAL_MS]」时跑；
 * - 计划若会**删本地数据或存在冲突**（[SyncPlan.needsConfirm]，与手动同步的二次确认同一判定）
 *   ⇒ **绝不自动执行**，留给用户手动确认。静默删掉用户的账目，比「少同步一次」糟糕得多；
 * - 失败一律**不打扰**（不弹窗、不提示），游标语义与手动同步完全一致：**失败不推进**，下次重跑即幂等续上。
 *
 * 并发：`tryLock` 保证同一时刻只有一轮；与手动同步的互斥由 `busy` 态与「预览==执行」契约兜底。
 */
@Singleton
class AutoSyncManager @Inject constructor(
    private val store: SyncAuthStore,
    private val sync: SyncUseCase,
) {

    private val running = Mutex()

    /**
     * 前台可见（`MainActivity.onStart`）时调用。
     *
     * @return 是否真的执行了一轮（false = 开关关/未登录/未到间隔/空计划/计划危险/已有轮次在跑）。
     */
    suspend fun runOnForeground(): Boolean {
        if (!running.tryLock()) return false
        try {
            if (!store.autoSyncEnabled()) return false
            val account = store.current()
            if (!account.isLoggedIn) return false
            if (!isDue(account.lastSyncAt, System.currentTimeMillis())) return false

            val preview = sync.preview(SyncMode.BOTH)
            if (preview !is SyncPreview.Ready) return false
            if (preview.plan.isEmpty) return false
            // 危险计划（会删本地/有冲突）不自动跑：交给用户在手动同步里看清数字后确认。
            if (preview.plan.needsConfirm) return false

            sync.execute(preview.plan, preview.nextCursor)
            return true
        } finally {
            running.unlock()
        }
    }

    companion object {
        /** 两次自动同步的最小间隔：5 分钟（同一次前台会话内不反复跑网络）。 */
        const val MIN_INTERVAL_MS: Long = 5 * 60 * 1000L

        /** 纯函数（可单测）：从未同步过、或距上次同步已超过 [minIntervalMs] ⇒ 该跑。 */
        fun isDue(
            lastSyncAtIso: String,
            nowMs: Long,
            minIntervalMs: Long = MIN_INTERVAL_MS,
        ): Boolean {
            if (lastSyncAtIso.isEmpty()) return true
            val last = runCatching { Instant.parse(lastSyncAtIso).toEpochMilli() }.getOrNull()
                ?: return true // 时间戳解析不了 ⇒ 当作该跑（宁可多跑一次，不可长期不跑）
            return nowMs - last >= minIntervalMs
        }
    }
}
