package com.minibook.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.data.local.db.dao.SyncConflictDao
import com.minibook.data.remote.SyncApiException
import com.minibook.data.remote.SyncAuthStore
import com.minibook.domain.usecase.sync.CloudStatusLine
import com.minibook.domain.usecase.sync.SyncEntityType
import com.minibook.domain.usecase.sync.SyncMode
import com.minibook.domain.usecase.sync.SyncPlan
import com.minibook.domain.usecase.sync.SyncPreview
import com.minibook.domain.usecase.sync.SyncRunResult
import com.minibook.domain.usecase.sync.SyncUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 云同步页面 ViewModel（F-15 / TASK-067；2026-10-12 起**只管同步**）。
 *
 * 分工：账号相关（登录 / 注册 / 改资料 / 改口令 / 退出 / 注销）全部在
 * [AccountViewModel] 与「账号」页；本页只留同步本身（自动同步开关 + 手动三模式 + 状态 + 冲突）。
 *
 * 关键约束（与设计一致，不是实现细节）：
 * - **预览 == 执行**：`preview()` 拿到的 [SyncPlan] 原样交给 `execute()`，中间不给任何"重新算一遍"的机会，
 *   否则用户确认的「删 3 条」和真正执行的可能是两回事；
 * - 预览为**空计划**时不弹确认框，直接提示「已经一致」（不烦人）；
 * - 执行结果**原样展示四个计数**，失败时明说「游标未推进」，不粉饰。
 */
@HiltViewModel
class SettingsCloudViewModel @Inject constructor(
    private val store: SyncAuthStore,
    private val sync: SyncUseCase,
    private val conflictDao: SyncConflictDao,
) : ViewModel() {

    data class UiState(
        val loggedIn: Boolean = false,
        val accountName: String = "",
        val statusText: String = "",
        val lastSyncAt: String = "",
        val conflictCount: Int = 0,
        /** 自动同步开关（DEC-038，默认开；只在已登录时有效）。 */
        val autoSync: Boolean = true,
        val busy: Boolean = false,
        val busyText: String = "处理中…",
        val message: String? = null,
        val prompt: PreviewPrompt? = null,
        val result: SyncRunResult? = null,
        val conflicts: List<ConflictUi> = emptyList(),
        val showConflicts: Boolean = false,
    ) {
        /** 未登录或忙碌时同步按钮全部禁用（防重复点击与半途状态）。 */
        val syncEnabled: Boolean get() = loggedIn && !busy
    }

    /** 待确认的预览（含 plan 本体，确认后原样执行）。 */
    data class PreviewPrompt(
        val mode: SyncMode,
        val plan: SyncPlan,
        val nextCursor: Long,
        val title: String,
        val message: String,
        val danger: Boolean,
        val details: List<String>,
    )

    /** 冲突留档的一行（含两份副本，便于用户手工恢复）。 */
    data class ConflictUi(
        val head: String,
        val detectedAt: String,
        val localJson: String,
        val remoteJson: String,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch { refreshAccount() }
    }

    // ---------- 同步 ----------
    fun setAutoSync(enabled: Boolean) = viewModelScope.launch {
        store.setAutoSyncEnabled(enabled)
        _ui.update { it.copy(autoSync = enabled) }
        toast(
            if (enabled) {
                "已开启自动同步：打开 / 回到 App 时会静默同步一次"
            } else {
                "已关闭自动同步：只走手动同步"
            },
        )
    }

    // ---------- 同步 ----------

    /** dry-run：算出计划并弹确认（空计划直接提示，不打扰）。 */
    fun preview(mode: SyncMode) = viewModelScope.launch {
        val state = _ui.value
        if (!state.syncEnabled) {
            return@launch toast(if (!state.loggedIn) "请先登录云端账号" else "正在忙，请稍候")
        }
        _ui.update { it.copy(busy = true, busyText = "正在预演${mode.label}…") }
        val outcome = sync.preview(mode)
        _ui.update { it.copy(busy = false) }
        when (outcome) {
            is SyncPreview.Failed -> toast(outcome.message)
            is SyncPreview.Ready -> {
                if (outcome.plan.isEmpty) {
                    toast(SyncPresenter.emptyPlanMessage(mode))
                } else {
                    val details = SyncPresenter.previewDetails(mode, outcome.plan)
                    _ui.update {
                        it.copy(
                            prompt = PreviewPrompt(
                                mode = mode,
                                plan = outcome.plan,
                                nextCursor = outcome.nextCursor,
                                title = SyncPresenter.confirmTitle(mode),
                                // 明细直接拼进确认文案：确认框是用户唯一能看清"要动什么"的地方。
                                message = SyncPresenter.confirmMessage(mode, outcome.plan) +
                                    "\n\n" + details.joinToString("\n"),
                                danger = SyncPresenter.needsDangerConfirm(outcome.plan),
                                details = details,
                            ),
                        )
                    }
                }
            }
        }
    }

    /** 执行**预览时那一份**计划（不重算）。 */
    fun confirmPreview() = viewModelScope.launch {
        val prompt = _ui.value.prompt ?: return@launch
        _ui.update { it.copy(prompt = null, busy = true, busyText = "正在${prompt.mode.label}…") }
        val result = sync.execute(prompt.plan, prompt.nextCursor)
        _ui.update { it.copy(busy = false, result = result) }
        refreshAccount()
        if (result.ok) {
            _ui.update { it.copy(message = SyncPresenter.resultText(result)) }
        }
    }

    fun cancelPreview() = _ui.update { it.copy(prompt = null) }

    fun dismissResult() = _ui.update { it.copy(result = null) }

    // ---------- 冲突留档 ----------

    fun openConflicts() = viewModelScope.launch {
        val rows = conflictDao.recent(CONFLICT_PAGE).map { entity ->
            ConflictUi(
                head = "${typeLabel(entity.entityType)} ${SyncPresenter.shortId(entity.entityId)} · " +
                    SyncPresenter.outcomeText(entity.resolution.orEmpty()),
                detectedAt = SyncPresenter.humanTime(entity.detectedAt),
                localJson = entity.localJson,
                remoteJson = entity.remoteJson,
            )
        }
        _ui.update { it.copy(conflicts = rows, showConflicts = true) }
    }

    fun closeConflicts() = _ui.update { it.copy(showConflicts = false) }

    fun clearMessage() = _ui.update { it.copy(message = null) }

    // ---------- 内部 ----------

    private suspend fun refreshAccount() {
        val account = store.current()
        val line = CloudStatusLine(
            loggedIn = account.isLoggedIn,
            baseUrl = account.baseUrl,
            username = account.username,
            lastSyncAt = account.lastSyncAt,
            lastSyncMode = account.lastSyncMode,
            conflictCount = conflictDao.unresolvedCount(),
            serverCursor = account.serverCursor,
        )
        _ui.update {
            it.copy(
                loggedIn = account.isLoggedIn,
                accountName = account.username,
                statusText = SyncPresenter.statusText(line),
                lastSyncAt = account.lastSyncAt,
                conflictCount = line.conflictCount,
                autoSync = store.autoSyncEnabled(),
            )
        }
    }

    private fun toast(message: String) = _ui.update { it.copy(message = message) }

    /** 统一忙碌态 + 统一异常文案（网络/鉴权/其他三类分开说）。 */
    private suspend fun runBusy(text: String, block: suspend () -> Unit) {
        _ui.update { it.copy(busy = true, busyText = text) }
        try {
            block()
            refreshAccount()
        } catch (e: SyncApiException) {
            toast(e.userMessage())
        } catch (e: Exception) {
            toast(e.message ?: "操作失败：${e::class.simpleName}")
        } finally {
            _ui.update { it.copy(busy = false) }
        }
    }

    private fun typeLabel(wire: String): String =
        SyncEntityType.fromWire(wire)?.label ?: wire

    private companion object {
        const val CONFLICT_PAGE = 20
    }
}
