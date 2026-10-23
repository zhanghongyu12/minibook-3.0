package com.minibook.ui.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.BuildConfig
import com.minibook.data.remote.SyncApiException
import com.minibook.data.remote.SyncAuthStore
import com.minibook.data.remote.SyncLoginRequest
import com.minibook.data.remote.SyncRegisterRequest
import com.minibook.data.remote.SyncRemote
import com.minibook.data.remote.SyncUrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 登录 / 注册页 ViewModel（2026-10-12 第二轮：从「账号」页拆出的独立页面）。
 *
 * 为什么要拆：账号页原来的内嵌表单既不像 App 的登录页（没有独立页面、没有主按钮层级、
 * 错误只能靠转瞬即逝的 toast），又和资料展示混在一屏。现在登录与注册各自一页：
 * 大标题 + 大输入框 + 主按钮 + 页面内错误文案，成功后自动返回账号页。
 *
 * 与 [AccountViewModel] 的分工：本 VM **只管认证**（登录 / 注册），
 * 登录成功即写会话，剩下的资料展示与修改归账号页。
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val store: SyncAuthStore,
    private val remote: SyncRemote,
    @ApplicationContext context: Context,
) : ViewModel() {

    /** 调试版允许 http（内网自测）；正式版强制 https。按真实 flag 判定，不看构建类型。 */
    private val debuggable: Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** 只有调试版显示「服务器地址」输入框（正式版用户不该看到地址）。 */
    val showAddressField: Boolean = BuildConfig.DEBUG

    data class UiState(
        val baseUrl: String = "",
        val username: String = "",
        val password: String = "",
        val confirm: String = "",
        /** 注册页协议勾选（TASK-076）：**默认不勾**（合规硬项），未勾选时注册被本地拦下。 */
        val agreed: Boolean = false,
        val busy: Boolean = false,
        /** 页面内的错误文案（比 toast 更好读：不会消失、就在按钮上方）。 */
        val error: String? = null,
        /** 认证成功一次 +1：页面据此自动返回。 */
        val doneTick: Int = 0,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        // 少打字：预填上次用的用户名；调试版顺带预填上次的服务器地址。
        viewModelScope.launch {
            val account = store.current()
            val saved = store.baseUrl()
            _ui.update {
                it.copy(
                    username = account.username,
                    baseUrl = it.baseUrl.ifEmpty { saved },
                )
            }
        }
    }

    // ---------- 表单 ----------

    fun onBaseUrlChange(value: String) = _ui.update { it.copy(baseUrl = value, error = null) }

    fun onUsernameChange(value: String) = _ui.update { it.copy(username = value, error = null) }

    fun onPasswordChange(value: String) = _ui.update { it.copy(password = value, error = null) }

    fun onConfirmChange(value: String) = _ui.update { it.copy(confirm = value, error = null) }

    /** 注册页协议勾选（TASK-076）。取消勾选不清错误——用户可能正要看清那句提示。 */
    fun onAgreedChange(value: Boolean) = _ui.update { it.copy(agreed = value, error = null) }

    fun clearError() = _ui.update { it.copy(error = null) }

    // ---------- 提交 ----------

    fun login() = submit(register = false)

    fun register() = submit(register = true)

    /**
     * 注册 = 注册 + 顺带登录（注册接口只回用户信息），少一步操作，
     * 也避免「注册成功但没登上」的困惑。
     */
    private fun submit(register: Boolean) = viewModelScope.launch {
        val state = _ui.value
        if (state.busy) return@launch
        val username = state.username.trim()
        val password = state.password
        val invalid = if (register) {
            SyncPresenter.registerError(username, password)
                ?: SyncPresenter.confirmError(password, state.confirm)
                ?: SyncPresenter.agreementError(state.agreed)
        } else {
            SyncPresenter.loginError(username, password)
        }
        if (invalid != null) {
            _ui.update { it.copy(error = invalid) }
            return@launch
        }
        val base = resolveBaseUrl() ?: return@launch

        _ui.update { it.copy(busy = true, error = null) }
        try {
            val deviceId = store.deviceId()
            if (register) {
                remote.register(
                    base,
                    SyncRegisterRequest(
                        username = username,
                        password = password,
                        deviceId = deviceId,
                        deviceName = Build.MODEL,
                    ),
                )
            }
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
            _ui.update { it.copy(busy = false, password = "", confirm = "", doneTick = it.doneTick + 1) }
        } catch (e: SyncApiException) {
            if (BuildConfig.DEBUG) Log.w(TAG, "认证失败（已分类）：register=$register", e)
            _ui.update { it.copy(busy = false, error = SyncPresenter.authErrorMessage(e.code, e.userMessage())) }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "认证失败：register=$register", e)
            _ui.update {
                it.copy(
                    busy = false,
                    error = "操作失败：${e.message ?: e::class.simpleName}（请检查服务器地址与网络）",
                )
            }
        }
    }

    /**
     * 解析服务地址：**用户不需要知道也不需要填**。
     *
     * 优先级 = 调试版输入框（仅调试版可见）> 已保存地址 > `BuildConfig.SYNC_BASE_URL`（构建类型内置）。
     * 三者都空 ⇒ 明确提示，绝不静默失败。
     */
    private suspend fun resolveBaseUrl(): String? {
        val typed = _ui.value.baseUrl.trim().takeIf { showAddressField }.orEmpty()
        val raw = typed.ifEmpty { store.baseUrl() }.ifEmpty { BuildConfig.SYNC_BASE_URL }
        if (raw.isBlank()) {
            _ui.update { it.copy(error = "本安装包未内置服务器地址，请更新到正式版本") }
            return null
        }
        return try {
            SyncUrlValidator.normalize(raw, debuggable)
        } catch (e: SyncApiException) {
            _ui.update { it.copy(error = "服务器地址无效：${e.userMessage()}") }
            null
        }
    }

    private companion object {
        const val TAG = "MiniBookAuth"
    }
}
