package com.minibook.ui.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.data.local.db.dao.SyncConflictDao
import com.minibook.data.remote.SyncApiException
import com.minibook.data.remote.SyncAuthStore
import com.minibook.data.remote.SyncLoginRequest
import com.minibook.data.remote.SyncLogoutRequest
import com.minibook.data.remote.SyncRegisterRequest
import com.minibook.data.remote.SyncRemote
import com.minibook.data.remote.SyncUrlValidator
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
 * 云同步页面 ViewModel（F-15 / TASK-067）。
 *
 * 关键约束（与设计一致，不是实现细节）：
 * - **预览 == 执行**：`preview()` 拿到的 [SyncPlan] 原样交给 `execute()`，中间不给任何"重新算一遍"的机会，
 *   否则用户确认的「删 3 条」和真正执行的可能是两回事；
 * - 预览为**空计划**时不弹确认框，直接提示「已经一致」（不烦人）；
 * - 执行结果**原样展示四个计数**，失败时明说「游标未推进」，不粉饰；
 * - 口令只活在内存里：登录/注册成功后立即清空，绝不落盘（落盘的只有加密令牌）。
 */
@HiltViewModel
class SettingsCloudViewModel @Inject constructor(
    private val store: SyncAuthStore,
    private val remote: SyncRemote,
    private val sync: SyncUseCase,
    private val conflictDao: SyncConflictDao,
    @ApplicationContext context: Context,
) : ViewModel() {

    /** 调试版允许 http（DEC-034：内网自测）；正式版强制 https。与构建类型无关地按真实 flag 判定。 */
    private val debuggable: Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    data class UiState(
        val baseUrl: String = "",
        val username: String = "",
        val password: String = "",
        val loggedIn: Boolean = false,
        val accountName: String = "",
        val statusText: String = "",
        val lastSyncAt: String = "",
        val conflictCount: Int = 0,
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

    // ---------- 账号 ----------

    fun onBaseUrlChange(value: String) = _ui.update { it.copy(baseUrl = value) }

    fun onUsernameChange(value: String) = _ui.update { it.copy(username = value) }

    fun onPasswordChange(value: String) = _ui.update { it.copy(password = value) }

    /**
     * 注册并**顺带登录**（注册接口只回用户信息）：少一步操作，也避免"注册成功但没登上"的困惑。
     */
    fun register() = viewModelScope.launch {
        val state = _ui.value
        val base = normalizeOrToast(state.baseUrl) ?: return@launch
        val username = state.username.trim()
        val password = state.password
        if (username.isEmpty() || password.isEmpty()) {
            return@launch toast("请先填写用户名和口令")
        }
        runBusy("正在注册…") {
            val deviceId = store.deviceId()
            remote.register(
                base,
                SyncRegisterRequest(
                    username = username,
                    password = password,
                    deviceId = deviceId,
                    deviceName = Build.MODEL,
                ),
            )
            val dto = remote.login(
                base,
                SyncLoginRequest(
                    username = username,
                    password = password,
                    deviceId = deviceId,
                    deviceName = Build.MODEL,
                    platform = "android",
                ),
            )
            store.saveSession(
                baseUrl = base,
                userId = dto.user.userId,
                username = dto.user.username,
                accessToken = dto.accessToken,
                refreshToken = dto.refreshToken,
                expiresInSeconds = dto.expiresIn,
            )
            _ui.update { it.copy(password = "") }
            toast("注册成功，已登录为 ${dto.user.username}")
        }
    }

    fun login() = viewModelScope.launch {
        val state = _ui.value
        val base = normalizeOrToast(state.baseUrl) ?: return@launch
        val username = state.username.trim()
        val password = state.password
        if (username.isEmpty() || password.isEmpty()) {
            return@launch toast("请先填写用户名和口令")
        }
        runBusy("正在登录…") {
            val deviceId = store.deviceId()
            val dto = remote.login(
                base,
                SyncLoginRequest(
                    username = username,
                    password = password,
                    deviceId = deviceId,
                    deviceName = Build.MODEL,
                    platform = "android",
                ),
            )
            store.saveSession(
                baseUrl = base,
                userId = dto.user.userId,
                username = dto.user.username,
                accessToken = dto.accessToken,
                refreshToken = dto.refreshToken,
                expiresInSeconds = dto.expiresIn,
            )
            _ui.update { it.copy(password = "") }
            toast("登录成功：${dto.user.username}")
        }
    }

    /**
     * 退出登录：**先尽力通知服务端撤销刷新令牌**（失败也照常清本地）。
     * 清本地会话**不动任何业务数据**——退出登录不是删数据。
     */
    fun logout() = viewModelScope.launch {
        runBusy("正在退出…") {
            val base = store.baseUrl()
            val refresh = store.refreshToken()
            if (base.isNotEmpty() && !refresh.isNullOrEmpty()) {
                try {
                    remote.logout(base, SyncLogoutRequest(refresh))
                } catch (e: Exception) {
                    // 网络不可达也要能退出：令牌本身留在服务端到过期为止，不影响本地隐私。
                }
            }
            store.clearSession()
            toast("已退出登录；本地数据未被改动")
        }
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
                baseUrl = account.baseUrl,
                username = account.username.ifEmpty { it.username },
                loggedIn = account.isLoggedIn,
                accountName = account.username,
                statusText = SyncPresenter.statusText(line),
                lastSyncAt = account.lastSyncAt,
                conflictCount = line.conflictCount,
            )
        }
    }

    /** 地址规范化：失败直接提示（不静默改动用户输入）。 */
    private fun normalizeOrToast(raw: String): String? {
        if (raw.isBlank()) {
            toast("请先填写服务器地址")
            return null
        }
        return try {
            SyncUrlValidator.normalize(raw, debuggable)
        } catch (e: SyncApiException) {
            toast("服务器地址无效：${e.userMessage()}")
            null
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
