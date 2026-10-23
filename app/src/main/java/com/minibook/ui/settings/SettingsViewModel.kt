package com.minibook.ui.settings

import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minibook.data.local.datastore.DarkModeSetting
import com.minibook.data.local.datastore.LockTimeout
import com.minibook.data.local.datastore.SettingsDataStore
import com.minibook.data.local.mail.MailAccountStore
import com.minibook.data.local.security.AppLockController
import com.minibook.data.local.security.BiometricHelper
import com.minibook.data.local.security.LockSwitchResult
import com.minibook.data.remote.SyncAuthStore
import com.minibook.domain.usecase.export.ExportAllUseCase
import com.minibook.domain.usecase.export.ImportAllUseCase
import com.minibook.domain.usecase.export.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页 ViewModel（TASK-027，UI §2 P-06、ARCHITECTURE §9/§10/§11、DEC-002/DEC-003/DEC-005）。
 *
 * 暴露设置项状态流（暗色模式 / 应用锁开关 / 锁定时长 / 加密态只读标记，均来自
 * [SettingsDataStore]，`stateIn` WhileSubscribed(5_000) 软切换），并承载以下业务动作：
 *
 * - 暗色模式三态：[setDarkMode] 写 DataStore，经 TASK-019 ThemeViewModel 即时生效（F-09）。
 * - 应用锁开关：[setAppLockEnabled]——开启前先检 [BiometricHelper.isDeviceSecure]（DEC-003，
 *   设备无系统锁屏时不开启、暴露 [needDeviceSecure] 引导事件）；开启走
 *   [AppLockController.upgradeToAuthKey]（DEC-002，blob 重加密为 auth 包裹后落库）；关闭走
 *   [AppLockController.downgradeToPlainKey]（DEC-002，blob 重加密为 plain 包裹后落库，
 *   关锁不降级明文，数据仍保持加密）。
 * - 锁定时长五档：[setLockTimeout] 写 DataStore（TASK-017 闲置重锁计时消费）。
 * - 导出：[export] 生成明文备份文件（DEC-005，`filesDir`），SAF 目标 Uri 写入由 UI 层完成
 *   （[ActivityResultContracts.CreateDocument] + `contentResolver.openOutputStream` 复制）。
 *   忙碌态由 [setBusy] 包裹（配合 `FullScreenBusyOverlay` 锁数据入口）。
 * - 导入：[importFromUri] 读 Uri 内容 → [ImportAllUseCase] 严格校验（失败零落库）→
 *   [ImportResult] 落 [importResult] 并以 [snackbar] 播报结果。
 *
 * 统一轻提示通道 [snackbar]：导出/导入结果、开/关锁结果均由 VM 写入，UI collect 后经
 * `MiniBookSnackbarHost` 展示并 [clearSnackbar]。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val appLockController: AppLockController,
    private val biometricHelper: BiometricHelper,
    private val exportAll: ExportAllUseCase,
    private val importAll: ImportAllUseCase,
    private val mailAccountStore: MailAccountStore,
    private val syncAuthStore: SyncAuthStore,
) : ViewModel() {

    /** 暗色模式三态（默认跟随系统）。 */
    val darkMode: StateFlow<DarkModeSetting> = settingsDataStore.darkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DarkModeSetting.SYSTEM)

    /** 应用锁开关（默认关）。 */
    val appLockEnabled: StateFlow<Boolean> = settingsDataStore.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * 设置首页「账号」行的一句话状态（DEC-038）：未登录 / 已登录为谁。
     *
     * 只读 [SyncAuthStore] 的账号态（**不揣明文令牌**），登录态变化会自动反映到首页。
     */
    val accountSummary: StateFlow<String> = syncAuthStore.state
        .map { if (it.isLoggedIn) "已登录：${it.username}" else "未登录 · 点此登录云端账号" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "未登录 · 点此登录云端账号")

    /** 锁闲置超时五档（默认 1 分钟）。 */
    val lockTimeout: StateFlow<LockTimeout> = settingsDataStore.lockTimeout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockTimeout.ONE_MINUTE)

    /** 加密态只读标记（SQLCipher + Keystore 已启用，恒 true，F-10 不可关）。 */
    val encryptionEnabled: StateFlow<Boolean> = settingsDataStore.encryptionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** 自定义主题色（ARGB hex），无值 null = 跟随默认/动态取色。 */
    val themeColor: StateFlow<String?> = settingsDataStore.themeColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 已配置邮箱凭据账号数（F-13 多账号：设置页「邮箱凭据」行尾部「已配置 N 个」）。
     * 初值取 Store 当前值（构造时已同步读盘），后续随 upsert/delete 增删联动。
     */
    val mailAccountCount: StateFlow<Int> = mailAccountStore.accounts
        .map { it.size }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            mailAccountStore.accounts.value.size,
        )

    private val _isBusy = MutableStateFlow(false)

    /** 导出/导入忙碌态；true 时 UI 层展示 [FullScreenBusyOverlay] 锁数据入口。 */
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)

    /** 统一轻提示通道：非 null 即待展示，UI 展示后调 [clearSnackbar]。 */
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    private val _importResult = MutableStateFlow<ImportResult?>(null)

    /** 最近一次导入结果（Success/Failure）；Failure 携带原因且**零落库**（API §11.3）。 */
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    private val _needDeviceSecure = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 设备无系统锁屏引导事件（DEC-003）：开启应用锁前检测失败时发射，UI 展示 GuidanceCard。 */
    val needDeviceSecure: SharedFlow<Unit> = _needDeviceSecure.asSharedFlow()

    private val _needBiometric = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * 未录入生物识别引导事件（F-16j，2026-10-13）。
     *
     * 只设了系统锁屏（PIN/图案/密码）却没有录入指纹/人脸的设备，Keystore 生成 auth-gated key
     * 会抛 `InvalidAlgorithmParameterException: At least one biometric must be enrolled …`，
     * 旧实现直接崩在主线程。开启应用锁前用 [BiometricHelper.hasEnrolledBiometric] 分诊，
     * 不满足时发本事件由 UI 引导去系统设置录入，**不启用、不降级明文**。
     */
    val needBiometric: SharedFlow<Unit> = _needBiometric.asSharedFlow()

    /** 清除待展示轻提示。 */
    fun clearSnackbar() {
        _snackbar.value = null
    }

    /** 清除最近导入结果。 */
    fun clearImportResult() {
        _importResult.value = null
    }

    /** 设置忙碌态（UI 层在导出/导入操作前后包裹，配合 [FullScreenBusyOverlay]）。 */
    fun setBusy(busy: Boolean) {
        _isBusy.value = busy
    }

    /** 写轻提示（UI 层复制备份到 SAF Uri 等场景复用统一 snackbar 通道）。 */
    fun showSnackbar(message: String) {
        _snackbar.value = message
    }

    /** 写暗色模式三态（软切换，TASK-019 Theme 即时生效，F-09）。 */
    fun setDarkMode(setting: DarkModeSetting) {
        viewModelScope.launch { settingsDataStore.setDarkMode(setting) }
    }

    /** 写锁闲置超时五档（TASK-017 消费并驱动闲置重锁计时）。 */
    fun setLockTimeout(timeout: LockTimeout) {
        viewModelScope.launch { settingsDataStore.setLockTimeout(timeout) }
    }

    /** 写自定义主题色；null 清除恢复默认（软切换，Theme 即时生效）。 */
    fun setThemeColor(hex: String?) {
        viewModelScope.launch { settingsDataStore.setThemeColor(hex) }
    }

    /**
     * 切换应用锁开关。
     *
     * 开启（true）：先检 [BiometricHelper.isDeviceSecure]（DEC-003）——设备已设系统锁屏才走
     * [AppLockController.upgradeToAuthKey] 将 master key blob 重加密为 auth 包裹（DEC-002，
     * 认证后落盘；开启锁前必须确保 blob 最终为 auth 包裹，否则下次解锁用 auth key 解 plain
     * 包裹密文会 GCM 失败）；成功才落库开启，失败播报原因。设备无系统锁屏时不开启，仅发射
     * [needDeviceSecure] 引导事件由 UI 展示引导卡跳系统安全设置。
     *
     * 关闭（false）：经 [AppLockController.downgradeToPlainKey] 将 master key blob 重加密为
     * plain 包裹（DEC-002，关锁不降级明文，数据仍保持加密）；成功后落库关闭，失败播报原因。
     *
     * @param activity 当前 [FragmentActivity]（BiometricPrompt 宿主；开/关路径均需要）。
     */
    fun setAppLockEnabled(enabled: Boolean, activity: FragmentActivity?) {
        if (_isBusy.value) return
        if (enabled) {
            // DEC-003：设备无系统锁屏时不开锁（不降级明文），暴露引导事件。
            if (!biometricHelper.isDeviceSecure()) {
                _needDeviceSecure.tryEmit(Unit)
                return
            }
            // F-16j（2026-10-13）：只设了系统锁屏、未录入生物识别的设备，Keystore 生成
            // 「每次使用都要认证」的 key 会抛 InvalidAlgorithmParameterException（主线程未捕获 ⇒
            // 崩溃，模拟器实测）。开启前先分诊，缺生物识别时只引导、不启用。
            if (!biometricHelper.hasEnrolledBiometric()) {
                _needBiometric.tryEmit(Unit)
                return
            }
            // DEC-002：开启锁须先把 blob 重加密为 auth 包裹（认证后），成功才落库开启；
            // 与关闭路径的 downgrade 编排对称——写标志本身不回滚，但开启前 blob 必为 auth 包裹。
            val host = activity
            if (host == null) {
                _snackbar.value = "开启失败：缺少页面上下文"
                return
            }
            appLockController.upgradeToAuthKey(host) { result ->
                when (result) {
                    LockSwitchResult.SUCCESS -> {
                        viewModelScope.launch {
                            settingsDataStore.setAppLockEnabled(true)
                            _snackbar.value = "应用锁已开启，下次进入应用需生物识别解锁"
                        }
                    }
                    LockSwitchResult.AUTH_FAILED ->
                        _snackbar.value = "开启失败：用户取消或认证未通过"
                    LockSwitchResult.REWRAP_FAILED ->
                        _snackbar.value = "开启失败：重包裹失败，请重试"
                }
            }
        } else {
            // DEC-002：关锁须先把 blob 重加密为 plain 包裹（认证后），成功才落库关闭。
            val host = activity
            if (host == null) {
                _snackbar.value = "关闭失败：缺少页面上下文"
                return
            }
            appLockController.downgradeToPlainKey(host) { result ->
                when (result) {
                    LockSwitchResult.SUCCESS -> {
                        viewModelScope.launch {
                            settingsDataStore.setAppLockEnabled(false)
                            _snackbar.value = "应用锁已关闭，数据仍保持加密"
                        }
                    }
                    LockSwitchResult.AUTH_FAILED ->
                        _snackbar.value = "关闭失败：用户取消或认证未通过"
                    LockSwitchResult.REWRAP_FAILED ->
                        _snackbar.value = "关闭失败：重包裹失败，请重试"
                }
            }
        }
    }

    /**
     * 生成明文备份文件（DEC-005，`filesDir` 下 `minibook_backup_yyyyMMdd_HHmmss.json`）。
     *
     * 导出流程第一步；SAF 目标 Uri 的写入由 UI 层完成——UI 以
     * [ActivityResultContracts.CreateDocument] 取 Uri 后 `contentResolver.openOutputStream`
     * 复制本文件内容。忙碌态由 UI 层 [setBusy] 包裹（覆盖「生成 + 复制」全程）。
     *
     * @return 已写入的明文备份 [File]。
     */
    suspend fun export(): File = exportAll()

    /**
     * 从 SAF Uri 全量导入。
     *
     * UI 层经 [ActivityResultContracts.OpenDocument] 取 Uri 并经强确认（danger）后调用本方法；
     * VM 读 Uri 内容（[Dispatchers.IO]）→ [ImportAllUseCase] 严格校验（失败零落库，API §11.3）
     * → 结果落 [importResult] 并播报 [snackbar]（Success 各表行数 / Failure 原因）。
     *
     * @param activity 当前 [FragmentActivity]（提供 `contentResolver` 读 Uri）。
     * @param uri SAF 目标备份文件 Uri。
     */
    fun importFromUri(activity: FragmentActivity, uri: Uri) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val json = withContext(Dispatchers.IO) {
                    activity.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes().toString(Charsets.UTF_8)
                    }
                }
                if (json == null) {
                    _snackbar.value = "读取备份文件失败"
                    return@launch
                }
                val result = importAll(json)
                _importResult.value = result
                _snackbar.value = when (result) {
                    is ImportResult.Success -> "导入成功：账本 ${result.ledgers} 个、分类 ${result.categories} 个、交易 ${result.transactions} 笔、债务 ${result.debts} 笔"
                    is ImportResult.Failure -> "导入失败：${result.reason}"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbar.value = "导入失败：${e.message}"
            } finally {
                _isBusy.value = false
            }
        }
    }
}
