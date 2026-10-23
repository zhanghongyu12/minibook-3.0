package com.minibook.ui.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.BuildConfig
import com.minibook.data.remote.SyncApiException
import com.minibook.data.remote.SyncAuthStore
import com.minibook.data.remote.SyncDeleteAccountRequest
import com.minibook.data.remote.SyncLogoutRequest
import com.minibook.data.remote.SyncProfileUpdateRequest
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
 * 「账号」页 ViewModel（2026-10-12；第二轮改造）。
 *
 * 第二轮变化（用户要求）：
 * 1. **登录 / 注册各自独立成页**（见 [AuthViewModel] + `LoginScreen` / `RegisterScreen`），
 *    本 VM 只负责「已登录后的资料与会话」；
 * 2. 资料字段扩充为：昵称 / 邮箱 / 个性签名 / 地区（可改）+ 用户名 / 注册时间 / 上次登录（只读）；
 *    2026-10-13 起账号页不再展示账号 ID（用户要求）。
 * 3. 云同步入口只保留在「数据」页，账号页不再重复放一个。
 *
 * 三条不能破的约束：
 * 1. **服务地址不暴露给用户**：取已保存地址 > `BuildConfig.SYNC_BASE_URL`（按构建类型内置）；
 *    正式版界面既不显示也不可改，调试版的临时改指向在登录页。
 * 2. **口令只活在内存**：每次操作后立即清空，绝不落盘（落盘的只有 Keystore 加密后的令牌）。
 * 3. **退出 / 注销只动云端会话**：本地数据一条不动。
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val store: SyncAuthStore,
    private val remote: SyncRemote,
    @ApplicationContext context: Context,
) : ViewModel() {

    /** 调试版允许 http（内网自测）；正式版强制 https。按真实 flag 判定，不看构建类型。 */
    private val debuggable: Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    data class UiState(
        val loggedIn: Boolean = false,
        val accountName: String = "",
        val userId: String = "",
        val displayName: String = "",
        val nickname: String = "",
        val email: String = "",
        val bio: String = "",
        val region: String = "",
        val createdAt: String = "",
        val lastLoginAt: String = "",
        // —— 「编辑资料」页草稿（每次拉到资料后同步为当前值）——
        val nicknameDraft: String = "",
        val emailDraft: String = "",
        val bioDraft: String = "",
        val regionDraft: String = "",
        // —— 改口令 / 退出 / 注销 ——
        val oldPassword: String = "",
        val newPassword: String = "",
        val deletePassword: String = "",
        val dialog: Dialog? = null,
        val busy: Boolean = false,
        val busyText: String = "处理中…",
        val message: String? = null,
        /** 资料保存成功次数：编辑页据此自动返回。 */
        val savedTick: Int = 0,
    )

    /** 当前打开的对话框（互斥，避免叠窗）。 */
    enum class Dialog { PASSWORD, LOGOUT, DELETE }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch { refreshAccount() }
    }

    /** 页面每次进入都刷一次：登录 / 注册在独立页面完成后，返回这里要能看到新资料。 */
    fun refresh() = viewModelScope.launch { refreshAccount() }

    // ---------- 表单 ----------

    fun onNicknameDraftChange(value: String) = _ui.update { it.copy(nicknameDraft = value) }

    fun onEmailDraftChange(value: String) = _ui.update { it.copy(emailDraft = value) }

    fun onBioDraftChange(value: String) = _ui.update { it.copy(bioDraft = value) }

    fun onRegionDraftChange(value: String) = _ui.update { it.copy(regionDraft = value) }

    fun onOldPasswordChange(value: String) = _ui.update { it.copy(oldPassword = value) }

    fun onNewPasswordChange(value: String) = _ui.update { it.copy(newPassword = value) }

    fun onDeletePasswordChange(value: String) = _ui.update { it.copy(deletePassword = value) }

    fun clearMessage() = _ui.update { it.copy(message = null) }

    fun openDialog(dialog: Dialog) = _ui.update {
        it.copy(dialog = dialog, oldPassword = "", newPassword = "", deletePassword = "")
    }

    fun closeDialog() = _ui.update {
        it.copy(dialog = null, oldPassword = "", newPassword = "", deletePassword = "")
    }

    // ---------- 资料 ----------

    /**
     * 保存资料：**只提交真正改动过的字段**（null = 不改，空串 = 清除）。
     *
     * `SyncProfileUpdateRequest` 的 null 字段因 `explicitNulls = false` 不会上线，
     * 服务端按「字段是否出现」判定 ⇒ 改邮箱不会顺手清掉个性签名。
     */
    fun saveProfile() = viewModelScope.launch {
        val state = _ui.value
        SyncPresenter.profileError(state.emailDraft)?.let { reason -> return@launch toast(reason) }
        val nickname = state.nicknameDraft.trim()
        val email = state.emailDraft.trim()
        val bio = state.bioDraft.trim()
        val region = state.regionDraft.trim()
        val request = SyncProfileUpdateRequest(
            nickname = nickname.takeIf { it != state.nickname },
            email = email.takeIf { it != state.email },
            bio = bio.takeIf { it != state.bio },
            region = region.takeIf { it != state.region },
        )
        if (request.nickname == null && request.email == null &&
            request.bio == null && request.region == null
        ) {
            return@launch toast("资料没有改动")
        }
        val base = resolveBaseUrl() ?: return@launch
        val token = store.accessToken() ?: return@launch toast("登录已失效，请重新登录")
        runBusy("正在保存…") {
            val user = remote.updateProfile(base, token, request).user
            _ui.update {
                it.copy(
                    nickname = user.nickname.orEmpty(),
                    email = user.email.orEmpty(),
                    bio = user.bio.orEmpty(),
                    region = user.region.orEmpty(),
                    displayName = SyncPresenter.accountDisplayName(user.nickname, user.username),
                    savedTick = it.savedTick + 1,
                )
            }
            // 昵称改了立刻同步到本地缓存，首页那行下一次组合就是新名字。
            store.updateNickname(user.nickname.orEmpty())
            toast("资料已保存")
        }
    }

    fun changePassword() = viewModelScope.launch {
        val state = _ui.value
        SyncPresenter.passwordChangeError(state.oldPassword, state.newPassword)?.let { reason ->
            return@launch toast(reason)
        }
        val base = resolveBaseUrl() ?: return@launch
        val token = store.accessToken() ?: return@launch toast("登录已失效，请重新登录")
        runBusy("正在修改口令…") {
            val result = remote.updateProfile(
                base,
                token,
                SyncProfileUpdateRequest(
                    oldPassword = state.oldPassword,
                    newPassword = state.newPassword,
                ),
            )
            // 服务端改口令时会撤销**全部**旧刷新令牌并换发新令牌：必须立刻覆盖本地，
            // 否则下一次 401 刷新会拿着刚被撤销的令牌去换，人被莫名登出。
            result.tokens?.let { store.updateTokens(it.accessToken, it.refreshToken, it.expiresIn) }
            closeDialog()
            toast("口令已修改；其它设备需重新登录")
        }
    }

    // ---------- 退出 / 注销 ----------

    /**
     * 退出登录：**先尽力通知服务端撤销刷新令牌**（失败也照常清本地）。
     * 清本地会话**不动任何业务数据**——退出登录不是删数据。
     */
    fun logout() = viewModelScope.launch {
        runBusy("正在退出…") {
            val base = store.baseUrl().ifEmpty { BuildConfig.SYNC_BASE_URL }
            val refresh = store.refreshToken()
            if (base.isNotEmpty() && !refresh.isNullOrEmpty()) {
                try {
                    remote.logout(base, SyncLogoutRequest(refresh))
                } catch (e: Exception) {
                    // 网络不可达也要能退出：令牌留在服务端到过期为止，不影响本地隐私。
                }
            }
            store.clearSession()
            closeDialog()
            toast("已退出登录；本地数据未被改动")
        }
    }

    /** 注销账号：删云端账号与全部云端副本；**本地数据保留**（用户手机上的账目不受影响）。 */
    fun deleteAccount() = viewModelScope.launch {
        val password = _ui.value.deletePassword
        if (password.isEmpty()) {
            return@launch toast("请输入口令以确认注销")
        }
        val base = resolveBaseUrl() ?: return@launch
        val token = store.accessToken() ?: return@launch toast("登录已失效，请重新登录")
        runBusy("正在注销账号…") {
            remote.deleteAccount(base, token, SyncDeleteAccountRequest(password))
            store.clearSession()
            closeDialog()
            toast("账号已注销，云端数据已删除；本地数据保留")
        }
    }

    // ---------- 内部 ----------

    private suspend fun refreshAccount() {
        val account = store.current()
        _ui.update {
            it.copy(
                loggedIn = account.isLoggedIn,
                accountName = account.username,
                displayName = SyncPresenter.accountDisplayName(it.nickname, account.username),
            )
        }
        if (!account.isLoggedIn) return
        // 资料只有服务端有；拉不到（离线）就保留本地值，不打扰用户。
        try {
            val dto = remote.me(store.baseUrl(), store.accessToken().orEmpty())
            _ui.update {
                it.copy(
                    accountName = dto.username,
                    userId = dto.userId,
                    nickname = dto.nickname.orEmpty(),
                    email = dto.email.orEmpty(),
                    bio = dto.bio.orEmpty(),
                    region = dto.region.orEmpty(),
                    createdAt = dto.createdAt.orEmpty(),
                    lastLoginAt = dto.lastLoginAt.orEmpty(),
                    displayName = SyncPresenter.accountDisplayName(dto.nickname, dto.username),
                    // 草稿与资料同步：进「编辑资料」页时就是当前值
                    nicknameDraft = dto.nickname.orEmpty(),
                    emailDraft = dto.email.orEmpty(),
                    bioDraft = dto.bio.orEmpty(),
                    regionDraft = dto.region.orEmpty(),
                )
            }
            // 把服务端昵称缓存到 SyncAuthStore：首页顶部「登录信息」行据此显示昵称，
            // 且**不需要为了显示名字而发网络请求**（2026-10-13 用户要求）。
            store.updateNickname(dto.nickname.orEmpty())
        } catch (e: Exception) {
            // 静默：离线也要能看账号信息、能退出登录
        }
    }

    /**
     * 解析服务地址：**用户不需要知道也不需要填**。
     *
     * 优先级 = 已保存地址 > `BuildConfig.SYNC_BASE_URL`（构建类型内置）。
     * 两者都空 ⇒ 明确提示「本安装包未内置服务器地址」，绝不静默失败。
     */
    private suspend fun resolveBaseUrl(): String? {
        val raw = store.baseUrl().ifEmpty { BuildConfig.SYNC_BASE_URL }
        if (raw.isBlank()) {
            toast("本安装包未内置服务器地址，请更新到正式版本")
            return null
        }
        return try {
            SyncUrlValidator.normalize(raw, debuggable)
        } catch (e: SyncApiException) {
            toast("服务器地址无效：${e.userMessage()}")
            null
        }
    }

    private companion object {
        const val TAG = "MiniBookAccount"
    }

    private fun toast(message: String) = _ui.update { it.copy(message = message) }

    /** 统一忙碌态 + 统一异常文案（网络/鉴权/其他三类分开说）。 */
    private suspend fun runBusy(text: String, block: suspend () -> Unit) {
        _ui.update { it.copy(busy = true, busyText = text) }
        try {
            block()
            refreshAccount()
        } catch (e: SyncApiException) {
            if (BuildConfig.DEBUG) Log.w(TAG, "账号操作失败（已分类）：$text", e)
            toast(SyncPresenter.authErrorMessage(e.code, e.userMessage()))
        } catch (e: Exception) {
            // 未分类异常（如平台限制）只有类名，看不出在哪一步：调试版把栈打进 logcat 便于定位。
            if (BuildConfig.DEBUG) Log.w(TAG, "账号操作失败：$text", e)
            toast(e.message ?: "操作失败：${e::class.simpleName}")
        } finally {
            _ui.update { it.copy(busy = false) }
        }
    }
}
